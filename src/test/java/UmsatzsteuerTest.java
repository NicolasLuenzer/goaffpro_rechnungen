import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Umsatzsteuerliche Behandlung der Provisionsgutschrift.
 *
 * GoAffPro kennt kein eigenes USt-ID-Feld: tax_identification_number ist ein Sammelfeld, in dem
 * Steuernummern, Steuer-IDs und Umsatzsteuer-IDs nebeneinander stehen. Nur eine Umsatzsteuer-ID
 * begründet den Steuerausweis - eine Steuernummer hat jede steuerpflichtige Person.
 */
class UmsatzsteuerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String treatment(String taxIdentificationNumber) throws Exception {
        JsonNode affiliate = taxIdentificationNumber == null
                ? null
                : MAPPER.readTree("{\"name\":\"Beraterin\",\"tax_identification_number\":"
                        + MAPPER.writeValueAsString(taxIdentificationNumber) + "}");
        Method m = WebUiServer.class.getDeclaredMethod("resolveTaxTreatment", JsonNode.class);
        m.setAccessible(true);
        return ((Enum<?>) m.invoke(null, affiliate)).name();
    }

    private static double vat(double net, String treatmentName) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("calculateVat", double.class,
                GutschriftTextTest.taxClass());
        m.setAccessible(true);
        return (Double) m.invoke(null, net, GutschriftTextTest.taxTreatment(treatmentName));
    }

    @Test
    void deutscheUmsatzsteuerIdLoestNeunzehnProzentAus() throws Exception {
        assertEquals("STANDARD", treatment("DE449899715"));
        assertEquals(19.0, vat(100.0, "STANDARD"), 0.0001);
    }

    /**
     * Der Testfall aus dem Bestand: "de123654" ist als deutsche USt-IdNr zu kurz (DE + 6 statt 9
     * Ziffern). Die Erkennung bleibt bewusst tolerant - stillschweigend keine Steuer auszuweisen
     * wäre der gefährlichere Fehler. Auffallen soll der Formfehler in der Stammdaten-Validierung.
     */
    @Test
    void formalFehlerhafteUmsatzsteuerIdGiltTrotzdemAlsSolche() throws Exception {
        assertEquals("STANDARD", treatment("de123654"));
        assertTrue(invokeLooksLikeVatId("de123654"));
        assertFalse(invokeIsStructurallyValid("de123654"),
                "Die Validierung muss die zu kurze ID als fehlerhaft melden");
        assertTrue(invokeIsStructurallyValid("DE449899715"));
    }

    @Test
    void auslaendischeUmsatzsteuerIdErgibtReverseCharge() throws Exception {
        assertEquals("REVERSE_CHARGE", treatment("LU22 899 700"));
        assertEquals(0.0, vat(100.0, "REVERSE_CHARGE"), 0.0001);
        assertTrue(invokeIsStructurallyValid("LU22 899 700"), "LU + 8 Ziffern ist gültig");
    }

    /**
     * Die eigentliche Korrektur: diese Werte stehen real im Sammelfeld und sind Steuernummern
     * bzw. Steuer-IDs. Vorher löste jeder nicht-leere Wert 19 % aus.
     */
    @Test
    void reineSteuernummerLoestKeineUmsatzsteuerAus() throws Exception {
        for (String steuernummer : new String[]{
                "302/5039/0202", "102/259/82401", "95382047671", "03985830096",
                "63085240719", "72 980 534 363", "11 117 271 80682 57 2443",
                "23453245", "2342394832098", "12357895"}) {
            assertEquals("KLEINUNTERNEHMER", treatment(steuernummer),
                    "Steuernummer darf keinen Steuerausweis auslösen: " + steuernummer);
        }
    }

    @Test
    void ohneAngabeGiltDieKleinunternehmerregelung() throws Exception {
        assertEquals("KLEINUNTERNEHMER", treatment(""));
        assertEquals("KLEINUNTERNEHMER", treatment("   "));
        assertEquals("KLEINUNTERNEHMER", treatment(null));
        assertEquals(0.0, vat(100.0, "KLEINUNTERNEHMER"), 0.0001);
    }

    @Test
    void schreibweiseIstEgal() throws Exception {
        assertEquals("STANDARD", treatment("de 449 899 715"));
        assertEquals("STANDARD", treatment("DE-449899715"));
        assertEquals("STANDARD", treatment("DE449899715 "));
    }

    /** Die Beträge der Beispielrechnung, damit die Rundung nachvollziehbar bleibt. */
    @Test
    void steuerbetragRundetWieInDenUebrigenRechnungen() throws Exception {
        assertEquals(403.56, vat(2124.00, "STANDARD"), 0.005);
    }

    private static boolean invokeLooksLikeVatId(String raw) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("looksLikeVatId", String.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, raw);
    }

    private static boolean invokeIsStructurallyValid(String raw) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("isStructurallyValidVatId", String.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, raw);
    }
}
