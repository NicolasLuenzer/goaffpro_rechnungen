import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Erkennung von Vertragsdokumenten je Beraterin fuer die Stammdaten-Validierung.
 *
 * Datengrundlage ist /v1/admin/files OHNE fields-Parameter - nur dann liefert GoAffPro
 * affiliate_id, file_title und filename mit.
 */
class ValidationContractTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode files(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    /**
     * Der reale Fall aus dem Shop: der unterschriebene SERVICEPARTNER-Vertrag liegt als
     * "contract_<contract_id>.pdf" ab und traegt das Wort "Vertrag" ausschliesslich im Titel.
     * Eine Suche nur im Dateinamen faende ihn nicht - deshalb ist das der wichtigste Test.
     */
    @Test
    void vertragWirdAuchErkanntWennNurDerTitelIhnNennt() throws Exception {
        Map<String, String> result = WebUiServer.collectContractDocuments(files("""
                [{"affiliate_id":15047196,"filename":"contract_qthnidfhol.pdf","file_title":"VEMMiNA Beratervertrag"}]
                """));

        assertEquals(Map.of("15047196", "VEMMiNA Beratervertrag"), result);
        assertFalse(WebUiServer.mentionsContract("contract_qthnidfhol.pdf"),
                "Der Dateiname allein enthält kein 'Vertrag' - genau daran scheitert eine Dateinamenssuche");
    }

    @Test
    void vertragWirdAuchImDateinamenErkannt() throws Exception {
        Map<String, String> result = WebUiServer.collectContractDocuments(files("""
                [{"affiliate_id":4711,"filename":"Beratervertrag_2026.pdf","file_title":""}]
                """));

        assertEquals(1, result.size());
        assertEquals("Beratervertrag_2026.pdf", result.get("4711"),
                "Ohne Titel muss der Dateiname als Bezeichnung einspringen");
    }

    @Test
    void grossKleinschreibungIstEgal() {
        assertTrue(WebUiServer.mentionsContract("VERTRAG.pdf"));
        assertTrue(WebUiServer.mentionsContract("vertrag"));
        assertTrue(WebUiServer.mentionsContract("VEMMiNA Beratervertrag"));
        assertTrue(WebUiServer.mentionsContract("Rahmenvertragsanlage"));
        assertFalse(WebUiServer.mentionsContract(""));
        assertFalse(WebUiServer.mentionsContract(null));
    }

    @Test
    void dateiOhneVertragsbezugZaehltNicht() throws Exception {
        Map<String, String> result = WebUiServer.collectContractDocuments(files("""
                [{"affiliate_id":15325690,"filename":"Bestellschein Schweiz.pdf","file_title":"Bestellschein Schweiz.pdf"},
                 {"affiliate_id":16085086,"filename":"Rabattcode 5%.png","file_title":"Rabattcode 5%.png"}]
                """));

        assertTrue(result.isEmpty(), "Marketingmaterial darf nicht als Vertrag durchgehen: " + result);
    }

    /**
     * affiliate_id kommt als JSON-Zahl, die ID der Tabellenzeile als String. Ohne Normalisierung
     * ueber asText() waere der Schluessel nie gleich und die Zuordnung schluege immer fehl.
     */
    @Test
    void zahlenIdWirdMitStringIdVerknuepft() throws Exception {
        Map<String, String> result = WebUiServer.collectContractDocuments(files("""
                [{"affiliate_id":21004180,"filename":"contract_qthnidfhol.pdf","file_title":"VEMMiNA Beratervertrag"}]
                """));

        assertTrue(result.containsKey("21004180"), "Schlüssel muss der String der Zahl sein: " + result.keySet());
        assertEquals("VEMMiNA Beratervertrag", result.get("21004180"));
    }

    @Test
    void dateiOhneAffiliateBezugWirdUebersprungen() throws Exception {
        Map<String, String> result = WebUiServer.collectContractDocuments(files("""
                [{"filename":"Mustervertrag.pdf","file_title":"Mustervertrag"},
                 {"affiliate_id":null,"filename":"Vertragsvorlage.pdf","file_title":"Vertragsvorlage"}]
                """));

        assertTrue(result.isEmpty(), "Ohne affiliate_id lässt sich niemandem etwas zuordnen: " + result);
    }

    @Test
    void beraterinOhneDateienHatKeinenVertrag() throws Exception {
        assertTrue(WebUiServer.collectContractDocuments(files("[]")).isEmpty());
        assertTrue(WebUiServer.collectContractDocuments(null).isEmpty());
    }

    @Test
    void mehrereVertraegeJeBeraterinLiefernDenErsten() throws Exception {
        Map<String, String> result = WebUiServer.collectContractDocuments(files("""
                [{"affiliate_id":99,"filename":"a.pdf","file_title":"Erster Vertrag"},
                 {"affiliate_id":99,"filename":"b.pdf","file_title":"Zweiter Vertrag"}]
                """));

        assertEquals(1, result.size());
        assertEquals("Erster Vertrag", result.get("99"));
    }

    /**
     * Ein Ausfall des Dateiabrufs darf nicht als "kein Vertrag" durchgehen: sonst zeigte die
     * Tabelle fuer alle ❌ und die Erinnerungsmail mahnte einen Vertrag an, den es geben koennte.
     */
    @Test
    void fehlgeschlagenerDateiabrufMeldetUnbekanntStattNein() {
        assertEquals("unbekannt", WebUiServer.contractStatusFor(false, ""));
        assertEquals("unbekannt", WebUiServer.contractStatusFor(false, "VEMMiNA Beratervertrag"));
        assertEquals("nein", WebUiServer.contractStatusFor(true, ""));
        assertEquals("nein", WebUiServer.contractStatusFor(true, "   "));
        assertEquals("ja", WebUiServer.contractStatusFor(true, "VEMMiNA Beratervertrag"));
    }
}
