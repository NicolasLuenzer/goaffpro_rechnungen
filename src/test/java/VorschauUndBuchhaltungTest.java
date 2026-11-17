import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vorschau ohne Nummernverbrauch (ENTWURF mit Wasserzeichen, ohne XML) und die eigene Mail an die
 * Buchhaltung.
 */
class VorschauUndBuchhaltungTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tmp;

    private static Class<?> kindClass() throws Exception {
        return Class.forName("WebUiServer$DocumentKind");
    }

    private static Object kind(String name) throws Exception {
        for (Object constant : kindClass().getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) return constant;
        }
        throw new IllegalArgumentException(name);
    }

    private static Object invoke(Method m, Object... args) throws Exception {
        m.setAccessible(true);
        try {
            return m.invoke(null, args);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private static ObjectNode payment() throws Exception {
        return (ObjectNode) MAPPER.readTree("""
                {"id":"3573613","affiliate_id":"21009999","amount":"31.82","currency":"EUR","created_at":"2026-11-12T09:00:00Z",
                 "payment_details":{"account_number":"DE02120300000000202051","branch_code":"BYLADEM1001","account_name":"Katrin Muster"},
                 "transactions":[
                   {"entity_type":"orders","event_type":"insert","amount":"31.82","created_at":"2026-10-04T18:25:00Z","metadata":{"order_number":"#1","order_value":"100.00","commission_on":"80.00"}}]}
                """);
    }

    private static JsonNode affiliate() throws Exception {
        return MAPPER.readTree("""
                {"id":"21009999","name":"Katrin Muster","email":"katrin@example.com","address_1":"Musterweg 1",
                 "zip":"12345","city":"Musterstadt","country":"DE","tax_identification_number":"21/815/30140"}
                """);
    }

    private static Properties config() {
        Properties config = new Properties();
        config.setProperty("eInvoiceBuyerName", "S+R Linear Technology GmbH");
        config.setProperty("eInvoiceBuyerCountry", "DE");
        return config;
    }

    private Object draftOverview(JsonNode payment) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("buildCommissionOverview", JsonNode.class, JsonNode.class,
                Properties.class, String.class, String.class, kindClass(), int.class);
        ObjectNode response = MAPPER.createObjectNode();
        response.putArray("payments").add(payment);
        return invoke(m, response, affiliate(), config(), "ENTWURF", "", kind("GUTSCHRIFT"), 0);
    }

    private byte[] renderDocument(Path xmlPath, String number, Object overview, boolean draft) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("renderDocumentPdf", Path.class, Path.class, JsonNode.class,
                JsonNode.class, Properties.class, String.class, String.class, GutschriftTextTest.taxClass(),
                kindClass(), String.class, Class.forName("WebUiServer$CommissionOverview"), boolean.class);
        return (byte[]) invoke(m, tmp, xmlPath, payment(), affiliate(), config(), number, "Oktober 2026",
                GutschriftTextTest.taxTreatment(true), kind("GUTSCHRIFT"), "", overview, draft);
    }

    private Path createXml() throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("createZugferdInvoiceXml",
                Path.class, JsonNode.class, JsonNode.class, Properties.class,
                String.class, String.class, GutschriftTextTest.taxClass(), kindClass(), String.class);
        Path xml = tmp.resolve("gutschrift.xml");
        invoke(m, xml, payment(), affiliate(), config(), "GS-2026-0099", "Oktober 2026",
                GutschriftTextTest.taxTreatment(true), kind("GUTSCHRIFT"), "");
        return xml;
    }

    // ── Vorschau ──

    @Test
    void vorschauTraegtEntwurfUndWasserzeichenAufJederSeiteUndKeineXml() throws Exception {
        byte[] pdf = renderDocument(createXml(), "ENTWURF", draftOverview(payment()), true);
        try (PDDocument document = PDDocument.load(pdf)) {
            assertTrue(document.getNumberOfPages() >= 2, "Gutschrift und Provisionsübersicht");
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                // Schräg gesetzter Text kommt beim Auslesen mit zufälligen Leerzeichen heraus.
                String text = stripper.getText(document).replaceAll("\\s+", "");
                assertTrue(text.contains("ENTWURF") && text.contains("keingültigerBeleg"), "Wasserzeichen fehlt auf Seite " + page + ": " + text);
            }
            String all = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(all.contains("Gutschriftnummer ENTWURF"), all);
            assertTrue(all.contains("Anlage zur Gutschrift ENTWURF"), all);
            assertFalse(all.matches(".*GS-\\d{4}-\\d{4}.*"), "Eine Vorschau trägt keine echte Belegnummer");
            var names = document.getDocumentCatalog().getNames();
            assertTrue(names == null || names.getEmbeddedFiles() == null, "Eine Vorschau ist kein Beleg und bekommt keine XML");
            for (PDPage page : document.getPages()) {
                PDResources res = page.getResources();
                for (COSName name : res.getFontNames()) {
                    PDFont font = res.getFont(name);
                    assertTrue(font.isEmbedded() && font.getName().contains("LiberationSans"), font.getName());
                }
            }
        }
    }

    @Test
    void echterBelegHatKeinWasserzeichen() throws Exception {
        byte[] pdf = renderDocument(createXml(), "GS-2026-0099", null, false);
        try (PDDocument document = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertFalse(text.contains("ENTWURF"), text);
            assertEquals(List.of("factur-x.xml"),
                    List.copyOf(document.getDocumentCatalog().getNames().getEmbeddedFiles().getNames().keySet()));
        }
    }

    @Test
    void wasserzeichenLiegtHinterDemText() throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("appendDraftWatermark", String.class);
        Document doc = Jsoup.parse((String) invoke(m, "<html><head></head><body><p>Beleg</p></body></html>"));
        assertEquals("ENTWURF kein gültiger Beleg", doc.body().child(0).text());
        assertEquals("Beleg", doc.body().child(1).text(), "Der Beleg selbst folgt unverändert");
        String css = doc.head().select("style").html();
        assertTrue(css.contains("position: fixed"), "Nur fixed wiederholt sich auf jeder Seite");
        assertTrue(css.contains("z-index: -1"), "Das Wasserzeichen darf den Beleg nicht verdecken");
    }

    // ── Buchhaltung ──

    @Test
    void buchhaltungsMailNenntBelegBeraterinZahllaufUndAnhaenge() throws Exception {
        Method subject = WebUiServer.class.getDeclaredMethod("accountingMailSubject", kindClass(), String.class, String.class, String.class);
        assertEquals("Gutschrift GS-2026-0042-1 – Katrin Muster – Zahllauf 3573613",
                invoke(subject, kind("GUTSCHRIFT"), "GS-2026-0042-1", "Katrin Muster", "3573613"));

        Method body = WebUiServer.class.getDeclaredMethod("accountingMailBody", kindClass(), String.class, String.class,
                String.class, String.class, String.class, JsonNode.class, String.class, List.class);
        String text = (String) invoke(body, kind("GUTSCHRIFT"), "GS-2026-0042-1", "GS-2026-0042", "Katrin Muster", "3573613",
                "04.10.2026 bis 28.10.2026", payment(), "e-rechnung",
                List.of(tmp.resolve("gutschrift_GS-2026-0042-1_x.pdf"), tmp.resolve("gutschrift_GS-2026-0042-1_x.xml")));
        assertTrue(text.contains("anbei die Gutschrift GS-2026-0042-1 für Katrin Muster."), text);
        assertTrue(text.contains("Zahllauf: 3573613"), text);
        assertTrue(text.contains("Leistungszeitraum: 04.10.2026 bis 28.10.2026"), text);
        assertTrue(text.contains("Betrag (netto): 31,82 €"), text);
        assertTrue(text.contains("Ersetzt: Gutschrift GS-2026-0042"), "Die Buchhaltung muss sehen, was storniert wird");
        assertTrue(text.contains("ZUGFeRD-XML (EN 16931) liegt bei"), text);
        assertTrue(text.contains("Anhänge: gutschrift_GS-2026-0042-1_x.pdf, gutschrift_GS-2026-0042-1_x.xml"), text);
    }

    @Test
    void buchhaltungsMailOhneVorgaengerUndOhneSteuerkennung() throws Exception {
        Method html = WebUiServer.class.getDeclaredMethod("accountingMailHtml", kindClass(), String.class, String.class,
                String.class, String.class, String.class, JsonNode.class, String.class, List.class);
        String out = (String) invoke(html, kind("GUTSCHRIFT"), "GS-2026-0042", "", "Anna <b>&</b>", "1",
                "Oktober 2026", payment(), "ohne-steuerkennung", List.of(tmp.resolve("gutschrift.pdf")));
        assertFalse(out.contains("Ersetzt"), out);
        assertTrue(out.contains("weder Steuernummer noch USt-IdNr."), out);
        assertTrue(out.contains("Anna &lt;b&gt;&amp;&lt;/b&gt;"), "Namen aus GoAffPro werden maskiert");
    }
}
