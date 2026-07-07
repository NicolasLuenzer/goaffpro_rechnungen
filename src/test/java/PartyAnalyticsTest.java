import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PartyAnalyticsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokePartyAnalytics(String showcaseJson, String orderJson) throws Exception {
        JsonNode showcaseRoot = MAPPER.readTree(showcaseJson);
        JsonNode orderRoot = MAPPER.readTree(orderJson);
        Method method = WebUiServer.class.getDeclaredMethod(
                "buildPartyAnalyticsPayload",
                JsonNode.class, JsonNode.class, LocalDate.class, LocalDate.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(
                null,
                showcaseRoot,
                orderRoot,
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void partyAnalytics_matchesShowcaseOrdersBySubId() throws Exception {
        Map<String, Object> payload = invokePartyAnalytics("""
                {
                  "showcases": [
                    {
                      "_id": "party-1",
                      "sub_id": "sub-1",
                      "ref_code": "BT-1",
                      "affiliate_id": "a1",
                      "affiliate": {"id": "a1", "name": "Beraterin A"},
                      "hostName": "Host A",
                      "partyTitle": "Backtreff A",
                      "starts_at": "2026-01-01T00:00:00Z",
                      "ends_at": "2026-01-31T23:59:59Z",
                      "orders": {"num_orders": 2, "total": "300.00", "commission": "30.00"}
                    }
                  ]
                }
                """, """
                {
                  "orders": [
                    {
                      "id": "o1",
                      "number": "1001",
                      "total": "100.00",
                      "status": "paid",
                      "affiliate_id": "a1",
                      "created_at": "2026-01-10T10:00:00Z",
                      "customer_email": "kunde1@example.com",
                      "conversion_source": "party-link",
                      "sub_id": "sub-1",
                      "shipping_address": {"name": "Kunde Eins", "zip": "12345"},
                      "line_items": [{"name": "Produkt A", "sku": "sku-a", "quantity": "1", "price": "100.00"}]
                    },
                    {
                      "id": "o2",
                      "number": "1002",
                      "total": "200.00",
                      "status": "paid",
                      "affiliate_id": "a1",
                      "created_at": "2026-01-11T10:00:00Z",
                      "customer_email": "kunde2@example.com",
                      "conversion_source": "party-link",
                      "sub_id": "sub-1",
                      "shipping_address": {"name": "Kunde Zwei", "zip": "12345"},
                      "line_items": [{"name": "Produkt B", "sku": "sku-b", "quantity": "2", "price": "100.00"}]
                    },
                    {
                      "id": "o3",
                      "total": "999.00",
                      "created_at": "2026-01-12T10:00:00Z",
                      "conversion_source": "affiliate-link",
                      "sub_id": "sub-1"
                    }
                  ]
                }
                """);

        Map<String, Object> summary = (Map<String, Object>) payload.get("summary");
        List<Map<String, Object>> partyRows = (List<Map<String, Object>>) payload.get("partyRows");
        assertEquals(1, summary.get("partyCount"));
        assertEquals(2, summary.get("matchedOrderCount"));
        assertEquals(300.0, (Double) summary.get("matchedTotal"), 0.001);
        assertEquals("Backtreff A", partyRows.get(0).get("partyTitle"));
        assertEquals(2, partyRows.get(0).get("matchedOrderCount"));
        assertEquals(3, partyRows.get(0).get("productUnits"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void partyAnalytics_flagsRepeatedCustomersCartsOutsidePeriodAndAffiliateMismatch() throws Exception {
        Map<String, Object> payload = invokePartyAnalytics("""
                {
                  "showcases": [
                    {
                      "_id": "party-1",
                      "sub_id": "sub-1",
                      "affiliate_id": "a1",
                      "affiliate": {"id": "a1", "name": "Beraterin A"},
                      "partyTitle": "Backtreff A",
                      "starts_at": "2026-01-01T00:00:00Z",
                      "ends_at": "2026-01-05T23:59:59Z",
                      "orders": {"num_orders": 3, "total": "300.00"}
                    },
                    {
                      "_id": "party-2",
                      "sub_id": "sub-2",
                      "affiliate_id": "a1",
                      "affiliate": {"id": "a1", "name": "Beraterin A"},
                      "partyTitle": "Backtreff B",
                      "starts_at": "2026-01-06T00:00:00Z",
                      "ends_at": "2026-01-31T23:59:59Z",
                      "orders": {"num_orders": 1, "total": "50.00"}
                    }
                  ]
                }
                """, """
                {
                  "orders": [
                    {
                      "id": "o1",
                      "total": "100.00",
                      "affiliate_id": "a1",
                      "created_at": "2026-01-02T10:00:00Z",
                      "customer_email": "same@example.com",
                      "conversion_source": "party-link",
                      "sub_id": "sub-1",
                      "line_items": [{"name": "Produkt A", "sku": "sku-a", "quantity": "1", "price": "100.00"}]
                    },
                    {
                      "id": "o2",
                      "total": "100.00",
                      "affiliate_id": "a2",
                      "created_at": "2026-01-10T10:00:00Z",
                      "customer_email": "same@example.com",
                      "conversion_source": "party-link",
                      "sub_id": "sub-1",
                      "line_items": [{"name": "Produkt A", "sku": "sku-a", "quantity": "1", "price": "100.00"}]
                    },
                    {
                      "id": "o3",
                      "total": "50.00",
                      "affiliate_id": "a1",
                      "created_at": "2026-01-12T10:00:00Z",
                      "customer_email": "same@example.com",
                      "conversion_source": "party-link",
                      "sub_id": "sub-2",
                      "line_items": [{"name": "Produkt B", "sku": "sku-b", "quantity": "1", "price": "50.00"}]
                    }
                  ]
                }
                """);

        List<Map<String, Object>> partyRows = (List<Map<String, Object>>) payload.get("partyRows");
        Map<String, Object> flagged = partyRows.stream()
                .filter(p -> "party-1".equals(p.get("partyId")))
                .findFirst()
                .orElseThrow();

        assertEquals("Pr\u00fcfen", flagged.get("riskLevel"));
        assertTrue((Integer) flagged.get("riskScore") >= 80);
        List<String> reasons = (List<String>) flagged.get("riskReasons");
        assertTrue(reasons.stream().anyMatch(r -> r.contains("mehrfach in derselben Party")));
        assertTrue(reasons.stream().anyMatch(r -> r.contains("anderen Partys")));
        assertTrue(reasons.stream().anyMatch(r -> r.contains("identischer Warenkorb")));
        assertTrue(reasons.stream().anyMatch(r -> r.contains("ausserhalb des Party-Zeitraums")));
        assertTrue(reasons.stream().anyMatch(r -> r.contains("abweichender Affiliate-ID")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void partyAnalytics_handlesMissingFieldsWithoutFailing() throws Exception {
        Map<String, Object> payload = invokePartyAnalytics("""
                {
                  "showcases": [
                    {
                      "_id": "party-1",
                      "sub_id": "sub-1",
                      "partyTitle": "Backtreff ohne Details",
                      "created_at": "2026-01-03T00:00:00Z",
                      "orders": {"num_orders": 1, "total": "0"}
                    }
                  ]
                }
                """, """
                {
                  "orders": [
                    {
                      "id": "o1",
                      "created_at": "2026-01-04T10:00:00Z",
                      "conversion_source": "party-link",
                      "sub_id": "sub-1"
                    }
                  ]
                }
                """);

        Map<String, Object> summary = (Map<String, Object>) payload.get("summary");
        List<Map<String, Object>> partyRows = (List<Map<String, Object>>) payload.get("partyRows");
        assertEquals(1, summary.get("partyCount"));
        assertEquals(1, summary.get("matchedOrderCount"));
        assertEquals("Backtreff ohne Details", partyRows.get(0).get("partyTitle"));
        assertNotNull(partyRows.get(0).get("riskReasons"));
    }
}
