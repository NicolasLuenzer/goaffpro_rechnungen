import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.xml.DomXmpParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ZUGFeRD/Factur-X im Profil EN16931: XML-Kennung, Abrechnungszeitraum, PDF/A-3 mit
 * eingebetteten Schriften und XMP, Anhang factur-x.xml - und dass PDF und XML dasselbe sagen.
 */
class FacturXPdfTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tmp;

    // ── Reflection-Helfer ──

    private static String periodLabel(JsonNode payment) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("buildPaymentPeriodLabel", JsonNode.class);
        m.setAccessible(true);
        return (String) m.invoke(null, payment);
    }

    private Path createXml(JsonNode payment, JsonNode affiliate, Properties config) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("createZugferdInvoiceXml",
                Path.class, JsonNode.class, JsonNode.class, Properties.class,
                String.class, String.class, GutschriftTextTest.taxClass());
        m.setAccessible(true);
        Path xml = tmp.resolve("gutschrift.xml");
        m.invoke(null, xml, payment, affiliate, config, "GS-2026-0099", periodLabel(payment),
                GutschriftTextTest.taxTreatment(true));
        return xml;
    }

    private Path createPdf(Path xmlOrNull, JsonNode payment, JsonNode affiliate, Properties config) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("createEInvoicePdfWithEmbeddedXml",
                Path.class, Path.class, JsonNode.class, JsonNode.class, Properties.class,
                String.class, String.class, GutschriftTextTest.taxClass());
        m.setAccessible(true);
        Path pdf = tmp.resolve("gutschrift.pdf");
        m.invoke(null, pdf, xmlOrNull, payment, affiliate, config, "GS-2026-0099", periodLabel(payment),
                GutschriftTextTest.taxTreatment(true));
        return pdf;
    }

    private static boolean hasSellerTaxIdentifier(JsonNode affiliate) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("hasSellerTaxIdentifier", JsonNode.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, affiliate);
    }

    // ── Testdaten: Kleinunternehmerin mit Steuernummer, drei Oktober-Provisionen über 31,82 € ──

    private static JsonNode payment() throws Exception {
        return MAPPER.readTree("""
                {"id":"3573613","amount":"31.82","currency":"EUR","created_at":"2026-11-12T09:00:00Z",
                 "payment_details":{"account_number":"DE02120300000000202051","branch_code":"BYLADEM1001","account_name":"Katrin Muster"},
                 "transactions":[
                   {"tx_id":1,"entity_type":"orders","entity_id":11,"event_type":"insert","amount":"6.54","created_at":"2026-10-04T18:25:00Z","metadata":{"order_number":"#1"}},
                   {"tx_id":2,"entity_type":"orders","entity_id":12,"event_type":"insert","amount":"9.82","created_at":"2026-10-11T17:24:00Z","metadata":{"order_number":"#2"}},
                   {"tx_id":3,"entity_type":"orders","entity_id":13,"event_type":"insert","amount":"15.46","created_at":"2026-10-28T20:55:00Z","metadata":{"order_number":"#3"}}]}
                """);
    }

    private static JsonNode affiliate(String taxNumber) throws Exception {
        return MAPPER.readTree("""
                {"id":"21009999","name":"Katrin Muster","email":"katrin@example.com","phone":"+49123",
                 "address_1":"Musterweg 1","zip":"12345","city":"Musterstadt","country":"DE",
                 "tax_identification_number":"%s"}
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

    private static String xmlValue(String xml, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(xml);
        assertTrue(m.find(), "Nicht in der XML gefunden: " + regex);
        return m.group(1);
    }

    private static String embeddedXml(PDDocument document) throws Exception {
        PDComplexFileSpecification fs = document.getDocumentCatalog().getNames().getEmbeddedFiles()
                .getNames().get("factur-x.xml");
        return new String(fs.getEmbeddedFile().toByteArray(), StandardCharsets.UTF_8);
    }

    private static List<PDFont> allPageFonts(PDDocument document) throws Exception {
        List<PDFont> fonts = new ArrayList<>();
        for (PDPage page : document.getPages()) {
            PDResources res = page.getResources();
            for (COSName name : res.getFontNames()) fonts.add(res.getFont(name));
        }
        return fonts;
    }

    // ── XML ──

    @Test
    void xml_profilkennungIstEn16931() throws Exception {
        String xml = Files.readString(createXml(payment(), affiliate("21/815/30140"), config()));
        assertTrue(xml.contains("<ram:ID>urn:cen.eu:en16931:2017</ram:ID>"));
        assertFalse(xml.contains("zugferd.de:2p2:basic"), "Die BASIC-Kennung passt nicht zu den enthaltenen Feldern");
    }

    @Test
    void xml_abrechnungszeitraumStehtNachDerSteuerUndVorDenZahlungsbedingungen() throws Exception {
        String xml = Files.readString(createXml(payment(), affiliate("21/815/30140"), config()));
        int settlement = xml.indexOf("<ram:ApplicableHeaderTradeSettlement>");
        int tax = xml.indexOf("</ram:ApplicableTradeTax>", settlement);
        int period = xml.indexOf("<ram:BillingSpecifiedPeriod>", settlement);
        int terms = xml.indexOf("<ram:SpecifiedTradePaymentTerms>", settlement);
        assertTrue(tax < period && period < terms, "CII-Reihenfolge: ApplicableTradeTax, BillingSpecifiedPeriod, PaymentTerms");
        assertTrue(xml.contains("<ram:StartDateTime><udt:DateTimeString format=\"102\">20261004</udt:DateTimeString></ram:StartDateTime>"));
        assertTrue(xml.contains("<ram:EndDateTime><udt:DateTimeString format=\"102\">20261028</udt:DateTimeString></ram:EndDateTime>"));
    }

    /** Das Datum gilt in Berlin: 28.10. 23:30 UTC ist schon der 29.10. */
    @Test
    void xml_abrechnungszeitraumRechnetInBerlinerZeit() throws Exception {
        JsonNode payment = MAPPER.readTree("""
                {"id":"1","amount":"5.00","currency":"EUR","created_at":"2026-11-12T09:00:00Z",
                 "transactions":[{"tx_id":1,"entity_type":"orders","entity_id":1,"event_type":"insert","amount":"5.00","created_at":"2026-10-28T23:30:00Z"}]}
                """);
        String xml = Files.readString(createXml(payment, affiliate("21/815/30140"), config()));
        assertTrue(xml.contains("<ram:EndDateTime><udt:DateTimeString format=\"102\">20261029</udt:DateTimeString>"));
    }

    @Test
    void xml_ohneDatierbareBuchungenKeinAbrechnungszeitraum() throws Exception {
        JsonNode payment = MAPPER.readTree("""
                {"id":"1","amount":"5.00","currency":"EUR","created_at":"2026-11-12T09:00:00Z","transactions":[]}
                """);
        String xml = Files.readString(createXml(payment, affiliate("21/815/30140"), config()));
        assertFalse(xml.contains("BillingSpecifiedPeriod"));
    }

    /** Im CII-Schema Pflicht; füllbar nur mit einem Lieferdatum, das es hier nicht gibt. */
    @Test
    void xml_lieferBlockBleibtAlsPflichtelementStehen() throws Exception {
        String xml = Files.readString(createXml(payment(), affiliate("21/815/30140"), config()));
        assertTrue(xml.contains("<ram:ApplicableHeaderTradeDelivery/>"));
    }

    @Test
    void steuerkennungWirdNurAusDenStammdatenErkannt() throws Exception {
        assertTrue(hasSellerTaxIdentifier(affiliate("21/815/30140")));
        assertTrue(hasSellerTaxIdentifier(affiliate("DE123456789")));
        assertFalse(hasSellerTaxIdentifier(affiliate("")));
        assertFalse(hasSellerTaxIdentifier(affiliate("   ")));
        assertFalse(hasSellerTaxIdentifier(null));
    }

    // ── PDF ──

    @Test
    void pdf_anhangHeisstFacturXmlMitRichtigerKennzeichnung() throws Exception {
        JsonNode payment = payment();
        Path pdf = createPdf(createXml(payment, affiliate("21/815/30140"), config()), payment, affiliate("21/815/30140"), config());
        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            PDDocumentCatalog catalog = document.getDocumentCatalog();
            Map<String, PDComplexFileSpecification> files = catalog.getNames().getEmbeddedFiles().getNames();
            assertEquals(List.of("factur-x.xml"), new ArrayList<>(files.keySet()), "Nur unter diesem Namen erkennt Mustang die XML");
            PDComplexFileSpecification fs = files.get("factur-x.xml");
            assertEquals("factur-x.xml", fs.getFile());
            assertEquals("factur-x.xml", fs.getFileUnicode());
            assertEquals("text/xml", fs.getEmbeddedFile().getSubtype());
            assertNotNull(fs.getEmbeddedFile().getModDate());
            assertEquals("Alternative", fs.getCOSObject().getNameAsString(COSName.AF_RELATIONSHIP));
            COSArray af = (COSArray) catalog.getCOSObject().getDictionaryObject(COSName.AF);
            assertEquals(1, af.size(), "Die Anlage muss im /AF des Dokuments stehen");
        }
    }

    @Test
    void pdf_xmpIstPdfA3UndPasstZurXml() throws Exception {
        JsonNode payment = payment();
        Path pdf = createPdf(createXml(payment, affiliate("21/815/30140"), config()), payment, affiliate("21/815/30140"), config());
        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            byte[] raw = document.getDocumentCatalog().getMetadata().toByteArray();
            String xmp = new String(raw, StandardCharsets.UTF_8);

            // Strikt geparst: Ein Namensraum ohne Schemabeschreibung würde hier scheitern.
            XMPMetadata parsed = new DomXmpParser().parse(new ByteArrayInputStream(raw));
            PDFAIdentificationSchema id = parsed.getPDFIdentificationSchema();
            assertEquals(3, id.getPart());
            assertEquals("B", id.getConformance());

            assertTrue(xmp.contains("<fx:DocumentFileName>factur-x.xml</fx:DocumentFileName>"));
            assertTrue(xmp.contains("<fx:ConformanceLevel>EN 16931</fx:ConformanceLevel>"));
            assertTrue(xmp.contains("<fx:DocumentType>INVOICE</fx:DocumentType>"));
            assertTrue(xmp.contains("<pdfaSchema:namespaceURI>urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#</pdfaSchema:namespaceURI>"));
            assertTrue(embeddedXml(document).contains("<ram:ID>urn:cen.eu:en16931:2017</ram:ID>"),
                    "XMP-Konformitätsstufe EN 16931 gehört zur XML-Kennung urn:cen.eu:en16931:2017");
        }
    }

    @Test
    void pdf_infoDictionaryUndXmpStimmenUeberein() throws Exception {
        JsonNode payment = payment();
        Path pdf = createPdf(createXml(payment, affiliate("21/815/30140"), config()), payment, affiliate("21/815/30140"), config());
        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            String xmp = new String(document.getDocumentCatalog().getMetadata().toByteArray(), StandardCharsets.UTF_8);
            var info = document.getDocumentInformation();
            assertEquals("Gutschrift GS-2026-0099", info.getTitle());
            assertTrue(xmp.contains("<rdf:li xml:lang=\"x-default\">" + info.getTitle() + "</rdf:li>"));
            assertTrue(xmp.contains("<pdf:Producer>" + info.getProducer() + "</pdf:Producer>"));
            assertTrue(xmp.contains("<xmp:CreatorTool>" + info.getCreator() + "</xmp:CreatorTool>"));
            String created = xmlValue(xmp, "<xmp:CreateDate>([^<]+)</xmp:CreateDate>");
            assertEquals(info.getCreationDate().toInstant(), OffsetDateTime.parse(created).toInstant());
        }
    }

    @Test
    void pdf_hatOutputIntentUndNurEingebetteteSchriften() throws Exception {
        JsonNode payment = payment();
        Path pdf = createPdf(createXml(payment, affiliate("21/815/30140"), config()), payment, affiliate("21/815/30140"), config());
        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            assertFalse(document.getDocumentCatalog().getOutputIntents().isEmpty(), "PDF/A verlangt einen OutputIntent");
            List<PDFont> fonts = allPageFonts(document);
            assertFalse(fonts.isEmpty());
            for (PDFont font : fonts) {
                assertTrue(font.isEmbedded(), "Nicht eingebettet: " + font.getName());
                assertTrue(font.getName().contains("LiberationSans"), "Unerwartete Schrift: " + font.getName());
            }
            assertTrue(fonts.stream().anyMatch(f -> f.getName().contains("Bold")), "Fettschrift muss als eigener Schnitt eingebettet sein");
        }
    }

    /** Auch eine Designer-Vorlage mit beliebiger Schrift darf nie auf eine Standardschrift fallen. */
    @Test
    void pdf_unbekannteSchriftfamilieWirdTrotzdemEingebettet() throws Exception {
        Properties config = config();
        config.setProperty("eInvoicePdfTemplateHtml",
                "<html><body><p style=\"font-family:'Comic Sans MS'\">Gutschrift <b>{{gutschriftNr}}</b> <i>kursiv</i></p>"
                        + "<p style=\"font-family:Georgia, serif\">{{amount}}</p><pre>{{paymentId}}</pre></body></html>");
        JsonNode payment = payment();
        Path pdf = createPdf(createXml(payment, affiliate("21/815/30140"), config), payment, affiliate("21/815/30140"), config);
        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            for (PDFont font : allPageFonts(document)) {
                assertTrue(font.isEmbedded(), "Nicht eingebettet: " + font.getName());
            }
        }
    }

    /** Was die Beraterin liest, muss dem entsprechen, was die Buchhaltungssoftware einliest. */
    @Test
    void abgleich_sichtbareAngabenEntsprechenDerXml() throws Exception {
        JsonNode payment = payment();
        JsonNode affiliate = affiliate("21/815/30140");
        Path pdf = createPdf(createXml(payment, affiliate, config()), payment, affiliate, config());
        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            String xml = embeddedXml(document);
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");

            String number = xmlValue(xml, "<rsm:ExchangedDocument>\\s*<ram:ID>([^<]+)</ram:ID>");
            assertEquals("GS-2026-0099", number);
            assertTrue(text.contains(number));

            String issue = xmlValue(xml, "<ram:IssueDateTime><udt:DateTimeString format=\"102\">(\\d{8})<");
            String issueDe = issue.substring(6, 8) + "." + issue.substring(4, 6) + "." + issue.substring(0, 4);
            assertTrue(text.contains(issueDe), "Gutschriftdatum " + issueDe + " fehlt im PDF");

            String position = xmlValue(xml, "<ram:SpecifiedTradeProduct><ram:Name>([^<]+)</ram:Name>");
            assertEquals("Vermittlungsprovision lt. Provisionszeitraum 04.10.2026 bis 28.10.2026", position);
            assertTrue(text.contains(position), "Positionstext muss im PDF genauso stehen");

            assertEquals("20261004", xmlValue(xml, "<ram:StartDateTime><udt:DateTimeString format=\"102\">(\\d{8})<"));
            assertEquals("20261028", xmlValue(xml, "<ram:EndDateTime><udt:DateTimeString format=\"102\">(\\d{8})<"));

            assertEquals("31.82", xmlValue(xml, "<ram:TaxBasisTotalAmount>([^<]+)<"));
            assertEquals("0.00", xmlValue(xml, "<ram:TaxTotalAmount[^>]*>([^<]+)<"));
            assertEquals("31.82", xmlValue(xml, "<ram:DuePayableAmount>([^<]+)<"));
            assertTrue(text.contains("31,82 €"), "Betrag 31,82 € fehlt im PDF");
            assertTrue(text.contains("0,00 €"), "Umsatzsteuer 0,00 € fehlt im PDF");

            String iban = xmlValue(xml, "<ram:IBANID>([^<]+)<");
            String bic = xmlValue(xml, "<ram:BICID>([^<]+)<");
            assertTrue(text.contains(iban), "IBAN fehlt im PDF");
            assertTrue(text.contains(bic), "BIC fehlt im PDF");
            assertTrue(xml.contains("<ram:ID schemeID=\"FC\">21/815/30140</ram:ID>"));
            assertTrue(text.contains("21/815/30140"), "Steuernummer fehlt im PDF");
        }
    }

    // ── Ohne Steuerkennung: PDF-Gutschrift, aber keine E-Rechnung ──

    @Test
    void ohneXmlEntstehtPdfAOhneAnhangUndOhneFacturXAngaben() throws Exception {
        Path pdf = createPdf(null, payment(), affiliate(""), config());
        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            PDDocumentCatalog catalog = document.getDocumentCatalog();
            assertTrue(catalog.getNames() == null || catalog.getNames().getEmbeddedFiles() == null,
                    "Ohne Steuerkennung darf keine XML eingebettet sein");
            assertNull(catalog.getCOSObject().getDictionaryObject(COSName.AF));
            String xmp = new String(catalog.getMetadata().toByteArray(), StandardCharsets.UTF_8);
            assertTrue(xmp.contains("<pdfaid:part>3</pdfaid:part>"), "Die PDF bleibt trotzdem PDF/A-3");
            assertFalse(xmp.contains("fx:"), "Ohne Rechnungsdaten keine Factur-X-Angaben in den Metadaten");
            for (PDFont font : allPageFonts(document)) assertTrue(font.isEmbedded());
            assertTrue(new PDFTextStripper().getText(document).contains("31,82"));
        }
    }
}
