import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für die datumsgesteuerte Unterscheidung Gutschrift / Rechnung (Altfälle bis 31.12.2025).
 *
 * Rückbau in einem Jahr: diese Datei ersatzlos löschen.
 */
class RechnungDocumentKindTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── Reflection-Helfer ──

    private static Object resolveDecision(JsonNode payment, Properties config) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("resolveDocumentKind", JsonNode.class, Properties.class);
        m.setAccessible(true);
        return m.invoke(null, payment, config);
    }

    private static Object recordValue(Object record, String accessor) throws Exception {
        Method m = record.getClass().getDeclaredMethod(accessor);
        m.setAccessible(true);
        return m.invoke(record);
    }

    /** Name der ermittelten DocumentKind-Konstante bzw. null bei gemischtem Zahllauf. */
    private static String kindName(Object decision) throws Exception {
        Object kind = recordValue(decision, "kind");
        return kind == null ? null : ((Enum<?>) kind).name();
    }

    private static Object documentKind(String name) throws Exception {
        Class<?> kindClass = Class.forName("WebUiServer$DocumentKind");
        for (Object constant : kindClass.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) return constant;
        }
        throw new IllegalArgumentException("Unbekannte DocumentKind-Konstante: " + name);
    }

    private static void invokeCreateZugferdXml(Path xmlPath, JsonNode payment, JsonNode affiliate, Properties config,
                                               String documentNumber, String periodLabel, boolean isKlein,
                                               String kindName) throws Exception {
        Class<?> kindClass = Class.forName("WebUiServer$DocumentKind");
        Method m = WebUiServer.class.getDeclaredMethod("createZugferdInvoiceXml",
                Path.class, JsonNode.class, JsonNode.class, Properties.class,
                String.class, String.class, boolean.class, kindClass);
        m.setAccessible(true);
        m.invoke(null, xmlPath, payment, affiliate, config, documentNumber, periodLabel, isKlein, documentKind(kindName));
    }

    private static String invokeRenderPdfViewHtml(String template, JsonNode payment, JsonNode affiliate, Properties config,
                                                  String documentNumber, String periodLabel, boolean isKlein,
                                                  String kindName) throws Exception {
        Class<?> kindClass = Class.forName("WebUiServer$DocumentKind");
        Method m = WebUiServer.class.getDeclaredMethod("renderEInvoicePdfViewHtml",
                String.class, JsonNode.class, JsonNode.class, Properties.class,
                String.class, String.class, boolean.class, kindClass);
        m.setAccessible(true);
        return (String) m.invoke(null, template, payment, affiliate, config, documentNumber, periodLabel, isKlein, documentKind(kindName));
    }

    private static String invokeStaticString(String methodName) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod(methodName);
        m.setAccessible(true);
        return (String) m.invoke(null);
    }

    // ── Testdaten ──

    private static JsonNode paymentWithTransactionDates(String... isoDates) throws Exception {
        StringBuilder json = new StringBuilder("{\"id\":\"p1\",\"amount\":\"100.00\",\"currency\":\"EUR\",")
                .append("\"created_at\":\"2026-02-01T10:00:00Z\",\"transactions\":[");
        for (int i = 0; i < isoDates.length; i++) {
            if (i > 0) json.append(',');
            json.append("{\"id\":\"t").append(i).append("\",\"amount\":\"10.00\",\"created_at\":\"").append(isoDates[i]).append("\"}");
        }
        json.append("]}");
        return MAPPER.readTree(json.toString());
    }

    private static Properties baseConfig() {
        Properties config = new Properties();
        config.setProperty("eInvoiceBuyerName", "S+R Linear Technology GmbH");
        config.setProperty("legacyBuyerName", "VEMMiNA Qualitäts- Haushaltsprodukte GmbH");
        return config;
    }

    // ── Stichtagsermittlung ──

    @Test
    void alleTransaktionenVorStichtagLiefernRechnung() throws Exception {
        Object decision = resolveDecision(paymentWithTransactionDates("2025-06-01T10:00:00Z", "2025-11-30T10:00:00Z"), baseConfig());
        assertEquals("RECHNUNG", kindName(decision));
        assertEquals(false, recordValue(decision, "mixed"));
        assertEquals("transactions", recordValue(decision, "source"));
        assertEquals(2, recordValue(decision, "beforeCutoffCount"));
    }

    @Test
    void alleTransaktionenAbStichtagLiefernGutschrift() throws Exception {
        Object decision = resolveDecision(paymentWithTransactionDates("2026-01-02T08:00:00Z", "2026-03-01T08:00:00Z"), baseConfig());
        assertEquals("GUTSCHRIFT", kindName(decision));
        assertEquals(false, recordValue(decision, "mixed"));
        assertEquals(2, recordValue(decision, "fromCutoffCount"));
    }

    @Test
    void gemischterZahllaufLiefertMixedOhneDokumentart() throws Exception {
        Object decision = resolveDecision(paymentWithTransactionDates("2025-12-20T10:00:00Z", "2026-01-05T10:00:00Z"), baseConfig());
        assertEquals(true, recordValue(decision, "mixed"));
        assertNull(kindName(decision), "Bei gemischtem Zahllauf darf keine Dokumentart bestimmt werden");
        assertEquals(1, recordValue(decision, "beforeCutoffCount"));
        assertEquals(1, recordValue(decision, "fromCutoffCount"));
        assertEquals(10.0, (Double) recordValue(decision, "beforeCutoffAmount"), 0.001);
        assertEquals(10.0, (Double) recordValue(decision, "fromCutoffAmount"), 0.001);
    }

    @Test
    void exaktMitternachtBerlinZaehltZumNeuenJahr() throws Exception {
        // 2025-12-31T23:00:00Z == 01.01.2026 00:00:00 Berlin -> ab Stichtag
        Object decision = resolveDecision(paymentWithTransactionDates("2025-12-31T23:00:00Z"), baseConfig());
        assertEquals("GUTSCHRIFT", kindName(decision));
    }

    @Test
    void eineSekundeVorMitternachtBerlinIstNochAltfall() throws Exception {
        // 2025-12-31T22:59:59Z == 31.12.2025 23:59:59 Berlin -> vor Stichtag
        Object decision = resolveDecision(paymentWithTransactionDates("2025-12-31T22:59:59Z"), baseConfig());
        assertEquals("RECHNUNG", kindName(decision));
    }

    @Test
    void ohneTransaktionenGreiftDasZahllaufDatum() throws Exception {
        JsonNode payment = MAPPER.readTree("{\"id\":\"p1\",\"amount\":\"100.00\",\"created_at\":\"2025-08-01T10:00:00Z\"}");
        Object decision = resolveDecision(payment, baseConfig());
        assertEquals("RECHNUNG", kindName(decision));
        assertEquals("paymentCreatedAt", recordValue(decision, "source"));
    }

    @Test
    void ohneJedesDatumFaelltAufGutschriftZurueck() throws Exception {
        JsonNode payment = MAPPER.readTree("{\"id\":\"p1\",\"amount\":\"100.00\",\"created_at\":\"kaputt\",\"transactions\":[]}");
        Object decision = resolveDecision(payment, baseConfig());
        assertEquals("GUTSCHRIFT", kindName(decision));
        assertEquals("default", recordValue(decision, "source"));
    }

    @Test
    void unlesbareTransaktionsdatenWerdenIgnoriert() throws Exception {
        JsonNode payment = MAPPER.readTree("{\"id\":\"p1\",\"amount\":\"100.00\",\"created_at\":\"2026-02-01T10:00:00Z\","
                + "\"transactions\":[{\"amount\":\"10.00\",\"created_at\":\"\"},{\"amount\":\"10.00\",\"created_at\":\"2026-01-05T10:00:00Z\"}]}");
        Object decision = resolveDecision(payment, baseConfig());
        assertEquals("GUTSCHRIFT", kindName(decision));
        assertEquals(false, recordValue(decision, "mixed"), "Undatierte Transaktionen dürfen keinen Mixed-Fall auslösen");
    }

    @Test
    void stichtagIstKonfigurierbar() throws Exception {
        Properties config = baseConfig();
        config.setProperty("rechnungCutoffDate", "2025-07-01");
        Object decision = resolveDecision(paymentWithTransactionDates("2025-08-15T10:00:00Z"), config);
        assertEquals("GUTSCHRIFT", kindName(decision));
    }

    @Test
    void stichtagsermittlungVeraendertDieKonfigurationNicht() throws Exception {
        // Absicherung: bei einem gemischten Zahllauf darf keine Belegnummer verbraucht werden.
        Properties config = baseConfig();
        Properties before = (Properties) config.clone();
        resolveDecision(paymentWithTransactionDates("2025-12-20T10:00:00Z", "2026-01-05T10:00:00Z"), config);
        assertEquals(before, config, "resolveDocumentKind darf die Konfiguration (und damit Zähler) nicht anfassen");
    }

    // ── ZUGFeRD ──

    @Test
    void zugferdRechnungHatTypeCode380(@TempDir Path tempDir) throws Exception {
        Path xml = tempDir.resolve("rechnung.xml");
        invokeCreateZugferdXml(xml, paymentWithTransactionDates("2025-06-01T10:00:00Z"), null, baseConfig(),
                "RE-2026-0001", "01.06.2025 bis 01.06.2025", true, "RECHNUNG");
        String content = Files.readString(xml, StandardCharsets.UTF_8);
        assertTrue(content.contains("<ram:TypeCode>380</ram:TypeCode>"), "Rechnung muss TypeCode 380 tragen");
        assertFalse(content.contains("<ram:TypeCode>389</ram:TypeCode>"), "Rechnung darf nicht als Self-Billed (389) ausgezeichnet sein");
        assertTrue(content.contains("RE-2026-0001"));
    }

    @Test
    void zugferdGutschriftBehaeltTypeCode389(@TempDir Path tempDir) throws Exception {
        Path xml = tempDir.resolve("gutschrift.xml");
        invokeCreateZugferdXml(xml, paymentWithTransactionDates("2026-02-01T10:00:00Z"), null, baseConfig(),
                "GS-2026-0001", "01.02.2026 bis 01.02.2026", true, "GUTSCHRIFT");
        String content = Files.readString(xml, StandardCharsets.UTF_8);
        assertTrue(content.contains("<ram:TypeCode>389</ram:TypeCode>"), "Regressionsanker: Gutschrift bleibt 389");
    }

    @Test
    void zugferdRechnungNutztDieAltGesellschaft(@TempDir Path tempDir) throws Exception {
        Path xml = tempDir.resolve("rechnung.xml");
        invokeCreateZugferdXml(xml, paymentWithTransactionDates("2025-06-01T10:00:00Z"), null, baseConfig(),
                "RE-2026-0001", "Zeitraum", true, "RECHNUNG");
        String content = Files.readString(xml, StandardCharsets.UTF_8);
        assertTrue(content.contains("VEMMiNA Qualit"), "Rechnung muss gegen die Alt-Gesellschaft laufen");
        assertFalse(content.contains("S+R Linear Technology GmbH"), "S+R darf auf einer Altfall-Rechnung nicht auftauchen");
    }

    @Test
    void zugferdGutschriftNutztWeiterhinSR(@TempDir Path tempDir) throws Exception {
        Path xml = tempDir.resolve("gutschrift.xml");
        invokeCreateZugferdXml(xml, paymentWithTransactionDates("2026-02-01T10:00:00Z"), null, baseConfig(),
                "GS-2026-0001", "Zeitraum", true, "GUTSCHRIFT");
        String content = Files.readString(xml, StandardCharsets.UTF_8);
        assertTrue(content.contains("S+R Linear Technology GmbH"));
        assertFalse(content.contains("VEMMiNA Qualit"));
    }

    @Test
    void zugferdRechnungBehaeltKleinunternehmerregelung(@TempDir Path tempDir) throws Exception {
        Path xml = tempDir.resolve("rechnung.xml");
        invokeCreateZugferdXml(xml, paymentWithTransactionDates("2025-06-01T10:00:00Z"), null, baseConfig(),
                "RE-2026-0001", "Zeitraum", true, "RECHNUNG");
        String content = Files.readString(xml, StandardCharsets.UTF_8);
        assertTrue(content.contains("<ram:CategoryCode>E</ram:CategoryCode>"), "Kleinunternehmerin bleibt steuerbefreit");
        assertTrue(content.contains("19 UStG"), "§-19-Hinweis muss auch auf der Rechnung stehen");
    }

    // ── Vorlagen ──

    @Test
    void rechnungPdfVorlageTraegtRechnungsWortlaut() throws Exception {
        String template = invokeStaticString("getDefaultRechnungPdfViewHtmlTemplate");
        assertTrue(template.contains(">Rechnung<"), "Titelzeile muss 'Rechnung' lauten");
        assertTrue(template.contains("Rechnungsnummer"));
        assertTrue(template.contains("Rechnungsdatum"));
        assertTrue(template.contains("Auszahlungsdatum"));
    }

    @Test
    void rechnungPdfVorlageEnthaeltKeineGutschriftsHinweise() throws Exception {
        String template = invokeStaticString("getDefaultRechnungPdfViewHtmlTemplate");
        assertFalse(template.contains("Gutschrift"), "Auf einer Rechnung darf 'Gutschrift' nicht vorkommen");
        assertFalse(template.contains("&sect; 14"), "§-14-Selbstabrechnungshinweis gehört nicht auf eine Rechnung");
        assertFalse(template.contains("widersprochen"), "Widerspruchshinweis gehört nicht auf eine Rechnung");
    }

    @Test
    void rechnungMailVorlageTraegtRechnungsWortlaut() throws Exception {
        String template = invokeStaticString("getDefaultRechnungMailHtmlTemplate");
        assertTrue(template.contains("Rechnungsnummer"));
        assertFalse(template.contains("Gutschrift"));
        assertFalse(template.contains("§ 14"));
    }

    @Test
    void rechnungPdfVorlageRendertOhneOffenePlatzhalter() throws Exception {
        String template = invokeStaticString("getDefaultRechnungPdfViewHtmlTemplate");
        JsonNode affiliate = MAPPER.readTree("{\"name\":\"Erika Muster\",\"email\":\"e@example.com\",\"address_1\":\"Weg 1\",\"zip\":\"12345\",\"city\":\"Ort\"}");
        String rendered = invokeRenderPdfViewHtml(template, paymentWithTransactionDates("2025-06-01T10:00:00Z"), affiliate,
                baseConfig(), "RE-2026-0001", "01.06.2025 bis 30.06.2025", true, "RECHNUNG");
        assertFalse(rendered.contains("{{"), "Es dürfen keine unaufgelösten Platzhalter übrig bleiben: " + firstPlaceholder(rendered));
        assertTrue(rendered.contains("RE-2026-0001"));
        assertTrue(rendered.contains("VEMMiNA Qualit"));
    }

    private static String firstPlaceholder(String html) {
        var matcher = Pattern.compile("\\{\\{[^}]*}}").matcher(html);
        return matcher.find() ? matcher.group() : "";
    }

    // ── Nummernkreise ──

    @Test
    void rechnungsnummerFolgtEigenemZaehler() throws Exception {
        Class<?> kindClass = Class.forName("WebUiServer$DocumentKind");
        Method m = WebUiServer.class.getDeclaredMethod("generateNextDocumentNumber", Properties.class, kindClass);
        m.setAccessible(true);

        Properties config = baseConfig();
        int year = java.time.LocalDate.now().getYear();
        config.setProperty("gutschriftCounter", "17");
        config.setProperty("gutschriftCounterYear", String.valueOf(year));

        String first = (String) m.invoke(null, config, documentKind("RECHNUNG"));
        assertEquals(String.format("RE-%d-0001", year), first, "Der Rechnungszähler startet unabhängig bei 1");
        assertEquals("17", config.getProperty("gutschriftCounter"), "Der Gutschriftzähler darf unberührt bleiben");

        String second = (String) m.invoke(null, config, documentKind("RECHNUNG"));
        assertEquals(String.format("RE-%d-0002", year), second);

        String gutschrift = (String) m.invoke(null, config, documentKind("GUTSCHRIFT"));
        assertEquals(String.format("GS-%d-0018", year), gutschrift, "Der Gutschriftzähler läuft parallel weiter");
    }

    @Test
    void rechnungsnummerStartetNachJahreswechselNeu() throws Exception {
        Class<?> kindClass = Class.forName("WebUiServer$DocumentKind");
        Method m = WebUiServer.class.getDeclaredMethod("generateNextDocumentNumber", Properties.class, kindClass);
        m.setAccessible(true);

        Properties config = baseConfig();
        config.setProperty("rechnungCounter", "77");
        config.setProperty("rechnungCounterYear", "2024");

        String number = (String) m.invoke(null, config, documentKind("RECHNUNG"));
        assertEquals(String.format("RE-%d-0001", java.time.LocalDate.now().getYear()), number);
    }
}
