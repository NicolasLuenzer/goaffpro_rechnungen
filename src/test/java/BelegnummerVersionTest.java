import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Versionierte Belegnummern: Ein Zahllauf hat genau eine Stammnummer, jede erneute Erzeugung
 * ist eine Version davon (GS-2026-0042-1, -2 ...) und verweist auf den Beleg, den sie ersetzt.
 */
class BelegnummerVersionTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tmp;

    // ── Register ──

    private DocumentNumberRegistry.Issued issue(String paymentId, String kind, AtomicInteger calls, String nextBase)
            throws Exception {
        return DocumentNumberRegistry.issue(tmp.resolve("data").resolve("sync.sqlite"), paymentId, kind,
                Optional::empty, () -> {
                    calls.incrementAndGet();
                    return nextBase;
                });
    }

    @Test
    void ersteErzeugungVergibtStammnummer() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        DocumentNumberRegistry.Issued issued = issue("2887600", "gutschrift", calls, "GS-2026-0042");

        assertEquals("GS-2026-0042", issued.number());
        assertEquals("", issued.replaces(), "Die Stammnummer ersetzt nichts");
        assertEquals(0, issued.version());
        assertEquals(1, calls.get(), "Genau eine fortlaufende Nummer gezogen");
    }

    @Test
    void erneuteErzeugungIstVersionDerStammnummer() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        issue("2887600", "gutschrift", calls, "GS-2026-0042");

        DocumentNumberRegistry.Issued v1 = issue("2887600", "gutschrift", calls, "GS-2026-0043");
        assertEquals("GS-2026-0042-1", v1.number());
        assertEquals("GS-2026-0042", v1.replaces());
        assertEquals(1, v1.version());

        DocumentNumberRegistry.Issued v2 = issue("2887600", "gutschrift", calls, "GS-2026-0044");
        assertEquals("GS-2026-0042-2", v2.number());
        assertEquals("GS-2026-0042-1", v2.replaces(), "Eine Version ersetzt den unmittelbar vorherigen Beleg");

        assertEquals(1, calls.get(), "Versionen ziehen keine neue fortlaufende Nummer");
    }

    @Test
    void zahllaeufeUndDokumentartenSindGetrennt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        issue("2887600", "gutschrift", calls, "GS-2026-0042");

        assertEquals("GS-2026-0043", issue("2887601", "gutschrift", calls, "GS-2026-0043").number());
        assertEquals("RE-2026-0001", issue("2887600", "rechnung", calls, "RE-2026-0001").number());
        assertEquals("GS-2026-0042-1", issue("2887600", "gutschrift", calls, "GS-2026-0099").number());
        assertEquals(3, calls.get());
    }

    @Test
    void altbestandWirdZurStammnummer() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        DocumentNumberRegistry.Issued issued = DocumentNumberRegistry.issue(tmp.resolve("sync.sqlite"), "2887600", "gutschrift",
                () -> Optional.of(new DocumentNumberRegistry.Existing("GS-2026-0001", "GS-2026-0007")),
                () -> {
                    calls.incrementAndGet();
                    return "GS-2026-0401";
                });

        assertEquals("GS-2026-0001-1", issued.number());
        assertEquals("GS-2026-0007", issued.replaces(), "Ersetzt wird der zuletzt vergebene alte Beleg");
        assertEquals(0, calls.get(), "Für den Altbestand wird keine neue Nummer gezogen");
    }

    @Test
    void registerBleibtNachNeuemOeffnenErhalten() throws Exception {
        Path db = tmp.resolve("sync.sqlite");
        DocumentNumberRegistry.issue(db, "1", "gutschrift", Optional::empty, () -> "GS-2026-0005");
        DocumentNumberRegistry.issue(db, "1", "gutschrift", Optional::empty, () -> "GS-2026-0006");

        DocumentNumberRegistry.Issued issued = DocumentNumberRegistry.issue(db, "1", "gutschrift",
                Optional::empty, () -> fail("Keine neue Nummer erwartet"));
        assertEquals("GS-2026-0005-2", issued.number());
    }

    @Test
    void doppelteStammnummerScheitertOhneEintrag() throws Exception {
        Path db = tmp.resolve("sync.sqlite");
        DocumentNumberRegistry.issue(db, "1", "gutschrift", Optional::empty, () -> "GS-2026-0005");

        assertThrows(Exception.class, () -> DocumentNumberRegistry.issue(db, "2", "gutschrift",
                Optional::empty, () -> "GS-2026-0005"), "Eine Stammnummer darf nur einem Zahllauf gehören");

        // Der gescheiterte Versuch hinterlässt nichts: Zahllauf 2 bekommt beim nächsten Mal eine neue Nummer.
        assertEquals("GS-2026-0006", DocumentNumberRegistry.issue(db, "2", "gutschrift",
                Optional::empty, () -> "GS-2026-0006").number());
    }

    // ── Altbestand aus Export-Ordnern und Versandprotokoll ──

    private static Object documentKind(String name) throws Exception {
        Class<?> kindClass = Class.forName("WebUiServer$DocumentKind");
        for (Object constant : kindClass.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) return constant;
        }
        throw new IllegalArgumentException(name);
    }

    @SuppressWarnings("unchecked")
    private static Optional<DocumentNumberRegistry.Existing> findExisting(Properties config, Path exportDir,
                                                                         String paymentId, String kindName) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("findExistingDocumentNumbers",
                Properties.class, Path.class, String.class, Class.forName("WebUiServer$DocumentKind"));
        m.setAccessible(true);
        return (Optional<DocumentNumberRegistry.Existing>) m.invoke(null, config, exportDir, paymentId, documentKind(kindName));
    }

    private static void touch(Path dir, String file) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(file), "x");
    }

    @Test
    void altbestandAusExportOrdnernUndVersandprotokoll() throws Exception {
        Path exports = tmp.resolve("exports");
        Path run = exports.resolve("export_2026-10-05_2887600_Katrin_Muster");
        touch(run, "provisionsnachweis_GS-2026-0012_20261005-101010.pdf");
        touch(run, "gutschrift_GS-2026-0012_20261005-101010.xml");
        touch(run, "provisionsnachweis_GS-2026-0003_20261005-090000.pdf");
        touch(run, "rechnung_RE-2026-0001_20261005-080000.pdf");
        // Anderer Zahllauf mit gleichem Präfix in der ID darf nicht mitzählen.
        touch(exports.resolve("export_2026-10-05_28876001_Andere"), "provisionsnachweis_GS-2026-0001_20261005-070000.pdf");

        Properties config = new Properties();
        config.setProperty("sentMailLogJson", MAPPER.writeValueAsString(java.util.List.of(
                java.util.Map.of("paymentId", "2887600", "pdfFile", "provisionsnachweis_GS-2026-0015_20261006-101010.pdf"),
                java.util.Map.of("paymentId", "9999999", "documentNumber", "GS-2026-0002"))));

        DocumentNumberRegistry.Existing existing = findExisting(config, exports, "2887600", "GUTSCHRIFT").orElseThrow();
        assertEquals("GS-2026-0003", existing.baseNumber(), "Früheste Gutschrift des Zahllaufs");
        assertEquals("GS-2026-0015", existing.lastNumber(), "Zuletzt vergebene Gutschrift, auch aus dem Versandprotokoll");

        DocumentNumberRegistry.Existing rechnung = findExisting(config, exports, "2887600", "RECHNUNG").orElseThrow();
        assertEquals("RE-2026-0001", rechnung.baseNumber(), "Nummernkreise werden nicht vermischt");

        assertTrue(findExisting(config, exports, "1234567", "GUTSCHRIFT").isEmpty());
        assertTrue(findExisting(new Properties(), tmp.resolve("gibt-es-nicht"), "2887600", "GUTSCHRIFT").isEmpty());
    }

    // ── Verweis auf den ersetzten Beleg ──

    private static JsonNode payment() throws Exception {
        return MAPPER.readTree("""
                {"id":"3573613","amount":"31.82","currency":"EUR","created_at":"2026-11-12T09:00:00Z",
                 "payment_details":{"account_number":"DE02120300000000202051","branch_code":"BYLADEM1001","account_name":"Katrin Muster"},
                 "transactions":[
                   {"tx_id":1,"entity_type":"orders","entity_id":11,"event_type":"insert","amount":"31.82","created_at":"2026-10-04T18:25:00Z","metadata":{"order_number":"#1"}}]}
                """);
    }

    private static JsonNode affiliate() throws Exception {
        return MAPPER.readTree("""
                {"id":"21009999","name":"Katrin Muster","email":"katrin@example.com","phone":"+49123",
                 "address_1":"Musterweg 1","zip":"12345","city":"Musterstadt","country":"DE",
                 "tax_identification_number":"012/345/67890"}
                """);
    }

    private static Properties config() {
        Properties config = new Properties();
        config.setProperty("eInvoiceBuyerName", "S+R Linear Technology GmbH");
        config.setProperty("eInvoiceBuyerStreet", "Bleidenröder Str. 11");
        config.setProperty("eInvoiceBuyerZip", "35315");
        config.setProperty("eInvoiceBuyerCity", "Homberg/Ohm");
        config.setProperty("eInvoiceBuyerCountry", "DE");
        config.setProperty("eInvoiceBuyerVatId", "DE459084219");
        return config;
    }

    private String createXml(String number, String replaces) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("createZugferdInvoiceXml",
                Path.class, JsonNode.class, JsonNode.class, Properties.class,
                String.class, String.class, GutschriftTextTest.taxClass(),
                Class.forName("WebUiServer$DocumentKind"), String.class);
        m.setAccessible(true);
        Path xml = tmp.resolve("gutschrift.xml");
        m.invoke(null, xml, payment(), affiliate(), config(), number, "Oktober 2026",
                GutschriftTextTest.taxTreatment(true), documentKind("GUTSCHRIFT"), replaces);
        return Files.readString(xml, StandardCharsets.UTF_8);
    }

    @Test
    void xmlVerweistAufErsetztenBeleg() throws Exception {
        String xml = createXml("GS-2026-0042-1", "GS-2026-0042");

        assertTrue(xml.contains("<ram:ID>GS-2026-0042-1</ram:ID>"));
        assertTrue(xml.contains("<ram:InvoiceReferencedDocument><ram:IssuerAssignedID>GS-2026-0042</ram:IssuerAssignedID></ram:InvoiceReferencedDocument>"),
                "BT-25 nennt den ersetzten Beleg");
        int summation = xml.indexOf("</ram:SpecifiedTradeSettlementHeaderMonetarySummation>");
        int reference = xml.indexOf("<ram:InvoiceReferencedDocument>");
        int settlementEnd = xml.indexOf("</ram:ApplicableHeaderTradeSettlement>");
        assertTrue(summation < reference && reference < settlementEnd,
                "CII-Reihenfolge: InvoiceReferencedDocument folgt auf die Summen");
    }

    @Test
    void xmlOhneVorgaengerHatKeinenVerweis() throws Exception {
        String xml = createXml("GS-2026-0042", "");
        assertFalse(xml.contains("InvoiceReferencedDocument"));
        assertFalse(xml.contains("{{"), "Kein Platzhalter darf übrig bleiben");
    }

    private static String renderDefault(String kindName, String number) throws Exception {
        Class<?> kindClass = Class.forName("WebUiServer$DocumentKind");
        Method template = WebUiServer.class.getDeclaredMethod("defaultPdfViewTemplate", kindClass);
        template.setAccessible(true);
        Method render = WebUiServer.class.getDeclaredMethod("renderEInvoicePdfViewHtml",
                String.class, JsonNode.class, JsonNode.class, Properties.class,
                String.class, String.class, GutschriftTextTest.taxClass(), kindClass);
        render.setAccessible(true);
        return (String) render.invoke(null, template.invoke(null, documentKind(kindName)), payment(), affiliate(), config(),
                number, "Oktober 2026", GutschriftTextTest.taxTreatment(true), documentKind(kindName));
    }

    private static String insertRow(String html, String number, String replaces, String kindName) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("insertReplacesDocumentRow",
                String.class, String.class, String.class, Class.forName("WebUiServer$DocumentKind"));
        m.setAccessible(true);
        return (String) m.invoke(null, html, number, replaces, documentKind(kindName));
    }

    private static void assertRowBelowNumber(String html, String number, String label, String replaces) {
        Document doc = Jsoup.parse(html);
        Element numberRow = doc.select("tr").stream()
                .filter(tr -> tr.select("> td").size() >= 2 && number.equals(tr.select("> td").last().text().trim()))
                .findFirst().orElseThrow(() -> new AssertionError("Nummernzeile fehlt"));
        Element next = numberRow.nextElementSibling();
        assertNotNull(next, "Unter der Nummernzeile fehlt der Hinweis");
        assertEquals(label, next.select("> td").first().text().trim());
        assertEquals(replaces, next.select("> td").last().text().trim());
    }

    @Test
    void hinweiszeileInDerGutschriftVorlage() throws Exception {
        String html = insertRow(renderDefault("GUTSCHRIFT", "GS-2026-0042-1"), "GS-2026-0042-1", "GS-2026-0042", "GUTSCHRIFT");
        assertRowBelowNumber(html, "GS-2026-0042-1", "Ersetzt Gutschrift", "GS-2026-0042");
    }

    @Test
    void hinweiszeileInDerRechnungVorlage() throws Exception {
        String html = insertRow(renderDefault("RECHNUNG", "RE-2026-0003-2"), "RE-2026-0003-2", "RE-2026-0003-1", "RECHNUNG");
        assertRowBelowNumber(html, "RE-2026-0003-2", "Ersetzt Rechnung", "RE-2026-0003-1");
    }

    @Test
    void hinweisOhneNummernzeileStehtOben() throws Exception {
        String html = insertRow("<html><body><p>Eigene Vorlage GS-2026-0042-1</p></body></html>",
                "GS-2026-0042-1", "GS-2026-0042", "GUTSCHRIFT");
        Element first = Jsoup.parse(html).body().child(0);
        assertEquals("Ersetzt Gutschrift Nr. GS-2026-0042", first.text());
    }

    @Test
    void ohneVorgaengerBleibtDasHtmlUnveraendert() throws Exception {
        String rendered = renderDefault("GUTSCHRIFT", "GS-2026-0042");
        assertSame(rendered, insertRow(rendered, "GS-2026-0042", "", "GUTSCHRIFT"));
    }

    @Test
    void ePdfZeigtDenErsetztenBeleg() throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("createEInvoicePdfWithEmbeddedXml",
                Path.class, Path.class, JsonNode.class, JsonNode.class, Properties.class,
                String.class, String.class, GutschriftTextTest.taxClass(),
                Class.forName("WebUiServer$DocumentKind"), String.class);
        m.setAccessible(true);
        Path pdf = tmp.resolve("gutschrift.pdf");
        try {
            m.invoke(null, pdf, null, payment(), affiliate(), config(), "GS-2026-0042-1", "Oktober 2026",
                    GutschriftTextTest.taxTreatment(true), documentKind("GUTSCHRIFT"), "GS-2026-0042");
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(text.contains("GS-2026-0042-1"), text);
            assertTrue(text.contains("Ersetzt Gutschrift GS-2026-0042"), text);
        }
    }
}
