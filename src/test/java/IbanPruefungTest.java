import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Syntax- und Prüfsummenprüfung der IBAN. Der Grund steht als "Korrekte IBAN (die hinterlegte IBAN …)"
 * in der Erinnerungsmail - er muss also stimmen und für die Beraterin verständlich sein.
 */
class IbanPruefungTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String NBSP = " ";

    @TempDir
    Path tmp;

    private static Object invoke(String name, Object arg) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod(name, String.class);
        m.setAccessible(true);
        try {
            return m.invoke(null, arg);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private static String problem(String iban) throws Exception {
        return (String) invoke("ibanProblem", iban);
    }

    private static boolean valid(String iban) throws Exception {
        return (Boolean) invoke("isValidIban", iban);
    }

    /** Prüfziffern nach ISO 7064 berechnen - so erzeugt der Test passende IBANs selbst. */
    private static String withCheckDigits(String country, String bban) {
        String rearranged = bban + country + "00";
        int mod = 0;
        for (char c : rearranged.toCharArray()) {
            int value = Character.isDigit(c) ? c - '0' : c - 'A' + 10;
            mod = ((value < 10 ? mod * 10 : mod * 100) + value) % 97;
        }
        return country + String.format("%02d", 98 - mod) + bban;
    }

    @Test
    void gueltigeIbansAusDenLiefergebieten() throws Exception {
        for (String iban : new String[]{
                "DE89370400440532013000", "AT611904300234573201", "CH9300762011623852957",
                "NL91ABNA0417164300", "BE68539007547034", "LU280019400644750000"}) {
            assertEquals("", problem(iban), iban);
            assertTrue(valid(iban), iban);
        }
    }

    @Test
    void leerzeichenUndKleinschreibungSindKeinFehler() throws Exception {
        assertTrue(valid("DE89 3704 0044 0532 0130 00"));
        assertTrue(valid("de89370400440532013000"));
        // So kam es aus GoAffPro: geschützte Leerzeichen (U+00A0). Bisher galt die IBAN deshalb als ungültig.
        String withNbsp = String.join(NBSP, "AT61", "1904", "3002", "3457", "3201");
        assertEquals("", problem(withNbsp));
        assertTrue(valid(withNbsp));
        assertTrue(valid("\tDE89370400440532013000 "));
    }

    @Test
    void falschePruefsummeWirdErkannt() throws Exception {
        assertEquals("hat eine falsche Prüfsumme (vermutlich ein Tippfehler oder Zahlendreher)",
                problem("DE89370400440532013001"));
        // Zahlendreher: zwei benachbarte Ziffern vertauscht
        assertFalse(valid("DE89370400440532031000"));
    }

    @Test
    void laengeWirdJeLandGeprueft() throws Exception {
        assertEquals("hat 21 statt 22 Zeichen", problem("DE8937040044053201300"));
        assertEquals("hat 23 statt 22 Zeichen", problem("DE893704004405320130001"));
        assertEquals("hat 19 statt 20 Zeichen", problem("AT61190430023457320"));
    }

    @Test
    void buchstabeImReinNumerischenKontoteil() throws Exception {
        // "O" statt "0" - in Deutschland besteht der Kontoteil nur aus Ziffern.
        assertEquals("enthält nach den Prüfziffern Buchstaben, erlaubt sind dort nur Ziffern",
                problem("DE8937040044053201300O"));
        // In den Niederlanden gehören Buchstaben dazu (Bankkürzel).
        assertEquals("", problem("NL91ABNA0417164300"));
    }

    @Test
    void unzulaessigeZeichenWerdenGenannt() throws Exception {
        // Aus den echten Stammdaten
        assertEquals("enthält unzulässige Zeichen („ö“)", problem("JöLKJ"));
        assertEquals("enthält unzulässige Zeichen („-“)", problem("DE89-3704-0044-0532-0130-00"));
        assertEquals("enthält unzulässige Zeichen („ß“, „/“)", problem("DE89ß3704/0044"));
    }

    @Test
    void ohneLaenderkennungUndPruefziffern() throws Exception {
        assertEquals("beginnt nicht mit Länderkennung und zwei Prüfziffern (z. B. DE12 …)", problem("370400440532013000"));
        assertEquals("beginnt nicht mit Länderkennung und zwei Prüfziffern (z. B. DE12 …)", problem("DEXX370400440532013000"));
    }

    @Test
    void pruefziffer99StattZweiIstFalsch() throws Exception {
        // 99 ergibt dieselbe Prüfsumme wie 02, Prüfziffern laufen aber nur von 02 bis 98.
        String bban = null;
        for (int i = 0; i < 10_000 && bban == null; i++) {
            String candidate = "370400440532" + String.format("%06d", i);
            if (withCheckDigits("DE", candidate).startsWith("DE02")) bban = candidate;
        }
        assertNotNull(bban, "Testdaten: keine IBAN mit Prüfziffern 02 gefunden");
        assertEquals("", problem("DE02" + bban));
        assertEquals("hat eine falsche Prüfsumme (vermutlich ein Tippfehler oder Zahlendreher)", problem("DE99" + bban));
    }

    @Test
    void unbekanntesLandNurAllgemeinGeprueft() throws Exception {
        // Kein Fehlalarm für Länder außerhalb der Längentabelle - nur Spanne 15–34 und Prüfsumme.
        String iban = withCheckDigits("QQ", "1234567890123");
        assertEquals("", problem(iban));
        assertEquals("ist mit 9 Zeichen zu kurz", problem(withCheckDigits("QQ", "12345")));
    }

    @Test
    void leerIstKeinSyntaxfehler() throws Exception {
        // Eine fehlende IBAN meldet die Prüfung auf fehlende Felder - nicht diese hier.
        assertEquals("", problem(null));
        assertEquals("", problem("   "));
        assertFalse(valid(null));
        assertFalse(valid(""));
    }

    @Test
    void maskierungMitGeschuetztenLeerzeichen() throws Exception {
        assertEquals("AT61 **** **** **** 3201",
                invoke("maskIban", String.join(NBSP, "AT61", "1904", "3002", "3457", "3201")));
    }

    @Test
    void eRechnungEnthaeltDieIbanOhneLeerzeichen() throws Exception {
        String grouped = String.join(NBSP, "DE02", "1203", "0000", "0000", "2020", "51");
        JsonNode payment = MAPPER.readTree("""
                {"id":"3573613","affiliate_id":"21009999","amount":"31.82","currency":"EUR","created_at":"2026-11-12T09:00:00Z",
                 "payment_details":{"account_number":"%s","branch_code":"BYLADEM1001","account_name":"Katrin Muster"},
                 "transactions":[
                   {"entity_type":"orders","event_type":"insert","amount":"31.82","created_at":"2026-10-04T18:25:00Z","metadata":{"order_number":"#1","order_value":"100.00","commission_on":"80.00"}}]}
                """.formatted(grouped));
        JsonNode affiliate = MAPPER.readTree("""
                {"id":"21009999","name":"Katrin Muster","email":"katrin@example.com","address_1":"Musterweg 1",
                 "zip":"12345","city":"Musterstadt","country":"DE","tax_identification_number":"21/815/30140"}
                """);
        Properties config = new Properties();
        config.setProperty("eInvoiceBuyerName", "S+R Linear Technology GmbH");
        config.setProperty("eInvoiceBuyerCountry", "DE");

        Class<?> kindClass = Class.forName("WebUiServer$DocumentKind");
        Object gutschrift = null;
        for (Object constant : kindClass.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals("GUTSCHRIFT")) gutschrift = constant;
        }
        Method m = WebUiServer.class.getDeclaredMethod("createZugferdInvoiceXml",
                Path.class, JsonNode.class, JsonNode.class, Properties.class,
                String.class, String.class, GutschriftTextTest.taxClass(), kindClass, String.class);
        m.setAccessible(true);
        Path xml = tmp.resolve("gutschrift.xml");
        try {
            m.invoke(null, xml, payment, affiliate, config, "GS-2026-0099", "Oktober 2026",
                    GutschriftTextTest.taxTreatment(true), gutschrift, "");
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
        String content = Files.readString(xml, StandardCharsets.UTF_8);
        assertTrue(content.contains("<ram:IBANID>DE02120300000000202051</ram:IBANID>"), content);
        assertFalse(content.contains(NBSP), "Kein geschütztes Leerzeichen in der XML");
    }
}
