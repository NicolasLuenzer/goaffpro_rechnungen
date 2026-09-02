import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Vollständige Datensicherung und Wiederherstellung: packt Datenbank, heruntergeladene Dateien,
 * erzeugte Belege und beide Einstellungsdateien in ein ZIP und spielt sie auf einer anderen
 * Umgebung wieder ein.
 *
 * Bewusst als eigene Klasse ohne statischen Zustand: alle Speicherorte kommen als Parameter
 * herein, dadurch läuft der komplette Round-Trip in Tests gegen temporäre Verzeichnisse.
 */
public final class BackupService {

    static final int FORMAT_VERSION = 1;
    static final String MANIFEST_ENTRY = "manifest.json";
    static final String UI_SETTINGS_FILENAME = "goaffpro_ui_settings.properties";
    static final String DB_FILENAME = "goaffpro_sync.sqlite";

    /** Diese Schlüssel gelten als Zugangsdaten und fliegen ohne ausdrückliche Auswahl raus. */
    static final Set<String> SECRET_EXPORT_KEYS = Set.of("goaffproAPIKey", "smtpPassword", "smtpUsername");

    /** Umgebungsspezifisch - dürfen beim Import NIE vom Archiv übernommen werden. */
    static final List<String> PATH_KEYS = List.of("pdfExportPath", "goaffproSyncDataPath");

    static final List<String> COUNTER_KEYS =
            List.of("gutschriftCounter", "gutschriftCounterYear", "rechnungCounter", "rechnungCounterYear");

    static final String MAIL_LOG_KEY = "sentMailLogJson";
    static final List<String> MAIL_LOG_PATH_FIELDS =
            List.of("pdfPath", "jsonPath", "zugferdPath", "eInvoiceViewPdfPath");

    static final List<String> DB_TABLES = List.of(
            "sync_entities", "sync_runs", "sync_endpoint_stats",
            "sync_snapshots", "sync_diagnostic_runs", "sync_diagnostic_results");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long MAX_ENTRIES = 200_000L;
    private static final long MAX_TOTAL_UNCOMPRESSED = 5L * 1024 * 1024 * 1024;
    private static final long MAX_SINGLE_ENTRY = 2L * 1024 * 1024 * 1024;

    private BackupService() {
    }

    // ── Speicherorte ────────────────────────────────────────────────────────────

    public record BackupLocations(Path configFile, Path dataDir, Path exportDir) {
        public Path dbFile() { return dataDir.resolve(DB_FILENAME); }
        public Path fileDir() { return dataDir.resolve("goaffpro_files"); }
        public Path uiFile() { return exportDir.resolve(UI_SETTINGS_FILENAME); }
        public Path backupDir() { return dataDir.resolve("backups"); }
    }

    public interface ProgressSink {
        void phase(String key, String label);
        void progress(long doneBytes, long totalBytes);
        void note(String message);
    }

    /** Ergebnis eines Imports. `settings` muss vom Aufrufer persistiert werden. */
    public record ImportReport(Properties settings, int filePathsRebased, int filePathsMissing,
                               int filePathsForeign, int mailLogRebased, int mailLogDropped,
                               boolean countersTaken, boolean secretsIncluded, List<String> notes) {
    }

    // ── Export ──────────────────────────────────────────────────────────────────

    public static Path createArchive(BackupLocations src, boolean includeSecrets, Path targetZip,
                                     ProgressSink progress) throws Exception {
        Files.createDirectories(targetZip.getParent());
        Path part = targetZip.resolveSibling(targetZip.getFileName() + ".part");
        Files.deleteIfExists(part);

        note(progress, "vorbereiten", "Bestand wird ermittelt");
        long total = Math.max(1, byteSize(src.dbFile()) + treeSize(src.fileDir()) + treeSize(src.exportDir()));
        long[] done = {0};

        Path dbSnapshot = null;
        try {
            // Konsistenter DB-Snapshot: die laufende Datenbank kann nicht einfach kopiert werden.
            phase(progress, "datenbank-snapshot", "Datenbank wird gesichert");
            if (Files.exists(src.dbFile())) {
                Files.createDirectories(src.backupDir());
                dbSnapshot = Files.createTempFile(src.backupDir(), "snapshot-", ".sqlite");
                GoAffProSyncService.snapshotDatabase(src.dbFile(), dbSnapshot);
            }

            phase(progress, "einstellungen", "Einstellungen werden gelesen");
            Properties config = loadProperties(src.configFile());
            Properties ui = loadProperties(src.uiFile());
            Properties configOut = includeSecrets ? copyOf(config) : stripSecrets(config);
            Properties uiOut = includeSecrets ? copyOf(ui) : stripSecrets(ui);
            boolean anySecretPresent = SECRET_EXPORT_KEYS.stream()
                    .anyMatch(k -> notBlank(config.getProperty(k)) || notBlank(ui.getProperty(k)));

            try (OutputStream fileOut = Files.newOutputStream(part);
                 ZipOutputStream zip = new ZipOutputStream(fileOut, StandardCharsets.UTF_8)) {
                zip.setLevel(Deflater.BEST_SPEED);

                ObjectNode manifest = buildManifest(src, includeSecrets, anySecretPresent, config,
                        dbSnapshot, configOut, uiOut);
                writeEntry(zip, MANIFEST_ENTRY, MAPPER.writerWithDefaultPrettyPrinter()
                        .writeValueAsBytes(manifest));

                writeEntry(zip, "config/config.properties", propertiesToBytes(configOut, "GoAffPro config"));
                writeEntry(zip, "settings/" + UI_SETTINGS_FILENAME, propertiesToBytes(uiOut, "GoAffPro UI settings"));

                if (dbSnapshot != null) {
                    phase(progress, "datenbank", "Datenbank wird gepackt");
                    copyFileIntoZip(zip, "db/" + DB_FILENAME, dbSnapshot, progress, done, total);
                }

                phase(progress, "dateien", "Heruntergeladene Dateien werden gepackt");
                addTree(zip, "files/", src.fileDir(), null, progress, done, total);

                phase(progress, "belege", "Erzeugte Belege werden gepackt");
                // Die UI-Einstellungen liegen physisch im Belegordner. Sie werden hier
                // ausgelassen, sonst läge die UNGEFILTERTE Fassung im Archiv.
                addTree(zip, "exports/", src.exportDir(), UI_SETTINGS_FILENAME, progress, done, total);
            }

            Files.move(part, targetZip, StandardCopyOption.REPLACE_EXISTING);
            phase(progress, "fertig", "Sicherung erstellt");
            return targetZip;
        } catch (Exception e) {
            Files.deleteIfExists(part);
            throw e;
        } finally {
            if (dbSnapshot != null) Files.deleteIfExists(dbSnapshot);
        }
    }

    private static ObjectNode buildManifest(BackupLocations src, boolean includeSecrets,
                                            boolean anySecretPresent, Properties config,
                                            Path dbSnapshot, Properties configOut, Properties uiOut)
            throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("formatVersion", FORMAT_VERSION);
        root.put("createdAt", Instant.now().toString());

        ObjectNode source = root.putObject("source");
        source.put("osName", System.getProperty("os.name", ""));
        source.put("fileSeparator", java.io.File.separator);
        source.put("configPath", src.configFile().toAbsolutePath().toString());
        source.put("dataDir", src.dataDir().toAbsolutePath().toString());
        source.put("fileDir", src.fileDir().toAbsolutePath().toString());
        source.put("pdfExportPath", src.exportDir().toAbsolutePath().toString());

        ObjectNode contents = root.putObject("contents");
        contents.put("database", dbSnapshot != null);
        contents.put("files", Files.isDirectory(src.fileDir()));
        contents.put("exports", Files.isDirectory(src.exportDir()));
        contents.put("secrets", includeSecrets ? (anySecretPresent ? "included" : "none-present") : "stripped");

        // Zählerstände gehören sichtbar ins Manifest: ihr Verlust ist der einzige irreversible
        // Schaden. Fehlende Zähler werden ausgelassen - niemals als 0 geschrieben.
        ObjectNode counters = root.putObject("counters");
        for (String key : COUNTER_KEYS) {
            String v = config.getProperty(key);
            if (notBlank(v)) counters.put(key, v.trim());
        }

        ObjectNode database = root.putObject("database");
        if (dbSnapshot != null) {
            database.put("byteSize", Files.size(dbSnapshot));
            ObjectNode tables = database.putObject("tables");
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbSnapshot.toAbsolutePath())) {
                for (String table : DB_TABLES) {
                    ArrayNode cols = tables.putArray(table);
                    for (String col : columnsOf(c, table)) cols.add(col);
                }
                database.put("entityCount", scalar(c, "SELECT COUNT(*) FROM sync_entities"));
                database.put("filePathRows",
                        scalar(c, "SELECT COUNT(*) FROM sync_entities WHERE file_path IS NOT NULL AND file_path <> ''"));
            }
        }

        ObjectNode stats = root.putObject("stats");
        stats.put("fileBytes", treeSize(src.fileDir()));
        stats.put("exportBytes", treeSize(src.exportDir()));
        ArrayNode loose = stats.putArray("exportLooseFiles");
        if (Files.isDirectory(src.exportDir())) {
            try (var s = Files.list(src.exportDir())) {
                s.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .filter(n -> !n.equals(UI_SETTINGS_FILENAME))
                        .sorted()
                        .forEach(loose::add);
            }
        }

        ObjectNode checksums = root.putObject("checksums");
        if (dbSnapshot != null) checksums.put("db/" + DB_FILENAME, sha256(Files.readAllBytes(dbSnapshot)));
        checksums.put("config/config.properties", sha256(propertiesToBytes(configOut, "GoAffPro config")));
        checksums.put("settings/" + UI_SETTINGS_FILENAME, sha256(propertiesToBytes(uiOut, "GoAffPro UI settings")));
        return root;
    }

    // ── Manifest lesen ──────────────────────────────────────────────────────────

    /** Liest nur das Manifest; bricht ab, sobald es gefunden ist (schnell auch bei 100 MB). */
    public static ObjectNode readManifest(Path zip) throws Exception {
        try (InputStream in = Files.newInputStream(zip);
             ZipInputStream zis = new ZipInputStream(in, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (MANIFEST_ENTRY.equals(entry.getName())) {
                    JsonNode node = MAPPER.readTree(zis.readAllBytes());
                    if (!node.isObject()) break;
                    return (ObjectNode) node;
                }
                zis.closeEntry();
            }
        }
        throw new IOException("Die Datei ist kein GoAffPro-Sicherungsarchiv (manifest.json fehlt).");
    }

    static void validateManifest(ObjectNode manifest) throws IOException {
        int version = manifest.path("formatVersion").asInt(0);
        if (version > FORMAT_VERSION) {
            throw new IOException("Das Archiv wurde mit einer neueren Programmversion erstellt (Format "
                    + version + "). Bitte zuerst diese Umgebung aktualisieren.");
        }
        if (version < 1) {
            throw new IOException("Archivformat wird nicht unterstützt.");
        }
    }

    // ── Import ──────────────────────────────────────────────────────────────────

    public static ImportReport applyArchive(Path zip, BackupLocations dst, Path stagingDir,
                                            ProgressSink progress) throws Exception {
        ObjectNode manifest = readManifest(zip);
        validateManifest(manifest);

        phase(progress, "entpacken", "Archiv wird entpackt");
        extractTo(zip, stagingDir, progress);
        verifyChecksums(manifest, stagingDir);
        verifySchema(manifest, stagingDir);

        List<String> notes = new ArrayList<>();

        phase(progress, "ersetzen", "Datenbestand wird ersetzt");
        Path stagedDb = stagingDir.resolve("db").resolve(DB_FILENAME);
        if (Files.exists(stagedDb)) {
            Files.createDirectories(dst.dataDir());
            Files.deleteIfExists(dst.dbFile());
            // Seitendateien des ALTEN Bestands entfernen, sonst liegt ein fremder WAL daneben.
            Files.deleteIfExists(dst.dataDir().resolve(DB_FILENAME + "-wal"));
            Files.deleteIfExists(dst.dataDir().resolve(DB_FILENAME + "-shm"));
            Files.move(stagedDb, dst.dbFile(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            notes.add("Das Archiv enthielt keine Datenbank - der vorhandene Bestand bleibt.");
        }

        Path stagedFiles = stagingDir.resolve("files");
        if (Files.isDirectory(stagedFiles)) {
            deleteRecursively(dst.fileDir());
            Files.createDirectories(dst.fileDir().getParent());
            moveTree(stagedFiles, dst.fileDir());
        }

        Path stagedExports = stagingDir.resolve("exports");
        if (Files.isDirectory(stagedExports)) {
            // Das Belegverzeichnis selbst NIE löschen - im Container ist es ein Bind-Mount.
            Files.createDirectories(dst.exportDir());
            clearDirectoryContents(dst.exportDir());
            try (var children = Files.list(stagedExports)) {
                for (Path child : children.toList()) {
                    moveTree(child, dst.exportDir().resolve(child.getFileName().toString()));
                }
            }
        }

        phase(progress, "pfade-anpassen", "Pfade werden auf diese Umgebung angepasst");
        char srcSep = manifest.path("source").path("fileSeparator").asText("/").charAt(0);
        String srcFileDir = manifest.path("source").path("fileDir").asText("");
        String srcExportDir = manifest.path("source").path("pdfExportPath").asText("");

        int[] fileStats = {0, 0, 0}; // rebased, missing, foreign
        if (Files.exists(dst.dbFile()) && notBlank(srcFileDir)) {
            rebaseEntityFilePaths(dst, srcFileDir, srcSep, fileStats);
        }

        phase(progress, "einstellungen", "Einstellungen werden übernommen");
        Properties archiveConfig = loadProperties(stagingDir.resolve("config").resolve("config.properties"));
        Properties archiveUi = loadProperties(stagingDir.resolve("settings").resolve(UI_SETTINGS_FILENAME));
        Properties current = loadProperties(dst.configFile());

        Properties merged = new Properties();
        merged.putAll(archiveConfig);
        merged.putAll(archiveUi); // UI-Einstellungen gewinnen, wie im laufenden Betrieb auch

        // Umgebungsspezifische Pfade auf die ZIELWERTE zwingen.
        merged.setProperty("pdfExportPath", dst.exportDir().toAbsolutePath().toString());
        merged.setProperty("goaffproSyncDataPath", dst.dataDir().toAbsolutePath().toString());

        boolean countersTaken = false;
        JsonNode counters = manifest.path("counters");
        for (String key : COUNTER_KEYS) {
            String fromManifest = counters.path(key).asText("");
            if (notBlank(fromManifest)) {
                merged.setProperty(key, fromManifest.trim());
                countersTaken = true;
            } else if (notBlank(current.getProperty(key))) {
                merged.setProperty(key, current.getProperty(key)); // Zielwert behalten
            } else {
                merged.remove(key);
            }
        }

        boolean secretsIncluded = "included".equals(manifest.path("contents").path("secrets").asText(""));
        if (!secretsIncluded) {
            for (String key : SECRET_EXPORT_KEYS) {
                if (notBlank(current.getProperty(key))) merged.setProperty(key, current.getProperty(key));
                else merged.remove(key);
            }
            notes.add("Das Archiv enthielt keine Zugangsdaten - es gelten die der Zielumgebung "
                    + "bzw. die Umgebungsvariablen.");
        }

        int[] mailStats = {0, 0};
        if (notBlank(srcExportDir)) {
            String rebased = rebaseMailLogJson(merged.getProperty(MAIL_LOG_KEY), srcExportDir, srcSep,
                    dst.exportDir().toAbsolutePath().toString(), java.io.File.separatorChar, mailStats);
            if (rebased != null) merged.setProperty(MAIL_LOG_KEY, rebased);
        }

        return new ImportReport(merged, fileStats[0], fileStats[1], fileStats[2],
                mailStats[0], mailStats[1], countersTaken, secretsIncluded, notes);
    }

    private static void rebaseEntityFilePaths(BackupLocations dst, String srcFileDir, char srcSep, int[] stats)
            throws Exception {
        String dstFileDir = dst.fileDir().toAbsolutePath().toString();
        Map<String, String> updates = new LinkedHashMap<>();
        List<String[]> keys = new ArrayList<>();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dst.dbFile().toAbsolutePath());
             PreparedStatement ps = c.prepareStatement(
                     "SELECT entity_type, external_id, file_path FROM sync_entities "
                             + "WHERE file_path IS NOT NULL AND file_path <> ''");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                keys.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
            }
        }
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dst.dbFile().toAbsolutePath())) {
            c.setAutoCommit(false);
            try (PreparedStatement set = c.prepareStatement(
                    "UPDATE sync_entities SET file_path=? WHERE entity_type=? AND external_id=?");
                 PreparedStatement clear = c.prepareStatement(
                         "UPDATE sync_entities SET file_path=NULL, file_hash=NULL, file_size=0 "
                                 + "WHERE entity_type=? AND external_id=?")) {
                for (String[] row : keys) {
                    String neu = rebaseStoredPath(row[2], srcFileDir, srcSep, dstFileDir, java.io.File.separatorChar);
                    if (neu != null && Files.exists(Paths.get(neu))) {
                        set.setString(1, neu);
                        set.setString(2, row[0]);
                        set.setString(3, row[1]);
                        set.addBatch();
                        stats[0]++;
                    } else {
                        // Lieber NULL als ein falscher Pfad: der nächste Sync lädt sauber nach,
                        // und die Inventar-Statistik zählt nicht länger nicht vorhandene Dateien.
                        clear.setString(1, row[0]);
                        clear.setString(2, row[1]);
                        clear.addBatch();
                        if (neu == null) stats[2]++; else stats[1]++;
                    }
                }
                set.executeBatch();
                clear.executeBatch();
            }
            c.commit();
        }
        updates.clear();
    }

    // ── Pfad-Umschreibung ───────────────────────────────────────────────────────

    /**
     * Schreibt einen gespeicherten absoluten Pfad auf eine andere Umgebung um.
     * Reines String-Ersetzen genügt nicht: bei Windows -> Linux müssen auch die Trennzeichen
     * im Rest des Pfades gedreht werden. Liefert null, wenn der Pfad nicht unter der
     * Quellwurzel liegt (dann ist er hier bedeutungslos).
     */
    static String rebaseStoredPath(String stored, String srcPrefix, char srcSep,
                                   String dstPrefix, char dstSep) {
        if (stored == null || stored.isBlank() || srcPrefix == null || srcPrefix.isBlank()) return null;
        String value = stored.trim();
        String prefix = srcPrefix.trim();
        boolean ignoreCase = srcSep == '\\';
        String haystack = ignoreCase ? value.toLowerCase(java.util.Locale.ROOT) : value;
        String needle = ignoreCase ? prefix.toLowerCase(java.util.Locale.ROOT) : prefix;
        if (!haystack.startsWith(needle)) return null;

        String rest = value.substring(prefix.length());
        while (!rest.isEmpty() && (rest.charAt(0) == '/' || rest.charAt(0) == '\\')) {
            rest = rest.substring(1);
        }
        if (rest.isEmpty()) return dstPrefix;
        String[] parts = rest.split("[/\\\\]");
        return dstPrefix + dstSep + String.join(String.valueOf(dstSep), parts);
    }

    /** Schreibt die vier Pfadfelder im Versandprotokoll um. stats = {umgeschrieben, verworfen}. */
    static String rebaseMailLogJson(String json, String srcExportDir, char srcSep,
                                    String dstExportDir, char dstSep, int[] stats) {
        if (json == null || json.isBlank()) return json;
        try {
            JsonNode parsed = MAPPER.readTree(json);
            if (!parsed.isArray()) return json;
            for (JsonNode row : parsed) {
                if (!row.isObject()) continue;
                ObjectNode obj = (ObjectNode) row;
                for (String field : MAIL_LOG_PATH_FIELDS) {
                    String value = obj.path(field).asText("");
                    if (value.isBlank()) continue;
                    String neu = rebaseStoredPath(value, srcExportDir, srcSep, dstExportDir, dstSep);
                    // Leerer String statt null: die Oberfläche zeigt dann automatisch nur den
                    // Dateinamen statt eines toten Links.
                    obj.put(field, neu == null ? "" : neu);
                    if (neu == null) stats[1]++; else stats[0]++;
                }
            }
            return MAPPER.writeValueAsString(parsed);
        } catch (Exception e) {
            return json;
        }
    }

    static Properties stripSecrets(Properties in) {
        Properties out = copyOf(in);
        for (String key : SECRET_EXPORT_KEYS) out.remove(key);
        return out;
    }

    static boolean isUnderRoot(Path candidate, Path root) {
        if (candidate == null || root == null) return false;
        try {
            return candidate.toRealPath().startsWith(root.toRealPath());
        } catch (IOException e) {
            return candidate.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize());
        }
    }

    // ── ZIP-Hilfen ──────────────────────────────────────────────────────────────

    private static void extractTo(Path zip, Path stagingRoot, ProgressSink progress) throws Exception {
        Files.createDirectories(stagingRoot);
        Path root = stagingRoot.toAbsolutePath().normalize();
        long entries = 0;
        long totalBytes = 0;
        try (InputStream in = Files.newInputStream(zip);
             ZipInputStream zis = new ZipInputStream(in, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IOException("Archiv enthält zu viele Einträge.");
                String name = entry.getName().replace('\\', '/');
                if (!name.equals(MANIFEST_ENTRY) && !hasAllowedPrefix(name)) {
                    throw new IOException("Unerwarteter Eintrag im Archiv: " + entry.getName());
                }
                Path target = safeTarget(root, name, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    zis.closeEntry();
                    continue;
                }
                Files.createDirectories(target.getParent());
                long written = 0;
                byte[] buffer = new byte[64 * 1024];
                try (OutputStream out = Files.newOutputStream(target)) {
                    int n;
                    while ((n = zis.read(buffer)) > 0) {
                        written += n;
                        totalBytes += n;
                        if (written > MAX_SINGLE_ENTRY || totalBytes > MAX_TOTAL_UNCOMPRESSED) {
                            throw new IOException("Archiv ist unerwartet gross - Abbruch.");
                        }
                        out.write(buffer, 0, n);
                    }
                }
                if (entry.getLastModifiedTime() != null) {
                    Files.setLastModifiedTime(target, entry.getLastModifiedTime());
                }
                zis.closeEntry();
            }
        }
        note(progress, "entpacken", "Entpackt: " + entries + " Einträge");
    }

    private static boolean hasAllowedPrefix(String name) {
        return name.startsWith("config/") || name.startsWith("settings/") || name.startsWith("db/")
                || name.startsWith("files/") || name.startsWith("exports/");
    }

    /** Zip-Slip-Abwehr: absolute Pfade, Laufwerksbuchstaben und ".." werden abgelehnt. */
    private static Path safeTarget(Path root, String normalizedName, String originalName) throws IOException {
        if (normalizedName.startsWith("/") || normalizedName.contains("..")
                || normalizedName.matches("^[A-Za-z]:.*")) {
            throw new IOException("Unerlaubter Pfad im Archiv: " + originalName);
        }
        Path target = root.resolve(normalizedName).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Unerlaubter Pfad im Archiv: " + originalName);
        }
        return target;
    }

    private static void verifyChecksums(ObjectNode manifest, Path staging) throws Exception {
        JsonNode checksums = manifest.path("checksums");
        var names = checksums.fieldNames();
        while (names.hasNext()) {
            String entryName = names.next();
            Path file = staging.resolve(entryName.replace('/', java.io.File.separatorChar));
            if (!Files.exists(file)) continue;
            String actual = sha256(Files.readAllBytes(file));
            String expected = checksums.path(entryName).asText("");
            if (!expected.isBlank() && !expected.equals(actual)) {
                throw new IOException("Prüfsumme stimmt nicht: " + entryName
                        + " - das Archiv ist beschädigt oder wurde verändert.");
            }
        }
    }

    private static void verifySchema(ObjectNode manifest, Path staging) throws Exception {
        Path db = staging.resolve("db").resolve(DB_FILENAME);
        if (!Files.exists(db)) return;
        JsonNode tables = manifest.path("database").path("tables");
        if (!tables.isObject()) return;
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath())) {
            for (String table : DB_TABLES) {
                if (!tables.has(table)) continue;
                List<String> actual = columnsOf(c, table);
                for (JsonNode expected : tables.path(table)) {
                    if (!actual.contains(expected.asText())) {
                        throw new IOException("Die Datenbank im Archiv passt nicht zu dieser Programmversion: "
                                + "Spalte " + expected.asText() + " fehlt in " + table + ".");
                    }
                }
            }
        }
    }

    private static void addTree(ZipOutputStream zip, String prefix, Path dir, String skipFileName,
                                ProgressSink progress, long[] done, long total) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) return;
        Path base = dir.toAbsolutePath().normalize();
        Files.walkFileTree(base, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (skipFileName != null && file.getFileName().toString().equals(skipFileName)) {
                    return FileVisitResult.CONTINUE;
                }
                String rel = base.relativize(file).toString().replace(java.io.File.separatorChar, '/');
                copyFileIntoZip(zip, prefix + rel, file, progress, done, total);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                note(progress, "warnung", "Übersprungen: " + file + " (" + exc.getMessage() + ")");
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void copyFileIntoZip(ZipOutputStream zip, String entryName, Path file,
                                        ProgressSink progress, long[] done, long total) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        try {
            entry.setLastModifiedTime(Files.getLastModifiedTime(file));
        } catch (IOException ignored) {
            // Zeitstempel ist Komfort, kein Muss
        }
        zip.putNextEntry(entry);
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(file)) {
            int n;
            while ((n = in.read(buffer)) > 0) {
                zip.write(buffer, 0, n);
                done[0] += n;
            }
        }
        zip.closeEntry();
        progressOf(progress, done[0], total);
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    // ── Datei- und Properties-Hilfen ────────────────────────────────────────────

    static Properties loadProperties(Path file) {
        Properties p = new Properties();
        if (file == null || !Files.exists(file)) return p;
        try (InputStream is = Files.newInputStream(file)) {
            p.load(is);
        } catch (IOException ignored) {
        }
        return p;
    }

    private static byte[] propertiesToBytes(Properties p, String comment) throws IOException {
        try (var out = new java.io.ByteArrayOutputStream()) {
            p.store(out, comment);
            return out.toByteArray();
        }
    }

    private static Properties copyOf(Properties in) {
        Properties out = new Properties();
        if (in != null) out.putAll(in);
        return out;
    }

    static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) return;
        try (var walk = Files.walk(path)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static void clearDirectoryContents(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (var children = Files.list(dir)) {
            for (Path child : children.toList()) deleteRecursively(child);
        }
    }

    /** Verschiebt, mit Rückfall auf Kopieren, falls Quelle und Ziel auf verschiedenen Volumes liegen. */
    private static void moveTree(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return;
        } catch (IOException ignored) {
            // Dateisystemgrenze - dann kopieren
        }
        if (Files.isDirectory(source)) {
            Files.createDirectories(target);
            try (var children = Files.list(source)) {
                for (Path child : children.toList()) {
                    moveTree(child, target.resolve(child.getFileName().toString()));
                }
            }
            Files.deleteIfExists(source);
        } else {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(source);
        }
    }

    private static long byteSize(Path file) {
        try {
            return Files.exists(file) ? Files.size(file) : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    static long treeSize(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return 0;
        final long[] sum = {0};
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    sum[0] += attrs.size();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
        return sum[0];
    }

    private static List<String> columnsOf(Connection c, String table) throws Exception {
        List<String> cols = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) cols.add(rs.getString("name"));
        }
        return cols;
    }

    private static long scalar(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder sb = new StringBuilder("sha256:");
        for (byte b : digest.digest(data)) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static void phase(ProgressSink sink, String key, String label) {
        if (sink != null) sink.phase(key, label);
    }

    private static void note(ProgressSink sink, String key, String message) {
        if (sink != null) sink.note(message);
    }

    private static void progressOf(ProgressSink sink, long done, long total) {
        if (sink != null) sink.progress(done, total);
    }

    static String archiveFileName(String prefix, boolean withSecrets) {
        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return prefix + "_" + ts + (withSecrets ? "_mit-zugangsdaten" : "") + ".zip";
    }

    /** Behält die neuesten `keep` Dateien mit dem Präfix, löscht ältere. */
    static void pruneOldArchives(Path dir, String prefix, int keep) {
        if (!Files.isDirectory(dir)) return;
        try (var list = Files.list(dir)) {
            List<Path> matches = list
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .filter(p -> p.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
            for (int i = keep; i < matches.size(); i++) Files.deleteIfExists(matches.get(i));
        } catch (IOException ignored) {
        }
    }
}
