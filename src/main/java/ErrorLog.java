import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Fehlerprotokoll der ganzen Anwendung: Fehlerantworten des Servers (ab HTTP 400), unbehandelte
 * Ausnahmen, Fehler im Browser und Abbrüche der Zeitpläne. Gedacht zum Weiterreichen - der
 * Klartext enthält, was zur Fehlersuche nötig ist, und keine bekannten Zugangsdaten.
 *
 * Eine JSON-Datei im Datenverzeichnis: nicht in der Sync-Datenbank, damit auch deren Ausfall
 * hier landet, und nicht im Exportordner, den Datensicherung und Umzug mitnehmen.
 *
 * Ohne statischen Zustand: Speicherort und Uhr kommen von außen, dadurch läuft alles in Tests
 * gegen temporäre Verzeichnisse.
 */
public final class ErrorLog {

    static final String FILENAME = "fehlerprotokoll.json";
    static final int MAX_ENTRIES = 500;
    static final int MAX_MESSAGE_CHARS = 2000;
    static final int MAX_DETAIL_CHARS = 12000;
    static final int MAX_FIELD_CHARS = 300;
    /** Derselbe Fehler innerhalb dieses Abstands zählt hoch, statt eine neue Zeile anzulegen. */
    static final Duration MERGE_WINDOW = Duration.ofMinutes(10);
    /** So viele der jüngsten Einträge werden auf Wiederholungen geprüft. */
    static final int MERGE_LOOKBACK = 20;
    /** Kürzere Werte werden nicht geschwärzt: Sie kämen auch in harmlosen Wörtern vor. */
    static final int MIN_SECRET_LENGTH = 6;
    static final String REDACTED = "[entfernt]";
    static final DateTimeFormatter LOCAL_TIME =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.of("Europe/Berlin"));

    /**
     * Ein Eintrag. {@code count} > 1 heißt: Derselbe Fehler trat mehrfach auf, zuerst
     * {@code firstAt}, zuletzt {@code lastAt}. {@code status} ist der HTTP-Status oder 0.
     */
    public record Entry(long id, Instant firstAt, Instant lastAt, int count, String source, String area,
                        String action, int status, String message, String detail) {
    }

    private static final class State {
        Instant clearedAt;
        long nextId = 1;
        final List<Entry> entries = new ArrayList<>();
    }

    private final Supplier<Path> fileSupplier;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Object lock = new Object();

    public ErrorLog(Supplier<Path> fileSupplier) {
        this(fileSupplier, Clock.systemUTC());
    }

    ErrorLog(Supplier<Path> fileSupplier, Clock clock) {
        this.fileSupplier = Objects.requireNonNull(fileSupplier);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Hält einen Fehler fest. Wirft nie: Ein unlesbares oder volles Protokoll darf weder eine
     * Anfrage noch einen Hintergrundjob scheitern lassen.
     */
    public void record(String source, String area, String action, int status, String message, String detail,
                       Collection<String> secrets) {
        try {
            // Außerhalb der Sperre: Der Lieferant liest Einstellungen und nimmt dafür eigene Sperren.
            Path file = fileSupplier.get();
            String cleanSource = clip(redact(text(source), secrets), MAX_FIELD_CHARS);
            String cleanArea = clip(redact(text(area), secrets), MAX_FIELD_CHARS);
            String cleanAction = clip(redact(text(action), secrets), MAX_FIELD_CHARS);
            String cleanMessage = clip(redact(text(message), secrets), MAX_MESSAGE_CHARS);
            if (cleanMessage.isBlank()) cleanMessage = "Unbekannter Fehler";
            String cleanDetail = clip(redact(text(detail), secrets), MAX_DETAIL_CHARS);
            synchronized (lock) {
                State state = read(file);
                Instant now = clock.instant();
                int lookback = Math.min(MERGE_LOOKBACK, state.entries.size());
                for (int i = 0; i < lookback; i++) {
                    Entry e = state.entries.get(i);
                    boolean same = e.source().equals(cleanSource) && e.area().equals(cleanArea)
                            && e.action().equals(cleanAction) && e.status() == status
                            && e.message().equals(cleanMessage);
                    if (same && !e.lastAt().plus(MERGE_WINDOW).isBefore(now)) {
                        // Die Details der ersten Meldung bleiben; meist ist es derselbe Stacktrace.
                        state.entries.remove(i);
                        state.entries.add(0, new Entry(e.id(), e.firstAt(), now, e.count() + 1, e.source(), e.area(),
                                e.action(), e.status(), e.message(), e.detail()));
                        write(file, state);
                        return;
                    }
                }
                state.entries.add(0, new Entry(state.nextId++, now, now, 1, cleanSource, cleanArea, cleanAction,
                        status, cleanMessage, cleanDetail));
                while (state.entries.size() > MAX_ENTRIES) state.entries.remove(state.entries.size() - 1);
                write(file, state);
            }
        } catch (Exception e) {
            System.err.println("Fehlerprotokoll nicht beschreibbar: " + e.getMessage());
        }
    }

    /** Alle Einträge, zuletzt aufgetretene zuerst. */
    public List<Entry> entries() {
        try {
            Path file = fileSupplier.get();
            synchronized (lock) {
                return List.copyOf(read(file).entries);
            }
        } catch (Exception e) {
            System.err.println("Fehlerprotokoll nicht lesbar: " + e.getMessage());
            return List.of();
        }
    }

    /** Zeitpunkt des letzten Leerens oder null. Ältere Sync-Fehler gelten damit als erledigt. */
    public Instant clearedAt() {
        try {
            Path file = fileSupplier.get();
            synchronized (lock) {
                return read(file).clearedAt;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public void clear() throws IOException {
        Path file = fileSupplier.get();
        synchronized (lock) {
            State state = read(file);
            state.entries.clear();
            state.clearedAt = clock.instant();
            write(file, state);
        }
    }

    public Path file() {
        return fileSupplier.get();
    }

    /**
     * Klartext zum Kopieren: Kopfzeilen, dann die Einträge (zuletzt aufgetretene zuerst) mit
     * Anfrage, Meldung und eingerückten Details.
     */
    static String formatText(List<Entry> entries, List<String> headerLines) {
        StringBuilder out = new StringBuilder();
        out.append("VEMMiNA Assistent – Fehlerprotokoll\n");
        for (String line : headerLines) out.append(line).append('\n');
        if (entries.isEmpty()) {
            out.append("\nKeine Fehler protokolliert.\n");
            return out.toString();
        }
        for (Entry e : entries) {
            out.append('\n').append('[').append(LOCAL_TIME.format(e.firstAt())).append("] ").append(e.source());
            if (!e.area().isBlank()) out.append(" · ").append(e.area());
            if (e.status() > 0) out.append(" · HTTP ").append(e.status());
            if (e.count() > 1) {
                out.append(" · ").append(e.count()).append("× bis ").append(LOCAL_TIME.format(e.lastAt()));
            }
            out.append('\n');
            if (!e.action().isBlank()) out.append("  ").append(e.action()).append('\n');
            for (String line : e.message().split("\\R")) out.append("  ").append(line).append('\n');
            if (!e.detail().isBlank()) {
                for (String line : e.detail().split("\\R")) out.append("    ").append(line).append('\n');
            }
        }
        return out.toString();
    }

    /** Ersetzt jeden bekannten geheimen Wert (API-Schlüssel, Passwort) durch {@link #REDACTED}. */
    static String redact(String value, Collection<String> secrets) {
        String result = text(value);
        if (secrets == null || result.isEmpty()) return result;
        for (String secret : secrets) {
            String s = secret == null ? "" : secret.trim();
            if (s.length() >= MIN_SECRET_LENGTH) result = result.replace(s, REDACTED);
        }
        return result;
    }

    static String clip(String value, int max) {
        String v = text(value);
        return v.length() <= max ? v : v.substring(0, max) + " … (gekürzt)";
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    // ── Datei ──

    private State read(Path file) {
        State state = new State();
        if (file == null || !Files.exists(file)) return state;
        try {
            JsonNode root = mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
            String cleared = root.path("clearedAt").asText("");
            if (!cleared.isBlank()) state.clearedAt = Instant.parse(cleared);
            for (JsonNode n : root.path("entries")) {
                long id = n.path("id").asLong(0);
                state.entries.add(new Entry(id, Instant.parse(n.path("firstAt").asText()),
                        Instant.parse(n.path("lastAt").asText()), Math.max(1, n.path("count").asInt(1)),
                        n.path("source").asText(""), n.path("area").asText(""), n.path("action").asText(""),
                        n.path("status").asInt(0), n.path("message").asText(""), n.path("detail").asText("")));
                state.nextId = Math.max(state.nextId, id + 1);
            }
            state.nextId = Math.max(state.nextId, root.path("nextId").asLong(1));
        } catch (Exception e) {
            // Kaputte Datei beiseitelegen statt sie zu überschreiben - sie könnte noch gebraucht werden.
            try {
                Files.move(file, file.resolveSibling(FILENAME + ".defekt-" + clock.millis()), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
            }
            System.err.println("Fehlerprotokoll war unlesbar und wurde neu begonnen: " + e.getMessage());
            return new State();
        }
        return state;
    }

    private void write(Path file, State state) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("version", 1);
        if (state.clearedAt != null) root.put("clearedAt", state.clearedAt.toString());
        root.put("nextId", state.nextId);
        ArrayNode list = root.putArray("entries");
        for (Entry e : state.entries) {
            ObjectNode n = list.addObject();
            n.put("id", e.id());
            n.put("firstAt", e.firstAt().toString());
            n.put("lastAt", e.lastAt().toString());
            n.put("count", e.count());
            n.put("source", e.source());
            n.put("area", e.area());
            n.put("action", e.action());
            n.put("status", e.status());
            n.put("message", e.message());
            n.put("detail", e.detail());
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = file.resolveSibling(FILENAME + ".tmp");
        Files.writeString(tmp, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
