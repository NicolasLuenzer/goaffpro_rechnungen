import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 360°-Sicht: Verkäufe einer Beraterin mit Aufschlüsselung wie die Bestellansicht in GoAffPro.
 * Die Fixtures haben die Feldform echter GoAffPro-Antworten (Zahllauf 3591001, #16791, #8811),
 * die Kundendaten sind erfunden.
 */
class BeraterinnenVerkaeufeTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double CENT = 0.000001;

    private static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        try {
            return m.invoke(null, args);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sales(JsonNode advisor, List<JsonNode> direct, List<JsonNode> rewards,
                                             Map<String, JsonNode> teamOrders, Map<String, JsonNode> partners,
                                             Map<String, Set<String>> paid) throws Exception {
        return (Map<String, Object>) invoke("buildAdvisorSalesPayload",
                new Class<?>[]{JsonNode.class, List.class, List.class, Map.class, Map.class, Map.class},
                advisor, direct, rewards, teamOrders, partners, paid);
    }

    private static Map<String, Object> direct(JsonNode... orders) throws Exception {
        return sales(advisor(), List.of(orders), List.of(), Map.of(), Map.of(), Map.of());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("saleRows");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lines(Map<String, Object> row) {
        return (List<Map<String, Object>>) row.get("lines");
    }

    private static JsonNode json(String text) throws Exception {
        return MAPPER.readTree(text);
    }

    private static JsonNode advisor() throws Exception {
        return json("{\"id\":\"15366929\",\"name\":\"Beraterin Muster\",\"email\":\"Beraterin@Example.com\"}");
    }

    /** Wie #16791, auf drei Positionen gekürzt: 20 %, 7 % und eine Position ohne Provision. */
    private static JsonNode order16791() throws Exception {
        return json("""
                {"id":92935314,"number":"#16791","total":"31.68","subtotal":"27.08","status":"approved",
                 "affiliate_id":15366929,"created_at":"2026-10-29T10:24:42.000Z","commission":"4.832300",
                 "coupons":"bttheo3","conversion_source":"party-link","sub_id":"theo3","is_new_customer":0,
                 "customer_email":"kundin@example.com",
                 "customer":{"name":"Kundin Muster","email":"kundin@example.com","phone":"+49 171 5550000",
                             "address_1":"Musterweg 1","zip":"12345","city":"Musterstadt","is_new_customer":false},
                 "line_items":[
                   {"name":"Devonport - Blue","sku":"9707","quantity":1,"refund_quantity":0,"price":27.45,"total_price":27.45,
                    "total_discount":0.57,"total_tax":4.29,"total":22.59,"cv":22.59,"commission":4.518,
                    "commission_value":"20","commission_type":"percentage"},
                   {"name":"Bernsteingold®","sku":"6509","quantity":1,"refund_quantity":0,"price":4.9,"total_price":4.9,
                    "total_discount":0.1,"total_tax":0.31,"total":4.49,"cv":4.49,"commission":0.3143,
                    "commission_value":"7","commission_type":"percentage"},
                   {"name":"Tüten für Bestellungen","sku":"140","quantity":13,"refund_quantity":0,"price":0,"total_price":0,
                    "total_discount":0,"total_tax":0,"total":0,"cv":0,"commission":0,
                    "commission_value":"0","commission_type":"percentage"}]}
                """);
    }

    private static JsonNode simpleOrder(String id, String number, String status, String created, int qty, int refundQty,
                                        String commission) throws Exception {
        return json("""
                {"id":%s,"number":"%s","total":"11.90","subtotal":"10.00","status":"%s","created_at":"%s","commission":"%s",
                 "line_items":[{"name":"Artikel %s","sku":"S%s","quantity":%d,"refund_quantity":%d,"total_price":11.9,
                   "total_discount":0,"total_tax":1.9,"total":10.0,"commission":%s,"commission_value":"20","commission_type":"percentage"}]}
                """.formatted(id, number, status, created, commission, id, id, qty, refundQty, commission));
    }

    // ── Aufschlüsselung je Verkauf ──

    @Test
    void aufschluesselungWieDieBestellansichtInGoAffPro() throws Exception {
        Map<String, Object> row = rows(direct(order16791())).get(0);

        assertEquals("direct", row.get("kind"));
        assertEquals("#16791", row.get("number"));
        assertEquals("freigegeben", row.get("statusLabel"));
        assertEquals(true, row.get("counted"));
        assertEquals(31.68, (double) row.get("total"), CENT, "Bestellwert");
        assertEquals(27.08, (double) row.get("subtotal"), CENT, "BMG");
        assertEquals(4.60, (double) row.get("tax"), CENT, "MwSt = Summe der Positionen");
        assertEquals(0.67, (double) row.get("discount"), CENT, "Rabatt = Summe der Positionen");
        assertEquals(0.0, (double) row.get("shipping"), CENT, "kein Versand");
        assertEquals(0.0, (double) row.get("adjustment"), CENT, "Provision = Summe der Positionen");
        assertEquals(4.8323, (double) row.get("commission"), CENT);
        assertEquals(List.of("bttheo3"), row.get("coupons"));
        assertEquals("party-link", row.get("conversionSource"));

        List<Map<String, Object>> lines = lines(row);
        assertEquals(3, lines.size());
        assertEquals("Devonport - Blue", lines.get(0).get("name"));
        assertEquals("20 %", lines.get(0).get("rate"));
        assertEquals(4.518, (double) lines.get(0).get("commission"), CENT);
        assertEquals(22.59, (double) lines.get(0).get("net"), CENT);
        assertEquals(0.57, (double) lines.get(0).get("discount"), CENT);
        assertEquals("7 %", lines.get(1).get("rate"));
        assertEquals(13, lines.get(2).get("quantity"));
    }

    @Test
    void kundinNurMitNameUndEmail() throws Exception {
        Map<String, Object> row = rows(direct(order16791())).get(0);
        assertEquals("Kundin Muster", row.get("customerName"));
        assertEquals("kundin@example.com", row.get("customerEmail"));
        assertEquals(false, row.get("ownOrder"));
        String json = MAPPER.writeValueAsString(row);
        assertFalse(json.contains("5550000"), "Telefon gelangt nicht in die Auswertung");
        assertFalse(json.contains("Musterweg"), "Adresse gelangt nicht in die Auswertung");
    }

    @Test
    void eigenbestellungOhneGrossUndKleinschreibung() throws Exception {
        JsonNode order = json("""
                {"id":1,"number":"#17380","total":"0","subtotal":"0","status":"new","created_at":"2026-11-20T15:23:19.000Z",
                 "commission":"0","customer":{"name":"Beraterin Muster","email":"beraterin@example.com"}}
                """);
        Map<String, Object> row = rows(direct(order)).get(0);
        assertEquals(true, row.get("ownOrder"), "Kundin ist die Beraterin selbst");
        assertEquals("beraterin@example.com", row.get("customerEmail"), "customer.email, wenn customer_email fehlt");
        assertEquals("offen", row.get("statusLabel"));
    }

    @Test
    void versandUndAnpassungWerdenAbgeleitet() throws Exception {
        JsonNode order = json("""
                {"id":5,"number":"#5","total":"54.90","subtotal":"42.02","status":"approved","created_at":"2026-10-05T10:00:00.000Z",
                 "commission":"10.00",
                 "line_items":[{"name":"Hardliner","quantity":2,"total_price":50.0,"total_discount":0,"total_tax":7.98,
                   "total":42.02,"commission":8.404,"commission_value":"20","commission_type":"percentage"}]}
                """);
        Map<String, Object> row = rows(direct(order)).get(0);
        assertEquals(4.90, (double) row.get("shipping"), CENT, "Bestellwert minus Positionen");
        assertEquals(1.596, (double) row.get("adjustment"), CENT, "Provision minus Summe der Positionen");
    }

    @Test
    void ohnePositionenIstDerVersandNichtBestimmbar() throws Exception {
        JsonNode order = json("{\"id\":6,\"number\":\"#6\",\"total\":\"20.00\",\"status\":\"approved\",\"created_at\":\"2026-10-05T10:00:00.000Z\"}");
        assertNull(rows(direct(order)).get(0).get("shipping"));
    }

    // ── Team ──

    @Test
    void teamVerkaufZaehltDieTeamProvisionDerBeraterin() throws Exception {
        JsonNode reward = json("""
                {"id":8895421,"amount":"7.923960","status":"approved","created_at":"2025-09-30T15:27:14.000Z",
                 "type":"sale_commission","order_id":"64001211",
                 "order":{"number":"#8811","status":"approved","id":64001211,"subtotal":"313.20","total":"313.20"}}
                """);
        JsonNode teamOrder = json("""
                {"id":64001211,"number":"#8811","total":"313.20","subtotal":"313.20","status":"approved",
                 "affiliate_id":15366535,"created_at":"2025-09-30T15:27:10.000Z","commission":"68.904000",
                 "customer":{"name":"Kundin der Partnerin","email":"kundin2@example.com"},
                 "line_items":[{"name":"Maxi-Schnitte","quantity":3,"total_price":124.3,"total_discount":0,"total_tax":0,
                   "total":124.3,"commission":24.86,"commission_value":"20","commission_type":"percentage"}]}
                """);
        JsonNode partner = json("{\"id\":\"15366535\",\"name\":\"Partnerin Muster\",\"email\":\"partnerin@example.com\"}");

        Map<String, Object> result = sales(advisor(), List.of(), List.of(reward), Map.of("64001211", teamOrder),
                Map.of("15366535", partner), Map.of("rewards:8895421", Set.of("2702284")));
        Map<String, Object> row = rows(result).get(0);

        assertEquals("team", row.get("kind"));
        assertEquals("Partnerin Muster", row.get("partnerName"));
        assertEquals("15366535", row.get("partnerId"));
        assertEquals(7.92396, (double) row.get("commission"), CENT, "Ihre Provision ist der Reward, nicht die der Partnerin");
        assertEquals(2.53, (double) row.get("teamRate"), 0.001);
        assertEquals(List.of("2702284"), row.get("payoutIds"));
        assertFalse(lines(row).get(0).containsKey("commission"), "Keine Positionsprovision der Partnerin");
        assertEquals(1, result.get("teamSalesCount"));
        assertEquals(0, result.get("directSalesCount"));
        assertEquals(7.92396, (double) result.get("teamCommission"), CENT);
        assertEquals(0.0, (double) result.get("teamCommissionUnpaid"), CENT);
        assertEquals(3, result.get("soldUnits"), "Team-Artikel zählen mit (direkt + indirekt)");
    }

    @Test
    void teamVerkaufOhneGeladeneBestellungNutztDieKurzfassungAusDemReward() throws Exception {
        JsonNode reward = json("""
                {"id":1,"amount":"3.15","status":"approved","created_at":"2025-06-23T09:55:36.000Z","order_id":"58144931",
                 "order":{"number":"#7074","id":58144931,"subtotal":"124.60","total":"114.63","created":"2025-06-23T09:55:07.000Z"}}
                """);
        Map<String, Object> row = rows(sales(advisor(), List.of(), List.of(reward), Map.of(), Map.of(), Map.of())).get(0);
        assertEquals("#7074", row.get("number"));
        assertEquals(124.60, (double) row.get("subtotal"), CENT);
        assertEquals("2025-06-23T09:55:07.000Z", row.get("createdAtIso"));
        assertTrue(lines(row).isEmpty());
    }

    // ── Kennzahlen ──

    @Test
    void kennzahlenZaehlenVerkaeufeNichtBuchungen() throws Exception {
        JsonNode paid = simpleOrder("1", "#1", "approved", "2026-10-02T10:00:00.000Z", 2, 1, "2.00");
        JsonNode open = simpleOrder("2", "#2", "new", "2026-10-03T10:00:00.000Z", 3, 0, "3.00");
        JsonNode rejected = simpleOrder("3", "#3", "rejected", "2026-10-04T10:00:00.000Z", 5, 0, "5.00");
        Map<String, Object> result = sales(advisor(), List.of(paid, open, rejected), List.of(), Map.of(), Map.of(),
                Map.of("orders:1", Set.of("3591001")));

        assertEquals(2, result.get("directSalesCount"), "Abgelehnte zählen nicht");
        assertEquals(4, result.get("soldUnits"), "(2 - 1 Retoure) + 3");
        assertEquals(5.0, (double) result.get("directCommission"), CENT);
        assertEquals(3.0, (double) result.get("directCommissionUnpaid"), CENT, "#2 ist noch nicht ausgezahlt");

        List<Map<String, Object>> rows = rows(result);
        assertEquals(List.of("#3", "#2", "#1"), rows.stream().map(r -> r.get("number")).toList(), "Neueste zuerst");
        assertEquals(false, rows.get(0).get("counted"));
        assertEquals("abgelehnt", rows.get(0).get("statusLabel"));
        assertEquals(List.of("3591001"), rows.get(2).get("payoutIds"));
        assertEquals(List.of(), rows.get(1).get("payoutIds"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> products = (List<Map<String, Object>>) result.get("productRows");
        assertEquals(List.of("Artikel 2", "Artikel 1"), products.stream().map(p -> p.get("productName")).toList(),
                "Nach Menge; Artikel abgelehnter Verkäufe fehlen");
        assertEquals(3, products.get(0).get("quantity"));
        assertEquals(10.0, (double) products.get(0).get("salesValue"), CENT, "Umsatz netto");
    }

    // ── Hilfsfunktionen ──

    @Test
    @SuppressWarnings("unchecked")
    void auszahlungsstandAusIsPaid() throws Exception {
        List<JsonNode> txs = new ArrayList<>();
        txs.add(json("{\"entity_type\":\"orders\",\"entity_id\":91682159,\"is_paid\":3591001}"));
        txs.add(json("{\"entity_type\":\"orders\",\"entity_id\":91682159,\"is_paid\":0}"));
        txs.add(json("{\"entity_type\":\"orders\",\"entity_id\":94319831,\"is_paid\":0}"));
        txs.add(json("{\"entity_type\":\"rewards\",\"entity_id\":8895421,\"is_paid\":2702284}"));
        Map<String, Set<String>> map = (Map<String, Set<String>>) invoke("payoutsByEntity", new Class<?>[]{List.class}, txs);
        assertEquals(Set.of("3591001"), map.get("orders:91682159"));
        assertNull(map.get("orders:94319831"), "is_paid 0 heißt noch nicht ausgezahlt");
        assertEquals(Set.of("2702284"), map.get("rewards:8895421"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void bestellungenInPaketenUeberGoAffProIds() throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= 120; i++) ids.add(String.valueOf(90000000 + i));
        List<String> urls = (List<String>) invoke("ordersByIdUrls", new Class<?>[]{List.class}, ids);
        assertEquals(3, urls.size(), "Höchstens 50 IDs je Abfrage");
        assertTrue(urls.get(0).startsWith("https://api.goaffpro.com/v1/admin/orders?id=90000001,"), urls.get(0));
        assertTrue(urls.get(0).contains("90000050&limit=50"), urls.get(0));
        assertTrue(urls.get(2).contains("?id=90000101,"), urls.get(2));
        assertTrue(urls.get(0).contains("line_items"), "Positionen werden mitgeladen");
    }

    @Test
    void zeitraumInBerlinerTagesgrenzen() throws Exception {
        String range = (String) invoke("goaffproCreatedRange", new Class<?>[]{LocalDate.class, LocalDate.class},
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-22"));
        // 01.10. 00:00 Sommerzeit = 30.09. 22:00 UTC; 23.11. 00:00 Winterzeit = 22.11. 23:00 UTC
        assertEquals("&created_at_min=2026-09-30T22:00:00.000Z&created_at_max=2026-11-22T22:59:59.999Z", range);
    }

    @Test
    void provisionssatz() throws Exception {
        Class<?>[] types = {String.class, String.class};
        assertEquals("20 %", invoke("commissionRateLabel", types, "20", "percentage"));
        assertEquals("7,5 %", invoke("commissionRateLabel", types, "7.5", "percentage"));
        assertEquals("fest", invoke("commissionRateLabel", types, "5", "fixed"));
        assertEquals("", invoke("commissionRateLabel", types, "", "percentage"));
    }
}
