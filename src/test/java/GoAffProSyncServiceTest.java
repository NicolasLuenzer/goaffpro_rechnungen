import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GoAffProSyncServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void resetRateWindow() {
        // Das Stundenfenster ist prozessglobal und würde sonst zwischen Tests leaken
        GoAffProSyncService.RateBudget.clearWindowForTests();
    }

    private static void initDatabase(Path db) throws Exception {
        Method method = GoAffProSyncService.class.getDeclaredMethod("initDatabase", Path.class);
        method.setAccessible(true);
        method.invoke(null, db);
    }

    private static GoAffProSyncService.UpsertResult upsert(Connection connection, String entityType, String externalId, JsonNode raw) throws Exception {
        Method method = GoAffProSyncService.class.getDeclaredMethod("upsertEntity", Connection.class, String.class, String.class, String.class, JsonNode.class);
        method.setAccessible(true);
        return (GoAffProSyncService.UpsertResult) method.invoke(null, connection, entityType, externalId, "/test", raw);
    }

    private static void markRemoteMissing(Connection connection, String entityType, String runStartedAt) throws Exception {
        Method method = GoAffProSyncService.class.getDeclaredMethod("markRemoteMissing", Connection.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(null, connection, entityType, runStartedAt);
    }

    private static void markInvalidEmptyEntities(Connection connection) throws Exception {
        Method method = GoAffProSyncService.class.getDeclaredMethod("markInvalidEmptyEntities", Connection.class);
        method.setAccessible(true);
        method.invoke(null, connection);
    }

    private static String withPagination(String path, int page, int limit) throws Exception {
        Method method = GoAffProSyncService.class.getDeclaredMethod("withPagination", String.class, int.class, int.class);
        method.setAccessible(true);
        return (String) method.invoke(null, path, page, limit);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String withPaginationMode(String path, int page, int limit, String modeName) throws Exception {
        Class<?> modeClass = Class.forName("GoAffProSyncService$PaginationMode");
        Object mode = Enum.valueOf((Class<Enum>) modeClass, modeName);
        Method method = GoAffProSyncService.class.getDeclaredMethod("withPagination", String.class, int.class, int.class, modeClass);
        method.setAccessible(true);
        return (String) method.invoke(null, path, page, limit, mode);
    }

    private static HttpServer fakeServer(FakeHandler handler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                FakeResponse response = handler.handle(exchange);
                byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(response.code(), bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (Exception e) {
                byte[] bytes = ("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private static String fakeBase(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private record FakeResponse(int code, String body) {
    }

    @FunctionalInterface
    private interface FakeHandler {
        FakeResponse handle(HttpExchange exchange) throws Exception;
    }

    @Test
    void syncStoreUpsertDetectsInsertedUnchangedAndUpdated(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("sync.sqlite");
        initDatabase(db);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            GoAffProSyncService.UpsertResult inserted = upsert(connection, "orders", "o1", MAPPER.readTree("""
                    {"id":"o1","total":"10.00","created_at":"2026-01-01T10:00:00Z"}
                    """));
            GoAffProSyncService.UpsertResult unchanged = upsert(connection, "orders", "o1", MAPPER.readTree("""
                    {"id":"o1","total":"10.00","created_at":"2026-01-01T10:00:00Z"}
                    """));
            GoAffProSyncService.UpsertResult updated = upsert(connection, "orders", "o1", MAPPER.readTree("""
                    {"id":"o1","total":"11.00","created_at":"2026-01-01T10:00:00Z"}
                    """));

            assertEquals(1, inserted.inserted);
            assertEquals(1, unchanged.unchanged);
            assertEquals(1, updated.updated);

            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*), raw_json FROM sync_entities WHERE entity_type='orders'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                    assertTrue(rs.getString(2).contains("\"11.00\""));
                }
            }
        }
    }

    @Test
    void syncStoreKeepsRemoteMissingRowsAsBackup(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("sync.sqlite");
        initDatabase(db);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            upsert(connection, "affiliates", "a1", MAPPER.readTree("{\"id\":\"a1\",\"name\":\"Alt\"}"));
            markRemoteMissing(connection, "affiliates", Instant.now().plusSeconds(60).toString());
            try (PreparedStatement ps = connection.prepareStatement("SELECT state FROM sync_entities WHERE entity_type='affiliates' AND external_id='a1'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("remote_missing", rs.getString(1));
                }
            }
        }
    }

    @Test
    void syncStatusOnEmptyStoreExplainsMissingInitialSync(@TempDir Path tempDir) throws Exception {
        Properties config = new Properties();
        config.setProperty("goaffproSyncDataPath", tempDir.toString());
        GoAffProSyncService service = new GoAffProSyncService();
        var status = service.status(config);
        assertEquals("Noch nicht synchronisiert", status.get("state"));
        assertEquals(0, status.get("entityCount"));
        assertTrue(status.get("dbPath").toString().endsWith("goaffpro_sync.sqlite"));
    }

    @Test
    void stableIdsUseDocumentIdentifiersBeforeHashFallback() throws Exception {
        assertEquals("affiliate-1", GoAffProSyncService.extractStableId(MAPPER.readTree("{\"id\":\"affiliate-1\",\"email\":\"x@example.com\"}"), "affiliates"));
        assertEquals("payment-1", GoAffProSyncService.extractStableId(MAPPER.readTree("{\"id\":\"payment-1\",\"affiliate_id\":\"a1\"}"), "payments"));
        assertEquals("tx-1", GoAffProSyncService.extractStableId(MAPPER.readTree("{\"tx_id\":\"tx-1\",\"id\":\"wrong\"}"), "transactions"));
        assertEquals("order-1", GoAffProSyncService.extractStableId(MAPPER.readTree("{\"order_id\":\"order-1\",\"number\":\"100\"}"), "orders"));
        assertEquals("party-1", GoAffProSyncService.extractStableId(MAPPER.readTree("{\"sub_id\":\"party-1\",\"title\":\"Backtreff\"}"), "showcases"));
        assertEquals("asset-1", GoAffProSyncService.extractStableId(MAPPER.readTree("{\"_id\":\"asset-1\"}"), "asset_contents"));
        assertEquals("CODE1", GoAffProSyncService.extractStableId(MAPPER.readTree("{\"code\":\"CODE1\"}"), "coupons"));
    }

    @Test
    void paginationUsesLimitAsPageSizeAndKeepsPaging() throws Exception {
        assertEquals("/v1/admin/payments?limit=500", withPagination("/v1/admin/payments?limit=500", 1, 500));
        assertEquals("/v1/admin/payments?limit=500&page=2&offset=500", withPagination("/v1/admin/payments?limit=500", 2, 500));
        assertEquals("/v1/admin/payments?limit=250&page=3&offset=500", withPagination("/v1/admin/payments", 3, 250));
        assertEquals("/v1/admin/payments?limit=500&offset=500", withPaginationMode("/v1/admin/payments?limit=500", 2, 500, "OFFSET_ONLY"));
        assertEquals("/v1/admin/payments?limit=500&page=2&offset=500", withPaginationMode("/v1/admin/payments?limit=500", 2, 500, "PAGE_AND_OFFSET"));
    }

    @Test
    void emptyRawObjectsAreMarkedInvalidAndExcludedFromReports(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("goaffpro_sync.sqlite");
        initDatabase(db);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            upsert(connection, "affiliates", "empty", MAPPER.readTree("{}"));
            upsert(connection, "affiliates", "valid", MAPPER.readTree("{\"id\":\"valid\",\"name\":\"Valid\"}"));
            markInvalidEmptyEntities(connection);

            try (PreparedStatement ps = connection.prepareStatement("SELECT state FROM sync_entities WHERE entity_type='affiliates' AND external_id='empty'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("invalid_empty", rs.getString(1));
                }
            }
        }

        Properties config = new Properties();
        config.setProperty("goaffproSyncDataPath", tempDir.toString());
        GoAffProSyncService service = new GoAffProSyncService();
        var entities = service.entities(config, "affiliates");
        assertEquals(1, entities.size());
        assertEquals("valid", entities.get(0).get("id").asText());
    }

    @Test
    void diagnosticsDoNotWriteSyncEntitiesAndDetectRepeatedPagination(@TempDir Path tempDir) throws Exception {
        HttpServer server = fakeServer(exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            String id = (query != null && (query.contains("offset=100") || query.contains("offset=200"))) ? "a2" : "a1";
            return new FakeResponse(200, "{\"affiliates\":[{\"id\":\"" + id + "\",\"name\":\"Advisor " + id + "\"}]}");
        });
        try {
            Properties config = new Properties();
            config.setProperty("goaffproSyncDataPath", tempDir.toString());
            config.setProperty("goaffproSyncApiBase", fakeBase(server));
            config.setProperty("goaffproSyncMaxCallsPerHour", "3600000");

            GoAffProSyncService service = new GoAffProSyncService();
            service.runDiagnostics(config, "test-key", java.util.List.of("affiliates"));

            Path db = GoAffProSyncService.resolveDbPath(config);
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db)) {
                try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM sync_entities")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(0, rs.getInt(1));
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*), MAX(recommendation) FROM sync_diagnostic_results")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertTrue(rs.getInt(1) >= 8);
                        assertEquals("Pagination anpassen", rs.getString(2));
                    }
                }
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void diagnosticsRetrySmallLimitAfterGatewayTimeout(@TempDir Path tempDir) throws Exception {
        HttpServer server = fakeServer(exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null && query.contains("limit=25")) {
                return new FakeResponse(200, "{\"traffic\":[{\"id\":\"t1\",\"created_at\":\"2026-01-01T00:00:00Z\"}]}");
            }
            return new FakeResponse(504, "{\"error\":\"timeout\"}");
        });
        try {
            Properties config = new Properties();
            config.setProperty("goaffproSyncDataPath", tempDir.toString());
            config.setProperty("goaffproSyncApiBase", fakeBase(server));
            config.setProperty("goaffproSyncMaxCallsPerHour", "3600000");

            GoAffProSyncService service = new GoAffProSyncService();
            service.runDiagnostics(config, "test-key", java.util.List.of("traffic"));

            Path db = GoAffProSyncService.resolveDbPath(config);
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
                 PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM sync_diagnostic_results WHERE variant='retry-limit-25-after-504' AND http_code=200")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertTrue(rs.getInt(1) >= 1);
                }
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void syncUsesOffsetForPaymentsAndSkipsBrokenEndpoints(@TempDir Path tempDir) throws Exception {
        List<String> requestedPaths = Collections.synchronizedList(new java.util.ArrayList<>());
        AtomicInteger brokenEndpointCalls = new AtomicInteger();
        HttpServer server = fakeServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getRawQuery();
            String full = path + (query == null ? "" : "?" + query);
            requestedPaths.add(full);
            if (path.contains("/groups") || path.contains("/store/logs") || path.contains("/creatives")) {
                brokenEndpointCalls.incrementAndGet();
                return new FakeResponse(500, "{\"error\":\"should not be called\"}");
            }
            if (path.equals("/v1/admin/payments")) {
                int offset = query != null && query.contains("offset=1000") ? 1000 : (query != null && query.contains("offset=500") ? 500 : 0);
                int count = offset == 1000 ? 25 : 500;
                return new FakeResponse(200, "{\"payments\":" + jsonItems("id", "p", offset, count) + "}");
            }
            if (path.equals("/v1/admin/connections")) {
                return new FakeResponse(200, "{\"connections\":" + jsonItems("id", "c", 0, 500) + "}");
            }
            if (path.equals("/v1/admin/traffic")) {
                return new FakeResponse(200, "{\"traffic\":" + jsonItems("id", "t", 0, 500) + "}");
            }
            if (path.equals("/v1/admin/store/config")) {
                return new FakeResponse(200, "{\"id\":\"store\"}");
            }
            return new FakeResponse(200, "[]");
        });
        try {
            Properties config = new Properties();
            config.setProperty("goaffproSyncDataPath", tempDir.toString());
            config.setProperty("goaffproSyncApiBase", fakeBase(server));
            config.setProperty("goaffproSyncMaxCallsPerHour", "3600000");

            GoAffProSyncService service = new GoAffProSyncService();
            service.runSync(config, "test-key", "initial");

            assertEquals(0, brokenEndpointCalls.get());
            assertTrue(requestedPaths.stream().anyMatch(path -> path.startsWith("/v1/admin/payments?") && path.contains("offset=500") && !path.contains("page=")));
            assertTrue(requestedPaths.stream().anyMatch(path -> path.startsWith("/v1/admin/payments?") && path.contains("offset=1000") && !path.contains("page=")));

            Path db = GoAffProSyncService.resolveDbPath(config);
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db)) {
                assertEntityCount(connection, "payments", 1025);
                assertEndpointWarning(connection, "connections", "Nur erste Seite synchronisiert");
                assertEndpointWarning(connection, "traffic", "Nur erste Seite synchronisiert");
                assertEndpointWarning(connection, "groups", "HTTP 504");
                assertEndpointWarning(connection, "store_logs", "HTTP 504");
                assertEndpointWarning(connection, "creatives", "deprecated");
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void extractTotalCountReadsKnownFieldsAndIgnoresAmbiguousCount() throws Exception {
        assertEquals(1250, GoAffProSyncService.extractTotalCount(MAPPER.readTree("{\"total\":1250,\"affiliates\":[{\"id\":\"a1\"}]}"), 1));
        assertEquals(42, GoAffProSyncService.extractTotalCount(MAPPER.readTree("{\"meta\":{\"total_results\":42},\"data\":[]}"), 0));
        assertEquals(7, GoAffProSyncService.extractTotalCount(MAPPER.readTree("{\"total_count\":7}"), 0));
        assertNull(GoAffProSyncService.extractTotalCount(MAPPER.readTree("{\"affiliates\":[{\"id\":\"a1\"}]}"), 1));
        assertNull(GoAffProSyncService.extractTotalCount(MAPPER.readTree("{\"total\":\"viele\"}"), 0));
        assertNull(GoAffProSyncService.extractTotalCount(MAPPER.readTree("{\"total\":3.5}"), 0));
        assertNull(GoAffProSyncService.extractTotalCount(MAPPER.readTree("[{\"id\":\"a1\"}]"), 1));
        // total kleiner als Items der Seite ist kein plausibles Gesamtergebnis
        assertNull(GoAffProSyncService.extractTotalCount(MAPPER.readTree("{\"total\":3}"), 5));
        // "count" == Items der Seite ist mehrdeutig (oft Seitengröße) und wird verworfen
        assertNull(GoAffProSyncService.extractTotalCount(MAPPER.readTree("{\"count\":500,\"orders\":[]}"), 500));
        assertEquals(9000, GoAffProSyncService.extractTotalCount(MAPPER.readTree("{\"count\":9000,\"orders\":[]}"), 500));
    }

    @Test
    void estimateEndpointCallsCoversPaginationModes() {
        GoAffProSyncService.EndpointSpec paginated =
                new GoAffProSyncService.EndpointSpec("orders", "Orders", "orders", "/v1/admin/orders?limit=500", "orders", true, true);
        assertEquals(3, GoAffProSyncService.estimateEndpointCalls(paginated, 1250));
        assertEquals(1, GoAffProSyncService.estimateEndpointCalls(paginated, 500));
        assertEquals(1, GoAffProSyncService.estimateEndpointCalls(paginated, 0));
        assertEquals(1, GoAffProSyncService.estimateEndpointCalls(paginated, null));

        GoAffProSyncService.EndpointSpec smallLimit =
                new GoAffProSyncService.EndpointSpec("products", "Products", "products", "/v1/admin/products?limit=250", "products", true, false, 250);
        assertEquals(2, GoAffProSyncService.estimateEndpointCalls(smallLimit, 500));

        GoAffProSyncService.EndpointSpec singleton =
                new GoAffProSyncService.EndpointSpec("store_config", "Store Config", "store_config", "/v1/admin/store/config", "config", false, false).singleton();
        assertEquals(1, GoAffProSyncService.estimateEndpointCalls(singleton, null));

        GoAffProSyncService.EndpointSpec partial =
                new GoAffProSyncService.EndpointSpec("traffic", "Traffic", "traffic", "/v1/admin/traffic?limit=500", "traffic", true, true)
                        .singlePagePartial("nur erste Seite");
        assertEquals(1, GoAffProSyncService.estimateEndpointCalls(partial, 5000));

        GoAffProSyncService.EndpointSpec skipped =
                new GoAffProSyncService.EndpointSpec("creatives", "Creatives", "creatives", "/v1/admin/creatives?limit=500", "creatives", true, false)
                        .skip("deprecated");
        assertEquals(0, GoAffProSyncService.estimateEndpointCalls(skipped, 5000));
    }

    @Test
    @SuppressWarnings("unchecked")
    void runSyncReportsProgressPlanAndEta(@TempDir Path tempDir) throws Exception {
        HttpServer server = fakeServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/v1/admin/affiliates")) {
                return new FakeResponse(200, "{\"total\":2,\"affiliates\":[{\"id\":\"a1\"},{\"id\":\"a2\"}]}");
            }
            if (path.equals("/v1/admin/store/config")) {
                return new FakeResponse(200, "{\"id\":\"store\"}");
            }
            return new FakeResponse(200, "[]");
        });
        try {
            Properties config = new Properties();
            config.setProperty("goaffproSyncDataPath", tempDir.toString());
            config.setProperty("goaffproSyncApiBase", fakeBase(server));
            config.setProperty("goaffproSyncMaxCallsPerHour", "3600000");

            GoAffProSyncService service = new GoAffProSyncService();
            GoAffProSyncService.SyncRunResult run = service.runSync(config, "test-key", "initial");
            var statusMap = run.toStatusMap();

            assertEquals("finished", statusMap.get("phase"));
            assertEquals(100, statusMap.get("progressPercent"));
            assertTrue(((Number) statusMap.get("endpointCount")).intValue() > 0);
            assertEquals(statusMap.get("endpointCount"), statusMap.get("endpointIndex"));

            List<java.util.Map<String, Object>> planned = (List<java.util.Map<String, Object>>) statusMap.get("plannedEndpoints");
            assertNotNull(planned);
            assertFalse(planned.isEmpty());
            var affiliates = planned.stream().filter(r -> "affiliates".equals(r.get("endpointKey"))).findFirst().orElseThrow();
            assertEquals("api", affiliates.get("estimateSource"));
            assertEquals("done", affiliates.get("state"));
            assertTrue(((Number) affiliates.get("expectedTotal")).intValue() >= 2);
            var creatives = planned.stream().filter(r -> "creatives".equals(r.get("endpointKey"))).findFirst().orElseThrow();
            assertEquals("skipped", creatives.get("state"));

            var status = service.status(config);
            var budget = (java.util.Map<String, Object>) status.get("budget");
            assertNotNull(budget);
            assertTrue(((Number) budget.get("callsUsedLastHour")).intValue() > 0);
            assertEquals(Boolean.FALSE, budget.get("waitingForBudget"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void slidingWindowAllowsBurstAndLegacyEnforcesSpacing() throws Exception {
        GoAffProSyncService.RateBudget burst = new GoAffProSyncService.RateBudget(10, 0, true);
        long start = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) burst.beforeCall();
        assertTrue(System.currentTimeMillis() - start < 1000, "Burst-Calls dürfen nicht warten");
        assertEquals(5, burst.callsInLastHour());
        assertEquals(5, burst.burstAvailableNow());

        GoAffProSyncService.RateBudget.clearWindowForTests();
        GoAffProSyncService.RateBudget full = new GoAffProSyncService.RateBudget(2, 0, true);
        full.beforeCall();
        full.beforeCall();
        assertEquals(0, full.burstAvailableNow());

        GoAffProSyncService.RateBudget.clearWindowForTests();
        GoAffProSyncService.RateBudget legacy = new GoAffProSyncService.RateBudget(3600000);
        long legacyStart = System.currentTimeMillis();
        legacy.beforeCall();
        legacy.beforeCall();
        assertTrue(System.currentTimeMillis() - legacyStart >= 250, "Legacy-Modus muss den Mindestabstand erzwingen");
    }

    @Test
    void rateWindowSurvivesRunBoundaries() throws Exception {
        GoAffProSyncService.RateBudget first = new GoAffProSyncService.RateBudget(100, 0, true);
        first.beforeCall();
        first.beforeCall();
        GoAffProSyncService.RateBudget second = new GoAffProSyncService.RateBudget(100, 0, true);
        assertEquals(2, second.callsInLastHour());
        second.beforeCall();
        assertEquals(3, second.callsInLastHour());
        assertEquals(97, second.burstAvailableNow());
    }

    @Test
    void etaUsesBurstSemanticsWhenWindowFree() {
        GoAffProSyncService.SyncRunResult run = new GoAffProSyncService.SyncRunResult("initial");
        run.callLimitPerHour = 60;
        run.estimatedTotalCalls = 10;
        run.budget = new GoAffProSyncService.RateBudget(60, 1500, true);
        assertEquals(15L, run.toStatusMap().get("etaSeconds"));

        run.budget = null;
        assertEquals(600L, run.toStatusMap().get("etaSeconds"));
    }

    @Test
    void downloadSkipAvoidsRedownloadAndDeltaSkipsDownloadPhase(@TempDir Path tempDir) throws Exception {
        AtomicInteger downloadHits = new AtomicInteger();
        HttpServer[] serverRef = new HttpServer[1];
        serverRef[0] = fakeServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/download/f1.pdf")) {
                downloadHits.incrementAndGet();
                return new FakeResponse(200, "PDF-BYTES");
            }
            if (path.equals("/v1/admin/files")) {
                return new FakeResponse(200, "{\"files\":[{\"id\":\"f1\",\"url\":\"" + fakeBase(serverRef[0]) + "/download/f1.pdf\"}]}");
            }
            return new FakeResponse(200, "[]");
        });
        try {
            Properties config = new Properties();
            config.setProperty("goaffproSyncDataPath", tempDir.toString());
            config.setProperty("goaffproSyncApiBase", fakeBase(serverRef[0]));
            config.setProperty("goaffproSyncMaxCallsPerHour", "3600000");

            GoAffProSyncService service = new GoAffProSyncService();
            service.runSync(config, "test-key", "initial");
            assertEquals(1, downloadHits.get(), "Initialsync lädt die Datei");

            GoAffProSyncService.SyncRunResult delta = service.runSync(config, "test-key", "delta");
            assertEquals(1, downloadHits.get(), "Delta überspringt die Download-Phase (Default)");
            assertEquals(0, delta.downloadsSkipped);

            config.setProperty("goaffproSyncDeltaDownloadsEnabled", "true");
            GoAffProSyncService.SyncRunResult deltaWithDownloads = service.runSync(config, "test-key", "delta");
            assertEquals(1, downloadHits.get(), "Vorhandene Datei wird nicht erneut geladen");
            assertEquals(1, deltaWithDownloads.downloadsSkipped);

            service.runSync(config, "test-key", "deep");
            assertEquals(2, downloadHits.get(), "Deep-Check lädt die Datei erneut (Re-Verify)");
        } finally {
            serverRef[0].stop(0);
        }
    }

    private static String jsonItems(String idKey, String prefix, int start, int count) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) json.append(',');
            json.append("{\"").append(idKey).append("\":\"").append(prefix).append(start + i).append("\"}");
        }
        json.append(']');
        return json.toString();
    }

    private static void assertEntityCount(Connection connection, String entityType, int expected) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM sync_entities WHERE entity_type=? AND state='active'")) {
            ps.setString(1, entityType);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(expected, rs.getInt(1));
            }
        }
    }

    @Test
    void describeThrowableBleibtAuchOhneMeldungAussagekraeftig() {
        assertEquals("IOException: Platte voll", GoAffProSyncService.describeThrowable(new java.io.IOException("Platte voll")));
        // getMessage() ist häufig null - dann sagt der Klassenname mehr als ein leerer Text
        assertEquals("NullPointerException", GoAffProSyncService.describeThrowable(new NullPointerException()));
        assertEquals("IllegalStateException", GoAffProSyncService.describeThrowable(new IllegalStateException("   ")));
        assertEquals("Unbekannter Fehler", GoAffProSyncService.describeThrowable(null));
    }

    @Test
    void verwaisteLaeufeWerdenNachProzessstartUnterschieden(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("goaffpro_sync.sqlite");
        initDatabase(db);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            // vor dem Prozessstart -> echter Neustart; nach dem Prozessstart -> Absturz im Prozess
            insertRunRow(connection, 1, "running", "2020-01-01T10:00:00Z");
            insertRunRow(connection, 2, "running", Instant.now().plusSeconds(60).toString());
            insertRunRow(connection, 3, "success", "2020-01-01T10:00:00Z");

            Method method = GoAffProSyncService.class.getDeclaredMethod("repairOrphanRuns", Connection.class);
            method.setAccessible(true);
            method.invoke(null, connection);

            assertEquals("error", runField(connection, 1, "status"));
            assertEquals(GoAffProSyncService.ORPHAN_RESTART_MESSAGE, runField(connection, 1, "error"));
            assertEquals("error", runField(connection, 2, "status"));
            assertEquals(GoAffProSyncService.ORPHAN_CRASH_MESSAGE, runField(connection, 2, "error"));
            assertFalse(runField(connection, 1, "finished_at").isBlank(), "Verwaiste Läufe brauchen ein Endedatum");

            // Abgeschlossene Läufe bleiben unangetastet
            assertEquals("success", runField(connection, 3, "status"));
            assertEquals("", runField(connection, 3, "error"));
        }
    }

    private static void insertRunRow(Connection connection, long id, String status, String startedAt) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO sync_runs(id, mode, status, started_at, error) VALUES(?,?,?,?,'')")) {
            ps.setLong(1, id);
            ps.setString(2, "delta");
            ps.setString(3, status);
            ps.setString(4, startedAt);
            ps.executeUpdate();
        }
    }

    private static String runField(Connection connection, long id, String column) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT " + column + " FROM sync_runs WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Lauf " + id + " fehlt");
                return Objects.toString(rs.getString(1), "");
            }
        }
    }

    private static void assertEndpointWarning(Connection connection, String endpointKey, String expectedPart) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT complete, warning FROM sync_endpoint_stats WHERE endpoint_key=?")) {
            ps.setString(1, endpointKey);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "missing endpoint stats for " + endpointKey);
                assertFalse(rs.getBoolean("complete"), endpointKey + " should be marked incomplete");
                assertTrue(rs.getString("warning").contains(expectedPart), rs.getString("warning"));
            }
        }
    }
}
