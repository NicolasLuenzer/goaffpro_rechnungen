import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.xml.DomXmpParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Provisionsübersicht als Anlage: im Gutschrift-PDF ab Seite 2, ohne PDF/A-3 oder Factur-X zu
 * beschädigen, im Stil der Gutschrift; eigenständig nur, wenn kein Gutschrift-PDF entsteht.
 */
class ProvisionsuebersichtTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tmp;

    // ── Reflection-Helfer ──

    private static Class<?> kindClass() throws Exception {
        return Class.forName("WebUiServer$DocumentKind");
    }

    private static Class<?> overviewClass() throws Exception {
        return Class.forName("WebUiServer$CommissionOverview");
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

    private static Object overview(JsonNode payment, JsonNode affiliate, Properties config, String number,
                                   String replaces, String kindName, int hidden) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("buildCommissionOverview", JsonNode.class, JsonNode.class,
                Properties.class, String.class, String.class, kindClass(), int.class);
        ObjectNode response = MAPPER.createObjectNode();
        response.putArray("payments").add(payment);
        return invoke(m, response, affiliate, config, number, replaces, kind(kindName), hidden);
    }

    private static String bodyHtml(Object overview) throws Exception {
        Method accessor = overviewClass().getDeclaredMethod("bodyHtml");
        accessor.setAccessible(true);
        return (String) accessor.invoke(overview);
    }

    private Path createXml(JsonNode payment, JsonNode affiliate, Properties config, String number) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("createZugferdInvoiceXml",
                Path.class, JsonNode.class, JsonNode.class, Properties.class,
                String.class, String.class, GutschriftTextTest.taxClass(), kindClass(), String.class);
        Path xml = tmp.resolve("gutschrift.xml");
        invoke(m, xml, payment, affiliate, config, number, "Oktober 2026", GutschriftTextTest.taxTreatment(true),
                kind("GUTSCHRIFT"), "");
        return xml;
    }

    private Path createDocumentPdf(Path xmlOrNull, JsonNode payment, JsonNode affiliate, Properties config,
                                   String number, String kindName, Object overview) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("createEInvoicePdfWithEmbeddedXml",
                Path.class, Path.class, JsonNode.class, JsonNode.class, Properties.class,
                String.class, String.class, GutschriftTextTest.taxClass(), kindClass(), String.class, overviewClass());
        Path pdf = tmp.resolve(kindName.toLowerCase() + ".pdf");
        invoke(m, pdf, xmlOrNull, payment, affiliate, config, number, "Oktober 2026",
                GutschriftTextTest.taxTreatment(true), kind(kindName), "", overview);
        return pdf;
    }

    private Path createStandalonePdf(Object overview) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("createCommissionOverviewPdf", Path.class, overviewClass());
        Path pdf = tmp.resolve("provisionsnachweis.pdf");
        invoke(m, pdf, overview);
        return pdf;
    }

    private static String appendOverview(String html, Object overview) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("appendCommissionOverview", String.class, overviewClass());
        return (String) invoke(m, html, overview);
    }

    private static String pageText(PDDocument document, int from, int to) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(from);
        stripper.setEndPage(to);
        return stripper.getText(document).replaceAll("\\s+", " ");
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + needle.length())) count++;
        return count;
    }

    private static void assertOnlyEmbeddedLiberationSans(PDDocument document) throws Exception {
        List<PDFont> fonts = new ArrayList<>();
        for (PDPage page : document.getPages()) {
            PDResources res = page.getResources();
            for (COSName name : res.getFontNames()) fonts.add(res.getFont(name));
        }
        assertFalse(fonts.isEmpty());
        for (PDFont font : fonts) {
            assertTrue(font.isEmbedded(), "Nicht eingebettet: " + font.getName());
            assertTrue(font.getName().contains("LiberationSans"), "Unerwartete Schrift: " + font.getName());
        }
    }

    private static void assertA4(PDPage page) {
        PDRectangle box = page.getMediaBox();
        assertEquals(PDRectangle.A4.getWidth(), box.getWidth(), 1.0, "A4-Breite erwartet");
        assertEquals(PDRectangle.A4.getHeight(), box.getHeight(), 1.0, "A4-Höhe erwartet");
    }

    private static int pdfaPart(PDDocument document) throws Exception {
        byte[] raw = document.getDocumentCatalog().getMetadata().toByteArray();
        XMPMetadata xmp = new DomXmpParser().parse(new ByteArrayInputStream(raw));
        return xmp.getPDFIdentificationSchema().getPart();
    }

    // ── Testdaten: drei Oktober-Provisionen über 31,82 € ──

    private static ObjectNode payment() throws Exception {
        return (ObjectNode) MAPPER.readTree("""
                {"id":"3573613","affiliate_id":"21009999","amount":"31.82","currency":"EUR","created_at":"2026-11-12T09:00:00Z",
                 "payment_details":{"account_number":"DE02120300000000202051","branch_code":"BYLADEM1001","account_name":"Katrin Muster","paid_via":"manual"},
                 "transactions":[
                   {"tx_id":3,"entity_type":"orders","entity_id":13,"event_type":"insert","amount":"15.46","created_at":"2026-10-28T20:55:00Z","metadata":{"order_number":"#3","order_value":"23.00","commission_on":"19.33"}},
                   {"tx_id":1,"entity_type":"orders","entity_id":11,"event_type":"insert","amount":"6.54","created_at":"2026-10-04T18:25:00Z","metadata":{"order_number":"#1","order_value":"40.82","commission_on":"32.70"}},
                   {"tx_id":2,"entity_type":"orders","entity_id":12,"event_type":"insert","amount":"9.82","created_at":"2026-10-11T17:24:00Z","metadata":{"order_number":"#2","order_value":"58.43","commission_on":"49.10"}}]}
                """);
    }

    private static ObjectNode affiliate(String taxNumber) throws Exception {
        return (ObjectNode) MAPPER.readTree("""
                {"id":"21009999","name":"Katrin Muster","email":"katrin@example.com","phone":"+49123",
                 "address_1":"Musterweg 1","zip":"12345","city":"Musterstadt","country":"DE",
                 "tax_identification_number":"%s","ref_code":"katrin","status":"approved"}
                """.formatted(taxNumber));
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

    private static ObjectNode tx(String event, double amount, String created, String orderNo, String orderValue, String bmg) {
        ObjectNode tx = MAPPER.createObjectNode();
        tx.put("entity_type", "orders");
        tx.put("event_type", event);
        tx.put("amount", String.valueOf(amount));
        tx.put("created_at", created);
        ObjectNode md = tx.putObject("metadata");
        md.put("order_number", orderNo);
        if (orderValue != null) md.put("order_value", orderValue);
        if (bmg != null) md.put("commission_on", bmg);
        return tx;
    }

    // ── Gutschrift mit Anlage ──

    @Test
    void gutschriftEnthaeltUebersichtAlsAnlageOhneFacturXZuBeschaedigen() throws Exception {
        ObjectNode payment = payment();
        ObjectNode affiliate = affiliate("21/815/30140");
        Properties config = config();
        Object overview = overview(payment, affiliate, config, "GS-2026-0099", "", "GUTSCHRIFT", 0);
        Path pdf = createDocumentPdf(createXml(payment, affiliate, config, "GS-2026-0099"), payment, affiliate, config,
                "GS-2026-0099", "GUTSCHRIFT", overview);

        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            int pages = document.getNumberOfPages();
            assertTrue(pages >= 2, "Seite 1 Gutschrift, ab Seite 2 Provisionsübersicht");
            String page1 = pageText(document, 1, 1);
            String page2 = pageText(document, 2, 2);
            String appendix = pageText(document, 2, pages);
            assertTrue(page1.contains("Gutschriftnummer GS-2026-0099"), page1);
            assertFalse(page1.contains("Provisionsübersicht"), "Die Anlage beginnt auf einer neuen Seite");
            assertTrue(page1.contains("Seite 1 von " + pages), page1);
            assertTrue(page2.contains("Anlage zur Gutschrift GS-2026-0099 Provisionsübersicht"), page2);
            assertTrue(appendix.contains("#1") && appendix.contains("#2") && appendix.contains("#3"), appendix);
            assertTrue(appendix.contains("31,82 €"), appendix);
            assertTrue(appendix.contains("Seite " + pages + " von " + pages), appendix);
            for (PDPage page : document.getPages()) assertA4(page);

            assertFalse(document.getDocumentCatalog().getOutputIntents().isEmpty(), "PDF/A verlangt einen OutputIntent");
            assertOnlyEmbeddedLiberationSans(document);
            assertEquals(3, pdfaPart(document));
            assertEquals(List.of("factur-x.xml"),
                    new ArrayList<>(document.getDocumentCatalog().getNames().getEmbeddedFiles().getNames().keySet()),
                    "Die Anlage-Seiten dürfen keine weitere eingebettete Datei mitbringen");
            COSArray af = (COSArray) document.getDocumentCatalog().getCOSObject().getDictionaryObject(COSName.AF);
            assertEquals(1, af.size());
            assertEquals("Gutschrift GS-2026-0099", document.getDocumentInformation().getTitle());
        }
    }

    @Test
    void anlageHaengtAuchAnEigeneVorlageOhneHeadAn() throws Exception {
        Object overview = overview(payment(), affiliate(""), config(), "GS-1", "", "GUTSCHRIFT", 0);
        String html = appendOverview("<div><table><tr><td>Gutschriftnummer</td><td>GS-1</td></tr></table></div>", overview);
        Document doc = Jsoup.parse(html);
        assertTrue(doc.head().select("style").html().contains(".pu-grid"), "Das CSS der Anlage gehört in den head");
        Element appendix = doc.body().selectFirst("div.pu-appendix");
        assertNotNull(appendix);
        assertSame(doc.body().children().last(), appendix, "Die Anlage steht am Ende des Belegs");
        assertEquals("Gutschriftnummer", doc.body().selectFirst("td").text(), "Der Beleg selbst bleibt unverändert vorn");
    }

    @Test
    void ohneAnlageBleibtDasHtmlUnveraendert() throws Exception {
        assertEquals("<p>x</p>", appendOverview("<p>x</p>", null));
    }

    // ── Eigenständige Übersicht (E-Gutschrift abgeschaltet) ──

    @Test
    void eigenstaendigeUebersichtIstPdfA3InA4() throws Exception {
        Path pdf = createStandalonePdf(overview(payment(), affiliate("21/815/30140"), config(), "GS-2026-0099", "", "GUTSCHRIFT", 0));
        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            assertA4(document.getPage(0));
            assertOnlyEmbeddedLiberationSans(document);
            assertEquals(3, pdfaPart(document));
            assertFalse(document.getDocumentCatalog().getOutputIntents().isEmpty());
            var names = document.getDocumentCatalog().getNames();
            assertTrue(names == null || names.getEmbeddedFiles() == null, "Ohne Gutschrift gibt es keine eingebettete XML");
            assertEquals("Provisionsübersicht GS-2026-0099", document.getDocumentInformation().getTitle());
            String text = pageText(document, 1, document.getNumberOfPages());
            assertTrue(text.contains("Anlage zur Gutschrift GS-2026-0099"), text);
            assertTrue(text.contains("Seite 1 von " + document.getNumberOfPages()), text);
        }
    }

    // ── Inhalt ──

    @Test
    void kennzahlenStammdatenUndHinweise() throws Exception {
        String html = bodyHtml(overview(payment(), affiliate("21/815/30140"), config(), "GS-2026-0099", "", "GUTSCHRIFT", 0));
        Document doc = Jsoup.parse(html);
        String text = doc.text();

        assertTrue(text.contains("Zahllauf-ID 3573613"), text);
        assertTrue(text.contains("Empfänger (Kontoinhaber) Katrin Muster"), text);
        assertTrue(text.contains("Zahlmethode SEPA (via manual)"), text);
        assertTrue(text.contains("IBAN (maskiert) DE02 **** **** **** 2051"), text);
        assertFalse(text.contains("DE02120300000000202051"), "Die IBAN steht in der Anlage nur maskiert");
        assertTrue(text.contains("Zeitraum der Transaktionen 04.10.2026 20:25 bis 28.10.2026 21:55"), text);
        assertTrue(text.contains("Anzahl Transaktionen im Zahllauf 3"), text);
        assertTrue(text.contains("Summe Bestellwert* 122,25 €"), text);
        assertTrue(text.contains("Summe Bemessungsgrundlage 101,13 €"), text);
        assertTrue(text.contains("Summe Provision 31,82 €"), text);
        assertTrue(text.contains("Gutschriftbetrag (netto) 31,82 €"), text);
        assertFalse(text.contains("Auszahlungsbetrag"), "Auf Seite 1 heißt so der Bruttobetrag");
        assertTrue(text.contains("Status freigegeben"), text);
        assertTrue(text.contains("Steuernummer 21/815/30140"), text);
        assertTrue(text.contains("Prüfen Sie, ob die Gutschrift korrekt ausgestellt wurde"), text);
        assertFalse(text.matches(".*\\b(ihr|seid)\\b.*"), "Die Hinweise siezen wie die Gutschrift");
        assertTrue(text.contains("als Anlage zur Gutschrift GS-2026-0099 gemäß § 14 UStG"), text);
        assertTrue(text.contains("info@vemmina.de"), "Kontakt wie in der Fußzeile der Gutschrift");
        assertTrue(text.matches(".*Inhalts-Hash \\(SHA-256\\): [0-9a-f]{64}.*"), text);
        assertFalse(text.contains("Storno-Buchungen mindern"), "Erklärung nur, wenn es Stornos oder Änderungen gibt");
        assertFalse(text.contains("Ersetzt"), "Ohne Vorgänger kein Ersetzt-Hinweis");
    }

    @Test
    void einzeltransaktionenChronologischMitSummenzeile() throws Exception {
        Document doc = Jsoup.parse(bodyHtml(overview(payment(), affiliate(""), config(), "GS-1", "", "GUTSCHRIFT", 0)));
        Element txTable = doc.select("table.pu-grid").get(1);
        List<String> orderNumbers = new ArrayList<>();
        for (Element row : txTable.select("tbody tr:not(.pu-total)")) orderNumbers.add(row.child(2).text());
        assertEquals(List.of("#1", "#2", "#3"), orderNumbers, "Chronologisch, nicht in API-Reihenfolge");
        List<String> total = txTable.select("tr.pu-total td").eachText();
        assertEquals(List.of("Summe", "122,25 €", "101,13 €", "31,82 €"), total);
    }

    @Test
    void stornoMindertBestellwertUndAenderungTraegtKeinen() throws Exception {
        ObjectNode payment = payment();
        ArrayNode txs = payment.putArray("transactions");
        txs.add(tx("insert", 10.0, "2026-10-01T10:00:00Z", "#100", "100.00", "80.00"));
        txs.add(tx("delete", -10.0, "2026-10-02T10:00:00Z", "#100", "100.00", "80.00"));
        ObjectNode update = tx("update", -5.0, "2026-10-03T10:00:00Z", "#200", null, null);
        ((ObjectNode) update.get("metadata")).put("new_status", "rejected");
        txs.add(update);
        payment.put("amount", "-5.00");

        Document doc = Jsoup.parse(bodyHtml(overview(payment, affiliate(""), config(), "GS-1", "", "GUTSCHRIFT", 0)));
        Element txTable = doc.select("table.pu-grid").get(1);
        var rows = txTable.select("tbody tr:not(.pu-total)");
        assertEquals("Direkt (Order) · Storno", rows.get(1).child(1).text());
        assertEquals("-100,00 €", rows.get(1).child(3).text(), "Storno zählt den Bestellwert negativ");
        assertEquals("-80,00 €", rows.get(1).child(4).text());
        assertEquals("Direkt (Order) · Änderung (abgelehnt)", rows.get(2).child(1).text());
        assertEquals("–", rows.get(2).child(3).text(), "Eine Änderung trägt keinen Bestellwert");
        assertEquals("–", rows.get(2).child(4).text());
        assertEquals(List.of("Summe", "0,00 €", "0,00 €", "-5,00 €"), txTable.select("tr.pu-total td").eachText());
        assertTrue(doc.text().contains("Storno-Buchungen mindern Bestellwert und Bemessungsgrundlage"), doc.text());
    }

    @Test
    void rundungsdifferenzZeigtKeinMinusNull() throws Exception {
        ObjectNode payment = payment();
        ((ObjectNode) payment.get("transactions").get(1)).put("amount", "6.5434"); // Summe 31,8234 > 31,82
        String html = bodyHtml(overview(payment, affiliate(""), config(), "GS-1", "", "GUTSCHRIFT", 0));
        assertTrue(Jsoup.parse(html).text().contains("Rundungsdifferenz (Betrag – Summe Provision) 0,00 €"), html);
        assertFalse(html.contains("-0,00"), "Fließkommareste dürfen nicht als -0,00 € erscheinen");
    }

    @Test
    void ausgeblendeteStornosUndErsetztHinweis() throws Exception {
        String text = Jsoup.parse(bodyHtml(overview(payment(), affiliate(""), config(), "GS-1-1", "GS-1", "GUTSCHRIFT", 70))).text();
        assertTrue(text.contains("Nicht aufgeführt (vor 01.10.2026) 70 stornierte Buchungen, Saldo 0,00 €"), text);
        assertTrue(text.contains("Ersetzt Gutschrift GS-1"), text);
    }

    @Test
    void ustIdUndSonderzeichenWerdenSauberDargestellt() throws Exception {
        ObjectNode affiliate = affiliate("DE 123 456 789");
        affiliate.put("name", "Anna <b>&</b> Co");
        String html = bodyHtml(overview(payment(), affiliate, config(), "GS-1", "", "GUTSCHRIFT", 0));
        assertFalse(html.contains("<b>&</b>"), "Werte aus GoAffPro werden maskiert");
        assertTrue(html.contains("Anna &lt;b&gt;&amp;&lt;/b&gt; Co"), html);
        assertTrue(Jsoup.parse(html).text().contains("USt-IdNr DE 123 456 789"), "Beschriftung wie auf Seite 1");
    }

    @Test
    void leererZahllaufOhneFragezeichenUndPlatzhalter() throws Exception {
        ObjectNode payment = payment();
        payment.putArray("transactions");
        Object overview = overview(payment, affiliate(""), config(), "GS-1", "", "GUTSCHRIFT", 0);
        assertTrue(Jsoup.parse(bodyHtml(overview)).text().contains("Keine Einzeltransaktionen im Zahllauf."));
        try (PDDocument document = PDDocument.load(createStandalonePdf(overview).toFile())) {
            String text = pageText(document, 1, document.getNumberOfPages());
            assertFalse(text.contains("?"), "Fehlende Glyphen erscheinen als ?: " + text);
            assertFalse(text.contains("{{"), text);
            assertTrue(text.contains("Zeitraum der Transaktionen k. A."), text);
        }
    }

    @Test
    void langeListeLaeuftUeberMehrereSeitenMitWiederholtemTabellenkopf() throws Exception {
        ObjectNode payment = payment();
        ArrayNode txs = payment.putArray("transactions");
        for (int i = 0; i < 80; i++) {
            txs.add(tx("insert", 1.0, String.format("2026-10-%02dT10:%02d:00Z", 1 + i % 28, i % 60), "#" + (1000 + i), "10.00", "8.00"));
        }
        payment.put("amount", "80.00");
        try (PDDocument document = PDDocument.load(createStandalonePdf(
                overview(payment, affiliate(""), config(), "GS-1", "", "GUTSCHRIFT", 0)).toFile())) {
            int pages = document.getNumberOfPages();
            assertTrue(pages >= 3, "80 Buchungen passen nicht auf zwei Seiten: " + pages);
            String text = pageText(document, 1, pages);
            assertTrue(count(text, "Bestellnummer") >= 2, "Der Tabellenkopf wiederholt sich auf jeder Seite");
            assertTrue(text.contains("#1079"), "Die letzte Buchung fehlt");
            assertTrue(text.contains("Seite " + pages + " von " + pages), text);
            Matcher total = Pattern.compile("Summe 800,00 € 640,00 € 80,00 €").matcher(text);
            assertTrue(total.find(), "Summenzeile am Ende der Liste");
        }
    }

    @Test
    void rechnungsAnlageOhneGutschriftTexteUndOhneBild() throws Exception {
        Properties config = config();
        Object overview = overview(payment(), affiliate("21/815/30140"), config, "RE-2025-0003", "", "RECHNUNG", 0);
        Path pdf = createDocumentPdf(null, payment(), affiliate("21/815/30140"), config, "RE-2025-0003", "RECHNUNG", overview);
        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            assertTrue(document.getNumberOfPages() >= 2);
            String text = pageText(document, 1, document.getNumberOfPages());
            assertTrue(text.contains("Anlage zur Rechnung RE-2025-0003"), text);
            assertTrue(text.contains("Rechnungsbetrag (netto)"), text);
            assertFalse(text.contains("§ 14"), "Altfall-Rechnungen tragen keinen Gutschrift-Rechtstext");
            assertFalse(text.contains("S+R"), "Die Altfall-Rechnung richtet sich an die Alt-Gesellschaft");
            assertFalse(text.contains("Gutschriftempfängerin"), text);
            for (PDPage page : document.getPages()) {
                for (COSName name : page.getResources().getXObjectNames()) {
                    assertFalse(page.getResources().getXObject(name) instanceof PDImageXObject, "Kein Logo in der Altfall-Rechnung");
                }
            }
            assertOnlyEmbeddedLiberationSans(document);
        }
    }
}
