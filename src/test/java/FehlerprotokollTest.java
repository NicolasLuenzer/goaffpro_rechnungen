import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fehlerprotokoll: Einträge landen dauerhaft in einer Datei, Wiederholungen zählen hoch,
 * Zugangsdaten werden geschwärzt, und ein kaputtes Protokoll legt nie die Anwendung lahm.
 */
class FehlerprotokollTest {

    @TempDir
    Path tmp;

    private static final Instant T0 = Instant.parse("2026-11-23T09:34:39Z");

    private ErrorLog log(Path file, Instant now) {
        return new ErrorLog(() -> file, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static void record(ErrorLog log, String message) {
        log.record("Server", "Gutschriftservice", "GET /api/provisionen-goaffpro/invoice-preview-pdf?paymentId=1",
                409, message, "", List.of());
    }

    @Test
    void neuesteZuerstUndDauerhaft() {
        Path file = tmp.resolve("data").resolve(ErrorLog.FILENAME);
        record(log(file, T0), "erster Fehler");
        record(log(file, T0.plusSeconds(60)), "zweiter Fehler");

        List<ErrorLog.Entry> entries = log(file, T0.plusSeconds(120)).entries();
        assertEquals(2, entries.size());
        assertEquals("zweiter Fehler", entries.get(0).message());
        assertEquals("erster Fehler", entries.get(1).message());
        assertEquals(409, entries.get(0).status());
        assertTrue(Files.exists(file), "Das Verzeichnis wird bei Bedarf angelegt");
        assertNotEquals(entries.get(0).id(), entries.get(1).id());
    }

    @Test
    void wiederholungZaehltHochStattNeuerZeile() {
        Path file = tmp.resolve(ErrorLog.FILENAME);
        record(log(file, T0), "Zahllauf gesperrt");
        record(log(file, T0.plusSeconds(60)), "etwas anderes");
        record(log(file, T0.plusSeconds(5 * 60)), "Zahllauf gesperrt");

        List<ErrorLog.Entry> entries = log(file, T0).entries();
        assertEquals(2, entries.size());
        ErrorLog.Entry merged = entries.get(0);
        assertEquals("Zahllauf gesperrt", merged.message(), "Die Wiederholung rückt nach oben");
        assertEquals(2, merged.count());
        assertEquals(T0, merged.firstAt());
        assertEquals(T0.plusSeconds(5 * 60), merged.lastAt());
    }

    @Test
    void nachLaengererPauseNeueZeile() {
        Path file = tmp.resolve(ErrorLog.FILENAME);
        record(log(file, T0), "Zahllauf gesperrt");
        record(log(file, T0.plus(ErrorLog.MERGE_WINDOW).plusSeconds(1)), "Zahllauf gesperrt");

        List<ErrorLog.Entry> entries = log(file, T0).entries();
        assertEquals(2, entries.size());
        assertEquals(1, entries.get(0).count());
    }

    @Test
    void zugangsdatenWerdenGeschwaerzt() {
        Path file = tmp.resolve(ErrorLog.FILENAME);
        String key = "ak_live_1234567890abcdef";
        log(file, T0).record("Server", "Auswertungen", "GET /api/analytics/fetch", 500,
                "Anfrage mit " + key + " abgelehnt", "Header x-goaffpro-access-token: " + key,
                java.util.Arrays.asList(key, null, "", "abc"));

        ErrorLog.Entry entry = log(file, T0).entries().get(0);
        assertEquals("Anfrage mit [entfernt] abgelehnt", entry.message());
        assertEquals("Header x-goaffpro-access-token: [entfernt]", entry.detail());
        assertFalse(String.join(" ", entry.message(), entry.detail()).contains(key));
        assertEquals("abc wird nicht geschwärzt", ErrorLog.redact("abc wird nicht geschwärzt", List.of("abc")),
                "Kurze Werte kämen auch in harmlosen Wörtern vor");
    }

    @Test
    void begrenztAufHoechstzahlUndKuerztLangeTexte() {
        Path file = tmp.resolve(ErrorLog.FILENAME);
        for (int i = 0; i < ErrorLog.MAX_ENTRIES + 5; i++) {
            record(log(file, T0.plusSeconds(i)), "Fehler " + i);
        }
        record(log(file, T0.plusSeconds(10_000)), "x".repeat(ErrorLog.MAX_MESSAGE_CHARS + 50));

        List<ErrorLog.Entry> entries = log(file, T0).entries();
        assertEquals(ErrorLog.MAX_ENTRIES, entries.size());
        assertTrue(entries.get(0).message().endsWith("(gekürzt)"));
        assertEquals("Fehler 6", entries.get(entries.size() - 1).message(), "Die ältesten fallen heraus");
    }

    @Test
    void leerenMerktSichDenZeitpunkt() throws Exception {
        Path file = tmp.resolve(ErrorLog.FILENAME);
        record(log(file, T0), "Fehler");
        log(file, T0.plusSeconds(30)).clear();

        ErrorLog after = log(file, T0.plusSeconds(60));
        assertTrue(after.entries().isEmpty());
        assertEquals(T0.plusSeconds(30), after.clearedAt());
        record(after, "neuer Fehler");
        assertEquals(1, after.entries().size());
        assertEquals(T0.plusSeconds(30), after.clearedAt(), "Neue Einträge lassen den Zeitpunkt stehen");
    }

    @Test
    void kaputteDateiWirdBeiseitegelegt() throws Exception {
        Path file = tmp.resolve(ErrorLog.FILENAME);
        Files.writeString(file, "{ das ist kein JSON");
        record(log(file, T0), "Fehler nach Absturz");

        assertEquals(1, log(file, T0).entries().size());
        try (var files = Files.list(tmp)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith(ErrorLog.FILENAME + ".defekt-")),
                    "Die kaputte Datei bleibt zur Ansicht erhalten");
        }
    }

    @Test
    void wirftNieAuchWennDieDateiNichtBeschreibbarIst() throws Exception {
        Path blocker = tmp.resolve("keinordner");
        Files.writeString(blocker, "eine Datei, kein Verzeichnis");
        ErrorLog broken = log(blocker.resolve(ErrorLog.FILENAME), T0);

        assertDoesNotThrow(() -> record(broken, "Fehler"));
        assertTrue(broken.entries().isEmpty());
        ErrorLog failingSupplier = new ErrorLog(() -> { throw new IllegalStateException("kein Pfad"); });
        assertDoesNotThrow(() -> record(failingSupplier, "Fehler"));
    }

    @Test
    void klartextEnthaeltAllesZurFehlersuche() {
        Path file = tmp.resolve(ErrorLog.FILENAME);
        log(file, T0).record("Server", "Gutschriftservice", "POST /api/provisionen-goaffpro/invoice-details-pdf", 500,
                "NullPointerException", "java.lang.NullPointerException\n\tat WebUiServer.x(WebUiServer.java:1)", List.of());
        log(file, T0.plusSeconds(90)).record("Server", "Gutschriftservice", "POST /api/provisionen-goaffpro/invoice-details-pdf", 500,
                "NullPointerException", "", List.of());

        String text = ErrorLog.formatText(log(file, T0).entries(), List.of("Version:  20261123-175"));
        assertTrue(text.startsWith("VEMMiNA Assistent – Fehlerprotokoll\nVersion:  20261123-175\n"), text);
        assertTrue(text.contains("[23.11.2026 10:34:39] Server · Gutschriftservice · HTTP 500 · 2× bis 23.11.2026 10:36:09"), text);
        assertTrue(text.contains("\n  POST /api/provisionen-goaffpro/invoice-details-pdf\n  NullPointerException\n"), text);
        assertTrue(text.contains("\n    \tat WebUiServer.x(WebUiServer.java:1)\n"), text);
        assertTrue(ErrorLog.formatText(List.of(), List.of()).contains("Keine Fehler protokolliert."));
    }

    // ── Anbindung im Server ──

    private static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    @Test
    void bereichNachApiPfad() throws Exception {
        Class<?>[] types = {String.class};
        assertEquals("Gutschriftservice", invoke("errorAreaFor", types, "/api/provisionen-goaffpro/invoice-preview-pdf"));
        assertEquals("Gutschriftservice", invoke("errorAreaFor", types, "/api/settings/recipient-mode"));
        assertEquals("Einstellungen", invoke("errorAreaFor", types, "/api/settings"));
        assertEquals("Auswertungen", invoke("errorAreaFor", types, "/api/analytics/advisor-detail"));
        assertEquals("Validierungen", invoke("errorAreaFor", types, "/api/validation/advisors"));
        assertEquals("GoAffPro Sync", invoke("errorAreaFor", types, "/api/sync/run"));
        assertEquals("Datensicherung", invoke("errorAreaFor", types, "/api/backup/export"));
        assertEquals("Allgemein", invoke("errorAreaFor", types, "/api/version"));
    }

    @Test
    void anfragezeileOhneZugangsdaten() throws Exception {
        Class<?>[] types = {String.class, URI.class};
        assertEquals("GET /api/provisionen-goaffpro/invoice-preview-pdf?paymentId=3597878",
                invoke("errorActionFor", types, "GET", URI.create("/api/provisionen-goaffpro/invoice-preview-pdf?paymentId=3597878")));
        assertEquals("GET /api/x?apiKey=[entfernt]&id=1&smtpPassword=[entfernt]",
                invoke("errorActionFor", types, "GET", URI.create("/api/x?apiKey=geheim123&id=1&smtpPassword=pw")));
        assertEquals("POST /api/settings", invoke("errorActionFor", types, "POST", URI.create("/api/settings")));
    }
}
