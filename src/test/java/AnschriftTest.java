import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Anschrift der Beraterin: GoAffPro erzwingt keine vollständige Adresse. Bei manchen Konten sind PLZ
 * und Ort gepflegt, die Straße aber leer - die zusammengesetzte Anschrift ist dann nicht leer und fiel
 * durch jede Prüfung, obwohl die Gutschrift die vollständige Anschrift braucht.
 */
class AnschriftTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Object invoke(String name, JsonNode affiliate) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod(name, JsonNode.class);
        m.setAccessible(true);
        try {
            return m.invoke(null, affiliate);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private static JsonNode affiliate(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    @SuppressWarnings("unchecked")
    private static List<String> missing(JsonNode affiliate) throws Exception {
        return (List<String>) invoke("missingAddressParts", affiliate);
    }

    private static String formatted(JsonNode affiliate) throws Exception {
        return (String) invoke("formatAffiliateAddress", affiliate);
    }

    @Test
    void plzUndOrtOhneStrasseIstUnvollstaendig() throws Exception {
        // So liefert GoAffPro das Konto aus dem Fehlerbild: address_1 ist null.
        JsonNode a = affiliate("""
                {"address_1":null,"address_2":null,"city":"Duttenbrunn","state":"Bayern","zip":"97225","country":"DE"}
                """);
        assertEquals("97225 Duttenbrunn, Bayern, DE", formatted(a), "Die Anschrift selbst ist nicht leer");
        assertEquals(List.of("Straße und Hausnummer"), missing(a));
    }

    @Test
    void vollstaendigeAnschriftHatKeineLuecke() throws Exception {
        JsonNode a = affiliate("""
                {"address_1":"Kirchgasse 32","address_2":"","city":"Kirtorf","state":"Hessen","zip":"36320","country":"DE"}
                """);
        assertEquals("Kirchgasse 32, 36320 Kirtorf, Hessen, DE", formatted(a));
        assertEquals(List.of(), missing(a));
    }

    @Test
    void strasseInZeileZweiZaehltAlsStrasse() throws Exception {
        JsonNode a = affiliate("""
                {"address_1":"","address_2":"Hauptstraße 5","city":"Ulm","zip":"89073","country":"DE"}
                """);
        assertEquals(List.of(), missing(a));
    }

    @Test
    void fehlendePlzUndOrtWerdenEinzelnGenannt() throws Exception {
        JsonNode a = affiliate("""
                {"address_1":"Hauptstraße 5","city":"","zip":null,"country":"DE"}
                """);
        assertEquals(List.of("PLZ", "Ort"), missing(a));
    }

    @Test
    void ohneJedeAnschriftHeisstEsSchlichtAdresse() throws Exception {
        assertEquals(List.of("Adresse"), missing(null));
        assertEquals(List.of("Adresse"), missing(affiliate("{}")));
        // Bundesland und Land allein sind keine Anschrift.
        assertEquals(List.of("Adresse"), missing(affiliate("""
                {"address_1":null,"city":null,"zip":"","state":"Bayern","country":"DE"}
                """)));
    }

    @Test
    void plzImOrtsfeldErscheintNichtDoppelt() throws Exception {
        // Aus den echten Stammdaten: Die PLZ steht zusätzlich im Ortsfeld.
        JsonNode a = affiliate("""
                {"address_1":"Rheinstraße 1","city":"6890 Lustenau","state":"Vorarlberg","zip":"6890","country":"AT"}
                """);
        assertEquals("Rheinstraße 1, 6890 Lustenau, Vorarlberg, AT", formatted(a));
        assertEquals(List.of(), missing(a));
    }

    @Test
    void aehnlichePlzImOrtWirdNichtVerschluckt() throws Exception {
        // Nur eine echte Wiederholung entfällt - "68901 …" beginnt zwar mit "6890", ist aber etwas anderes.
        JsonNode a = affiliate("""
                {"address_1":"Weg 2","city":"68901 Irgendwo","zip":"6890","country":"AT"}
                """);
        assertEquals("Weg 2, 6890 68901 Irgendwo, AT", formatted(a));
    }

    @Test
    void anschriftfeldDerGutschriftOhneStrasse() throws Exception {
        JsonNode a = affiliate("""
                {"address_1":null,"city":"Duttenbrunn","state":"Bayern","zip":"97225","country":"DE"}
                """);
        assertEquals(List.of("97225 Duttenbrunn, Bayern", "DE"), invoke("affiliateAddressParts", a));
    }
}
