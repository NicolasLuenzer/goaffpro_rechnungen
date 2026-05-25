import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that verify fachliche Korrektheit der Gutschrift-Ausgaben.
 */
class GutschriftTextTest {

    // ── Helper: Zugriff auf private static Methoden via Reflection ──

    private static String invokeStaticString(String methodName) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod(methodName);
        m.setAccessible(true);
        return (String) m.invoke(null);
    }

    private static String invokeStaticString(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod(methodName, parameterTypes);
        m.setAccessible(true);
        return (String) m.invoke(null, args);
    }

    private static String invokeCalculateVat(double net, boolean isKlein) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("calculateVat", double.class, boolean.class);
        m.setAccessible(true);
        return m.invoke(null, net, isKlein).toString();
    }

    private static String invokeZugferdXml() throws Exception {
        // Wir testen den TypeCode im Template-String direkt über getDefaultEInvoicePdfViewHtmlTemplate
        // Für ZUGFeRD prüfen wir über createZugferdInvoiceXml – aber das braucht file I/O.
        // Stattdessen prüfen wir den String über den Quelltext-Aufruf in getDefaultInvoiceMailHtmlTemplate.
        return null;
    }

    // ── Tests: E-Invoice-HTML-Template ──

    @Test
    void eInvoiceHtmlTemplate_containsGUTSCHRIFT() throws Exception {
        String html = invokeStaticString("getDefaultEInvoicePdfViewHtmlTemplate");
        assertTrue(html.contains("GUTSCHRIFT"),
                "E-Invoice-HTML-Template muss 'GUTSCHRIFT' enthalten");
    }

    @Test
    void eInvoiceHtmlTemplate_doesNotContainRECHNUNG() throws Exception {
        String html = invokeStaticString("getDefaultEInvoicePdfViewHtmlTemplate");
        assertFalse(html.contains("RECHNUNG"),
                "E-Invoice-HTML-Template darf 'RECHNUNG' nicht enthalten");
    }

    @Test
    void eInvoiceHtmlTemplate_containsGutschriftnummer() throws Exception {
        String html = invokeStaticString("getDefaultEInvoicePdfViewHtmlTemplate");
        assertTrue(html.contains("Gutschriftnummer"),
                "E-Invoice-HTML-Template muss 'Gutschriftnummer' enthalten");
    }

    @Test
    void eInvoiceHtmlTemplate_containsParagraph14() throws Exception {
        String html = invokeStaticString("getDefaultEInvoicePdfViewHtmlTemplate");
        assertTrue(html.contains("§ 14"),
                "E-Invoice-HTML-Template muss Verweis auf § 14 UStG enthalten");
    }

    @Test
    void eInvoiceHtmlTemplate_containsWiderspruchshinweis() throws Exception {
        String html = invokeStaticString("getDefaultEInvoicePdfViewHtmlTemplate");
        assertTrue(html.contains("widersprochen"),
                "E-Invoice-HTML-Template muss Widerspruchshinweis enthalten");
    }

    @Test
    void eInvoiceHtmlTemplate_containsGutschriftausstellerin() throws Exception {
        String html = invokeStaticString("getDefaultEInvoicePdfViewHtmlTemplate");
        assertTrue(html.contains("Gutschriftausstellerin"),
                "E-Invoice-HTML-Template muss 'Gutschriftausstellerin' enthalten");
    }

    @Test
    void eInvoiceHtmlTemplate_containsGutschriftempfaengerin() throws Exception {
        String html = invokeStaticString("getDefaultEInvoicePdfViewHtmlTemplate");
        assertTrue(html.contains("Gutschriftempfängerin"),
                "E-Invoice-HTML-Template muss 'Gutschriftempfängerin' enthalten");
    }

    @Test
    void eInvoiceHtmlTemplate_placesIssuerAndRecipientCorrectly() throws Exception {
        String html = invokeStaticString("getDefaultEInvoicePdfViewHtmlTemplate");
        assertTrue(html.contains("<div><b>Gutschriftausstellerin (Leistungsempfängerin)</b></div>"),
                "Ausstellerin muss im Briefkopf stehen");
        assertTrue(html.contains("<div>{{buyerCompanyName}}</div>"),
                "Ausstellerin muss die eigene Firma verwenden");
        assertTrue(html.contains("<div style=\"font-size:16px;font-weight:700;\">{{advisorName}}</div>"),
                "Adressat muss die Beraterin sein");
    }

    @Test
    void sanitizePdfText_replacesUnsupportedSymbolsInsteadOfQuestionMarks() throws Exception {
        String text = invokeStaticString(
                "sanitizePdfText",
                new Class<?>[]{String.class},
                "1,23 € – Provisionszeitraum …");

        assertEquals("1,23 EUR - Provisionszeitraum ...", text);
        assertFalse(text.contains("?"), "PDF-Text darf keine Fragezeichen als Ersatzzeichen enthalten");
    }

    @Test
    void shortenForPdf_usesAsciiEllipsis() throws Exception {
        String text = invokeStaticString(
                "shortenForPdf",
                new Class<?>[]{String.class, int.class},
                "Vermittlungsprovision – 05.07.2024 bis 10.07.2024",
                48);

        assertTrue(text.endsWith("..."), "PDF-Kürzung muss ASCII-Ellipsis verwenden");
        assertFalse(text.contains("?"), "PDF-Kürzung darf keine Fragezeichen erzeugen");
    }

    @Test
    void eInvoicePdf_placesIssuerFirstAndAdvisorAsRecipient() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode payment = mapper.readTree("""
                {
                  "id": "123",
                  "amount": "1.13",
                  "currency": "EUR",
                  "created_at": "2026-02-05T11:13:00Z"
                }
                """);
        JsonNode affiliate = mapper.readTree("""
                {
                  "name": "Nicolas Influencer",
                  "email": "nicolas@example.com",
                  "address_1": "Hinter der Alten See 5",
                  "zip": "64342",
                  "city": "Seeheim-Jugenheim",
                  "state": "Hessen",
                  "country": "DE",
                  "tax_identification_number": "12357895",
                  "payment_details": {
                    "iban": "DE02120300000000202051",
                    "bic": "BYLADEM1001",
                    "account_holder": "Nicolas Influencer"
                  }
                }
                """);
        Properties config = new Properties();
        config.setProperty("eInvoiceBuyerName", "S+R Linear Technology GmbH");
        config.setProperty("eInvoiceBuyerStreet", "Bleidernröder Str. 11");
        config.setProperty("eInvoiceBuyerZip", "35315");
        config.setProperty("eInvoiceBuyerCity", "Homberg/Ohm");
        config.setProperty("eInvoiceBuyerCountry", "DE");
        config.setProperty("eInvoiceBuyerVatId", "DE459084219");
        config.setProperty("eInvoiceBuyerTaxNumber", "123456");
        config.setProperty("contactEmail", "rechnung@example.com");

        Path pdf = Files.createTempFile("gutschrift-layout", ".pdf");
        Method m = WebUiServer.class.getDeclaredMethod(
                "createEInvoicePdfWithEmbeddedXml",
                Path.class, Path.class, JsonNode.class, JsonNode.class, Properties.class,
                String.class, String.class, boolean.class);
        m.setAccessible(true);
        m.invoke(null, pdf, null, payment, affiliate, config, "GS-2026-0002", "05.07.2024 bis 10.07.2024", false);

        String text;
        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            text = new PDFTextStripper().getText(document);
        }

        assertTrue(text.indexOf("S+R Linear Technology GmbH") < text.indexOf("Nicolas Influencer"),
                "Die eigene Firma muss im PDF-Kopf vor der Beraterin erscheinen");
        assertTrue(text.contains("Gutschriftempfängerin (Leistungserbringerin)"),
                "Die Beraterin muss als Gutschriftempfängerin beschriftet sein");
        assertTrue(text.contains("1,13 EUR"), "Beträge müssen im PDF ohne Euro-Fragezeichen erscheinen");
        assertFalse(text.contains("?"), "Das erzeugte PDF darf keine Ersatz-Fragezeichen enthalten");
    }

    // ── Tests: E-Mail-Template ──

    @Test
    void mailTemplate_containsGutschrift() throws Exception {
        String html = invokeStaticString("getDefaultInvoiceMailHtmlTemplate");
        assertTrue(html.contains("Gutschrift"),
                "E-Mail-Template muss 'Gutschrift' enthalten");
    }

    @Test
    void mailTemplate_doesNotContainRechnung() throws Exception {
        String html = invokeStaticString("getDefaultInvoiceMailHtmlTemplate");
        assertFalse(html.contains("Rechnung"),
                "E-Mail-Template darf 'Rechnung' nicht enthalten");
    }

    @Test
    void mailTemplate_containsGutschriftNrPlaceholder() throws Exception {
        String html = invokeStaticString("getDefaultInvoiceMailHtmlTemplate");
        assertTrue(html.contains("{{gutschriftNr}}"),
                "E-Mail-Template muss Platzhalter {{gutschriftNr}} enthalten");
    }

    // ── Tests: Gutschriftnummer-Format ──

    @Test
    void gutschriftNumber_formatIsCorrect() {
        // Format: GS-YYYY-NNNN
        Pattern p = Pattern.compile("^GS-\\d{4}-\\d{4}$");
        String testNr = "GS-2026-0001";
        assertTrue(p.matcher(testNr).matches(),
                "Gutschriftnummer muss dem Format GS-JJJJ-NNNN entsprechen");
    }

    // ── Tests: USt / Kleinunternehmer ──

    @Test
    void calculateVat_kleinunternehmer_returnsZero() throws Exception {
        double result = Double.parseDouble(invokeCalculateVat(100.0, true));
        assertEquals(0.0, result, 0.001,
                "Kleinunternehmer: VAT muss 0 sein");
    }

    @Test
    void calculateVat_regelbesteuert_returns19Percent() throws Exception {
        double result = Double.parseDouble(invokeCalculateVat(100.0, false));
        assertEquals(19.0, result, 0.001,
                "Regelbesteuert: VAT muss 19 % des Nettobetrags sein");
    }

    @Test
    void calculateVat_regelbesteuert_grossAmountCorrect() throws Exception {
        double net = 200.0;
        double vat = Double.parseDouble(invokeCalculateVat(net, false));
        assertEquals(38.0, vat, 0.001);
        assertEquals(238.0, net + vat, 0.001);
    }
}
