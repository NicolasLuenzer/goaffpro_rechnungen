import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.text.PDFTextStripper;
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

    /** Firmenname im Briefkopf, so wie ihn die Vorlagen über buyerProperty beziehen. */
    private static String buyerName(Properties config, String kindName) throws Exception {
        Class<?> kindClass = Class.forName("WebUiServer$DocumentKind");
        Object kind = documentKind(kindName);
        java.lang.reflect.Field defaults = kindClass.getDeclaredField("defaultBuyerName");
        defaults.setAccessible(true);
        Method m = WebUiServer.class.getDeclaredMethod("buyerProperty",
                Properties.class, kindClass, String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, config, kind, "Name", defaults.get(kind));
    }

    private static void invokeCreateZugferdXml(Path xmlPath, JsonNode payment, JsonNode affiliate, Properties config,
                                               String documentNumber, String periodLabel, boolean isKlein,
                                               String kindName) throws Exception {
        Class<?> kindClass = Class.forName("WebUiServer$DocumentKind");
        Method m = WebUiServer.class.getDeclaredMethod("createZugferdInvoiceXml",
                Path.class, JsonNode.class, JsonNode.class, Properties.class,
                String.class, String.class, GutschriftTextTest.taxClass(), kindClass);
        m.setAccessible(true);
        m.invoke(null, xmlPath, payment, affiliate, config, documentNumber, periodLabel, GutschriftTextTest.taxTreatment(isKlein), documentKind(kindName));
    }

    private static String invokeRenderPdfViewHtml(String template, JsonNode payment, JsonNode affiliate, Properties config,
                                                  String documentNumber, String periodLabel, boolean isKlein,
                                                  String kindName) throws Exception {
        Class<?> kindClass = Class.forName("WebUiServer$DocumentKind");
        Method m = WebUiServer.class.getDeclaredMethod("renderEInvoicePdfViewHtml",
                String.class, JsonNode.class, JsonNode.class, Properties.class,
                String.class, String.class, GutschriftTextTest.taxClass(), kindClass);
        m.setAccessible(true);
        return (String) m.invoke(null, template, payment, affiliate, config, documentNumber, periodLabel, GutschriftTextTest.taxTreatment(isKlein), documentKind(kindName));
    }

    private static String invokeStaticString(String methodName) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod(methodName);
        m.setAccessible(true);
        return (String) m.invoke(null);
    }

    private static String invokeBuildMailBody(JsonNode payment, JsonNode affiliate, String periodLabel,
                                              String documentNumber, String kindName, String buyerCompanyName) throws Exception {
        Class<?> kindClass = Class.forName("WebUiServer$DocumentKind");
        Method m = WebUiServer.class.getDeclaredMethod("buildInvoiceMailBody",
                JsonNode.class, JsonNode.class, String.class, String.class, kindClass, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, payment, affiliate, periodLabel, documentNumber,
                documentKind(kindName), buyerCompanyName);
    }

    private static String invokeBuildMailHtml(JsonNode payment, JsonNode affiliate, String periodLabel,
                                              String template, String documentNumber, String kindName,
                                              String buyerCompanyName) throws Exception {
        Class<?> kindClass = Class.forName("WebUiServer$DocumentKind");
        Method m = WebUiServer.class.getDeclaredMethod("buildInvoiceMailHtml",
                JsonNode.class, JsonNode.class, String.class, String.class, String.class, kindClass, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, payment, affiliate, periodLabel, template, documentNumber,
                documentKind(kindName), buyerCompanyName);
    }

    private static String invokeMailSubject(String kindName, String documentNumber,
                                            String periodLabel, String advisorName) throws Exception {
        Class<?> kindClass = Class.forName("WebUiServer$DocumentKind");
        Method m = WebUiServer.class.getDeclaredMethod("documentMailSubject",
                kindClass, String.class, String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, documentKind(kindName), documentNumber, periodLabel, advisorName);
    }

    private static void invokeMigrateTemplate(Properties config, String key,
                                              String previousFingerprint, String newDefault) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("migratePreviousRechnungTemplate",
                Properties.class, String.class, String.class, String.class);
        m.setAccessible(true);
        m.invoke(null, config, key, previousFingerprint, newDefault);
    }

    private static String invokeSha256(String value) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("sha256Hex", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, value);
    }

    private static void invokeCreateEInvoicePdf(Path pdf, JsonNode payment, JsonNode affiliate,
                                                Properties config, String documentNumber,
                                                String periodLabel, boolean isKlein, String kindName) throws Exception {
        Class<?> kindClass = Class.forName("WebUiServer$DocumentKind");
        Method m = WebUiServer.class.getDeclaredMethod("createEInvoicePdfWithEmbeddedXml",
                Path.class, Path.class, JsonNode.class, JsonNode.class, Properties.class,
                String.class, String.class, GutschriftTextTest.taxClass(), kindClass);
        m.setAccessible(true);
        m.invoke(null, pdf, null, payment, affiliate, config, documentNumber, periodLabel,
                GutschriftTextTest.taxTreatment(isKlein), documentKind(kindName));
    }

    private static boolean hasImageXObject(PDDocument document) throws Exception {
        for (PDPage page : document.getPages()) {
            PDResources resources = page.getResources();
            if (resources == null) continue;
            for (COSName name : resources.getXObjectNames()) {
                if (resources.isImageXObject(name)) return true;
            }
        }
        return false;
    }

    // ── Testdaten ──

    private static JsonNode paymentWithTransactionDates(String... isoDates) throws Exception {
        return payment("2026-02-01T10:00:00Z", isoDates);
    }

    /** Das Auszahlungsdatum (created_at des Zahllaufs) bestimmt die Dokumentart. */
    private static JsonNode payment(String payoutIso, String... transactionDates) throws Exception {
        StringBuilder json = new StringBuilder("{\"id\":\"p1\",\"amount\":\"100.00\",\"currency\":\"EUR\",")
                .append("\"created_at\":\"").append(payoutIso).append("\",\"transactions\":[");
        for (int i = 0; i < transactionDates.length; i++) {
            if (i > 0) json.append(',');
            json.append("{\"id\":\"t").append(i).append("\",\"amount\":\"10.00\",\"created_at\":\"").append(transactionDates[i]).append("\"}");
        }
        json.append("]}");
        return MAPPER.readTree(json.toString());
    }

    private static Properties baseConfig() {
        Properties config = new Properties();
        config.setProperty("eInvoiceBuyerName", "S+R Linear Technology GmbH");
        config.setProperty("legacyBuyerName", "VEMMiNA Qualitäts- Haushaltsprodukte GmbH");
        config.setProperty("legacyBuyerStreet", "Altmarkt 10");
        config.setProperty("legacyBuyerZip", "12345");
        config.setProperty("legacyBuyerCity", "Musterstadt");
        config.setProperty("legacyBuyerCountry", "DE");
        config.setProperty("legacyBuyerVatId", "DE123456789");
        config.setProperty("legacyBuyerTaxNumber", "12/345/67890");
        return config;
    }

    // ── Stichtagsermittlung ──

    @Test
    void auszahlungVorStichtagErgibtRechnung() throws Exception {
        Object decision = resolveDecision(payment("2025-11-30T10:00:00Z", "2025-06-01T10:00:00Z"), baseConfig());
        assertEquals("RECHNUNG", kindName(decision));
        assertEquals("paymentCreatedAt", recordValue(decision, "source"));
    }

    @Test
    void auszahlungAbStichtagErgibtGutschrift() throws Exception {
        Object decision = resolveDecision(payment("2026-03-01T08:00:00Z", "2026-01-02T08:00:00Z"), baseConfig());
        assertEquals("GUTSCHRIFT", kindName(decision));
        assertEquals("paymentCreatedAt", recordValue(decision, "source"));
    }

    /**
     * Der real aufgetretene Fall (Zahllauf 2887600): am 05.02.2026 ausgezahlt, die Provisionen
     * stammen aber aus 2025. Nach der fachlichen Regel zählt die Auszahlung, also Gutschrift -
     * so wie für diesen Zahllauf auch tatsächlich Gutschriften ausgestellt wurden. Vor der
     * Umstellung entschieden die Transaktionsdaten und es wurde eine Rechnung daraus.
     */
    @Test
    void auszahlung2026UeberAltprovisionenErgibtGutschrift() throws Exception {
        Object decision = resolveDecision(
                payment("2026-02-05T11:13:36Z", "2025-11-02T09:00:00Z", "2025-12-18T09:00:00Z"), baseConfig());
        assertEquals("GUTSCHRIFT", kindName(decision),
                "Maßgeblich ist das Auszahlungsdatum, nicht wann die Provision entstanden ist");
        assertEquals(2, recordValue(decision, "beforeCutoffCount"),
                "Die Altprovisionen bleiben als Information erhalten");
        assertEquals(0, recordValue(decision, "fromCutoffCount"));
    }

    @Test
    void provisionsdatumAendertDieDokumentartNichtMehr() throws Exception {
        Object nurAlt = resolveDecision(payment("2026-02-05T10:00:00Z", "2025-01-01T10:00:00Z"), baseConfig());
        Object nurNeu = resolveDecision(payment("2026-02-05T10:00:00Z", "2026-02-01T10:00:00Z"), baseConfig());
        Object gemischt = resolveDecision(payment("2026-02-05T10:00:00Z", "2025-12-20T10:00:00Z", "2026-01-05T10:00:00Z"), baseConfig());

        assertEquals("GUTSCHRIFT", kindName(nurAlt));
        assertEquals("GUTSCHRIFT", kindName(nurNeu));
        assertEquals("GUTSCHRIFT", kindName(gemischt));
    }

    /** Ein Zahllauf hat genau ein Auszahlungsdatum - der frühere 409-Fall kann nicht mehr auftreten. */
    @Test
    void gemischterZahllaufErzeugtTrotzdemEinenBeleg() throws Exception {
        Object decision = resolveDecision(
                payment("2026-02-01T10:00:00Z", "2025-12-20T10:00:00Z", "2026-01-05T10:00:00Z"), baseConfig());
        assertNotNull(kindName(decision), "Es muss immer eine Dokumentart bestimmt werden");
        assertEquals("GUTSCHRIFT", kindName(decision));
        assertEquals(1, recordValue(decision, "beforeCutoffCount"));
        assertEquals(1, recordValue(decision, "fromCutoffCount"));
        assertEquals(10.0, (Double) recordValue(decision, "beforeCutoffAmount"), 0.001);
        assertEquals(10.0, (Double) recordValue(decision, "fromCutoffAmount"), 0.001);
    }

    @Test
    void exaktMitternachtBerlinZaehltZumNeuenJahr() throws Exception {
        // 2025-12-31T23:00:00Z == 01.01.2026 00:00:00 Berlin -> ab Stichtag
        Object decision = resolveDecision(payment("2025-12-31T23:00:00Z"), baseConfig());
        assertEquals("GUTSCHRIFT", kindName(decision));
    }

    @Test
    void eineSekundeVorMitternachtBerlinIstNochAltfall() throws Exception {
        // 2025-12-31T22:59:59Z == 31.12.2025 23:59:59 Berlin -> vor Stichtag
        Object decision = resolveDecision(payment("2025-12-31T22:59:59Z"), baseConfig());
        assertEquals("RECHNUNG", kindName(decision));
    }

    @Test
    void ohneTransaktionenEntscheidetWeiterhinDasZahllaufDatum() throws Exception {
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
        assertEquals(1, recordValue(decision, "fromCutoffCount"),
                "Undatierte Transaktionen zählen in keiner der beiden Informationsspalten mit");
        assertEquals(0, recordValue(decision, "beforeCutoffCount"));
    }

    @Test
    void stichtagIstKonfigurierbar() throws Exception {
        Properties config = baseConfig();
        config.setProperty("rechnungCutoffDate", "2025-07-01");
        Object decision = resolveDecision(payment("2025-08-15T10:00:00Z"), config);
        assertEquals("GUTSCHRIFT", kindName(decision));
    }

    @Test
    void stichtagsermittlungVeraendertDieKonfigurationNicht() throws Exception {
        // Absicherung: die Stichtagsprüfung darf keine Belegnummer verbrauchen.
        Properties config = baseConfig();
        Properties before = (Properties) config.clone();
        resolveDecision(payment("2026-02-01T10:00:00Z", "2025-12-20T10:00:00Z", "2026-01-05T10:00:00Z"), config);
        assertEquals(before, config, "resolveDocumentKind darf die Konfiguration (und damit Zähler) nicht anfassen");
    }

    @Test
    void leererFirmennameFaelltAufDenVorgabewertZurueck() throws Exception {
        // Ein gesetzter, aber leerer Wert zählt wie ein fehlender - sonst bliebe der Briefkopf leer.
        Properties config = baseConfig();
        config.setProperty("eInvoiceBuyerName", "");
        assertFalse(buyerName(config, "GUTSCHRIFT").isBlank(),
                "Ein leerer eInvoiceBuyerName darf den Gutschrift-Briefkopf nicht namenlos lassen");

        config.setProperty("legacyBuyerName", "   ");
        assertTrue(buyerName(config, "RECHNUNG").contains("VEMMiNA"),
                "Die Rechnung muss auf den VEMMiNA-Vorgabewert zurückfallen");
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
        assertTrue(template.indexOf("{{advisorName}}") < template.indexOf("{{buyerCompanyName}}"),
                "Die Beraterin muss vor VEMMiNA als Rechnungsstellerin erscheinen");
        assertTrue(template.contains("{{advisorName}} - Rechnungsstellerin"));
        assertFalse(template.contains("vemminaLogoDataUri"), "Die Rechnung der Beraterin darf kein VEMMiNA-Logo tragen");
        assertFalse(template.contains("<img"), "Die Standardvorlage benötigt kein fremdes Absenderlogo");
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
        assertTrue(template.contains("von {{advisorName}} an {{buyerCompanyName}}"));
        assertTrue(template.contains("Guten Tag,"));
        assertTrue(template.contains("Diese Nachricht wurde automatisch erstellt."));
        assertFalse(template.contains("Ihre Rechnung"));
        assertFalse(template.contains("Ihr VEMMiNA Team"));
        assertFalse(template.contains("Gutschrift"));
        assertFalse(template.contains("§ 14"));
    }

    @Test
    void rechnungMailRendertNeutralAnVemminaInHtmlUndText() throws Exception {
        JsonNode payment = MAPPER.readTree("{\"id\":\"p1\",\"amount\":\"100.00\",\"currency\":\"EUR\",\"payment_method\":\"Banküberweisung\",\"created_at\":\"2026-02-01T10:00:00Z\",\"transactions\":[{}]}");
        JsonNode affiliate = MAPPER.readTree("{\"name\":\"Erika Muster\"}");
        String buyer = "VEMMiNA Qualitäts- Haushaltsprodukte GmbH";
        String template = invokeStaticString("getDefaultRechnungMailHtmlTemplate");

        String html = invokeBuildMailHtml(payment, affiliate, "01.06.2025 bis 30.06.2025",
                template, "RE-2026-0001", "RECHNUNG", buyer);
        String text = invokeBuildMailBody(payment, affiliate, "01.06.2025 bis 30.06.2025",
                "RE-2026-0001", "RECHNUNG", buyer);

        for (String rendered : new String[]{html, text}) {
            assertTrue(rendered.contains("Provisionsrechnung RE-2026-0001"));
            assertTrue(rendered.contains("Erika Muster"));
            assertTrue(rendered.contains(buyer));
            assertTrue(rendered.contains("Rechnungsstellerin"));
            assertTrue(rendered.contains("Rechnungsempfängerin"));
            assertTrue(rendered.contains("Diese Nachricht wurde automatisch erstellt."));
            assertFalse(rendered.contains("{{"));
            assertFalse(rendered.contains("Ihre Rechnung"));
            assertFalse(rendered.contains("Ihr VEMMiNA Team"));
        }
    }

    @Test
    void rechnungBetreffNenntBeraterinUndGutschriftBetreffBleibtUnveraendert() throws Exception {
        assertEquals("Provisionsrechnung RE-2026-0001 von Erika Muster",
                invokeMailSubject("RECHNUNG", "RE-2026-0001", "01.06.2025 bis 30.06.2025", "Erika Muster"));
        assertEquals("Ihre VEMMiNA-Provisionsgutschrift GS-2026-0001 – 01.01.2026 bis 31.01.2026",
                invokeMailSubject("GUTSCHRIFT", "GS-2026-0001", "01.01.2026 bis 31.01.2026", "Erika Muster"));
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

    /**
     * Der über goaffpro.legacyEInvoicePdfRenderer erreichbare PDFBox-Pfad kennt nur die Gutschrift:
     * er liest fest eInvoiceBuyer*, druckt "Gutschriftempfängerin" und den § 14-Widerspruchshinweis.
     * Eine Altfall-Rechnung käme dort mit falschem Aussteller UND falschem Rechtstext heraus,
     * deshalb muss sie diesen Pfad umgehen.
     */
    @Test
    void altRendererDrucktKeineGutschriftAufEineRechnung(@TempDir Path tempDir) throws Exception {
        JsonNode affiliate = MAPPER.readTree("{\"name\":\"Erika Muster\",\"address_1\":\"Weg 1\",\"zip\":\"12345\",\"city\":\"Ort\"}");
        Path pdf = tempDir.resolve("altfall-rechnung-altrenderer.pdf");
        String previous = System.getProperty("goaffpro.legacyEInvoicePdfRenderer");
        System.setProperty("goaffpro.legacyEInvoicePdfRenderer", "true");
        try {
            invokeCreateEInvoicePdf(pdf, paymentWithTransactionDates("2025-06-01T10:00:00Z"), affiliate,
                    baseConfig(), "RE-2026-0001", "01.06.2025 bis 30.06.2025", true, "RECHNUNG");
        } finally {
            if (previous == null) System.clearProperty("goaffpro.legacyEInvoicePdfRenderer");
            else System.setProperty("goaffpro.legacyEInvoicePdfRenderer", previous);
        }

        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            String text = new PDFTextStripper().getText(document);
            assertFalse(text.contains("Gutschriftempfängerin"),
                    "Eine Rechnung darf die Beraterin nicht als Gutschriftempfängerin ausweisen");
            assertFalse(text.contains("§ 14"),
                    "Der § 14-Hinweis gehört ausschließlich auf die Gutschrift");
            assertFalse(text.contains("S+R"),
                    "Auf einer Altfall-Rechnung darf S+R nicht auftauchen");
            assertTrue(text.contains("Rechnungsstellerin"),
                    "Auch über die Alt-Property muss die Rechnung als Rechnung gedruckt werden");
        }
    }

    @Test
    void rechnungPdfRendertBeraterinAlsAbsenderinOhneVemminaLogo(@TempDir Path tempDir) throws Exception {
        JsonNode affiliate = MAPPER.readTree("""
                {
                  "name":"Erika Muster",
                  "email":"erika@example.com",
                  "phone":"+49 170 1234567",
                  "address_1":"Beraterweg 1",
                  "zip":"54321",
                  "city":"Beraterstadt",
                  "country":"DE",
                  "tax_identification_number":"98/765/43210",
                  "payment_details":{"iban":"DE00123456780000000000","bic":"GENODEF1XXX","account_holder":"Erika Muster"}
                }
                """);
        Path pdf = tempDir.resolve("altfall-rechnung.pdf");
        invokeCreateEInvoicePdf(pdf, paymentWithTransactionDates("2025-06-01T10:00:00Z"), affiliate,
                baseConfig(), "RE-2026-0001", "01.06.2025 bis 30.06.2025", true, "RECHNUNG");

        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            String text = new PDFTextStripper().getText(document);
            assertEquals(1, document.getNumberOfPages());
            assertTrue(text.indexOf("Erika Muster") < text.indexOf("VEMMiNA Qualitäts- Haushaltsprodukte GmbH"),
                    "Die Rechnungsstellerin muss vor der Rechnungsempfängerin erscheinen");
            assertTrue(text.contains("Rechnungsstellerin (Leistungserbringerin)"));
            assertTrue(text.contains("Rechnungsempfängerin (Leistungsempfängerin)"));
            assertTrue(text.contains("Bankverbindung der Rechnungsstellerin"));
            assertFalse(text.contains("{{"));
            assertFalse(hasImageXObject(document), "Die Rechnung der Beraterin darf kein VEMMiNA-Logo enthalten");
        }
    }

    @Test
    void alteStandardvorlageWirdMitUndOhneAbschliessendesZeilenendeMigriert() throws Exception {
        String previousDefault = "<html>alter Standard</html>";
        String normalizedFingerprint = invokeSha256(previousDefault);
        Properties withoutTrailingNewline = new Properties();
        withoutTrailingNewline.setProperty("template", previousDefault);
        Properties withCrLfAndTrailingWhitespace = new Properties();
        withCrLfAndTrailingWhitespace.setProperty("template", previousDefault + "\r\n  \r\n");

        invokeMigrateTemplate(withoutTrailingNewline, "template", normalizedFingerprint,
                "<html>neuer Standard</html>");
        invokeMigrateTemplate(withCrLfAndTrailingWhitespace, "template", normalizedFingerprint,
                "<html>neuer Standard</html>");

        assertEquals("<html>neuer Standard</html>", withoutTrailingNewline.getProperty("template"));
        assertEquals("<html>neuer Standard</html>", withCrLfAndTrailingWhitespace.getProperty("template"));
    }

    @Test
    void individuelleOderLeereVorlageWirdNichtMigriert() throws Exception {
        String previousDefault = "<html>alter Standard</html>";
        String normalizedFingerprint = invokeSha256(previousDefault);
        Properties custom = new Properties();
        custom.setProperty("template", previousDefault + " angepasst");
        Properties blank = new Properties();
        blank.setProperty("template", "   ");

        invokeMigrateTemplate(custom, "template", normalizedFingerprint, "<html>neuer Standard</html>");
        invokeMigrateTemplate(blank, "template", normalizedFingerprint, "<html>neuer Standard</html>");

        assertEquals(previousDefault + " angepasst", custom.getProperty("template"));
        assertEquals("   ", blank.getProperty("template"));
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
