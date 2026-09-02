import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für Datensicherung und Wiederherstellung.
 * Alles läuft in temporären Verzeichnissen - kein HTTP, keine echte Konfiguration.
 */
class BackupServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── Aufbau einer vollständigen Quellumgebung ────────────────────────────────

    private static BackupService.BackupLocations fixture(Path root) throws Exception {
        Path configFile = root.resolve("config").resolve("config.properties");
        Path dataDir = root.resolve("data");
        Path exportDir = root.resolve("exports");
        Files.createDirectories(configFile.getParent());
        Files.createDirectories(dataDir);
        Files.createDirectories(exportDir);
        BackupService.BackupLocations loc = new BackupService.BackupLocations(configFile, dataDir, exportDir);

        // Datenbank mit einer Entität, deren file_path absolut auf das Quellverzeichnis zeigt
        Method init = GoAffProSyncService.class.getDeclaredMethod("initDatabase", Path.class);
        init.setAccessible(true);
        init.invoke(null, loc.dbFile());

        Path asset = loc.fileDir().resolve("files").resolve("109745-abcdef123456.bin");
        Files.createDirectories(asset.getParent());
        Files.write(asset, "ASSET".getBytes(StandardCharsets.UTF_8));

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + loc.dbFile().toAbsolutePath());
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO sync_entities(entity_type,external_id,api_path,content_hash,raw_json,"
                             + "first_seen_at,last_seen_at,last_changed_at,state,file_path,file_hash,file_size) "
                             + "VALUES('files','109745','/v1/admin/files','h','{\"id\":\"109745\"}',"
                             + "'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z','2026-01-01T00:00:00Z','active',?,'h',5)")) {
            ps.setString(1, asset.toAbsolutePath().toString());
            ps.executeUpdate();
        }

        // Beleg im Exportordner
        Path beleg = exportDir.resolve("export_2026-01-01_1_Testberaterin").resolve("provisionsnachweis_GS-2026-0394.pdf");
        Files.createDirectories(beleg.getParent());
        Files.write(beleg, "PDF".getBytes(StandardCharsets.UTF_8));

        // Konfiguration mit Zählern, Zugangsdaten und Versandprotokoll
        Properties config = new Properties();
        config.setProperty("gutschriftCounter", "394");
        config.setProperty("gutschriftCounterYear", "2026");
        config.setProperty("goaffproAPIKey", "GEHEIM-KEY");
        config.setProperty("smtpPassword", "GEHEIM-PASS");
        config.setProperty("smtpUsername", "user@example.com");
        config.setProperty("smtpHost", "smtp.example.com");
        config.setProperty("contactEmail", "info@example.com");
        config.setProperty("pdfExportPath", exportDir.toAbsolutePath().toString());
        config.setProperty("goaffproSyncDataPath", dataDir.toAbsolutePath().toString());
        config.setProperty("sentMailLogJson", "[{\"paymentId\":\"1\",\"pdfFile\":\"provisionsnachweis_GS-2026-0394.pdf\","
                + "\"pdfPath\":" + MAPPER.writeValueAsString(beleg.toAbsolutePath().toString()) + "}]");
        config.setProperty("sentReminderLogJson", "[{\"advisorId\":\"7\",\"toEmail\":\"a@b.de\"}]");
        store(config, configFile, "test");

        Properties ui = new Properties();
        ui.setProperty("smtpPassword", "GEHEIM-PASS");
        ui.setProperty("goaffproAPIKey", "GEHEIM-KEY");
        ui.setProperty("contactEmail", "info@example.com");
        ui.setProperty("goaffproSyncDataPath", dataDir.toAbsolutePath().toString());
        store(ui, loc.uiFile(), "ui");
        return loc;
    }

    private static void store(Properties p, Path file, String comment) throws Exception {
        Files.createDirectories(file.getParent());
        try (OutputStream os = Files.newOutputStream(file)) {
            p.store(os, comment);
        }
    }

    private static BackupService.BackupLocations emptyTarget(Path root) throws Exception {
        Path configFile = root.resolve("config").resolve("config.properties");
        Path dataDir = root.resolve("data");
        Path exportDir = root.resolve("exports");
        Files.createDirectories(configFile.getParent());
        Files.createDirectories(dataDir);
        Files.createDirectories(exportDir);
        return new BackupService.BackupLocations(configFile, dataDir, exportDir);
    }

    private static List<String> entryNames(Path zip) throws Exception {
        List<String> names = new java.util.ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                names.add(e.getName());
                zis.closeEntry();
            }
        }
        return names;
    }

    private static Properties propertiesFromZip(Path zip, String entryName) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.getName().equals(entryName)) {
                    Properties p = new Properties();
                    p.load(new java.io.ByteArrayInputStream(zis.readAllBytes()));
                    return p;
                }
                zis.closeEntry();
            }
        }
        return new Properties();
    }

    private static byte[] bytesFromZip(Path zip, String entryName) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.getName().equals(entryName)) return zis.readAllBytes();
                zis.closeEntry();
            }
        }
        return new byte[0];
    }

    // ── Round-Trip ──────────────────────────────────────────────────────────────

    @Test
    void roundTripStelltAlleDreiOrteWiederHer(@TempDir Path tmp) throws Exception {
        BackupService.BackupLocations src = fixture(tmp.resolve("quelle"));
        Path zip = tmp.resolve("archiv.zip");
        BackupService.createArchive(src, false, zip, null);
        assertTrue(Files.size(zip) > 0);

        BackupService.BackupLocations dst = emptyTarget(tmp.resolve("ziel"));
        BackupService.ImportReport report =
                BackupService.applyArchive(zip, dst, tmp.resolve("staging"), null);

        assertTrue(Files.exists(dst.dbFile()), "Datenbank muss angekommen sein");
        assertTrue(Files.exists(dst.fileDir().resolve("files").resolve("109745-abcdef123456.bin")));
        assertTrue(Files.exists(dst.exportDir().resolve("export_2026-01-01_1_Testberaterin")
                .resolve("provisionsnachweis_GS-2026-0394.pdf")));
        assertEquals("394", report.settings().getProperty("gutschriftCounter"));
        assertTrue(report.countersTaken());
    }

    @Test
    void archivEnthaeltKeineWalSeitendateien(@TempDir Path tmp) throws Exception {
        BackupService.BackupLocations src = fixture(tmp.resolve("quelle"));
        Path zip = tmp.resolve("a.zip");
        BackupService.createArchive(src, false, zip, null);
        assertTrue(entryNames(zip).stream().noneMatch(n -> n.endsWith("-wal") || n.endsWith("-shm")));
    }

    // ── Zugangsdaten ────────────────────────────────────────────────────────────

    @Test
    void exportOhneZugangsdatenEntferntSieAusBeidenDateien(@TempDir Path tmp) throws Exception {
        BackupService.BackupLocations src = fixture(tmp.resolve("quelle"));
        Path zip = tmp.resolve("a.zip");
        BackupService.createArchive(src, false, zip, null);

        Properties config = propertiesFromZip(zip, "config/config.properties");
        Properties ui = propertiesFromZip(zip, "settings/goaffpro_ui_settings.properties");
        for (String key : List.of("goaffproAPIKey", "smtpPassword", "smtpUsername")) {
            assertNull(config.getProperty(key), key + " darf nicht im Archiv stehen");
            assertNull(ui.getProperty(key), key + " darf nicht in den UI-Einstellungen stehen");
        }
    }

    @Test
    void exportOhneZugangsdatenBehaeltInfrastrukturSchluessel(@TempDir Path tmp) throws Exception {
        BackupService.BackupLocations src = fixture(tmp.resolve("quelle"));
        Path zip = tmp.resolve("a.zip");
        BackupService.createArchive(src, false, zip, null);
        Properties config = propertiesFromZip(zip, "config/config.properties");
        assertEquals("smtp.example.com", config.getProperty("smtpHost"));
        assertEquals("info@example.com", config.getProperty("contactEmail"));
    }

    @Test
    void exportLaesstUiEinstellungenAusDemBelegbaumWeg(@TempDir Path tmp) throws Exception {
        // Anti-Leak: die Datei liegt physisch im Belegordner und enthält Zugangsdaten
        BackupService.BackupLocations src = fixture(tmp.resolve("quelle"));
        Path zip = tmp.resolve("a.zip");
        BackupService.createArchive(src, false, zip, null);
        assertFalse(entryNames(zip).contains("exports/goaffpro_ui_settings.properties"),
                "Die ungefilterte UI-Einstellungsdatei darf nicht im Belegbaum landen");
        assertTrue(entryNames(zip).contains("settings/goaffpro_ui_settings.properties"));
    }

    @Test
    void exportMitZugangsdatenNimmtSieMit(@TempDir Path tmp) throws Exception {
        BackupService.BackupLocations src = fixture(tmp.resolve("quelle"));
        Path zip = tmp.resolve("a.zip");
        BackupService.createArchive(src, true, zip, null);
        assertEquals("GEHEIM-KEY", propertiesFromZip(zip, "config/config.properties").getProperty("goaffproAPIKey"));
    }

    @Test
    void importOhneZugangsdatenBehaeltDieDerZielumgebung(@TempDir Path tmp) throws Exception {
        BackupService.BackupLocations src = fixture(tmp.resolve("quelle"));
        Path zip = tmp.resolve("a.zip");
        BackupService.createArchive(src, false, zip, null);

        BackupService.BackupLocations dst = emptyTarget(tmp.resolve("ziel"));
        Properties vorhanden = new Properties();
        vorhanden.setProperty("goaffproAPIKey", "ZIEL-KEY");
        store(vorhanden, dst.configFile(), "ziel");

        BackupService.ImportReport report = BackupService.applyArchive(zip, dst, tmp.resolve("st"), null);
        assertEquals("ZIEL-KEY", report.settings().getProperty("goaffproAPIKey"));
        assertFalse(report.secretsIncluded());
    }

    // ── Pfad-Umschreibung ───────────────────────────────────────────────────────

    @Test
    void pfadUmschreibungWindowsNachLinux() {
        String neu = BackupService.rebaseStoredPath(
                "C:\\a\\data\\goaffpro_files\\files\\x.bin",
                "C:\\a\\data\\goaffpro_files", '\\', "/app/data/goaffpro_files", '/');
        assertEquals("/app/data/goaffpro_files/files/x.bin", neu);
    }

    @Test
    void pfadUmschreibungLinuxNachWindows() {
        String neu = BackupService.rebaseStoredPath(
                "/app/data/goaffpro_files/files/x.bin",
                "/app/data/goaffpro_files", '/', "C:\\ziel\\goaffpro_files", '\\');
        assertEquals("C:\\ziel\\goaffpro_files\\files\\x.bin", neu);
    }

    @Test
    void pfadUmschreibungLiefertNullBeiFremdemPraefix() {
        assertNull(BackupService.rebaseStoredPath("/woanders/x.bin", "/app/data", '/', "/ziel", '/'));
        assertNull(BackupService.rebaseStoredPath(null, "/app/data", '/', "/ziel", '/'));
    }

    @Test
    void pfadUmschreibungIstUnterWindowsGrossKleinTolerant() {
        String neu = BackupService.rebaseStoredPath(
                "c:\\A\\Data\\goaffpro_files\\x.bin",
                "C:\\a\\data\\goaffpro_files", '\\', "/ziel", '/');
        assertEquals("/ziel/x.bin", neu);
    }

    @Test
    void importSchreibtFilePathAufDieZielumgebungUm(@TempDir Path tmp) throws Exception {
        BackupService.BackupLocations src = fixture(tmp.resolve("quelle"));
        Path zip = tmp.resolve("a.zip");
        BackupService.createArchive(src, false, zip, null);

        BackupService.BackupLocations dst = emptyTarget(tmp.resolve("ziel"));
        BackupService.ImportReport report = BackupService.applyArchive(zip, dst, tmp.resolve("st"), null);

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dst.dbFile().toAbsolutePath());
             PreparedStatement ps = c.prepareStatement("SELECT file_path FROM sync_entities WHERE external_id='109745'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            String pfad = rs.getString(1);
            assertNotNull(pfad, "file_path muss neu gesetzt sein");
            assertTrue(pfad.startsWith(dst.fileDir().toAbsolutePath().toString()),
                    "file_path muss auf die Zielumgebung zeigen, war: " + pfad);
            assertTrue(Files.exists(Path.of(pfad)), "Die Datei muss unter dem neuen Pfad liegen");
        }
        assertEquals(1, report.filePathsRebased());
        assertEquals(0, report.filePathsMissing());
    }

    @Test
    void importSetztFilePathAufNullWennDieDateiFehlt(@TempDir Path tmp) throws Exception {
        BackupService.BackupLocations src = fixture(tmp.resolve("quelle"));
        // Asset vor dem Export entfernen -> Eintrag zeigt ins Leere
        BackupService.deleteRecursively(src.fileDir());
        Path zip = tmp.resolve("a.zip");
        BackupService.createArchive(src, false, zip, null);

        BackupService.BackupLocations dst = emptyTarget(tmp.resolve("ziel"));
        BackupService.ImportReport report = BackupService.applyArchive(zip, dst, tmp.resolve("st"), null);

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dst.dbFile().toAbsolutePath());
             PreparedStatement ps = c.prepareStatement("SELECT file_path, file_size FROM sync_entities WHERE external_id='109745'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertNull(rs.getString(1), "Fehlende Datei -> file_path NULL statt falscher Pfad");
            assertEquals(0, rs.getInt(2));
        }
        assertEquals(0, report.filePathsRebased());
    }

    @Test
    void importSchreibtNurDasVersandprotokollUm(@TempDir Path tmp) throws Exception {
        BackupService.BackupLocations src = fixture(tmp.resolve("quelle"));
        Path zip = tmp.resolve("a.zip");
        BackupService.createArchive(src, false, zip, null);

        BackupService.BackupLocations dst = emptyTarget(tmp.resolve("ziel"));
        BackupService.ImportReport report = BackupService.applyArchive(zip, dst, tmp.resolve("st"), null);

        String mailLog = report.settings().getProperty("sentMailLogJson");
        assertTrue(mailLog.contains(dst.exportDir().getFileName().toString()),
                "pdfPath muss auf das Zielverzeichnis zeigen: " + mailLog);
        assertFalse(mailLog.contains(src.exportDir().toAbsolutePath().toString()));
        // Die anderen beiden Protokolle enthalten keine Pfadfelder und bleiben unverändert
        assertEquals("[{\"advisorId\":\"7\",\"toEmail\":\"a@b.de\"}]",
                report.settings().getProperty("sentReminderLogJson"));
        assertEquals(1, report.mailLogRebased());
    }

    @Test
    void versandprotokollBekommtLeerenTextStattTotemLink() {
        int[] stats = {0, 0};
        String json = "[{\"pdfPath\":\"/fremd/x.pdf\"}]";
        String neu = BackupService.rebaseMailLogJson(json, "/quelle", '/', "/ziel", '/', stats);
        assertTrue(neu.contains("\"pdfPath\":\"\""), "Nicht zuordenbar -> leerer String, war: " + neu);
        assertEquals(1, stats[1]);
    }

    @Test
    void importUebernimmtNichtDieQuellpfade(@TempDir Path tmp) throws Exception {
        BackupService.BackupLocations src = fixture(tmp.resolve("quelle"));
        Path zip = tmp.resolve("a.zip");
        BackupService.createArchive(src, false, zip, null);

        BackupService.BackupLocations dst = emptyTarget(tmp.resolve("ziel"));
        BackupService.ImportReport report = BackupService.applyArchive(zip, dst, tmp.resolve("st"), null);

        assertEquals(dst.exportDir().toAbsolutePath().toString(), report.settings().getProperty("pdfExportPath"));
        assertEquals(dst.dataDir().toAbsolutePath().toString(), report.settings().getProperty("goaffproSyncDataPath"));
    }

    // ── Zähler ──────────────────────────────────────────────────────────────────

    @Test
    void importBehaeltZielzaehlerWennDasArchivKeineHat(@TempDir Path tmp) throws Exception {
        Path quelle = tmp.resolve("quelle");
        BackupService.BackupLocations src = fixture(quelle);
        Properties ohneZaehler = BackupService.loadProperties(src.configFile());
        ohneZaehler.remove("gutschriftCounter");
        ohneZaehler.remove("gutschriftCounterYear");
        store(ohneZaehler, src.configFile(), "ohne");

        Path zip = tmp.resolve("a.zip");
        BackupService.createArchive(src, false, zip, null);

        BackupService.BackupLocations dst = emptyTarget(tmp.resolve("ziel"));
        Properties ziel = new Properties();
        ziel.setProperty("gutschriftCounter", "12");
        ziel.setProperty("gutschriftCounterYear", "2026");
        store(ziel, dst.configFile(), "ziel");

        BackupService.ImportReport report = BackupService.applyArchive(zip, dst, tmp.resolve("st"), null);
        assertEquals("12", report.settings().getProperty("gutschriftCounter"),
                "Ohne Zähler im Archiv darf der Zielzähler nicht überschrieben werden");
        assertFalse(report.countersTaken());
    }

    @Test
    void manifestSchreibtFehlendeZaehlerNichtAlsNull(@TempDir Path tmp) throws Exception {
        BackupService.BackupLocations src = fixture(tmp.resolve("quelle"));
        Path zip = tmp.resolve("a.zip");
        BackupService.createArchive(src, false, zip, null);
        ObjectNode manifest = BackupService.readManifest(zip);
        assertEquals("394", manifest.path("counters").path("gutschriftCounter").asText());
        assertFalse(manifest.path("counters").has("rechnungCounter"),
                "Nicht vorhandene Zähler dürfen gar nicht auftauchen (niemals als 0)");
    }

    // ── Sicherheit ──────────────────────────────────────────────────────────────

    @Test
    void importLehntZipSlipAb(@TempDir Path tmp) throws Exception {
        Path zip = tmp.resolve("boese.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip), StandardCharsets.UTF_8)) {
            ObjectNode m = MAPPER.createObjectNode();
            m.put("formatVersion", 1);
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(MAPPER.writeValueAsBytes(m));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("exports/../../evil.txt"));
            zos.write("boese".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        BackupService.BackupLocations dst = emptyTarget(tmp.resolve("ziel"));
        Exception ex = assertThrows(Exception.class,
                () -> BackupService.applyArchive(zip, dst, tmp.resolve("st"), null));
        assertTrue(ex.getMessage().contains("Unerlaubter Pfad"), ex.getMessage());
        assertFalse(Files.exists(tmp.resolve("evil.txt")));
    }

    @Test
    void importLehntAbsolutePfadeImArchivAb(@TempDir Path tmp) throws Exception {
        Path zip = tmp.resolve("boese2.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip), StandardCharsets.UTF_8)) {
            ObjectNode m = MAPPER.createObjectNode();
            m.put("formatVersion", 1);
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(MAPPER.writeValueAsBytes(m));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("/etc/passwd"));
            zos.write("x".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        BackupService.BackupLocations dst = emptyTarget(tmp.resolve("ziel"));
        assertThrows(Exception.class, () -> BackupService.applyArchive(zip, dst, tmp.resolve("st"), null));
    }

    @Test
    void importLehntUnbekanntesFormatAb(@TempDir Path tmp) throws Exception {
        Path zip = tmp.resolve("neu.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip), StandardCharsets.UTF_8)) {
            ObjectNode m = MAPPER.createObjectNode();
            m.put("formatVersion", 99);
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(MAPPER.writeValueAsBytes(m));
            zos.closeEntry();
        }
        BackupService.BackupLocations dst = emptyTarget(tmp.resolve("ziel"));
        Exception ex = assertThrows(Exception.class,
                () -> BackupService.applyArchive(zip, dst, tmp.resolve("st"), null));
        assertTrue(ex.getMessage().contains("neueren Programmversion"), ex.getMessage());
    }

    @Test
    void importLehntArchivOhneManifestAb(@TempDir Path tmp) throws Exception {
        Path zip = tmp.resolve("fremd.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip), StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry("irgendwas.txt"));
            zos.write("x".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        BackupService.BackupLocations dst = emptyTarget(tmp.resolve("ziel"));
        Exception ex = assertThrows(Exception.class,
                () -> BackupService.applyArchive(zip, dst, tmp.resolve("st"), null));
        assertTrue(ex.getMessage().contains("kein GoAffPro-Sicherungsarchiv"), ex.getMessage());
    }

    @Test
    void isUnderRootWehrtAusbrucheAb(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("exports");
        Files.createDirectories(root.resolve("export_1"));
        Path drin = root.resolve("export_1").resolve("a.pdf");
        Files.write(drin, "x".getBytes(StandardCharsets.UTF_8));
        Path draussen = tmp.resolve("geheim.env");
        Files.write(draussen, "SECRET".getBytes(StandardCharsets.UTF_8));
        Path geschwister = tmp.resolve("exports-evil");
        Files.createDirectories(geschwister);
        Path geschwisterDatei = geschwister.resolve("b.pdf");
        Files.write(geschwisterDatei, "y".getBytes(StandardCharsets.UTF_8));

        assertTrue(BackupService.isUnderRoot(drin, root));
        assertFalse(BackupService.isUnderRoot(draussen, root));
        assertFalse(BackupService.isUnderRoot(geschwisterDatei, root),
                "Gleiches Namenspräfix darf nicht als 'darunter' gelten");
    }

    // ── Datenbank-Snapshot ──────────────────────────────────────────────────────

    @Test
    void snapshotErfasstNochNichtEingecheckteWalDaten(@TempDir Path tmp) throws Exception {
        Path db = tmp.resolve("q.sqlite");
        Method init = GoAffProSyncService.class.getDeclaredMethod("initDatabase", Path.class);
        init.setAccessible(true);
        init.invoke(null, db);

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO sync_runs(mode,status,started_at) VALUES('delta','success','2026-01-01T00:00:00Z')")) {
            ps.executeUpdate();

            Path snapshot = tmp.resolve("kopie.sqlite");
            GoAffProSyncService.snapshotDatabase(db, snapshot);

            try (Connection s = DriverManager.getConnection("jdbc:sqlite:" + snapshot.toAbsolutePath());
                 PreparedStatement q = s.prepareStatement("SELECT COUNT(*) FROM sync_runs");
                 ResultSet rs = q.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Der Snapshot muss den vollständigen Stand enthalten");
            }
            assertFalse(Files.exists(tmp.resolve("kopie.sqlite-wal")), "Snapshot darf keinen WAL hinterlassen");
        }
    }

    @Test
    void snapshotUeberschreibtEineVorhandeneZieldatei(@TempDir Path tmp) throws Exception {
        Path db = tmp.resolve("q.sqlite");
        Method init = GoAffProSyncService.class.getDeclaredMethod("initDatabase", Path.class);
        init.setAccessible(true);
        init.invoke(null, db);
        Path ziel = tmp.resolve("kopie.sqlite");
        Files.write(ziel, "alt".getBytes(StandardCharsets.UTF_8));
        GoAffProSyncService.snapshotDatabase(db, ziel);
        assertTrue(Files.size(ziel) > 100, "VACUUM INTO verlangt eine nicht existierende Zieldatei");
    }

    // ── Aufräumen ───────────────────────────────────────────────────────────────

    @Test
    void aufraeumenBehaeltDieNeuestenArchive(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp);
        for (String name : List.of("export_20260101_000000.zip", "export_20260102_000000.zip",
                "export_20260103_000000.zip", "export_20260104_000000.zip")) {
            Files.write(tmp.resolve(name), "x".getBytes(StandardCharsets.UTF_8));
        }
        BackupService.pruneOldArchives(tmp, "export_", 2);
        assertTrue(Files.exists(tmp.resolve("export_20260104_000000.zip")));
        assertTrue(Files.exists(tmp.resolve("export_20260103_000000.zip")));
        assertFalse(Files.exists(tmp.resolve("export_20260101_000000.zip")));
    }

    // ── Reproduzierbare Serialisierung ──────────────────────────────────────────

    @Test
    void propertiesSerialisierungIstUeberDieZeitStabil() throws Exception {
        // Properties.store() schreibt eine Zeitstempel-Zeile. Der Inhalt wird zweimal
        // serialisiert (Pruefsumme im Manifest und ZIP-Eintrag); faellt dazwischen ein
        // Sekundenwechsel, passte die Pruefsumme frueher nicht mehr zum Eintrag.
        Properties p = new Properties();
        p.setProperty("gutschriftCounter", "394");
        p.setProperty("smtpHost", "mail.example.org");

        byte[] ersteFassung = BackupService.propertiesToBytes(p, "GoAffPro config");
        Thread.sleep(1100);
        byte[] zweiteFassung = BackupService.propertiesToBytes(p, "GoAffPro config");

        assertArrayEquals(ersteFassung, zweiteFassung,
                "Zwei Serialisierungen desselben Inhalts muessen Byte fuer Byte gleich sein");
    }

    @Test
    void archivEintragEnthaeltKeinenZeitstempel(@TempDir Path tmp) throws Exception {
        BackupService.BackupLocations quelle = fixture(tmp.resolve("quelle"));
        Path zip = BackupService.createArchive(quelle, false, tmp.resolve("sicherung.zip"), null);

        String inhalt = new String(bytesFromZip(zip, "config/config.properties"),
                StandardCharsets.ISO_8859_1);
        List<String> kommentare = inhalt.lines().filter(z -> z.startsWith("#")).toList();

        assertEquals(List.of("#GoAffPro config"), kommentare,
                "Im Archiv darf nur der feste Kommentar stehen, keine Zeitstempel-Zeile");
    }
}
