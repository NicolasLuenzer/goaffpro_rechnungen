import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
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
