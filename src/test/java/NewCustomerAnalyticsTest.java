import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NewCustomerAnalyticsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeNewCustomerAnalytics(String ordersJson, String affiliatesJson,
                                                                  Set<String> limitedWeeks) throws Exception {
        JsonNode orderRoot = MAPPER.readTree(ordersJson);
        List<JsonNode> orders = new ArrayList<>();
        orderRoot.path("orders").forEach(orders::add);

        JsonNode affiliateRoot = MAPPER.readTree(affiliatesJson);
        Map<String, JsonNode> affiliates = new LinkedHashMap<>();
        affiliateRoot.path("affiliates").forEach(a -> affiliates.put(a.path("id").asText(), a));

        Method method = WebUiServer.class.getDeclaredMethod(
                "buildNewCustomerAnalyticsPayload",
                List.class, Map.class, LocalDate.class, LocalDate.class, Set.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(
                null,
                orders,
                affiliates,
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"),
                limitedWeeks);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeLeaderNewCustomerAnalytics(List<JsonNode> orders,
                                                                        String affiliatesJson,
                                                                        Map<String, List<String>> childrenByParent,
                                                                        Set<String> limitedWeeks) throws Exception {
        JsonNode affiliateRoot = MAPPER.readTree(affiliatesJson);
        Map<String, JsonNode> affiliates = new LinkedHashMap<>();
        affiliateRoot.path("affiliates").forEach(a -> affiliates.put(a.path("id").asText(), a));

        Method method = WebUiServer.class.getDeclaredMethod(
                "buildLeaderNewCustomerAnalyticsPayload",
                List.class, Map.class, Map.class, LocalDate.class, LocalDate.class, Set.class, LocalDate.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(
                null,
                orders,
                affiliates,
                childrenByParent,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-06-15"),
                limitedWeeks,
                LocalDate.parse("2026-06-15"));
    }

    private static List<JsonNode> newCustomerOrders(String prefix, String affiliateId, YearMonth month, int count) {
        List<JsonNode> orders = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            var order = MAPPER.createObjectNode();
            int day = 1 + ((i - 1) % Math.min(15, month.lengthOfMonth()));
            order.put("id", prefix + "-" + i);
            order.put("affiliate_id", affiliateId);
            order.put("created_at", month.atDay(day) + "T10:00:00Z");
            order.put("total", "100.00");
            order.put("is_new_customer", true);
            orders.add(order);
        }
        return orders;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findLeader(Map<String, Object> payload, String leaderId) {
        return ((List<Map<String, Object>>) payload.get("leaderRows")).stream()
                .filter(row -> leaderId.equals(row.get("leaderId")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeLeaderWeeklyMailPayload(List<JsonNode> orders,
                                                                     String affiliatesJson,
                                                                     Map<String, List<String>> childrenByParent,
                                                                     Properties config,
                                                                     LocalDate referenceDate,
                                                                     boolean productionRequested,
                                                                     Set<String> limitedWeeks) throws Exception {
        JsonNode affiliateRoot = MAPPER.readTree(affiliatesJson);
        Map<String, JsonNode> affiliates = new LinkedHashMap<>();
        affiliateRoot.path("affiliates").forEach(a -> affiliates.put(a.path("id").asText(), a));

        Method method = WebUiServer.class.getDeclaredMethod(
                "buildLeaderWeeklyMailPayload",
                List.class, Map.class, Map.class, Properties.class, LocalDate.class, boolean.class, Set.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(
                null,
                orders,
                affiliates,
                childrenByParent,
                config,
                referenceDate,
                productionRequested,
                limitedWeeks);
    }

    @SuppressWarnings("unchecked")
    @Test
    void newCustomerAnalytics_recognizesTopLevelAndNestedNewCustomerFlags() throws Exception {
        Map<String, Object> payload = invokeNewCustomerAnalytics("""
                {
                  "orders": [
                    {
                      "id": "o1",
                      "total": "100.00",
                      "affiliate_id": "a1",
                      "created_at": "2026-01-01T10:00:00Z",
                      "is_new_customer": 1,
                      "customer": {"is_new_customer": false}
                    },
                    {
                      "id": "o2",
                      "total": "50.00",
                      "affiliate_id": "a1",
                      "created_at": "2026-01-02T10:00:00Z",
                      "customer": {"is_new_customer": true}
                    },
                    {
                      "id": "o3",
                      "total": "25.00",
                      "affiliate_id": "a1",
                      "created_at": "2026-01-03T10:00:00Z",
                      "is_new_customer": 0,
                      "customer": {"is_new_customer": true}
                    }
                  ]
                }
                """, """
                {
                  "affiliates": [
                    {"id": "a1", "name": "Beraterin A"}
                  ]
                }
                """, Set.of());

        Map<String, Object> summary = (Map<String, Object>) payload.get("summary");
        List<Map<String, Object>> advisorWeekRows = (List<Map<String, Object>>) payload.get("advisorWeekRows");

        assertEquals(3, summary.get("totalOrders"));
        assertEquals(2, summary.get("newCustomerOrders"));
        assertEquals(1, summary.get("returningCustomerOrders"));
        assertEquals(175.0, (Double) summary.get("totalRevenue"), 0.001);
        assertEquals(150.0, (Double) summary.get("newCustomerRevenue"), 0.001);
        assertEquals(2.0 / 3.0, (Double) summary.get("newCustomerRate"), 0.001);
        assertEquals("2026-KW01", advisorWeekRows.get(0).get("weekKey"));
        assertEquals("Beraterin A", advisorWeekRows.get(0).get("advisorName"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void newCustomerAnalytics_aggregatesByIsoWeekAndAffiliate() throws Exception {
        Map<String, Object> payload = invokeNewCustomerAnalytics("""
                {
                  "orders": [
                    {"id": "o1", "total": "100.00", "affiliate_id": "a1", "created_at": "2026-01-04T10:00:00Z", "is_new_customer": true},
                    {"id": "o2", "total": "200.00", "affiliate_id": "a1", "created_at": "2026-01-05T10:00:00Z", "is_new_customer": false},
                    {"id": "o3", "total": "300.00", "affiliate_id": "a2", "created_at": "2026-01-05T11:00:00Z", "is_new_customer": true}
                  ]
                }
                """, """
                {
                  "affiliates": [
                    {"id": "a1", "name": "Beraterin A"},
                    {"id": "a2", "name": "Beraterin B"}
                  ]
                }
                """, Set.of());

        List<Map<String, Object>> weekRows = (List<Map<String, Object>>) payload.get("weekRows");
        List<Map<String, Object>> advisorWeekRows = (List<Map<String, Object>>) payload.get("advisorWeekRows");

        assertEquals(2, weekRows.size());
        assertEquals("2026-KW01", weekRows.get(0).get("weekKey"));
        assertEquals(1, weekRows.get(0).get("newCustomerOrders"));
        assertEquals("2026-KW02", weekRows.get(1).get("weekKey"));
        assertEquals(2, weekRows.get(1).get("totalOrders"));
        assertEquals(1, weekRows.get(1).get("newCustomerOrders"));
        assertEquals(3, advisorWeekRows.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    void newCustomerAnalytics_handlesMissingFieldsAndLimitWarnings() throws Exception {
        Map<String, Object> payload = invokeNewCustomerAnalytics("""
                {
                  "orders": [
                    {"id": "o1", "created_at": "2026-01-06T10:00:00Z", "is_new_customer": "true"},
                    {"id": "o2", "affiliate_id": "a1", "is_new_customer": true}
                  ]
                }
                """, """
                {
                  "affiliates": [
                    {"id": "a1", "name": "Beraterin A"}
                  ]
                }
                """, Set.of("2026-KW02"));

        Map<String, Object> summary = (Map<String, Object>) payload.get("summary");
        List<String> warnings = (List<String>) payload.get("warnings");
        List<Map<String, Object>> advisorWeekRows = (List<Map<String, Object>>) payload.get("advisorWeekRows");

        assertEquals(1, summary.get("totalOrders"));
        assertEquals(1, summary.get("newCustomerOrders"));
        assertEquals(1, summary.get("advisorCount"));
        assertEquals("Unbekannt", advisorWeekRows.get(0).get("advisorName"));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("Order-Limit")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("ohne verwertbares Datum")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void leaderNewCustomerAnalytics_rollsUpSelfAndAllApprovedDownline() throws Exception {
        List<JsonNode> orders = new ArrayList<>();
        orders.addAll(newCustomerOrders("leader-may", "leader", YearMonth.parse("2026-05"), 5));
        orders.addAll(newCustomerOrders("direct-may", "direct", YearMonth.parse("2026-05"), 20));
        orders.addAll(newCustomerOrders("deep-may", "deep", YearMonth.parse("2026-05"), 15));
        orders.addAll(newCustomerOrders("blocked-may", "blocked", YearMonth.parse("2026-05"), 10));

        Map<String, List<String>> children = new LinkedHashMap<>();
        children.put("leader", List.of("direct", "blocked"));
        children.put("direct", List.of("deep"));

        Map<String, Object> payload = invokeLeaderNewCustomerAnalytics(orders, """
                {
                  "affiliates": [
                    {"id": "leader", "name": "Leader", "status": "approved"},
                    {"id": "direct", "name": "Direct", "status": "approved"},
                    {"id": "deep", "name": "Deep", "status": "approved"},
                    {"id": "blocked", "name": "Blocked", "status": "blocked"}
                  ]
                }
                """, children, Set.of());

        Map<String, Object> leader = findLeader(payload, "leader");
        List<Map<String, Object>> contributionRows = (List<Map<String, Object>>) payload.get("advisorContributionRows");

        assertEquals("OK", leader.get("status"));
        assertEquals(3, leader.get("teamSize"));
        assertEquals(40, leader.get("lastClosedMonthNewCustomers"));
        assertTrue(contributionRows.stream().anyMatch(row -> "leader".equals(row.get("advisorId"))));
        assertTrue(contributionRows.stream().anyMatch(row -> "direct".equals(row.get("advisorId"))));
        assertTrue(contributionRows.stream().anyMatch(row -> "deep".equals(row.get("advisorId"))));
        assertTrue(contributionRows.stream().noneMatch(row -> "blocked".equals(row.get("advisorId"))));
    }

    @SuppressWarnings("unchecked")
    @Test
    void leaderNewCustomerAnalytics_marksYellowDegradeAndKeepsCurrentMonthInformational() throws Exception {
        List<JsonNode> orders = new ArrayList<>();
        orders.addAll(newCustomerOrders("yellow-apr", "yellow-child", YearMonth.parse("2026-04"), 40));
        orders.addAll(newCustomerOrders("yellow-may", "yellow-child", YearMonth.parse("2026-05"), 39));
        orders.addAll(newCustomerOrders("yellow-jun", "yellow-child", YearMonth.parse("2026-06"), 99));
        orders.addAll(newCustomerOrders("red-apr", "red-child", YearMonth.parse("2026-04"), 39));
        orders.addAll(newCustomerOrders("red-may", "red-child", YearMonth.parse("2026-05"), 39));
        orders.addAll(newCustomerOrders("ok-may", "ok-child", YearMonth.parse("2026-05"), 40));

        Map<String, List<String>> children = new LinkedHashMap<>();
        children.put("yellow", List.of("yellow-child"));
        children.put("red", List.of("red-child"));
        children.put("ok", List.of("ok-child"));

        Map<String, Object> payload = invokeLeaderNewCustomerAnalytics(orders, """
                {
                  "affiliates": [
                    {"id": "yellow", "name": "Yellow", "status": "approved"},
                    {"id": "yellow-child", "name": "Yellow Child", "status": "approved"},
                    {"id": "red", "name": "Red", "status": "approved"},
                    {"id": "red-child", "name": "Red Child", "status": "approved"},
                    {"id": "ok", "name": "Ok", "status": "approved"},
                    {"id": "ok-child", "name": "Ok Child", "status": "approved"}
                  ]
                }
                """, children, Set.of("2026-KW23"));

        Map<String, Object> summary = (Map<String, Object>) payload.get("summary");
        List<String> warnings = (List<String>) payload.get("warnings");

        assertEquals("GELB", findLeader(payload, "yellow").get("status"));
        assertEquals(99, findLeader(payload, "yellow").get("liveMonthNewCustomers"));
        assertEquals("DEGRADIERUNG", findLeader(payload, "red").get("status"));
        assertEquals("OK", findLeader(payload, "ok").get("status"));
        assertEquals(1, summary.get("yellowCount"));
        assertEquals(1, summary.get("degradeCount"));
        assertEquals(1, summary.get("okCount"));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("Order-Limit")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void leaderWeeklyMail_rollsUpApprovedTeamAndRoutesTestModeToContactEmail() throws Exception {
        List<JsonNode> orders = new ArrayList<>();
        var leaderCurrent = MAPPER.createObjectNode();
        leaderCurrent.put("id", "current-1");
        leaderCurrent.put("affiliate_id", "leader");
        leaderCurrent.put("created_at", "2026-05-18T10:00:00Z");
        leaderCurrent.put("total", "100.00");
        leaderCurrent.put("is_new_customer", true);
        orders.add(leaderCurrent);

        var childCurrent = MAPPER.createObjectNode();
        childCurrent.put("id", "current-2");
        childCurrent.put("affiliate_id", "child");
        childCurrent.put("created_at", "2026-05-19T10:00:00Z");
        childCurrent.put("total", "100.00");
        childCurrent.put("is_new_customer", true);
        orders.add(childCurrent);

        var blockedCurrent = MAPPER.createObjectNode();
        blockedCurrent.put("id", "current-3");
        blockedCurrent.put("affiliate_id", "blocked");
        blockedCurrent.put("created_at", "2026-05-20T10:00:00Z");
        blockedCurrent.put("total", "100.00");
        blockedCurrent.put("is_new_customer", true);
        orders.add(blockedCurrent);

        var previous = MAPPER.createObjectNode();
        previous.put("id", "prev-1");
        previous.put("affiliate_id", "child");
        previous.put("created_at", "2026-05-12T10:00:00Z");
        previous.put("total", "50.00");
        previous.put("is_new_customer", true);
        orders.add(previous);

        var secondPrevious = MAPPER.createObjectNode();
        secondPrevious.put("id", "prev-2");
        secondPrevious.put("affiliate_id", "child");
        secondPrevious.put("created_at", "2026-05-05T10:00:00Z");
        secondPrevious.put("total", "25.00");
        secondPrevious.put("is_new_customer", true);
        orders.add(secondPrevious);

        Map<String, List<String>> children = new LinkedHashMap<>();
        children.put("leader", List.of("child", "blocked"));
        Properties config = new Properties();
        config.setProperty("contactEmail", "debug@example.test");
        config.setProperty("sendEmailsEnabled", "true");
        config.setProperty("leaderWeeklyMailProductionEnabled", "false");
        config.setProperty("leaderWeeklyReportTemplateHtml",
                "<html><body>{{leaderName}} {{currentWeekNewCustomers}} {{previousWeekNewCustomers}} {{secondPreviousWeekNewCustomers}} {{monthNewCustomers}} {{monthlyTarget}} {{statusLabel}} <table>{{teamRows}}</table></body></html>");

        Map<String, Object> payload = invokeLeaderWeeklyMailPayload(orders, """
                {
                  "affiliates": [
                    {"id": "leader", "name": "Leader ÄÖÜ", "email": "leader@example.test", "status": "approved"},
                    {"id": "child", "name": "Child", "email": "child@example.test", "status": "approved"},
                    {"id": "blocked", "name": "Blocked", "email": "blocked@example.test", "status": "blocked"}
                  ]
                }
                """, children, config, LocalDate.parse("2026-05-25"), false, Set.of());

        Map<String, Object> summary = (Map<String, Object>) payload.get("summary");
        List<Map<String, Object>> reports = (List<Map<String, Object>>) payload.get("reportRows");
        Map<String, Object> report = reports.get(0);

        assertEquals("18.05.2026 bis 24.05.2026", summary.get("periodLabel"));
        assertEquals("test", report.get("recipientMode"));
        assertEquals("debug@example.test", report.get("toEmail"));
        assertEquals(2, report.get("currentWeekNewCustomers"));
        assertEquals(1, report.get("previousWeekNewCustomers"));
        assertEquals(1, report.get("secondPreviousWeekNewCustomers"));
        assertEquals(4, report.get("monthNewCustomers"));
        assertEquals(2, report.get("teamSize"));
        assertFalse(report.get("renderedHtml").toString().contains("{{"));
        assertTrue(report.get("renderedHtml").toString().contains("Leader ÄÖÜ"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void leaderWeeklyMail_productionRoutingRequiresSeparateSwitchAndGlobalEmailSwitch() throws Exception {
        List<JsonNode> orders = newCustomerOrders("leader-current", "leader", YearMonth.parse("2026-05"), 1);
        Map<String, List<String>> children = Map.of("leader", List.of("child"));
        String affiliatesJson = """
                {
                  "affiliates": [
                    {"id": "leader", "name": "Leader", "email": "leader@example.test", "status": "approved"},
                    {"id": "child", "name": "Child", "email": "child@example.test", "status": "approved"}
                  ]
                }
                """;

        Properties safeConfig = new Properties();
        safeConfig.setProperty("contactEmail", "debug@example.test");
        safeConfig.setProperty("sendEmailsEnabled", "true");
        safeConfig.setProperty("leaderWeeklyMailProductionEnabled", "false");
        Map<String, Object> safePayload = invokeLeaderWeeklyMailPayload(orders, affiliatesJson, children, safeConfig, LocalDate.parse("2026-05-25"), true, Set.of());
        Map<String, Object> safeReport = ((List<Map<String, Object>>) safePayload.get("reportRows")).get(0);
        assertEquals("test", safeReport.get("recipientMode"));
        assertEquals("debug@example.test", safeReport.get("toEmail"));

        Properties productionConfig = new Properties();
        productionConfig.setProperty("contactEmail", "debug@example.test");
        productionConfig.setProperty("sendEmailsEnabled", "true");
        productionConfig.setProperty("leaderWeeklyMailProductionEnabled", "true");
        Map<String, Object> productionPayload = invokeLeaderWeeklyMailPayload(orders, affiliatesJson, children, productionConfig, LocalDate.parse("2026-05-25"), true, Set.of());
        Map<String, Object> productionReport = ((List<Map<String, Object>>) productionPayload.get("reportRows")).get(0);
        assertEquals("production", productionReport.get("recipientMode"));
        assertEquals("leader@example.test", productionReport.get("toEmail"));
    }

    @Test
    void leaderWeeklyMail_scheduleValidationFallsBackToMondayMorning() throws Exception {
        Method day = WebUiServer.class.getDeclaredMethod("normalizeLeaderWeeklyMailScheduleDay", String.class);
        Method time = WebUiServer.class.getDeclaredMethod("normalizeLeaderWeeklyMailScheduleTime", String.class);
        day.setAccessible(true);
        time.setAccessible(true);

        assertEquals("MONDAY", day.invoke(null, "nonsense"));
        assertEquals("FRIDAY", day.invoke(null, "Freitag"));
        assertEquals("08:00", time.invoke(null, "99:99"));
        assertEquals("06:30", time.invoke(null, "06:30"));
    }
}
