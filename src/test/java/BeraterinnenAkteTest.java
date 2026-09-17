import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Beraterinnen-Akte: alle Sync-Daten einer einzelnen Beraterin.
 *
 * Die Zuordnung läuft über json_extract auf raw_json - je Entitätstyp über einen anderen Pfad.
 */
class BeraterinnenAkteTest {

    private static Properties configFor(Path tempDir) {
        Properties config = new Properties();
        config.setProperty("goaffproSyncDataPath", tempDir.toString());
        return config;
    }

    /** Legt das Schema an (status() kehrt ohne Datei früh zurück, die leere Datei genügt SQLite). */
    private static GoAffProSyncService openDatabase(Path tempDir) throws Exception {
        Files.createFile(tempDir.resolve("goaffpro_sync.sqlite"));
        GoAffProSyncService service = new GoAffProSyncService();
        service.status(configFor(tempDir));
        return service;
    }

    private static void insert(Path tempDir, String entityType, String externalId, String rawJson) throws Exception {
        insert(tempDir, entityType, externalId, rawJson, Instant.now().toString());
    }

    private static void insert(Path tempDir, String entityType, String externalId, String rawJson, String createdAt) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("goaffpro_sync.sqlite"));
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO sync_entities(entity_type, external_id, api_path, content_hash, raw_json,"
                             + " remote_created_at, first_seen_at, last_seen_at, last_changed_at, state)"
                             + " VALUES(?,?,'/test','h',?,?,?,?,?, 'active')")) {
            String now = Instant.now().toString();
            ps.setString(1, entityType);
            ps.setString(2, externalId);
            ps.setString(3, rawJson);
            ps.setString(4, createdAt);
            ps.setString(5, now);
            ps.setString(6, now);
            ps.setString(7, now);
            ps.executeUpdate();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> akte, String key) {
        return (Map<String, Object>) ((Map<String, Object>) akte.get("sections")).get(key);
    }

    private static int total(Map<String, Object> akte, String key) {
        return ((Number) section(akte, key).get("total")).intValue();
    }

    @SuppressWarnings("unchecked")
    private static List<JsonNode> rows(Map<String, Object> akte, String key) {
        return (List<JsonNode>) section(akte, key).get("rows");
    }

    @Test
    void akteFindetAlleTypenEinerBeraterin(@TempDir Path tempDir) throws Exception {
        GoAffProSyncService service = openDatabase(tempDir);
        insert(tempDir, "affiliates", "4711", "{\"name\":\"Erika Muster\",\"status\":\"approved\",\"ref_code\":\"EM01\"}");
        insert(tempDir, "payments", "p1", "{\"id\":\"p1\",\"affiliate_id\":4711,\"amount\":\"14.01\"}");
        insert(tempDir, "orders", "o1", "{\"id\":\"o1\",\"affiliate_id\":4711,\"total\":\"219.46\"}");
        insert(tempDir, "orders", "o2", "{\"id\":\"o2\",\"affiliate_id\":4711,\"total\":\"10.00\"}");
        insert(tempDir, "rewards", "r1", "{\"id\":\"r1\",\"affiliate_id\":4711,\"amount\":\"5.306422\"}");
        // Fremde Beraterin - darf nicht mitgezählt werden.
        insert(tempDir, "orders", "o9", "{\"id\":\"o9\",\"affiliate_id\":9999,\"total\":\"99.00\"}");

        Map<String, Object> akte = service.akte(configFor(tempDir), "4711", 100, 0);

        assertEquals("Erika Muster", ((JsonNode) akte.get("affiliate")).path("name").asText());
        assertEquals(1, total(akte, "payments"));
        assertEquals(2, total(akte, "orders"), "Die Bestellung einer anderen Beraterin darf nicht mitzählen");
        assertEquals(1, total(akte, "rewards"));
        assertEquals(0, total(akte, "showcases"));
    }

    /**
     * connections trägt kein Top-Level-affiliate_id, sondern nur ein verschachteltes affiliate.id.
     * Ohne den Sonderpfad fiele der ganze Typ lautlos aus der Akte.
     */
    @Test
    void verschachtelterAffiliateBezugWirdGefunden(@TempDir Path tempDir) throws Exception {
        GoAffProSyncService service = openDatabase(tempDir);
        insert(tempDir, "affiliates", "4711", "{\"name\":\"Erika Muster\"}");
        insert(tempDir, "connections", "c1", "{\"id\":\"c1\",\"affiliate\":{\"id\":4711},\"customer\":{\"name\":\"Kundin\"}}");

        Map<String, Object> akte = service.akte(configFor(tempDir), "4711", 100, 0);
        assertEquals(1, total(akte, "connections"),
                "connections hängt an $.affiliate.id, nicht an $.affiliate_id");
        assertEquals("Kundin", rows(akte, "connections").get(0).path("customer").path("name").asText());
    }

    @Test
    void beraterinOhneDatenLiefertLeereAberGueltigeAkte(@TempDir Path tempDir) throws Exception {
        GoAffProSyncService service = openDatabase(tempDir);
        insert(tempDir, "affiliates", "4711", "{\"name\":\"Erika Muster\"}");

        Map<String, Object> akte = service.akte(configFor(tempDir), "4711", 100, 0);
        assertEquals("Erika Muster", ((JsonNode) akte.get("affiliate")).path("name").asText());
        for (String typ : new String[]{"payments", "orders", "rewards", "transactions", "showcases", "connections", "traffic", "coupons"}) {
            assertEquals(0, total(akte, typ), typ + " muss leer, aber vorhanden sein");
            assertTrue(rows(akte, typ).isEmpty());
        }
    }

    /**
     * Im echten Bestand hängen Transaktionen und Auszahlungen an fünf gelöschten Beraterinnen.
     * Diese Daten dürfen weder die Liste sprengen noch kommentarlos verschwinden.
     */
    @Test
    @SuppressWarnings("unchecked")
    void verwaisteDatenOhneBeraterinWerdenAusgewiesen(@TempDir Path tempDir) throws Exception {
        GoAffProSyncService service = openDatabase(tempDir);
        insert(tempDir, "affiliates", "4711", "{\"name\":\"Erika Muster\"}");
        insert(tempDir, "transactions", "t1", "{\"tx_id\":\"t1\",\"affiliate_id\":99999,\"amount\":\"1.00\"}");

        Map<String, Object> liste = service.akteList(configFor(tempDir));
        List<Map<String, Object>> rows = (List<Map<String, Object>>) liste.get("rows");
        assertEquals(2, rows.size(), "Die gelöschte Beraterin muss als eigener Eintrag auftauchen");

        Map<String, Object> verwaist = rows.stream()
                .filter(r -> Boolean.TRUE.equals(r.get("orphan"))).findFirst().orElseThrow();
        assertEquals("99999", verwaist.get("id"));
        assertEquals(1, ((Map<String, Integer>) verwaist.get("counts")).get("transactions"));

        // Die Akte selbst muss trotzdem lesbar sein.
        Map<String, Object> akte = service.akte(configFor(tempDir), "99999", 100, 0);
        assertEquals(1, total(akte, "transactions"));
        assertTrue(((JsonNode) akte.get("affiliate")).isEmpty(), "Es gibt keine Stammdaten mehr");
    }

    @Test
    void grosseKontenWerdenSeitenweiseGeliefert(@TempDir Path tempDir) throws Exception {
        GoAffProSyncService service = openDatabase(tempDir);
        insert(tempDir, "affiliates", "4711", "{\"name\":\"Vielbucherin\"}");
        for (int i = 0; i < 250; i++) {
            insert(tempDir, "transactions", "t" + i,
                    "{\"tx_id\":\"t" + i + "\",\"affiliate_id\":4711,\"amount\":\"1.000000\"}",
                    String.format("2026-01-%02dT10:00:00.000Z", (i % 28) + 1));
        }

        Map<String, Object> seite1 = service.akte(configFor(tempDir), "4711", 100, 0);
        assertEquals(250, total(seite1, "transactions"), "Die Gesamtzahl muss vollständig sein");
        assertEquals(100, rows(seite1, "transactions").size());
        assertEquals(Boolean.TRUE, section(seite1, "transactions").get("truncated"));

        Map<String, Object> seite3 = service.akte(configFor(tempDir), "4711", 100, 200);
        assertEquals(50, rows(seite3, "transactions").size(), "Die letzte Seite ist angebrochen");
        assertEquals(Boolean.FALSE, section(seite3, "transactions").get("truncated"));
    }

    @Test
    void neuesteDatensaetzeStehenOben(@TempDir Path tempDir) throws Exception {
        GoAffProSyncService service = openDatabase(tempDir);
        insert(tempDir, "affiliates", "4711", "{\"name\":\"Erika Muster\"}");
        insert(tempDir, "payments", "alt", "{\"id\":\"alt\",\"affiliate_id\":4711}", "2025-01-01T10:00:00.000Z");
        insert(tempDir, "payments", "neu", "{\"id\":\"neu\",\"affiliate_id\":4711}", "2026-06-01T10:00:00.000Z");

        List<JsonNode> rows = rows(service.akte(configFor(tempDir), "4711", 100, 0), "payments");
        assertEquals("neu", rows.get(0).path("id").asText(), "Neueste zuerst");
        assertEquals("alt", rows.get(1).path("id").asText());
    }

    /**
     * Up- und Downline kommen aus mlm_tree ($.parent). affiliates.parent_id ist im echten Bestand
     * redundant (76 von 76 identisch) und wird bewusst nicht als zweite Quelle benutzt.
     */
    @Test
    @SuppressWarnings("unchecked")
    void uplineUndDownlineKommenAusDemMlmBaum(@TempDir Path tempDir) throws Exception {
        GoAffProSyncService service = openDatabase(tempDir);
        insert(tempDir, "affiliates", "100", "{\"name\":\"Sponsorin\"}");
        insert(tempDir, "affiliates", "200", "{\"name\":\"Mittlere\"}");
        insert(tempDir, "affiliates", "300", "{\"name\":\"Unterste\"}");
        insert(tempDir, "mlm_tree", "200", "{\"id\":200,\"parent\":100}");
        insert(tempDir, "mlm_tree", "300", "{\"id\":300,\"parent\":200}");

        Map<String, Object> mlm = (Map<String, Object>) service.akte(configFor(tempDir), "200", 100, 0).get("mlm");
        assertEquals("100", mlm.get("uplineId"));
        assertEquals("Sponsorin", mlm.get("uplineName"));

        List<Map<String, String>> downline = (List<Map<String, String>>) mlm.get("downline");
        assertEquals(1, downline.size());
        assertEquals("Unterste", downline.get(0).get("name"));

        Map<String, Object> oben = (Map<String, Object>) service.akte(configFor(tempDir), "100", 100, 0).get("mlm");
        assertEquals("", oben.get("uplineId"), "Die oberste Beraterin hat keine Upline");
        assertEquals(1, ((List<?>) oben.get("downline")).size());
    }

    @Test
    void unbekannteBeraterinLiefertEineLeereAkteStattEinesFehlers(@TempDir Path tempDir) throws Exception {
        GoAffProSyncService service = openDatabase(tempDir);
        Map<String, Object> akte = service.akte(configFor(tempDir), "gibtesnicht", 100, 0);
        assertTrue(((JsonNode) akte.get("affiliate")).isEmpty());
        assertEquals(0, total(akte, "orders"));
    }
}
