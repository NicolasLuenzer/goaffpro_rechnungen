import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Local read-through backup store for GoAffPro Admin API data.
 *
 * The service intentionally stores raw JSON in addition to small inventory fields:
 * the current UI can evolve without losing structures that are not analysed yet.
 */
public class GoAffProSyncService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String API_BASE = "https://api.goaffpro.com";
    private static final int DEFAULT_LIMIT = 500;
    private static final int DEFAULT_CALL_LIMIT_PER_HOUR = 60;
    private static final int DEFAULT_DELTA_LOOKBACK_DAYS = 14;
    private static final long DEFAULT_MIN_FREE_BYTES = 512L * 1024L * 1024L;
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final String AFFILIATE_FIELDS = "id,avatar,honorific,date_of_birth,gender,name,first_name,last_name,email,ref_code,company_name,ref_codes,coupon,coupons,phone,website,facebook,twitter,instagram,address_1,address_2,city,state,zip,country,admin_note,extra_1,extra_2,extra_3,group_id,registration_ip,personal_message,payment_method,payment_details,commission,status,last_login,total_referral_earnings,total_network_earnings,total_amount_paid,total_amount_pending,total_other_earnings,number_of_orders,tax_identification_number,login_token,signup_page,comments,tags,approved_at,blocked_at,created_at,updated_at,parent_id,upline_affiliate_id,upline_id,parent_affiliate_id";
    private static final String PAYMENT_FIELDS = "id,affiliate_id,amount,currency,payment_method,payment_details,affiliate_message,admin_note,transactions,status,created_at,updated_at";
    private static final String ORDER_FIELDS = "id,order_id,number,total,currency,status,affiliate_id,created_at,updated_at,customer_email,customer,shipping_address,line_items,conversion_source,sub_id,is_new_customer";
    private static final String REWARD_FIELDS = "id,affiliate_id,amount,currency,status,created_at,updated_at,type,description";
    private static final String TRANSACTION_FIELDS = "tx_id,id,affiliate_id,amount,currency,event_type,entity_type,entity_id,is_paid,metadata,created_at,updated_at,endingBalance,startingBalance";
    private static final String TRAFFIC_FIELDS = "id,affiliate_id,referrer,source,medium,campaign,ip,country,city,device,browser,os,landing_page,page_url,created_at,updated_at";
    private static final String SHOWCASE_FIELDS = "id,title,name,host,affiliate_id,sub_id,status,starts_at,ends_at,created_at,updated_at,total,orders,count";
    private static final String CONNECTION_FIELDS = "id,affiliate_id,parent_id,upline_affiliate_id,upline_id,parent_affiliate_id,source,target,status,created_at,updated_at";
    private static final String FILE_FIELDS = "id,name,title,url,download_url,file_url,src,href,link,size,created_at,updated_at";
    private static final List<String> DEFAULT_DIAGNOSTIC_ENDPOINTS = List.of(
            "affiliates", "payments", "connections", "traffic", "groups", "creatives", "store_logs");
    private static final int DIAGNOSTIC_RAW_EXCERPT_LIMIT = 20 * 1024;
    enum PaginationMode {
        PAGE_AND_OFFSET,
        OFFSET_ONLY,
        SINGLE_PAGE_PARTIAL,
        SKIP_WITH_WARNING
    }

    /** Startzeit dieses Prozesses – unterscheidet "vom Neustart verwaist" von "hier abgestürzt". */
    private static final Instant PROCESS_START =
            ProcessHandle.current().info().startInstant().orElseGet(Instant::now);

    /** Null-sicherer Fehlertext: Throwable.getMessage() ist oft null (dann sagt die Klasse mehr). */
    static String describeThrowable(Throwable t) {
        if (t == null) return "Unbekannter Fehler";
        String message = t.getMessage();
        return (message == null || message.isBlank())
                ? t.getClass().getSimpleName()
                : t.getClass().getSimpleName() + ": " + message;
    }

    private static void logSyncFailure(String context, Throwable t) {
        System.err.println("GoAffPro " + context + " abgebrochen: " + describeThrowable(t));
        if (t != null) t.printStackTrace();
    }

    private static Map<String, Object> failedRunStatus(String mode, Throwable t) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("mode", mode);
        row.put("status", "error");
        row.put("phase", "finished");
        row.put("error", describeThrowable(t));
        row.put("finishedAt", Instant.now().toString());
        return row;
    }

    /** Wartungsmodus: währenddessen darf kein Sync starten (Sicherung/Import laufen). */
    private final AtomicBoolean maintenance = new AtomicBoolean(false);

    public boolean isBusy() {
        return running.get() || diagnosticsRunning.get();
    }

    public boolean isMaintenance() {
        return maintenance.get();
    }

    /** Belegt den Wartungsmodus, sofern gerade kein Lauf aktiv ist. */
    public boolean beginMaintenance() {
        if (isBusy()) return false;
        if (!maintenance.compareAndSet(false, true)) return false;
        if (isBusy()) { // Rennen: ein Lauf startete zwischen Prüfung und CAS
            maintenance.set(false);
            return false;
        }
        return true;
    }

    public void endMaintenance() {
        maintenance.set(false);
    }

    /** Nach einem Import zeigen die Momentaufnahmen auf Läufe der alten Datenbank. */
    public void resetAfterRestore() {
        currentRun = null;
        currentDiagnosticRun = null;
    }

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean diagnosticsRunning = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "goaffpro-sync");
        t.setDaemon(true);
        return t;
    });
    private volatile Map<String, Object> currentRun = null;
    private volatile Map<String, Object> currentDiagnosticRun = null;
    // Bleibt nach Laufende absichtlich gesetzt, damit das API-Budget-Gauge weiter die
    // Calls der letzten Stunde zeigt (die Zeitstempel altern von selbst heraus).
    private volatile RateBudget currentBudget = null;

    public Map<String, Object> startAsync(Properties config, String apiKey, String mode) throws Exception {
        String normalizedMode = normalizeMode(mode);
        if (maintenance.get()) {
            Map<String, Object> payload = status(config);
            payload.put("message", "Wartungsmodus: Es läuft gerade eine Sicherung oder ein Import.");
            return payload;
        }
        if (!isSyncEnabled(config)) {
            Map<String, Object> payload = status(config);
            payload.put("message", "GoAffPro Sync ist pausiert. Bitte zuerst fortsetzen.");
            return payload;
        }
        if (!running.compareAndSet(false, true)) {
            Map<String, Object> payload = status(config);
            payload.put("message", "Ein GoAffPro Sync läuft bereits.");
            return payload;
        }
        if (diagnosticsRunning.get()) {
            Map<String, Object> payload = status(config);
            payload.put("message", "Eine GoAffPro Sync-Diagnose läuft bereits.");
            running.set(false);
            return payload;
        }
        Properties snapshot = new Properties();
        snapshot.putAll(config);
        String apiKeySnapshot = Objects.toString(apiKey, "");
        executor.submit(() -> {
            // Throwable statt Exception: bei einem Error (z. B. NoClassDefFoundError) bliebe der
            // Zustand sonst dauerhaft auf "läuft" stehen, und submit() verwirft das Future -
            // der Fehler wäre nirgends sichtbar.
            try {
                runSync(snapshot, apiKeySnapshot, normalizedMode);
            } catch (Throwable t) {
                logSyncFailure("Sync (" + normalizedMode + ")", t);
                currentRun = failedRunStatus(normalizedMode, t);
            } finally {
                running.set(false);
            }
        });
        Map<String, Object> payload = status(config);
        payload.put("message", "GoAffPro Sync gestartet (" + normalizedMode + ").");
        return payload;
    }

    public Map<String, Object> startDiagnosticsAsync(Properties config, String apiKey, List<String> endpointKeys) throws Exception {
        if (maintenance.get()) {
            Map<String, Object> payload = diagnosticsLatest(config);
            payload.put("message", "Wartungsmodus: Es läuft gerade eine Sicherung oder ein Import.");
            return payload;
        }
        if (running.get()) {
            Map<String, Object> payload = diagnosticsLatest(config);
            payload.put("message", "Ein normaler GoAffPro Sync läuft bereits. Diagnose wurde nicht gestartet.");
            return payload;
        }
        if (!diagnosticsRunning.compareAndSet(false, true)) {
            Map<String, Object> payload = diagnosticsLatest(config);
            payload.put("message", "Eine GoAffPro Sync-Diagnose läuft bereits.");
            return payload;
        }
        Properties snapshot = new Properties();
        snapshot.putAll(config);
        String apiKeySnapshot = Objects.toString(apiKey, "");
        List<String> selectedEndpointKeys = normalizeDiagnosticEndpointKeys(endpointKeys);
        executor.submit(() -> {
            try {
                runDiagnostics(snapshot, apiKeySnapshot, selectedEndpointKeys);
            } catch (Throwable t) {
                logSyncFailure("Sync-Diagnose", t);
                currentDiagnosticRun = failedRunStatus("diagnostics", t);
            } finally {
                diagnosticsRunning.set(false);
            }
        });
        Map<String, Object> payload = diagnosticsLatest(config);
        payload.put("message", "GoAffPro Sync-Diagnose gestartet.");
        return payload;
    }

    public DiagnosticRunResult runDiagnostics(Properties config, String apiKey, List<String> endpointKeys) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("GoAffPro API-Key fehlt.");
        }
        if (running.get()) {
            throw new IOException("Ein normaler GoAffPro Sync läuft bereits.");
        }
        Path dataDir = resolveDataDir(config);
        Files.createDirectories(dataDir);
        Path db = resolveDbPath(config);
        initDatabase(db);

        List<String> selectedEndpointKeys = normalizeDiagnosticEndpointKeys(endpointKeys);
        DiagnosticRunResult run = new DiagnosticRunResult(selectedEndpointKeys);
        currentDiagnosticRun = run.toStatusMap();
        try (Connection connection = connect(db)) {
            long runId = insertDiagnosticRun(connection, run);
            run.runId = runId;
            RateBudget budget = newRateBudget(config);
            Map<String, EndpointSpec> specsByKey = endpointSpecs("initial", config).stream()
                    .collect(Collectors.toMap(spec -> spec.key, spec -> spec, (a, b) -> a, LinkedHashMap::new));
            for (String endpointKey : selectedEndpointKeys) {
                EndpointSpec endpoint = specsByKey.get(endpointKey);
                if (endpoint == null) {
                    run.warning("Unbekannter Diagnose-Endpoint: " + endpointKey);
                    continue;
                }
                run.currentEndpoint = endpoint.displayName;
                currentDiagnosticRun = run.toStatusMap();
                List<DiagnosticResult> results = diagnoseEndpoint(endpoint, apiKey, config, budget, run);
                String recommendation = recommendDiagnosticEndpoint(endpoint, results);
                for (DiagnosticResult result : results) {
                    result.recommendation = recommendation;
                    insertDiagnosticResult(connection, runId, result);
                    run.add(result);
                }
                currentDiagnosticRun = run.toStatusMap();
            }
            run.status = run.warnings.isEmpty() ? "success" : "warning";
            run.finishedAt = Instant.now().toString();
            updateDiagnosticRun(connection, runId, run);
            return run;
        } catch (Exception e) {
            run.status = "error";
            run.error = e.getMessage();
            run.finishedAt = Instant.now().toString();
            try (Connection connection = connect(db)) {
                if (run.runId > 0) updateDiagnosticRun(connection, run.runId, run);
            } catch (Exception ignored) {
            }
            throw e;
        } finally {
            currentDiagnosticRun = run.toStatusMap();
        }
    }

    public Map<String, Object> diagnosticsLatest(Properties config) throws Exception {
        Path db = resolveDbPath(config);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("running", diagnosticsRunning.get());
        payload.put("currentRun", currentDiagnosticRun);
        payload.put("defaultEndpoints", DEFAULT_DIAGNOSTIC_ENDPOINTS);
        payload.put("estimatedDefaultApiCalls", estimateDiagnosticCalls(DEFAULT_DIAGNOSTIC_ENDPOINTS));
        payload.put("estimatedDefaultMaxApiCalls", estimateDiagnosticCalls(DEFAULT_DIAGNOSTIC_ENDPOINTS) + DEFAULT_DIAGNOSTIC_ENDPOINTS.size());
        payload.put("callLimitPerHour", callLimitPerHour(config));
        payload.put("slidingWindowEnabled", slidingWindowEnabled(config));
        payload.put("minCallSpacingMs", minCallSpacingMs(config));
        payload.put("budgetCallsRemainingNow", newRateBudget(config).burstAvailableNow());
        if (!Files.exists(db)) {
            payload.put("latestRun", Map.of());
            payload.put("results", List.of());
            payload.put("endpointSummaries", List.of());
            return payload;
        }
        initDatabase(db);
        try (Connection connection = connect(db)) {
            Map<String, Object> latest = latestDiagnosticRun(connection);
            payload.put("latestRun", latest);
            long runId = longValue(latest.get("id"));
            List<Map<String, Object>> results = runId > 0 ? diagnosticResultRows(connection, runId) : List.of();
            payload.put("results", results);
            payload.put("endpointSummaries", diagnosticEndpointSummaries(results));
            return payload;
        }
    }

    public Map<String, Object> diagnosticRuns(Properties config, int limit) throws Exception {
        Path db = resolveDbPath(config);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("running", diagnosticsRunning.get());
        if (!Files.exists(db)) {
            payload.put("rows", List.of());
            return payload;
        }
        initDatabase(db);
        try (Connection connection = connect(db)) {
            payload.put("rows", diagnosticRunRows(connection, Math.max(1, Math.min(limit, 100))));
            return payload;
        }
    }

    public SyncRunResult runSync(Properties config, String apiKey, String mode) throws Exception {
        String normalizedMode = normalizeMode(mode);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("GoAffPro API-Key fehlt.");
        }
        Path dataDir = resolveDataDir(config);
        Path fileDir = dataDir.resolve("goaffpro_files");
        Files.createDirectories(fileDir);
        if (dataDir.toFile().getUsableSpace() < minFreeBytes(config)) {
            throw new IOException("Nicht genug freier Speicherplatz für GoAffPro Sync unter " + dataDir);
        }
        Path db = resolveDbPath(config);
        initDatabase(db);

        SyncRunResult run = new SyncRunResult(normalizedMode);
        currentRun = run.toStatusMap();
        try (Connection connection = connect(db)) {
            markInvalidEmptyEntities(connection);
            long runId = insertRun(connection, normalizedMode);
            run.runId = runId;
            RateBudget budget = newRateBudget(config);
            currentBudget = budget;
            run.budget = budget;
            List<EndpointSpec> endpoints = endpointSpecs(normalizedMode, config);
            run.callLimitPerHour = callLimitPerHour(config);
            run.endpointCount = endpoints.size();
            run.phase = "preflight";
            currentRun = run.toStatusMap();
            preflightEstimate(connection, endpoints, apiKey, config, budget, run, normalizedMode);
            run.phase = "endpoints";
            currentRun = run.toStatusMap();
            String runStartedAt = run.startedAt;
            for (EndpointSpec endpoint : endpoints) {
                if (!isSyncEnabled(config)) {
                    run.warning("Sync wurde pausiert.");
                    run.markPendingEndpointsSkipped();
                    break;
                }
                run.endpointIndex++;
                EndpointResult endpointResult = syncEndpoint(connection, endpoint, apiKey, normalizedMode, config, budget, runStartedAt, run);
                run.add(endpointResult);
                run.clearCurrentEndpoint();
                currentRun = run.toStatusMap();
            }
            if (assetDownloadEnabled(config) && (!"delta".equals(normalizedMode) || deltaDownloadsEnabled(config))) {
                run.phase = "downloads";
                currentRun = run.toStatusMap();
                EndpointResult downloaded = downloadLinkedFiles(connection, apiKey, config, budget, normalizedMode);
                run.add(downloaded);
                currentRun = run.toStatusMap();
            }
            run.phase = "finished";
            run.status = run.warnings.isEmpty() ? "success" : "warning";
            run.finishedAt = Instant.now().toString();
            updateRun(connection, runId, run);
            return run;
        } catch (Throwable t) {
            // Auch Error abfangen, damit der Lauf in der DB nicht auf "running" stehen bleibt.
            run.status = "error";
            run.error = describeThrowable(t);
            run.phase = "finished";
            run.finishedAt = Instant.now().toString();
            try (Connection connection = connect(db)) {
                if (run.runId > 0) updateRun(connection, run.runId, run);
            } catch (Exception writeFailure) {
                System.err.println("GoAffPro Sync: Lauf-Status konnte nicht geschrieben werden: "
                        + describeThrowable(writeFailure));
            }
            if (t instanceof Exception ex) throw ex;
            if (t instanceof Error err) throw err;
            throw new IOException(describeThrowable(t), t);
        } finally {
            currentRun = run.toStatusMap();
        }
    }

    /**
     * Baut die Plan-Zeilen für alle Endpoints und schätzt Datensatz- und Call-Mengen.
     * Stufen: Historie (sync_endpoint_stats) und lokaler Bestand kosten 0 Calls; nur wenn
     * beides fehlt (typisch: allererster Initialsync), fragen streng budgetierte
     * limit=1-Probes die API — mit Abbruch, sobald der erste Probe kein Total-Feld liefert.
     * Delta-Läufe erhalten bewusst keine Mengen-Schätzung: sie holen nur Neues seit dem
     * Lookback-Datum, historische Gesamtzahlen wären dafür irreführend.
     */
    private void preflightEstimate(Connection connection, List<EndpointSpec> endpoints, String apiKey,
                                   Properties config, RateBudget budget, SyncRunResult run, String mode) {
        boolean fullMode = "initial".equals(mode) || "deep".equals(mode);
        Map<String, Integer> historySeen = new HashMap<>();
        Map<String, Integer> inventoryCounts = new HashMap<>();
        if (fullMode) {
            try {
                historySeen = lastCompleteSeenByEndpoint(connection);
                inventoryCounts = activeEntityCountsByType(connection);
            } catch (Exception e) {
                run.warning("Pre-Flight: lokale Schätzquellen nicht lesbar: " + e.getMessage());
            }
        }
        boolean probeEnabled = fullMode && boolSetting(config, "goaffproSyncPreflightProbe", true);
        int maxProbeCalls = Math.max(0, intSetting(config, "goaffproSyncPreflightMaxProbeCalls", 6));
        int affiliateDetailLimit = Math.max(1, intSetting(config, "goaffproSyncAffiliateDetailLimit", 0));
        int probeCalls = 0;
        boolean probeUseless = false;
        run.plannedEndpoints.clear();
        for (EndpointSpec endpoint : endpoints) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("endpointKey", endpoint.key);
            row.put("displayName", endpoint.displayName);
            row.put("entityType", endpoint.entityType);
            Integer expectedTotal = null;
            String source = "unknown";
            if (endpoint.paginationMode == PaginationMode.SKIP_WITH_WARNING) {
                expectedTotal = 0;
                source = "skip";
            } else if (fullMode) {
                Integer history = historySeen.get(endpoint.key);
                Integer inventoryCount = inventoryCounts.get(endpoint.entityType);
                if (history != null && history > 0) {
                    expectedTotal = history;
                    source = "history";
                } else if (inventoryCount != null && inventoryCount > 0) {
                    expectedTotal = inventoryCount;
                    source = "inventory";
                } else if (probeEnabled && endpoint.paginated && !probeUseless && probeCalls < maxProbeCalls) {
                    probeCalls++;
                    String probePath = withPagination(
                            removeQueryParams(pathForMode(endpoint, mode, config), Set.of("limit", "page", "offset")), 1, 1);
                    EndpointResult probeResult = new EndpointResult(endpoint.key, endpoint.entityType, endpoint.displayName, probePath);
                    try {
                        JsonNode root = requestJson(probePath, apiKey, budget, probeResult, apiBase(config));
                        List<JsonNode> items = extractItems(root, endpoint);
                        Integer total = extractTotalCount(root, items.size());
                        if (total != null) {
                            expectedTotal = total;
                            source = "probe";
                        } else {
                            // API liefert endpoint-übergreifend einheitlich (k)ein Total —
                            // weitere Probes wären verschwendetes Budget
                            probeUseless = true;
                        }
                    } catch (Exception e) {
                        run.warning("Pre-Flight-Probe für " + endpoint.displayName + " fehlgeschlagen: " + e.getMessage());
                        probeUseless = true;
                    } finally {
                        run.apiCalls += probeResult.apiCalls;
                    }
                }
            }
            row.put("expectedTotal", expectedTotal);
            row.put("estimateSource", source);
            int extraCalls = 0;
            if (fullMode && endpoint.fetchAffiliateRelated && expectedTotal != null) {
                // commissions, coupons, referral_codes, tags + mlm/parents = 5 Calls je Affiliate
                extraCalls = Math.min(expectedTotal, affiliateDetailLimit) * 5;
            }
            row.put("extraCalls", extraCalls);
            if (fullMode || endpoint.paginationMode == PaginationMode.SKIP_WITH_WARNING) {
                row.put("expectedCalls", estimateEndpointCalls(endpoint, expectedTotal) + extraCalls);
            } else {
                row.put("expectedCalls", null);
            }
            row.put("state", "pending");
            row.put("seen", 0);
            row.put("apiCalls", 0);
            run.plannedEndpoints.add(row);
        }
        run.recomputeEstimates();
    }

    public Map<String, Object> status(Properties config) throws Exception {
        Path db = resolveDbPath(config);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", isSyncEnabled(config));
        payload.put("running", running.get());
        payload.put("dataDir", resolveDataDir(config).toString());
        payload.put("dbPath", db.toString());
        payload.put("fileDir", resolveDataDir(config).resolve("goaffpro_files").toString());
        payload.put("hourlyEnabled", hourlyEnabled(config));
        payload.put("deepEnabled", deepEnabled(config));
        payload.put("assetDownloadEnabled", assetDownloadEnabled(config));
        payload.put("callLimitPerHour", callLimitPerHour(config));
        payload.put("deltaLookbackDays", deltaLookbackDays(config));
        payload.put("minFreeBytes", minFreeBytes(config));
        payload.put("usableBytes", resolveDataDir(config).toFile().getUsableSpace());
        payload.put("currentRun", currentRun);
        Map<String, Object> budgetInfo = new LinkedHashMap<>();
        RateBudget budget = currentBudget;
        budgetInfo.put("callLimitPerHour", callLimitPerHour(config));
        budgetInfo.put("callsUsedLastHour", budget == null ? 0 : budget.callsInLastHour());
        budgetInfo.put("waitingForBudget", budget != null && budget.isWaiting());
        budgetInfo.put("nextCallInSeconds", budget == null ? 0L : budget.secondsUntilNextCall());
        payload.put("budget", budgetInfo);
        if (!Files.exists(db)) {
            payload.put("state", isSyncEnabled(config) ? "Noch nicht synchronisiert" : "Pausiert");
            payload.put("lastSuccessAt", "");
            payload.put("entityCount", 0);
            payload.put("remoteMissingCount", 0);
            payload.put("invalidEmptyCount", 0);
            payload.put("fileBytes", directorySize(resolveDataDir(config).resolve("goaffpro_files")));
            payload.put("inventory", List.of());
            payload.put("warnings", List.of("Noch kein lokaler GoAffPro Syncbestand vorhanden."));
            return payload;
        }
        initDatabase(db);
        try (Connection connection = connect(db)) {
            markInvalidEmptyEntitiesQuietly(connection);
            if (!running.get()) repairOrphanRuns(connection);
            Map<String, Object> last = lastRun(connection, null);
            Map<String, Object> lastSuccess = lastRun(connection, "success");
            if (lastSuccess.isEmpty()) lastSuccess = lastRun(connection, "warning");
            long entityCount = scalarLong(connection, "SELECT COUNT(*) FROM sync_entities WHERE state='active'");
            long remoteMissingCount = scalarLong(connection, "SELECT COUNT(*) FROM sync_entities WHERE state='remote_missing'");
            long invalidEmptyCount = scalarLong(connection, "SELECT COUNT(*) FROM sync_entities WHERE state='invalid_empty'");
            payload.put("lastRun", last);
            payload.put("lastSuccessAt", Objects.toString(lastSuccess.getOrDefault("finishedAt", ""), ""));
            payload.put("entityCount", entityCount);
            payload.put("remoteMissingCount", remoteMissingCount);
            payload.put("invalidEmptyCount", invalidEmptyCount);
            payload.put("fileBytes", directorySize(resolveDataDir(config).resolve("goaffpro_files")));
            payload.put("inventory", inventoryRows(connection));
            payload.put("recentRuns", runRows(connection, 8));
            payload.put("warnings", syncWarnings(connection));
            Map<String, Object> stateRun = running.get() && currentRun != null ? currentRun : last;
            payload.put("state", computeState(config, stateRun, lastSuccess));
            return payload;
        }
    }

    public Map<String, Object> inventory(Properties config) throws Exception {
        Path db = resolveDbPath(config);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dbPath", db.toString());
        payload.put("dataDir", resolveDataDir(config).toString());
        payload.put("fileBytes", directorySize(resolveDataDir(config).resolve("goaffpro_files")));
        if (!Files.exists(db)) {
            payload.put("rows", List.of());
            return payload;
        }
        initDatabase(db);
        try (Connection connection = connect(db)) {
            markInvalidEmptyEntitiesQuietly(connection);
            payload.put("rows", inventoryRows(connection));
            payload.put("endpointRows", endpointRows(connection));
            return payload;
        }
    }

    public Map<String, Object> runs(Properties config, int limit) throws Exception {
        Path db = resolveDbPath(config);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dbPath", db.toString());
        if (!Files.exists(db)) {
            payload.put("rows", List.of());
            return payload;
        }
        initDatabase(db);
        try (Connection connection = connect(db)) {
            payload.put("rows", runRows(connection, Math.max(1, Math.min(limit, 100))));
            return payload;
        }
    }

    public void setEnabled(Properties config, boolean enabled) {
        config.setProperty("goaffproSyncEnabled", String.valueOf(enabled));
    }

    public boolean shouldRunHourly(Properties config) {
        if (!isSyncEnabled(config) || !hourlyEnabled(config) || running.get()) return false;
        try {
            Map<String, Object> last = lastSuccessfulModeRun(config, "delta");
            String finishedAt = Objects.toString(last.get("finishedAt"), "");
            if (finishedAt.isBlank()) return true;
            return Duration.between(Instant.parse(finishedAt), Instant.now()).toMinutes() >= 60;
        } catch (Exception e) {
            return true;
        }
    }

    public boolean shouldRunNightly(Properties config) {
        if (!isSyncEnabled(config) || !deepEnabled(config) || running.get()) return false;
        ZonedDateTime now = ZonedDateTime.now(BERLIN);
        if (now.getHour() != 2 || now.getMinute() > 10) return false;
        try {
            Map<String, Object> last = lastSuccessfulModeRun(config, "deep");
            String finishedAt = Objects.toString(last.get("finishedAt"), "");
            if (finishedAt.isBlank()) return true;
            LocalDate lastDay = Instant.parse(finishedAt).atZone(BERLIN).toLocalDate();
            return lastDay.isBefore(now.toLocalDate());
        } catch (Exception e) {
            return true;
        }
    }

    public boolean hasEntityData(Properties config, String entityType) {
        try {
            Path db = resolveDbPath(config);
            if (!Files.exists(db)) return false;
            initDatabase(db);
            try (Connection connection = connect(db)) {
                markInvalidEmptyEntitiesQuietly(connection);
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT COUNT(*) FROM sync_entities WHERE entity_type=? AND state='active' AND TRIM(raw_json) <> '{}'")) {
                    ps.setString(1, entityType);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() && rs.getLong(1) > 0;
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

    public JsonNode rootFromStore(Properties config, String entityType, String arrayField) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode array = MAPPER.createArrayNode();
        try {
            for (JsonNode node : entities(config, entityType)) {
                array.add(node);
            }
        } catch (Exception ignored) {
        }
        root.set(arrayField, array);
        return root;
    }

    public List<JsonNode> entities(Properties config, String entityType) throws Exception {
        Path db = resolveDbPath(config);
        if (!Files.exists(db)) return List.of();
        initDatabase(db);
        List<JsonNode> rows = new ArrayList<>();
        try (Connection connection = connect(db)) {
            markInvalidEmptyEntitiesQuietly(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    // TRIM-Filter zusätzlich zur Markierung: so liefert die Abfrage auch dann keine
                    // leeren Objekte, wenn die Bereinigung wegen eines laufenden Syncs übersprungen wurde.
                    "SELECT raw_json FROM sync_entities WHERE entity_type=? AND state='active' AND TRIM(raw_json) <> '{}' ORDER BY external_id")) {
                ps.setString(1, entityType);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(MAPPER.readTree(rs.getString(1)));
                    }
                }
            }
        }
        return rows;
    }

    public Map<String, JsonNode> entityMap(Properties config, String entityType) throws Exception {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        for (JsonNode node : entities(config, entityType)) {
            String id = extractStableId(node, entityType);
            if (!id.isBlank()) map.put(id, node);
        }
        return map;
    }

    public Map<String, Object> dataSourceInfo(Properties config, String entityType) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("source", hasEntityData(config, entityType) ? "local-sync" : "live-api");
        try {
            Map<String, Object> s = status(config);
            info.put("syncState", s.get("state"));
            info.put("lastSyncAt", s.get("lastSuccessAt"));
        } catch (Exception ignored) {
        }
        return info;
    }

    private List<DiagnosticResult> diagnoseEndpoint(EndpointSpec endpoint, String apiKey, Properties config,
                                                    RateBudget budget, DiagnosticRunResult run) {
        List<DiagnosticResult> results = new ArrayList<>();
        Map<String, Set<String>> seenByFamily = new HashMap<>();
        boolean retriedGatewayTimeout = false;
        for (DiagnosticVariant variant : diagnosticVariants(endpoint)) {
            DiagnosticResult result = executeDiagnosticVariant(endpoint, variant, apiKey, config, budget, seenByFamily);
            results.add(result);
            run.apiCalls += result.apiCalls;
            if (!result.warning.isBlank()) run.warning(endpoint.displayName + "/" + variant.name + ": " + result.warning);
            currentDiagnosticRun = run.toStatusMap();
            if (result.httpCode == 504 && !retriedGatewayTimeout && !"small-limit-25".equals(variant.name)) {
                retriedGatewayTimeout = true;
                DiagnosticVariant retry = new DiagnosticVariant("retry-limit-25-after-504", diagnosticSmallLimitPath(endpoint), "small");
                DiagnosticResult retryResult = executeDiagnosticVariant(endpoint, retry, apiKey, config, budget, seenByFamily);
                results.add(retryResult);
                run.apiCalls += retryResult.apiCalls;
                if (!retryResult.warning.isBlank()) run.warning(endpoint.displayName + "/" + retry.name + ": " + retryResult.warning);
                currentDiagnosticRun = run.toStatusMap();
            }
        }
        return results;
    }

    private DiagnosticResult executeDiagnosticVariant(EndpointSpec endpoint, DiagnosticVariant variant, String apiKey,
                                                      Properties config, RateBudget budget, Map<String, Set<String>> seenByFamily) {
        DiagnosticResult result = new DiagnosticResult(endpoint, variant);
        EndpointResult requestCounter = new EndpointResult("diagnostic_" + endpoint.key, endpoint.entityType, endpoint.displayName, variant.path);
        String apiBase = apiBase(config);
        long started = System.nanoTime();
        try {
            HttpResponse response = request("GET", variant.path, apiKey, null, budget, requestCounter, 1, apiBase);
            result.apiCalls = requestCounter.apiCalls;
            result.httpCode = response.code;
            result.durationMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            result.responseBytes = response.body == null ? 0 : response.body.getBytes(StandardCharsets.UTF_8).length;
            result.rawExcerpt = trim(response.body, DIAGNOSTIC_RAW_EXCERPT_LIMIT);
            if (response.code < 200 || response.code >= 300) {
                result.error = trim(response.body, 1000);
                result.warning = "HTTP " + response.code;
                return result;
            }
            JsonNode root = MAPPER.readTree(response.body);
            result.arrayField = detectArrayField(root, endpoint);
            List<JsonNode> rawItems = extractItems(root, endpoint);
            result.itemCount = rawItems.size();
            List<String> ids = new ArrayList<>();
            Set<String> keys = new LinkedHashSet<>();
            int empty = 0;
            for (JsonNode item : rawItems) {
                if (isInvalidEmptyEntityItem(item)) {
                    empty++;
                } else if (item != null && item.isObject()) {
                    item.fieldNames().forEachRemaining(keys::add);
                }
                String id = extractStableId(item, endpoint.entityType);
                if (id.isBlank()) id = sha256Hex(canonicalJson(item));
                if (!id.isBlank() && ids.size() < 5) ids.add(id);
            }
            result.emptyItemCount = empty;
            result.stableIds = ids;
            result.sampleKeys = keys.stream().limit(20).toList();
            result.contentSignature = sha256Hex(ids.isEmpty()
                    ? rawItems.stream().map(GoAffProSyncService::canonicalJson).collect(Collectors.joining("\n"))
                    : String.join("\n", ids));
            result.fieldsEffective = result.itemCount == 0 || result.emptyItemCount < result.itemCount;
            Set<String> familySeen = seenByFamily.computeIfAbsent(variant.family, ignored -> new LinkedHashSet<>());
            int newIds = 0;
            for (String id : ids) {
                if (familySeen.add(id)) newIds++;
            }
            result.repeated = !ids.isEmpty() && newIds == 0;
            if (result.emptyItemCount > 0 && result.emptyItemCount == result.itemCount) {
                result.warning = "Alle Items sind leere Objekte.";
            } else if (result.repeated) {
                result.warning = "Diese Variante liefert keine neuen IDs gegenüber vorherigen Seiten.";
            }
            return result;
        } catch (Exception e) {
            result.apiCalls = requestCounter.apiCalls;
            result.durationMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            result.httpCode = 0;
            result.error = trim(e.getMessage(), 1000);
            result.warning = "Request fehlgeschlagen";
            return result;
        }
    }

    private EndpointResult syncEndpoint(Connection connection, EndpointSpec endpoint, String apiKey, String mode,
                                        Properties config, RateBudget budget, String runStartedAt, SyncRunResult run) throws Exception {
        String basePath = pathForMode(endpoint, mode, config);
        EndpointResult result = new EndpointResult(endpoint.key, endpoint.entityType, endpoint.displayName, basePath);
        if (endpoint.paginationMode == PaginationMode.SKIP_WITH_WARNING) {
            result.complete = false;
            result.warning(endpoint.skipWarning.isBlank()
                    ? "Endpoint wird im normalen Sync bewusst übersprungen."
                    : endpoint.skipWarning);
            run.lastWarning = lastOf(result.warnings);
            updateEndpointStats(connection, result);
            return result;
        }
        Set<String> seenIds = new LinkedHashSet<>();
        Set<String> pageSignatures = new LinkedHashSet<>();
        int limit = endpoint.limit > 0 ? endpoint.limit : DEFAULT_LIMIT;
        boolean complete = true;
        int maxPages = Math.max(1, intSetting(config, "goaffproSyncMaxPagesPerEndpoint", 100));
        if (endpoint.paginationMode == PaginationMode.SINGLE_PAGE_PARTIAL) {
            maxPages = 1;
            complete = false;
        }
        String apiBase = apiBase(config);

        for (int page = 1; page <= maxPages; page++) {
            String path = endpoint.paginated ? withPagination(basePath, page, limit, endpoint.paginationMode) : basePath;
            run.setCurrentEndpoint(endpoint, page, path, result);
            currentRun = run.toStatusMap();
            JsonNode root;
            try {
                root = requestJson(path, apiKey, budget, result, apiBase);
            } catch (Exception e) {
                result.warning("Endpoint " + endpoint.displayName + " konnte nicht gelesen werden: " + e.getMessage());
                run.lastWarning = lastOf(result.warnings);
                complete = false;
                break;
            }
            storeSnapshot(connection, endpoint, page, root);
            List<JsonNode> rawItems = extractItems(root, endpoint);
            if (page == 1 && endpoint.paginated) {
                Integer apiTotal = extractTotalCount(root, rawItems.size());
                if (apiTotal != null) {
                    run.setApiTotal(endpoint.key, apiTotal, estimateEndpointCalls(endpoint, apiTotal));
                }
            }
            List<JsonNode> items = new ArrayList<>();
            int invalidItems = 0;
            for (JsonNode item : rawItems) {
                if (isInvalidEmptyEntityItem(item)) {
                    invalidItems++;
                } else {
                    items.add(item);
                }
            }
            if (invalidItems > 0) {
                result.warning("Endpoint " + endpoint.displayName + " lieferte " + invalidItems + " leere Objekte; vermutlich fehlt ein fields-Parameter oder GoAffPro liefert keine Detailfelder.");
                run.lastWarning = lastOf(result.warnings);
                complete = false;
                if (items.isEmpty()) break;
            }
            String signature = sha256Hex(items.stream().map(GoAffProSyncService::canonicalJson).collect(Collectors.joining("\n")));
            if (!pageSignatures.add(signature) && page > 1) {
                result.warning("Pagination bei " + endpoint.displayName + " liefert wiederholte Daten; Endpoint ggf. unvollständig.");
                run.lastWarning = lastOf(result.warnings);
                complete = false;
                break;
            }
            if (items.isEmpty() && page == 1 && endpoint.storeRootAsSingleton) {
                UpsertResult upsert = upsertEntity(connection, endpoint.entityType, "current", endpoint.path, root);
                result.add(upsert);
                seenIds.add("current");
            } else {
                int newOnPage = 0;
                for (JsonNode item : items) {
                    String id = extractStableId(item, endpoint.entityType);
                    if (id.isBlank()) id = sha256Hex(canonicalJson(item));
                    if (seenIds.add(id)) newOnPage++;
                    UpsertResult upsert = upsertEntity(connection, endpoint.entityType, id, endpoint.path, item);
                    result.add(upsert);
                }
                if (page > 1 && newOnPage == 0) {
                    result.warning("Pagination bei " + endpoint.displayName + " brachte keine neuen IDs; gestoppt.");
                    run.lastWarning = lastOf(result.warnings);
                    complete = false;
                    break;
                }
            }
            run.setCurrentEndpoint(endpoint, page, path, result);
            currentRun = run.toStatusMap();
            if (!endpoint.paginated || rawItems.size() < limit) break;
            if (rawItems.size() >= limit && page == maxPages) {
                if (endpoint.paginationMode == PaginationMode.SINGLE_PAGE_PARTIAL) {
                    result.warning(endpoint.skipWarning.isBlank()
                            ? "Nur erste Seite synchronisiert; API-Pagination liefert laut Diagnose wiederholte Daten."
                            : endpoint.skipWarning);
                } else {
                    result.warning("Maximale Seitenzahl bei " + endpoint.displayName + " erreicht; Endpoint ggf. unvollständig.");
                }
                run.lastWarning = lastOf(result.warnings);
                complete = false;
            }
        }

        if (endpoint.paginationMode == PaginationMode.SINGLE_PAGE_PARTIAL && result.warnings.isEmpty()) {
            result.warning(endpoint.skipWarning.isBlank()
                    ? "Nur erste Seite synchronisiert; API-Pagination liefert laut Diagnose wiederholte Daten."
                    : endpoint.skipWarning);
            run.lastWarning = lastOf(result.warnings);
            complete = false;
        }

        if (endpoint.fetchGroupMembers && complete) {
            result.add(syncGroupMembers(connection, apiKey, budget));
        }
        if (endpoint.fetchAffiliateRelated && complete && ("initial".equals(mode) || "deep".equals(mode))) {
            result.add(syncAffiliateRelated(connection, apiKey, config, budget));
        }
        if (endpoint.fetchAssetFolderContents && complete) {
            result.add(syncAssetFolderContents(connection, apiKey, budget));
        }

        if (complete && ("initial".equals(mode) || "deep".equals(mode))) {
            markRemoteMissing(connection, endpoint.entityType, runStartedAt);
        }
        result.complete = complete;
        updateEndpointStats(connection, result);
        return result;
    }

    private EndpointResult syncGroupMembers(Connection connection, String apiKey, RateBudget budget) throws Exception {
        EndpointResult result = new EndpointResult("groups_members", "group_members", "Gruppenmitglieder", "/v1/admin/groups/{id}/members");
        List<JsonNode> groups = entitiesFromConnection(connection, "groups");
        for (JsonNode group : groups) {
            String groupId = extractStableId(group, "groups");
            if (groupId.isBlank()) continue;
            try {
                JsonNode root = requestJson("/v1/admin/groups/" + encodePath(groupId) + "/members", apiKey, budget, result);
                storeSnapshot(connection, new EndpointSpec("groups_members", "Gruppenmitglieder", "group_members", "/v1/admin/groups/" + groupId + "/members", "members", false, false), 1, root);
                List<JsonNode> members = extractItems(root, new EndpointSpec("groups_members", "Gruppenmitglieder", "group_members", "", "members", false, false));
                if (members.isEmpty() && root.isArray()) members = jsonArray(root);
                int index = 0;
                for (JsonNode member : members) {
                    String memberId = member.isValueNode() ? member.asText("") : extractStableId(member, "group_members");
                    if (memberId.isBlank()) memberId = String.valueOf(index++);
                    ObjectNode wrapped = MAPPER.createObjectNode();
                    wrapped.put("group_id", groupId);
                    wrapped.set("member", member);
                    result.add(upsertEntity(connection, "group_members", groupId + "|" + memberId, "/v1/admin/groups/" + groupId + "/members", wrapped));
                }
            } catch (Exception e) {
                result.warning("Gruppenmitglieder für Gruppe " + groupId + " konnten nicht gelesen werden: " + e.getMessage());
            }
        }
        updateEndpointStats(connection, result);
        return result;
    }

    private EndpointResult syncAffiliateRelated(Connection connection, String apiKey, Properties config, RateBudget budget) throws Exception {
        EndpointResult result = new EndpointResult("affiliate_related", "affiliate_related", "Affiliate Detaildaten", "/v1/admin/affiliates/{id}/...");
        List<JsonNode> affiliates = entitiesFromConnection(connection, "affiliates");
        int limit = Math.max(1, intSetting(config, "goaffproSyncAffiliateDetailLimit", 0));
        int count = 0;
        for (JsonNode affiliate : affiliates) {
            if (limit > 0 && count >= limit) {
                result.warning("Affiliate Detail-Sync nach " + limit + " Affiliates gestoppt (Schutzlimit).");
                break;
            }
            String affiliateId = extractStableId(affiliate, "affiliates");
            if (affiliateId.isBlank()) continue;
            count++;
            for (String sub : List.of("commissions", "coupons", "referral_codes", "tags")) {
                String path = "/v1/admin/affiliates/" + encodePath(affiliateId) + "/" + sub;
                try {
                    JsonNode root = requestJson(path, apiKey, budget, result);
                    storeSnapshot(connection, new EndpointSpec("affiliate_" + sub, "Affiliate " + sub, "affiliate_" + sub, path, sub, false, false), 1, root);
                    List<JsonNode> items = extractItems(root, new EndpointSpec("affiliate_" + sub, "Affiliate " + sub, "affiliate_" + sub, path, sub, false, false));
                    if (items.isEmpty()) {
                        ObjectNode wrapped = MAPPER.createObjectNode();
                        wrapped.put("affiliate_id", affiliateId);
                        wrapped.set(sub, root);
                        result.add(upsertEntity(connection, "affiliate_" + sub, affiliateId, path, wrapped));
                    } else {
                        int index = 0;
                        for (JsonNode item : items) {
                            String id = extractStableId(item, "affiliate_" + sub);
                            if (id.isBlank()) id = String.valueOf(index++);
                            ObjectNode wrapped = MAPPER.createObjectNode();
                            wrapped.put("affiliate_id", affiliateId);
                            wrapped.set("item", item);
                            result.add(upsertEntity(connection, "affiliate_" + sub, affiliateId + "|" + id, path, wrapped));
                        }
                    }
                } catch (Exception e) {
                    result.warning("Affiliate-" + sub + " für " + affiliateId + " nicht lesbar: " + e.getMessage());
                }
            }
            try {
                String path = "/v1/admin/mlm/parents/" + encodePath(affiliateId);
                JsonNode root = requestJson(path, apiKey, budget, result);
                ObjectNode wrapped = MAPPER.createObjectNode();
                wrapped.put("affiliate_id", affiliateId);
                wrapped.set("parents", root);
                result.add(upsertEntity(connection, "mlm_parents", affiliateId, path, wrapped));
            } catch (Exception e) {
                result.warning("MLM-Parents für " + affiliateId + " nicht lesbar: " + e.getMessage());
            }
        }
        updateEndpointStats(connection, result);
        return result;
    }

    private EndpointResult syncAssetFolderContents(Connection connection, String apiKey, RateBudget budget) throws Exception {
        EndpointResult result = new EndpointResult("assets_folder_contents", "asset_contents", "Asset-Ordnerinhalte", "/v1/admin/assets/contents/{folderId}");
        List<JsonNode> folders = entitiesFromConnection(connection, "asset_folders");
        for (JsonNode folder : folders) {
            String folderId = extractStableId(folder, "asset_folders");
            if (folderId.isBlank()) continue;
            String path = "/v1/admin/assets/contents/" + encodePath(folderId);
            try {
                JsonNode root = requestJson(path, apiKey, budget, result);
                storeSnapshot(connection, new EndpointSpec("assets_folder_contents", "Asset-Ordnerinhalte", "asset_contents", path, "items", false, false), 1, root);
                List<JsonNode> items = extractItems(root, new EndpointSpec("assets_folder_contents", "Asset-Ordnerinhalte", "asset_contents", path, "items", false, false));
                int index = 0;
                for (JsonNode item : items) {
                    String id = extractStableId(item, "asset_contents");
                    if (id.isBlank()) id = folderId + "-" + index++;
                    ObjectNode wrapped = item.isObject() ? (ObjectNode) item.deepCopy() : MAPPER.createObjectNode().set("item", item);
                    wrapped.put("folder_id", folderId);
                    result.add(upsertEntity(connection, "asset_contents", id, path, wrapped));
                }
            } catch (Exception e) {
                result.warning("Asset-Ordner " + folderId + " nicht lesbar: " + e.getMessage());
            }
        }
        updateEndpointStats(connection, result);
        return result;
    }

    private EndpointResult downloadLinkedFiles(Connection connection, String apiKey, Properties config, RateBudget budget, String mode) throws Exception {
        EndpointResult result = new EndpointResult("file_downloads", "files", "Datei-/Asset-Downloads", "direct-url-fields");
        Path fileDir = resolveDataDir(config).resolve("goaffpro_files");
        Files.createDirectories(fileDir);
        // Deep-Check lädt alles neu (Re-Verify); sonst werden bereits vorhandene Dateien übersprungen
        boolean skipExisting = !"deep".equals(mode) && downloadSkipExistingEnabled(config);
        List<String> entityTypes = List.of("files", "asset_contents", "asset_folders", "creatives");
        for (String entityType : entityTypes) {
            for (StoredEntity entity : storedEntities(connection, entityType)) {
                String url = findDownloadUrl(entity.rawJson);
                if (url.isBlank()) continue;
                if (skipExisting && entity.filePath != null && !entity.filePath.isBlank() && Files.exists(Paths.get(entity.filePath))) {
                    result.skippedExisting++;
                    continue;
                }
                try {
                    DownloadedFile downloaded = downloadFile(url, apiKey, fileDir.resolve(entityType), entity.externalId, budget, result);
                    updateEntityFile(connection, entityType, entity.externalId, downloaded);
                    result.downloaded++;
                    result.fileBytes += downloaded.size;
                } catch (Exception e) {
                    result.warning("Datei " + entityType + "/" + entity.externalId + " konnte nicht geladen werden: " + e.getMessage());
                }
            }
        }
        updateEndpointStats(connection, result);
        return result;
    }

    private JsonNode requestJson(String path, String apiKey, RateBudget budget, EndpointResult result) throws Exception {
        return requestJson(path, apiKey, budget, result, API_BASE);
    }

    private JsonNode requestJson(String path, String apiKey, RateBudget budget, EndpointResult result, String apiBase) throws Exception {
        HttpResponse response = request("GET", path, apiKey, null, budget, result, 4, apiBase);
        if (response.code < 200 || response.code >= 300) {
            throw new IOException("GoAffPro API Fehler (" + response.code + "): " + trim(response.body, 500));
        }
        return MAPPER.readTree(response.body);
    }

    private HttpResponse request(String method, String pathOrUrl, String apiKey, String body,
                                 RateBudget budget, EndpointResult result) throws Exception {
        return request(method, pathOrUrl, apiKey, body, budget, result, 4, API_BASE);
    }

    private HttpResponse request(String method, String pathOrUrl, String apiKey, String body,
                                 RateBudget budget, EndpointResult result, int maxAttempts, String apiBase) throws Exception {
        budget.beforeCall();
        String normalizedApiBase = Objects.toString(apiBase, API_BASE).trim();
        if (normalizedApiBase.isBlank()) normalizedApiBase = API_BASE;
        String urlText = pathOrUrl.startsWith("http") ? pathOrUrl : normalizedApiBase + pathOrUrl;
        int attempts = 0;
        while (true) {
            attempts++;
            HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            if (urlText.startsWith(normalizedApiBase)) {
                connection.setRequestProperty("x-goaffpro-access-token", apiKey);
            }
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String responseBody = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            result.apiCalls++;
            if ((code == 429 || code >= 500) && attempts < maxAttempts) {
                long waitMs = retryAfterMillis(connection.getHeaderField("Retry-After"), attempts);
                Thread.sleep(waitMs);
                continue;
            }
            return new HttpResponse(code, responseBody);
        }
    }

    private DownloadedFile downloadFile(String url, String apiKey, Path targetDir, String externalId,
                                        RateBudget budget, EndpointResult result) throws Exception {
        budget.beforeCall();
        Files.createDirectories(targetDir);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(120000);
        if (url.startsWith(API_BASE)) {
            connection.setRequestProperty("x-goaffpro-access-token", apiKey);
        }
        int code = connection.getResponseCode();
        result.apiCalls++;
        if (code < 200 || code >= 300) {
            InputStream err = connection.getErrorStream();
            String body = err == null ? "" : new String(err.readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("Download HTTP " + code + ": " + trim(body, 300));
        }
        byte[] bytes;
        try (InputStream is = connection.getInputStream()) {
            bytes = is.readAllBytes();
        }
        String hash = sha256Hex(bytes);
        String extension = extensionFromUrl(url);
        Path file = targetDir.resolve(safeFileName(externalId + "-" + hash.substring(0, 12) + extension));
        Files.write(file, bytes);
        return new DownloadedFile(file.toString(), hash, bytes.length);
    }

    private static List<EndpointSpec> endpointSpecs(String mode, Properties config) {
        boolean full = "initial".equals(mode) || "deep".equals(mode);
        List<EndpointSpec> specs = new ArrayList<>();
        specs.add(new EndpointSpec("affiliates", "Affiliates", "affiliates", withFields("/v1/admin/affiliates?limit=500", AFFILIATE_FIELDS), "affiliates", true, false).pagination(PaginationMode.OFFSET_ONLY).affiliateRelated(full));
        specs.add(new EndpointSpec("orders", "Orders", "orders", withFields("/v1/admin/orders?limit=500", ORDER_FIELDS), "orders", true, true));
        specs.add(new EndpointSpec("orders_system", "System-Orders", "orders_system", withFields("/v1/admin/orders/system?limit=500", ORDER_FIELDS), "orders", true, true));
        specs.add(new EndpointSpec("rewards", "Rewards", "rewards", withFields("/v1/admin/rewards?limit=500", REWARD_FIELDS), "rewards", true, true));
        specs.add(new EndpointSpec("payments", "Payments", "payments", withFields("/v1/admin/payments?limit=500", PAYMENT_FIELDS), "payments", true, true).pagination(PaginationMode.OFFSET_ONLY));
        specs.add(new EndpointSpec("payment_requests", "Payment Requests", "payment_requests", withFields("/v1/admin/payments/requests?limit=500", PAYMENT_FIELDS), "payment_requests", true, true));
        specs.add(new EndpointSpec("payments_pending", "Pending Payments", "payments_pending", withFields("/v1/admin/payments/pending?limit=500", PAYMENT_FIELDS), "pending", true, false));
        specs.add(new EndpointSpec("payment_sessions", "Payment Sessions", "payment_sessions", withFields("/v1/admin/payments/sessions?limit=500", PAYMENT_FIELDS), "sessions", true, true));
        specs.add(new EndpointSpec("unpaid_transactions", "Unpaid Transactions", "unpaid_transactions", withFields("/v1/admin/payments/transactions/unpaid?limit=500", TRANSACTION_FIELDS), "transactions", true, true));
        specs.add(new EndpointSpec("transactions", "Transactions", "transactions", withFields("/v1/admin/transactions?limit=500", TRANSACTION_FIELDS), "transactions", true, true));
        specs.add(new EndpointSpec("traffic", "Traffic", "traffic", withFields("/v1/admin/traffic?limit=500", TRAFFIC_FIELDS), "traffic", true, true)
                .singlePagePartial("Nur erste Seite synchronisiert; API-Pagination wiederholt laut Diagnose Daten."));
        specs.add(new EndpointSpec("showcases", "Showcases / Partys", "showcases", withFields("/v1/admin/showcases?limit=500", SHOWCASE_FIELDS), "showcases", true, true));
        specs.add(new EndpointSpec("mlm_tree", "MLM Baum", "mlm_tree", "/v1/admin/mlm/tree", "tree", false, false).singleton());
        specs.add(new EndpointSpec("connections", "Connections", "connections", withFields("/v1/admin/connections?limit=500", CONNECTION_FIELDS), "connections", true, false)
                .singlePagePartial("Nur erste Seite synchronisiert; API-Pagination wiederholt laut Diagnose Daten."));
        specs.add(new EndpointSpec("commissions", "Commissions", "commissions", "/v1/admin/commissions?limit=500", "commissions", true, false));
        specs.add(new EndpointSpec("commission_collections", "Commission Collections", "commission_collections", "/v1/admin/commissions/collections?limit=500", "collections", true, false));
        specs.add(new EndpointSpec("commission_products", "Commission Products", "commission_products", "/v1/admin/commissions/products?limit=250", "products", true, false, 250));
        specs.add(new EndpointSpec("creatives", "Creatives", "creatives", "/v1/admin/creatives?limit=500", "creatives", true, false)
                .skip("Endpoint ist deprecated; soweit verfügbar werden Assets über /admin/assets synchronisiert."));
        specs.add(new EndpointSpec("files", "Affiliate Files", "files", withFields("/v1/admin/files?limit=500", FILE_FIELDS), "files", true, true));
        specs.add(new EndpointSpec("groups", "Groups", "groups", "/v1/admin/groups?limit=500", "groups", true, false)
                .skip("Endpoint liefert in der Diagnose HTTP 504; für erneute Prüfung Diagnosemodus nutzen."));
        specs.add(new EndpointSpec("coupons", "Coupons", "coupons", "/v1/admin/coupons?limit=500", "coupons", true, false));
        specs.add(new EndpointSpec("webhooks", "Webhooks", "webhooks", "/v1/admin/webhooks?limit=500", "webhooks", true, false));
        specs.add(new EndpointSpec("store_config", "Store Config", "store_config", "/v1/admin/store/config", "config", false, false).singleton());
        specs.add(new EndpointSpec("store_logs", "Store Logs", "store_logs", "/v1/admin/store/logs?limit=500", "logs", true, true)
                .skip("Endpoint liefert in der Diagnose HTTP 504; für erneute Prüfung Diagnosemodus nutzen."));
        specs.add(new EndpointSpec("assets_folders", "Asset Folders", "asset_folders", "/v1/admin/assets/folders", "folders", false, false).assetFolderContents(true));
        specs.add(new EndpointSpec("assets_contents_root", "Asset Root Contents", "asset_contents", "/v1/admin/assets/contents?limit=500", "items", true, false));
        return specs;
    }

    private static String pathForMode(EndpointSpec endpoint, String mode, Properties config) {
        if (!"delta".equals(mode) || !endpoint.supportsCreatedFilter) return endpoint.path;
        LocalDate from = LocalDate.now(BERLIN).minusDays(deltaLookbackDays(config));
        return appendQuery(endpoint.path, "created_at_min=" + from + "T00:00:00.000Z");
    }

    private static String withPagination(String path, int page, int limit) {
        return withPagination(path, page, limit, PaginationMode.PAGE_AND_OFFSET);
    }

    private static String withPagination(String path, int page, int limit, PaginationMode mode) {
        String withLimit = hasQueryParam(path, "limit") ? path : appendQuery(path, "limit=" + limit);
        if (page <= 1) return withLimit;
        int offset = (page - 1) * limit;
        if (mode == PaginationMode.OFFSET_ONLY) {
            return appendQuery(withLimit, "offset=" + offset);
        }
        return appendQuery(withLimit, "page=" + page + "&offset=" + ((page - 1) * limit));
    }

    private static String withFields(String path, String fields) {
        if (fields == null || fields.isBlank() || hasQueryParam(path, "fields")) return path;
        return appendQuery(path, "fields=" + fields);
    }

    private static List<DiagnosticVariant> diagnosticVariants(EndpointSpec endpoint) {
        String base = endpoint.path;
        String baseNoPaging = removeQueryParams(base, Set.of("limit", "page", "offset"));
        String limit100 = appendQuery(baseNoPaging, "limit=100");
        List<DiagnosticVariant> variants = new ArrayList<>();
        variants.add(new DiagnosticVariant("base-current", base, "base"));
        variants.add(new DiagnosticVariant("page-1", appendQuery(limit100, "page=1"), "page"));
        variants.add(new DiagnosticVariant("page-2", appendQuery(limit100, "page=2"), "page"));
        variants.add(new DiagnosticVariant("page-3", appendQuery(limit100, "page=3"), "page"));
        variants.add(new DiagnosticVariant("offset-0", appendQuery(limit100, "offset=0"), "offset"));
        variants.add(new DiagnosticVariant("offset-100", appendQuery(limit100, "offset=100"), "offset"));
        variants.add(new DiagnosticVariant("offset-200", appendQuery(limit100, "offset=200"), "offset"));
        variants.add(new DiagnosticVariant("small-limit-25", diagnosticSmallLimitPath(endpoint), "small"));
        return variants;
    }

    private static String diagnosticSmallLimitPath(EndpointSpec endpoint) {
        return appendQuery(removeQueryParams(endpoint.path, Set.of("limit", "page", "offset")), "limit=25");
    }

    private static String removeQueryParams(String path, Set<String> removeKeys) {
        int question = path.indexOf('?');
        if (question < 0) return path;
        String base = path.substring(0, question);
        String query = path.substring(question + 1);
        List<String> kept = new ArrayList<>();
        for (String part : query.split("&")) {
            if (part.isBlank()) continue;
            String key = part;
            int equals = key.indexOf('=');
            if (equals >= 0) key = key.substring(0, equals);
            if (!removeKeys.contains(key)) kept.add(part);
        }
        return kept.isEmpty() ? base : base + "?" + String.join("&", kept);
    }

    private static String detectArrayField(JsonNode root, EndpointSpec endpoint) {
        if (root == null || root.isNull() || root.isMissingNode()) return "";
        if (root.isArray()) return "$";
        JsonNode explicit = root.get(endpoint.arrayField);
        if (explicit != null && explicit.isArray()) return endpoint.arrayField;
        for (String key : List.of("data", "items", "results", "rows", "affiliates", "orders", "payments", "rewards", "traffic", "showcases", "files", "groups", "coupons", "webhooks", "transactions", "logs")) {
            JsonNode found = root.get(key);
            if (found != null && found.isArray()) return key;
        }
        return "";
    }

    private static List<JsonNode> extractItems(JsonNode root, EndpointSpec endpoint) {
        if (root == null || root.isNull() || root.isMissingNode()) return List.of();
        if (root.isArray()) return jsonArray(root);
        JsonNode explicit = root.get(endpoint.arrayField);
        if (explicit != null && explicit.isArray()) return jsonArray(explicit);
        for (String key : List.of("data", "items", "results", "rows", "affiliates", "orders", "payments", "rewards", "traffic", "showcases", "files", "groups", "coupons", "webhooks", "transactions", "logs")) {
            JsonNode found = root.get(key);
            if (found != null && found.isArray()) return jsonArray(found);
        }
        return List.of();
    }

    private static List<JsonNode> jsonArray(JsonNode array) {
        List<JsonNode> list = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode item : array) list.add(item);
        }
        return list;
    }

    static Integer extractTotalCount(JsonNode root, int itemCountOnPage) {
        Integer direct = totalFieldValue(root, itemCountOnPage);
        if (direct != null) return direct;
        if (root != null && root.isObject()) {
            return totalFieldValue(root.get("meta"), itemCountOnPage);
        }
        return null;
    }

    private static Integer totalFieldValue(JsonNode node, int itemCountOnPage) {
        if (node == null || !node.isObject()) return null;
        for (String key : List.of("total", "total_count", "totalCount", "total_results", "count")) {
            JsonNode value = node.get(key);
            if (value == null || !value.isIntegralNumber()) continue;
            long total = value.asLong();
            if (total < itemCountOnPage || total > Integer.MAX_VALUE) continue;
            // "count" ist oft die Item-Anzahl der aktuellen Seite, kein Gesamtwert
            if ("count".equals(key) && itemCountOnPage > 0 && total == itemCountOnPage) continue;
            return (int) total;
        }
        return null;
    }

    static int estimateEndpointCalls(EndpointSpec spec, Integer expectedTotal) {
        if (spec.paginationMode == PaginationMode.SKIP_WITH_WARNING) return 0;
        if (!spec.paginated || spec.paginationMode == PaginationMode.SINGLE_PAGE_PARTIAL) return 1;
        if (expectedTotal == null || expectedTotal < 0) return 1;
        int limit = spec.limit > 0 ? spec.limit : DEFAULT_LIMIT;
        return Math.max(1, (int) Math.ceil(expectedTotal / (double) limit));
    }

    private static Map<String, Integer> lastCompleteSeenByEndpoint(Connection connection) throws Exception {
        Map<String, Integer> result = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT endpoint_key, last_seen FROM sync_endpoint_stats WHERE complete=1 AND last_seen > 0");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString(1), rs.getInt(2));
            }
        }
        return result;
    }

    private static Map<String, Integer> activeEntityCountsByType(Connection connection) throws Exception {
        Map<String, Integer> result = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT entity_type, COUNT(*) FROM sync_entities WHERE state='active' GROUP BY entity_type");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString(1), rs.getInt(2));
            }
        }
        return result;
    }

    private static boolean isInvalidEmptyEntityItem(JsonNode item) {
        return item != null && item.isObject() && !item.fieldNames().hasNext();
    }

    private static String lastOf(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        return Objects.toString(values.get(values.size() - 1), "");
    }

    private static UpsertResult upsertEntity(Connection connection, String entityType, String externalId,
                                             String apiPath, JsonNode rawJson) throws Exception {
        String now = Instant.now().toString();
        String canonical = canonicalJson(rawJson);
        String hash = sha256Hex(canonical);
        String oldHash = null;
        try (PreparedStatement ps = connection.prepareStatement("SELECT content_hash FROM sync_entities WHERE entity_type=? AND external_id=?")) {
            ps.setString(1, entityType);
            ps.setString(2, externalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) oldHash = rs.getString(1);
            }
        }
        String state = "active";
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO sync_entities(entity_type, external_id, api_path, content_hash, raw_json, remote_created_at, remote_updated_at, first_seen_at, last_seen_at, last_changed_at, state)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(entity_type, external_id) DO UPDATE SET
                  api_path=excluded.api_path,
                  raw_json=excluded.raw_json,
                  remote_created_at=excluded.remote_created_at,
                  remote_updated_at=excluded.remote_updated_at,
                  last_seen_at=excluded.last_seen_at,
                  last_changed_at=CASE WHEN sync_entities.content_hash <> excluded.content_hash THEN excluded.last_changed_at ELSE sync_entities.last_changed_at END,
                  content_hash=excluded.content_hash,
                  state='active'
                """)) {
            ps.setString(1, entityType);
            ps.setString(2, externalId);
            ps.setString(3, apiPath);
            ps.setString(4, hash);
            ps.setString(5, canonical);
            ps.setString(6, firstNonBlank(text(rawJson, "created_at"), text(rawJson, "createdAt")));
            ps.setString(7, firstNonBlank(text(rawJson, "updated_at"), text(rawJson, "updatedAt"), text(rawJson, "modified_at")));
            ps.setString(8, now);
            ps.setString(9, now);
            ps.setString(10, now);
            ps.setString(11, state);
            ps.executeUpdate();
        }
        if (oldHash == null) return UpsertResult.inserted();
        if (!oldHash.equals(hash)) return UpsertResult.updated();
        return UpsertResult.unchanged();
    }

    private static void storeSnapshot(Connection connection, EndpointSpec endpoint, int page, JsonNode rawJson) throws Exception {
        String key = endpoint.key + "#page-" + page;
        String now = Instant.now().toString();
        String raw = canonicalJson(rawJson);
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO sync_snapshots(snapshot_key, endpoint_key, api_path, content_hash, raw_json, last_seen_at)
                VALUES(?,?,?,?,?,?)
                ON CONFLICT(snapshot_key) DO UPDATE SET endpoint_key=excluded.endpoint_key, api_path=excluded.api_path, content_hash=excluded.content_hash, raw_json=excluded.raw_json, last_seen_at=excluded.last_seen_at
                """)) {
            ps.setString(1, key);
            ps.setString(2, endpoint.key);
            ps.setString(3, endpoint.path);
            ps.setString(4, sha256Hex(raw));
            ps.setString(5, raw);
            ps.setString(6, now);
            ps.executeUpdate();
        }
    }

    private static void markRemoteMissing(Connection connection, String entityType, String runStartedAt) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE sync_entities
                SET state='remote_missing'
                WHERE entity_type=? AND last_seen_at < ? AND state='active'
                """)) {
            ps.setString(1, entityType);
            ps.setString(2, runStartedAt);
            ps.executeUpdate();
        }
    }

    private static void markInvalidEmptyEntities(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE sync_entities
                SET state='invalid_empty'
                WHERE state='active' AND TRIM(raw_json)='{}'
                """)) {
            ps.executeUpdate();
        }
    }

    static final String ORPHAN_RESTART_MESSAGE = "Abgebrochen (Anwendung neu gestartet)";
    static final String ORPHAN_CRASH_MESSAGE = "Unerwartet beendet – Details im Server-Log";

    /**
     * Offen gebliebene Läufe als Fehler abschließen und dabei ehrlich benennen, was passiert ist:
     * Läufe von vor dem Prozessstart sind einem Neustart zum Opfer gefallen, jüngere sind in
     * diesem Prozess abgestürzt. Der Vergleich läuft bewusst in Java statt in SQL, weil
     * Instant.toString() Nachkommastellen kürzt und ein lexikografischer Vergleich bei
     * sekundengleichen Zeitstempeln falsch wäre.
     */
    private static void repairOrphanRuns(Connection connection) throws Exception {
        Map<Long, String> startedAtById = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT id, started_at FROM sync_runs WHERE status='running'");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                startedAtById.put(rs.getLong("id"), rs.getString("started_at"));
            }
        }
        if (startedAtById.isEmpty()) return;

        String now = Instant.now().toString();
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE sync_runs SET status='error', error=?, finished_at=? WHERE id=?")) {
            for (Map.Entry<Long, String> entry : startedAtById.entrySet()) {
                boolean startedBeforeThisProcess = true; // unparsbar -> konservativ als Neustart werten
                try {
                    startedBeforeThisProcess = Instant.parse(entry.getValue()).isBefore(PROCESS_START);
                } catch (Exception ignored) {
                }
                ps.setString(1, startedBeforeThisProcess ? ORPHAN_RESTART_MESSAGE : ORPHAN_CRASH_MESSAGE);
                ps.setString(2, now);
                ps.setLong(3, entry.getKey());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void updateEndpointStats(Connection connection, EndpointResult result) throws Exception {
        String now = Instant.now().toString();
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO sync_endpoint_stats(endpoint_key, entity_type, display_name, api_path, last_success_at, last_status, last_error, last_seen, inserted, updated, unchanged, downloaded, file_bytes, api_calls, complete, warning)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(endpoint_key) DO UPDATE SET
                  entity_type=excluded.entity_type,
                  display_name=excluded.display_name,
                  api_path=excluded.api_path,
                  last_success_at=excluded.last_success_at,
                  last_status=excluded.last_status,
                  last_error=excluded.last_error,
                  last_seen=excluded.last_seen,
                  inserted=excluded.inserted,
                  updated=excluded.updated,
                  unchanged=excluded.unchanged,
                  downloaded=excluded.downloaded,
                  file_bytes=excluded.file_bytes,
                  api_calls=excluded.api_calls,
                  complete=excluded.complete,
                  warning=excluded.warning
                """)) {
            ps.setString(1, result.endpointKey);
            ps.setString(2, result.entityType);
            ps.setString(3, result.displayName);
            ps.setString(4, result.apiPath);
            ps.setString(5, now);
            ps.setString(6, result.warnings.isEmpty() && result.complete ? "success" : "warning");
            ps.setString(7, "");
            ps.setInt(8, result.seen);
            ps.setInt(9, result.inserted);
            ps.setInt(10, result.updated);
            ps.setInt(11, result.unchanged);
            ps.setInt(12, result.downloaded);
            ps.setLong(13, result.fileBytes);
            ps.setInt(14, result.apiCalls);
            ps.setBoolean(15, result.complete);
            ps.setString(16, String.join("\n", result.warnings));
            ps.executeUpdate();
        }
    }

    private static void initDatabase(Path db) throws Exception {
        Path parent = db.getParent();
        if (parent != null) Files.createDirectories(parent);
        try (Connection connection = connect(db);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_entities (
                      entity_type TEXT NOT NULL,
                      external_id TEXT NOT NULL,
                      api_path TEXT,
                      content_hash TEXT NOT NULL,
                      raw_json TEXT NOT NULL,
                      remote_created_at TEXT,
                      remote_updated_at TEXT,
                      first_seen_at TEXT NOT NULL,
                      last_seen_at TEXT NOT NULL,
                      last_changed_at TEXT NOT NULL,
                      state TEXT NOT NULL DEFAULT 'active',
                      file_path TEXT,
                      file_hash TEXT,
                      file_size INTEGER DEFAULT 0,
                      PRIMARY KEY(entity_type, external_id)
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sync_entities_type ON sync_entities(entity_type)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sync_entities_seen ON sync_entities(last_seen_at)");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_runs (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      mode TEXT NOT NULL,
                      status TEXT NOT NULL,
                      started_at TEXT NOT NULL,
                      finished_at TEXT,
                      api_calls INTEGER DEFAULT 0,
                      total_seen INTEGER DEFAULT 0,
                      total_inserted INTEGER DEFAULT 0,
                      total_updated INTEGER DEFAULT 0,
                      total_unchanged INTEGER DEFAULT 0,
                      total_downloaded INTEGER DEFAULT 0,
                      warning_count INTEGER DEFAULT 0,
                      error TEXT,
                      stats_json TEXT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_endpoint_stats (
                      endpoint_key TEXT PRIMARY KEY,
                      entity_type TEXT NOT NULL,
                      display_name TEXT NOT NULL,
                      api_path TEXT NOT NULL,
                      last_success_at TEXT,
                      last_status TEXT,
                      last_error TEXT,
                      last_seen INTEGER DEFAULT 0,
                      inserted INTEGER DEFAULT 0,
                      updated INTEGER DEFAULT 0,
                      unchanged INTEGER DEFAULT 0,
                      downloaded INTEGER DEFAULT 0,
                      file_bytes INTEGER DEFAULT 0,
                      api_calls INTEGER DEFAULT 0,
                      complete INTEGER DEFAULT 0,
                      warning TEXT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_snapshots (
                      snapshot_key TEXT PRIMARY KEY,
                      endpoint_key TEXT NOT NULL,
                      api_path TEXT NOT NULL,
                      content_hash TEXT NOT NULL,
                      raw_json TEXT NOT NULL,
                      last_seen_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_diagnostic_runs (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      mode TEXT NOT NULL,
                      status TEXT NOT NULL,
                      started_at TEXT NOT NULL,
                      finished_at TEXT,
                      selected_endpoints TEXT,
                      api_calls INTEGER DEFAULT 0,
                      warning_count INTEGER DEFAULT 0,
                      warnings_json TEXT,
                      error TEXT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_diagnostic_results (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      run_id INTEGER NOT NULL,
                      endpoint_key TEXT NOT NULL,
                      display_name TEXT NOT NULL,
                      entity_type TEXT NOT NULL,
                      variant TEXT NOT NULL,
                      api_path TEXT NOT NULL,
                      http_code INTEGER DEFAULT 0,
                      duration_ms INTEGER DEFAULT 0,
                      response_bytes INTEGER DEFAULT 0,
                      array_field TEXT,
                      item_count INTEGER DEFAULT 0,
                      empty_item_count INTEGER DEFAULT 0,
                      stable_ids_json TEXT,
                      sample_keys_json TEXT,
                      content_signature TEXT,
                      repeated INTEGER DEFAULT 0,
                      fields_effective INTEGER DEFAULT 0,
                      recommendation TEXT,
                      warning TEXT,
                      error TEXT,
                      raw_excerpt TEXT,
                      created_at TEXT NOT NULL
                    )
                    """);
        }
    }

    private static Connection connect(Path db) throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
        try (Statement st = connection.createStatement()) {
            // WAL: Leser (Statusabfragen der Oberfläche) blockieren nicht mehr gegen den
            // schreibenden Sync-Thread. Ist in der Datei gespeichert, daher ab dem zweiten
            // Verbindungsaufbau ein No-Op.
            st.execute("PRAGMA journal_mode=WAL");
            // Ohne busy_timeout wirft SQLite bei Konkurrenz sofort SQLITE_BUSY, statt kurz zu warten.
            st.execute("PRAGMA busy_timeout=10000");
        } catch (Exception e) {
            System.err.println("GoAffPro Sync: SQLite-PRAGMAs konnten nicht gesetzt werden: " + describeThrowable(e));
        }
        return connection;
    }

    /**
     * Datenhygiene (leere Objekte markieren) darf niemals einen Lesezugriff scheitern lassen.
     * Während eines Laufs wird sie übersprungen: dann schreibt ohnehin der Sync-Thread, und die
     * Oberfläche pollt den Status im Sekundentakt - das provozierte bisher SQLITE_BUSY.
     */
    private void markInvalidEmptyEntitiesQuietly(Connection connection) {
        if (running.get()) return;
        try {
            markInvalidEmptyEntities(connection);
        } catch (Exception e) {
            System.err.println("GoAffPro Sync: Bereinigung leerer Objekte übersprungen: " + describeThrowable(e));
        }
    }

    private static long insertRun(Connection connection, String mode) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO sync_runs(mode,status,started_at,stats_json) VALUES(?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, mode);
            ps.setString(2, "running");
            ps.setString(3, Instant.now().toString());
            ps.setString(4, "{}");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private static void updateRun(Connection connection, long runId, SyncRunResult run) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE sync_runs
                SET status=?, finished_at=?, api_calls=?, total_seen=?, total_inserted=?, total_updated=?, total_unchanged=?, total_downloaded=?, warning_count=?, error=?, stats_json=?
                WHERE id=?
                """)) {
            ps.setString(1, run.status);
            ps.setString(2, run.finishedAt);
            ps.setInt(3, run.apiCalls);
            ps.setInt(4, run.seen);
            ps.setInt(5, run.inserted);
            ps.setInt(6, run.updated);
            ps.setInt(7, run.unchanged);
            ps.setInt(8, run.downloaded);
            ps.setInt(9, run.warnings.size());
            ps.setString(10, run.error);
            ps.setString(11, canonicalJson(run.toStatusMap()));
            ps.setLong(12, runId);
            ps.executeUpdate();
        }
    }

    private static long insertDiagnosticRun(Connection connection, DiagnosticRunResult run) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO sync_diagnostic_runs(mode,status,started_at,selected_endpoints,warnings_json,error)
                VALUES(?,?,?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, run.mode);
            ps.setString(2, run.status);
            ps.setString(3, run.startedAt);
            ps.setString(4, canonicalJson(run.selectedEndpoints));
            ps.setString(5, "[]");
            ps.setString(6, "");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private static void updateDiagnosticRun(Connection connection, long runId, DiagnosticRunResult run) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE sync_diagnostic_runs
                SET status=?, finished_at=?, api_calls=?, warning_count=?, warnings_json=?, error=?
                WHERE id=?
                """)) {
            ps.setString(1, run.status);
            ps.setString(2, run.finishedAt);
            ps.setInt(3, run.apiCalls);
            ps.setInt(4, run.warnings.size());
            ps.setString(5, canonicalJson(run.warnings));
            ps.setString(6, run.error);
            ps.setLong(7, runId);
            ps.executeUpdate();
        }
    }

    private static void insertDiagnosticResult(Connection connection, long runId, DiagnosticResult result) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO sync_diagnostic_results(run_id,endpoint_key,display_name,entity_type,variant,api_path,http_code,duration_ms,response_bytes,array_field,item_count,empty_item_count,stable_ids_json,sample_keys_json,content_signature,repeated,fields_effective,recommendation,warning,error,raw_excerpt,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            ps.setLong(1, runId);
            ps.setString(2, result.endpointKey);
            ps.setString(3, result.displayName);
            ps.setString(4, result.entityType);
            ps.setString(5, result.variant);
            ps.setString(6, result.apiPath);
            ps.setInt(7, result.httpCode);
            ps.setLong(8, result.durationMs);
            ps.setLong(9, result.responseBytes);
            ps.setString(10, result.arrayField);
            ps.setInt(11, result.itemCount);
            ps.setInt(12, result.emptyItemCount);
            ps.setString(13, canonicalJson(result.stableIds));
            ps.setString(14, canonicalJson(result.sampleKeys));
            ps.setString(15, result.contentSignature);
            ps.setBoolean(16, result.repeated);
            ps.setBoolean(17, result.fieldsEffective);
            ps.setString(18, result.recommendation);
            ps.setString(19, result.warning);
            ps.setString(20, result.error);
            ps.setString(21, result.rawExcerpt);
            ps.setString(22, Instant.now().toString());
            ps.executeUpdate();
        }
    }

    private Map<String, Object> lastSuccessfulModeRun(Properties config, String mode) throws Exception {
        Path db = resolveDbPath(config);
        if (!Files.exists(db)) return Map.of();
        initDatabase(db);
        try (Connection connection = connect(db);
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT id, mode, status, started_at, finished_at, api_calls, total_seen, total_inserted, total_updated, warning_count, error
                     FROM sync_runs
                     WHERE mode=? AND status IN ('success','warning')
                     ORDER BY id DESC LIMIT 1
                     """)) {
            ps.setString(1, mode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? runRow(rs) : Map.of();
            }
        }
    }

    private static Map<String, Object> lastRun(Connection connection, String status) throws Exception {
        String sql = """
                SELECT id, mode, status, started_at, finished_at, api_calls, total_seen, total_inserted, total_updated, warning_count, error
                FROM sync_runs
                """ + (status == null ? "" : "WHERE status=? ") + "ORDER BY id DESC LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (status != null) ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? runRow(rs) : Map.of();
            }
        }
    }

    private static List<Map<String, Object>> runRows(Connection connection, int limit) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id, mode, status, started_at, finished_at, api_calls, total_seen, total_inserted, total_updated, warning_count, error
                FROM sync_runs ORDER BY id DESC LIMIT ?
                """)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(runRow(rs));
            }
        }
        return rows;
    }

    private static Map<String, Object> runRow(ResultSet rs) throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("mode", rs.getString("mode"));
        row.put("status", rs.getString("status"));
        row.put("startedAt", rs.getString("started_at"));
        row.put("finishedAt", rs.getString("finished_at"));
        row.put("apiCalls", rs.getInt("api_calls"));
        row.put("seen", rs.getInt("total_seen"));
        row.put("inserted", rs.getInt("total_inserted"));
        row.put("updated", rs.getInt("total_updated"));
        row.put("warningCount", rs.getInt("warning_count"));
        row.put("error", rs.getString("error"));
        return row;
    }

    private static Map<String, Object> latestDiagnosticRun(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id, mode, status, started_at, finished_at, selected_endpoints, api_calls, warning_count, warnings_json, error
                FROM sync_diagnostic_runs ORDER BY id DESC LIMIT 1
                """);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? diagnosticRunRow(rs) : Map.of();
        }
    }

    private static List<Map<String, Object>> diagnosticRunRows(Connection connection, int limit) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id, mode, status, started_at, finished_at, selected_endpoints, api_calls, warning_count, warnings_json, error
                FROM sync_diagnostic_runs ORDER BY id DESC LIMIT ?
                """)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(diagnosticRunRow(rs));
            }
        }
        return rows;
    }

    private static Map<String, Object> diagnosticRunRow(ResultSet rs) throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("mode", rs.getString("mode"));
        row.put("status", rs.getString("status"));
        row.put("startedAt", rs.getString("started_at"));
        row.put("finishedAt", rs.getString("finished_at"));
        row.put("selectedEndpoints", readStringList(rs.getString("selected_endpoints")));
        row.put("apiCalls", rs.getInt("api_calls"));
        row.put("warningCount", rs.getInt("warning_count"));
        row.put("warnings", readStringList(rs.getString("warnings_json")));
        row.put("error", rs.getString("error"));
        return row;
    }

    private static List<Map<String, Object>> diagnosticResultRows(Connection connection, long runId) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT endpoint_key, display_name, entity_type, variant, api_path, http_code, duration_ms, response_bytes, array_field, item_count, empty_item_count, stable_ids_json, sample_keys_json, content_signature, repeated, fields_effective, recommendation, warning, error, raw_excerpt, created_at
                FROM sync_diagnostic_results
                WHERE run_id=?
                ORDER BY id
                """)) {
            ps.setLong(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("endpointKey", rs.getString("endpoint_key"));
                    row.put("displayName", rs.getString("display_name"));
                    row.put("entityType", rs.getString("entity_type"));
                    row.put("variant", rs.getString("variant"));
                    row.put("apiPath", rs.getString("api_path"));
                    row.put("httpCode", rs.getInt("http_code"));
                    row.put("durationMs", rs.getLong("duration_ms"));
                    row.put("responseBytes", rs.getLong("response_bytes"));
                    row.put("arrayField", rs.getString("array_field"));
                    row.put("itemCount", rs.getInt("item_count"));
                    row.put("emptyItemCount", rs.getInt("empty_item_count"));
                    row.put("stableIds", readStringList(rs.getString("stable_ids_json")));
                    row.put("sampleKeys", readStringList(rs.getString("sample_keys_json")));
                    row.put("contentSignature", rs.getString("content_signature"));
                    row.put("repeated", rs.getBoolean("repeated"));
                    row.put("fieldsEffective", rs.getBoolean("fields_effective"));
                    row.put("recommendation", rs.getString("recommendation"));
                    row.put("warning", rs.getString("warning"));
                    row.put("error", rs.getString("error"));
                    row.put("rawExcerpt", rs.getString("raw_excerpt"));
                    row.put("createdAt", rs.getString("created_at"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private static List<Map<String, Object>> diagnosticEndpointSummaries(List<Map<String, Object>> results) {
        Map<String, List<Map<String, Object>>> byEndpoint = results.stream()
                .collect(Collectors.groupingBy(row -> Objects.toString(row.get("endpointKey"), ""), LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : byEndpoint.entrySet()) {
            List<Map<String, Object>> rows = entry.getValue();
            Map<String, Object> first = rows.get(0);
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("endpointKey", entry.getKey());
            summary.put("displayName", first.get("displayName"));
            summary.put("recommendation", first.get("recommendation"));
            summary.put("apiCalls", rows.size());
            summary.put("okResponses", rows.stream().filter(r -> intValue(r.get("httpCode")) >= 200 && intValue(r.get("httpCode")) < 300).count());
            summary.put("maxItems", rows.stream().mapToInt(r -> intValue(r.get("itemCount"))).max().orElse(0));
            summary.put("emptyItems", rows.stream().mapToInt(r -> intValue(r.get("emptyItemCount"))).sum());
            summary.put("warnings", rows.stream().map(r -> Objects.toString(r.get("warning"), "")).filter(w -> !w.isBlank()).distinct().toList());
            summaries.add(summary);
        }
        return summaries;
    }

    private static List<Map<String, Object>> inventoryRows(Connection connection) throws Exception {
        Map<String, Map<String, Object>> endpointByType = new HashMap<>();
        for (Map<String, Object> endpoint : endpointRows(connection)) {
            endpointByType.put(Objects.toString(endpoint.get("entityType"), ""), endpoint);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT entity_type,
                       SUM(CASE WHEN state='active' THEN 1 ELSE 0 END) AS total,
                       SUM(CASE WHEN state='remote_missing' THEN 1 ELSE 0 END) AS remote_missing,
                       SUM(CASE WHEN state='invalid_empty' THEN 1 ELSE 0 END) AS invalid_empty,
                       SUM(CASE WHEN state='active' AND file_path IS NOT NULL AND file_path <> '' THEN 1 ELSE 0 END) AS files,
                       SUM(CASE WHEN state='active' THEN COALESCE(file_size,0) ELSE 0 END) AS file_bytes,
                       MAX(last_seen_at) AS last_seen,
                       MAX(last_changed_at) AS last_changed
                FROM sync_entities
                GROUP BY entity_type
                ORDER BY entity_type
                """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String type = rs.getString("entity_type");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("entityType", type);
                row.put("displayName", displayNameForType(type));
                row.put("total", rs.getLong("total"));
                row.put("remoteMissing", rs.getLong("remote_missing"));
                row.put("invalidEmpty", rs.getLong("invalid_empty"));
                row.put("files", rs.getLong("files"));
                row.put("fileBytes", rs.getLong("file_bytes"));
                row.put("lastSeenAt", rs.getString("last_seen"));
                row.put("lastChangedAt", rs.getString("last_changed"));
                Map<String, Object> endpoint = endpointByType.getOrDefault(type, Map.of());
                row.put("complete", endpoint.getOrDefault("complete", true));
                String warning = Objects.toString(endpoint.getOrDefault("warning", ""), "");
                long invalidEmpty = rs.getLong("invalid_empty");
                if (invalidEmpty > 0) {
                    String invalidWarning = invalidEmpty + " leere Raw-Objekte als ungültig markiert.";
                    warning = warning.isBlank() ? invalidWarning : warning + "\n" + invalidWarning;
                }
                row.put("warning", warning);
                rows.add(row);
            }
        }
        return rows;
    }

    private static List<Map<String, Object>> endpointRows(Connection connection) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT endpoint_key, entity_type, display_name, api_path, last_success_at, last_status, last_error, last_seen, inserted, updated, unchanged, downloaded, file_bytes, api_calls, complete, warning
                FROM sync_endpoint_stats
                ORDER BY display_name
                """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("endpointKey", rs.getString("endpoint_key"));
                row.put("entityType", rs.getString("entity_type"));
                row.put("displayName", rs.getString("display_name"));
                row.put("apiPath", rs.getString("api_path"));
                row.put("lastSuccessAt", rs.getString("last_success_at"));
                row.put("lastStatus", rs.getString("last_status"));
                row.put("lastError", rs.getString("last_error"));
                row.put("seen", rs.getInt("last_seen"));
                row.put("inserted", rs.getInt("inserted"));
                row.put("updated", rs.getInt("updated"));
                row.put("unchanged", rs.getInt("unchanged"));
                row.put("downloaded", rs.getInt("downloaded"));
                row.put("fileBytes", rs.getLong("file_bytes"));
                row.put("apiCalls", rs.getInt("api_calls"));
                row.put("complete", rs.getBoolean("complete"));
                row.put("warning", rs.getString("warning"));
                rows.add(row);
            }
        }
        return rows;
    }

    private static List<String> syncWarnings(Connection connection) throws Exception {
        List<String> warnings = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT display_name, warning FROM sync_endpoint_stats
                WHERE warning IS NOT NULL AND warning <> ''
                ORDER BY display_name
                """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                warnings.add(rs.getString("display_name") + ": " + rs.getString("warning"));
            }
        }
        return warnings;
    }

    private static List<JsonNode> entitiesFromConnection(Connection connection, String entityType) throws Exception {
        List<JsonNode> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT raw_json FROM sync_entities WHERE entity_type=? AND state='active'")) {
            ps.setString(1, entityType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(MAPPER.readTree(rs.getString(1)));
            }
        }
        return rows;
    }

    private static List<StoredEntity> storedEntities(Connection connection, String entityType) throws Exception {
        List<StoredEntity> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT external_id, raw_json, file_path FROM sync_entities WHERE entity_type=? AND state='active'")) {
            ps.setString(1, entityType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new StoredEntity(rs.getString("external_id"), MAPPER.readTree(rs.getString("raw_json")), rs.getString("file_path")));
            }
        }
        return rows;
    }

    private static void updateEntityFile(Connection connection, String entityType, String externalId, DownloadedFile file) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE sync_entities SET file_path=?, file_hash=?, file_size=? WHERE entity_type=? AND external_id=?
                """)) {
            ps.setString(1, file.path);
            ps.setString(2, file.hash);
            ps.setLong(3, file.size);
            ps.setString(4, entityType);
            ps.setString(5, externalId);
            ps.executeUpdate();
        }
    }

    private static long scalarLong(Connection connection, String sql) throws Exception {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static List<String> readStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode node = MAPPER.readTree(json);
            if (!node.isArray()) return List.of();
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) values.add(item.asText(""));
            return values;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(Objects.toString(value, "0"));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(Objects.toString(value, "0"));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String computeState(Properties config, Map<String, Object> lastRun, Map<String, Object> lastSuccess) {
        if (!isSyncEnabled(config)) return "Pausiert";
        if (Boolean.TRUE.equals(lastRun.get("running"))) {
            String mode = Objects.toString(lastRun.get("mode"), "");
            return "initial".equals(mode) ? "Initialsync läuft" : "Sync läuft";
        }
        String lastStatus = Objects.toString(lastRun.get("status"), "");
        if ("error".equals(lastStatus)) return "Fehler";
        String finishedAt = Objects.toString(lastSuccess.get("finishedAt"), "");
        if (finishedAt.isBlank()) return "Noch nicht synchronisiert";
        try {
            long hours = Duration.between(Instant.parse(finishedAt), Instant.now()).toHours();
            boolean warning = "warning".equals(lastStatus);
            if (hours > 26) return warning ? "Veraltet mit Warnungen" : "Veraltet";
            return warning ? "Synchronisiert mit Warnungen" : "Aktuell";
        } catch (Exception e) {
            return "Unklar";
        }
    }

    private static boolean isSyncEnabled(Properties config) {
        return Boolean.parseBoolean(Objects.toString(config.getProperty("goaffproSyncEnabled"), "true"));
    }

    private static boolean hourlyEnabled(Properties config) {
        return Boolean.parseBoolean(Objects.toString(config.getProperty("goaffproSyncHourlyEnabled"), "false"));
    }

    private static boolean deepEnabled(Properties config) {
        return Boolean.parseBoolean(Objects.toString(config.getProperty("goaffproSyncDeepEnabled"), "false"));
    }

    private static boolean assetDownloadEnabled(Properties config) {
        return Boolean.parseBoolean(Objects.toString(config.getProperty("goaffproSyncAssetDownloadEnabled"), "true"));
    }

    private static int callLimitPerHour(Properties config) {
        return Math.max(10, intSetting(config, "goaffproSyncMaxCallsPerHour", DEFAULT_CALL_LIMIT_PER_HOUR));
    }

    private static long minCallSpacingMs(Properties config) {
        return Math.max(0, Math.min(60_000, intSetting(config, "goaffproSyncMinCallSpacingMs", 1500)));
    }

    private static boolean slidingWindowEnabled(Properties config) {
        return boolSetting(config, "goaffproSyncSlidingWindowEnabled", true);
    }

    private static boolean downloadSkipExistingEnabled(Properties config) {
        return boolSetting(config, "goaffproSyncDownloadSkipExistingEnabled", true);
    }

    private static boolean deltaDownloadsEnabled(Properties config) {
        return boolSetting(config, "goaffproSyncDeltaDownloadsEnabled", false);
    }

    private static RateBudget newRateBudget(Properties config) {
        return new RateBudget(callLimitPerHour(config), minCallSpacingMs(config), slidingWindowEnabled(config));
    }

    private static String apiBase(Properties config) {
        String configured = Objects.toString(config.getProperty("goaffproSyncApiBase"), "").trim();
        return configured.isBlank() ? API_BASE : configured;
    }

    private static int deltaLookbackDays(Properties config) {
        return Math.max(1, intSetting(config, "goaffproSyncDeltaLookbackDays", DEFAULT_DELTA_LOOKBACK_DAYS));
    }

    private static long minFreeBytes(Properties config) {
        try {
            return Math.max(0L, Long.parseLong(Objects.toString(config.getProperty("goaffproSyncMinFreeBytes"), String.valueOf(DEFAULT_MIN_FREE_BYTES))));
        } catch (Exception e) {
            return DEFAULT_MIN_FREE_BYTES;
        }
    }

    private static int intSetting(Properties config, String key, int fallback) {
        try {
            String raw = Objects.toString(config.getProperty(key), "").trim();
            return raw.isBlank() ? fallback : Integer.parseInt(raw);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean boolSetting(Properties config, String key, boolean fallback) {
        String raw = Objects.toString(config.getProperty(key), "").trim();
        return raw.isBlank() ? fallback : Boolean.parseBoolean(raw);
    }

    public static Path resolveDataDir(Properties config) {
        String configured = Objects.toString(config.getProperty("goaffproSyncDataPath"), "").trim();
        if (configured.isBlank()) {
            configured = System.getenv().getOrDefault("GOAFFPRO_SYNC_DATA_PATH", System.getenv().getOrDefault("DATA_PATH", "data"));
        }
        return Paths.get(configured).toAbsolutePath();
    }

    /**
     * WAL-sichere, kompakte Kopie der Datenbank. VACUUM INTO liest über eine normale Verbindung,
     * sieht also den vollständigen Stand inklusive WAL, und schreibt eine Datei ohne
     * Seitendateien. Die Zieldatei darf dabei nicht existieren.
     */
    public static void snapshotDatabase(Path sourceDb, Path targetFile) throws Exception {
        Path parent = targetFile.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.deleteIfExists(targetFile);
        try (Connection connection = connect(sourceDb); Statement st = connection.createStatement()) {
            // Backslashes sind in SQLite-Stringliteralen bedeutungslos; nur ' muss verdoppelt werden.
            String literal = targetFile.toAbsolutePath().toString().replace("'", "''");
            st.execute("VACUUM INTO '" + literal + "'");
        }
    }

    public static Path resolveDbPath(Properties config) {
        return resolveDataDir(config).resolve("goaffpro_sync.sqlite");
    }

    private static String normalizeMode(String mode) {
        String value = Objects.toString(mode, "").trim().toLowerCase(Locale.ROOT);
        if (!Set.of("initial", "delta", "deep").contains(value)) return "delta";
        return value;
    }

    private static List<String> normalizeDiagnosticEndpointKeys(List<String> endpointKeys) {
        if (endpointKeys == null || endpointKeys.isEmpty()) return DEFAULT_DIAGNOSTIC_ENDPOINTS;
        Set<String> allowed = new LinkedHashSet<>(DEFAULT_DIAGNOSTIC_ENDPOINTS);
        List<String> selected = endpointKeys.stream()
                .map(value -> Objects.toString(value, "").trim())
                .filter(value -> !value.isBlank())
                .filter(allowed::contains)
                .distinct()
                .toList();
        return selected.isEmpty() ? DEFAULT_DIAGNOSTIC_ENDPOINTS : selected;
    }

    private static int estimateDiagnosticCalls(List<String> endpointKeys) {
        int endpoints = normalizeDiagnosticEndpointKeys(endpointKeys).size();
        return endpoints * diagnosticVariants(new EndpointSpec("estimate", "Estimate", "estimate", "/v1/admin/estimate?limit=500", "items", true, false)).size();
    }

    static String recommendDiagnosticEndpoint(EndpointSpec endpoint, List<DiagnosticResult> results) {
        if ("creatives".equals(endpoint.key)) return "Endpoint ignorieren/deprecated";
        if (results == null || results.isEmpty()) return "API temporär fehlerhaft";
        boolean hasSuccess = results.stream().anyMatch(result -> result.httpCode >= 200 && result.httpCode < 300);
        boolean allServerErrors = results.stream().allMatch(result -> result.httpCode == 504 || result.httpCode >= 500 || result.httpCode == 0);
        boolean allSuccessfulItemsEmpty = results.stream()
                .filter(result -> result.httpCode >= 200 && result.httpCode < 300 && result.itemCount > 0)
                .anyMatch(result -> result.emptyItemCount == result.itemCount);
        boolean anyRepeated = results.stream().anyMatch(result -> result.repeated);
        boolean anyUseful = results.stream().anyMatch(result -> result.httpCode >= 200 && result.httpCode < 300 && result.fieldsEffective && result.emptyItemCount < result.itemCount);
        if (allServerErrors || !hasSuccess) return "API temporär fehlerhaft";
        if (allSuccessfulItemsEmpty) return "Fields anpassen";
        if (anyRepeated) return "Pagination anpassen";
        if (anyUseful || results.stream().anyMatch(result -> result.httpCode >= 200 && result.httpCode < 300)) return "OK";
        return "API temporär fehlerhaft";
    }

    public static String extractStableId(JsonNode node, String entityType) {
        if (node == null || node.isNull() || node.isMissingNode()) return "";
        if (node.isValueNode()) return node.asText("");
        for (String key : stableIdKeys(entityType)) {
            String value = text(node, key).trim();
            if (!value.isBlank()) return value;
        }
        for (String key : List.of("id", "_id", "order_id", "number", "tx_id", "session_id", "coupon_code", "code", "ref_code", "itemId", "item_id", "key", "name", "email")) {
            String value = text(node, key).trim();
            if (!value.isBlank()) return value;
        }
        JsonNode nested = node.get("item");
        if (nested != null && nested.isObject()) {
            String nestedId = extractStableId(nested, entityType);
            if (!nestedId.isBlank()) return nestedId;
        }
        return "";
    }

    private static List<String> stableIdKeys(String entityType) {
        return switch (Objects.toString(entityType, "")) {
            case "affiliates" -> List.of("id", "affiliate_id", "ref_code", "email");
            case "orders", "orders_system" -> List.of("id", "order_id", "number");
            case "payments", "payment_requests", "payments_pending", "payment_sessions" -> List.of("id", "payment_id");
            case "transactions", "unpaid_transactions" -> List.of("tx_id", "id");
            case "showcases" -> List.of("id", "sub_id", "slug", "title", "name");
            case "traffic" -> List.of("id", "visitor_id", "session_id", "created_at");
            case "connections" -> List.of("id", "connection_id");
            case "coupons" -> List.of("id", "coupon_code", "code");
            case "files", "asset_contents", "asset_folders" -> List.of("id", "_id", "itemId", "item_id", "key", "name", "url");
            case "groups", "group_members" -> List.of("id", "group_id", "member_id", "email", "name");
            case "commissions", "commission_collections", "commission_products", "rewards" -> List.of("id", "item_id", "key", "name");
            default -> List.of();
        };
    }

    private static String findDownloadUrl(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return "";
        if (node.isObject()) {
            for (String key : List.of("download_url", "downloadUrl", "file_url", "fileUrl", "url", "src", "href", "link")) {
                String value = text(node, key).trim();
                if (value.startsWith("http://") || value.startsWith("https://")) return value;
            }
            for (JsonNode child : node) {
                String found = findDownloadUrl(child);
                if (!found.isBlank()) return found;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findDownloadUrl(child);
                if (!found.isBlank()) return found;
            }
        }
        return "";
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node != null ? node.get(key) : null;
        return value != null && !value.isNull() ? value.asText("") : "";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private static String appendQuery(String path, String query) {
        return path + (path.contains("?") ? "&" : "?") + query;
    }

    private static boolean hasQueryParam(String path, String param) {
        return path.matches(".*[?&]" + java.util.regex.Pattern.quote(param) + "=.*");
    }

    private static String encodePath(String value) {
        return java.net.URLEncoder.encode(Objects.toString(value, ""), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String canonicalJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return Objects.toString(value, "");
        }
    }

    private static String sha256Hex(String input) {
        return sha256Hex(Objects.toString(input, "").getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "hash-unavailable";
        }
    }

    private static String trim(String value, int max) {
        String safe = Objects.toString(value, "");
        return safe.length() <= max ? safe : safe.substring(0, max) + "...";
    }

    private static long retryAfterMillis(String retryAfter, int attempts) {
        try {
            if (retryAfter != null && !retryAfter.isBlank()) {
                return Math.max(1000L, Long.parseLong(retryAfter.trim()) * 1000L);
            }
        } catch (Exception ignored) {
        }
        return Math.min(60000L, 1000L * (1L << attempts));
    }

    private static String extensionFromUrl(String url) {
        String clean = Objects.toString(url, "");
        int q = clean.indexOf('?');
        if (q >= 0) clean = clean.substring(0, q);
        int slash = clean.lastIndexOf('/');
        String name = slash >= 0 ? clean.substring(slash + 1) : clean;
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1 && name.length() - dot <= 8) {
            return name.substring(dot).replaceAll("[^a-zA-Z0-9.]", "");
        }
        return ".bin";
    }

    private static String safeFileName(String value) {
        return Objects.toString(value, "file").replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static long directorySize(Path dir) {
        if (!Files.exists(dir)) return 0L;
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String displayNameForType(String type) {
        return switch (type) {
            case "affiliates" -> "Affiliates / Beraterinnen";
            case "orders" -> "Orders";
            case "payments" -> "Payments";
            case "rewards" -> "Rewards";
            case "traffic" -> "Traffic";
            case "showcases" -> "Showcases / Partys";
            case "mlm_tree" -> "MLM Baum";
            case "files" -> "Affiliate Files";
            case "asset_contents" -> "Assets";
            case "asset_folders" -> "Asset Ordner";
            default -> type;
        };
    }

    private record HttpResponse(int code, String body) {
    }

    private record DownloadedFile(String path, String hash, long size) {
    }

    private record StoredEntity(String externalId, JsonNode rawJson, String filePath) {
    }

    static class RateBudget {
        private final int callLimitPerHour;
        private final long minSpacingMs;
        private final long legacyDelayMs;
        private final boolean slidingWindow;
        // Das Stundenfenster ist bewusst prozessglobal (static): RateBudget wird pro Lauf neu
        // erzeugt, aber die API-Rate gilt über Lauf- und Diagnose-Grenzen hinweg. Eigener
        // Monitor, weil beforeCall() unter dem this-Monitor schläft und die Status-Getter
        // davon nicht blockiert werden dürfen.
        private static final ArrayDeque<Long> SHARED_CALL_TIMES = new ArrayDeque<>();
        private static volatile long lastCallAt = 0L;
        private volatile boolean waiting;
        private volatile long nextCallAtMillis;

        RateBudget(int callLimitPerHour) {
            this(callLimitPerHour, 0L, false);
        }

        RateBudget(int callLimitPerHour, long minSpacingMs, boolean slidingWindow) {
            this.callLimitPerHour = Math.max(1, callLimitPerHour);
            long perCallInterval = Math.round(3600000.0 / this.callLimitPerHour);
            this.minSpacingMs = Math.max(0L, Math.min(minSpacingMs, perCallInterval));
            this.legacyDelayMs = Math.max(250L, perCallInterval);
            this.slidingWindow = slidingWindow;
        }

        synchronized void beforeCall() throws InterruptedException {
            // Schleife: nach dem Sleep kann ein anderer Thread (Diagnose) das Fenster gefüllt haben
            while (true) {
                long now = System.currentTimeMillis();
                long wait;
                if (slidingWindow) {
                    wait = 0L;
                    synchronized (SHARED_CALL_TIMES) {
                        pruneOldCalls(now);
                        if (SHARED_CALL_TIMES.size() >= callLimitPerHour) {
                            wait = SHARED_CALL_TIMES.peekFirst() + 3_600_000L - now;
                        }
                    }
                    wait = Math.max(wait, lastCallAt + minSpacingMs - now);
                } else {
                    wait = lastCallAt + legacyDelayMs - now;
                }
                if (wait <= 0) break;
                nextCallAtMillis = now + wait;
                waiting = true;
                try {
                    Thread.sleep(wait);
                } finally {
                    waiting = false;
                }
            }
            long stamp = System.currentTimeMillis();
            lastCallAt = stamp;
            synchronized (SHARED_CALL_TIMES) {
                SHARED_CALL_TIMES.addLast(stamp);
                pruneOldCalls(stamp);
            }
        }

        private static void pruneOldCalls(long now) {
            while (!SHARED_CALL_TIMES.isEmpty() && SHARED_CALL_TIMES.peekFirst() < now - 3_600_000L) {
                SHARED_CALL_TIMES.pollFirst();
            }
        }

        int callsInLastHour() {
            synchronized (SHARED_CALL_TIMES) {
                pruneOldCalls(System.currentTimeMillis());
                return SHARED_CALL_TIMES.size();
            }
        }

        int burstAvailableNow() {
            return Math.max(0, callLimitPerHour - callsInLastHour());
        }

        long minSpacingMs() {
            return minSpacingMs;
        }

        boolean isSlidingWindow() {
            return slidingWindow;
        }

        boolean isWaiting() {
            return waiting;
        }

        long secondsUntilNextCall() {
            if (!waiting) return 0L;
            return Math.max(0L, Math.round((nextCallAtMillis - System.currentTimeMillis()) / 1000.0));
        }

        static void clearWindowForTests() {
            synchronized (SHARED_CALL_TIMES) {
                SHARED_CALL_TIMES.clear();
            }
            lastCallAt = 0L;
        }
    }

    public static class DiagnosticRunResult {
        long runId;
        final String mode = "diagnostics";
        String status = "running";
        final String startedAt = Instant.now().toString();
        String finishedAt = "";
        String error = "";
        int apiCalls;
        int resultCount;
        String currentEndpoint = "";
        final List<String> selectedEndpoints;
        final List<String> warnings = new ArrayList<>();

        DiagnosticRunResult(List<String> selectedEndpoints) {
            this.selectedEndpoints = new ArrayList<>(selectedEndpoints);
        }

        void add(DiagnosticResult result) {
            if (result == null) return;
            resultCount++;
        }

        void warning(String warning) {
            if (warning != null && !warning.isBlank()) warnings.add(warning);
        }

        Map<String, Object> toStatusMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("runId", runId);
            row.put("mode", mode);
            row.put("status", status);
            row.put("startedAt", startedAt);
            row.put("finishedAt", finishedAt);
            row.put("error", error);
            row.put("apiCalls", apiCalls);
            row.put("resultCount", resultCount);
            row.put("currentEndpoint", currentEndpoint);
            row.put("selectedEndpoints", selectedEndpoints);
            row.put("warningCount", warnings.size());
            row.put("warnings", warnings);
            return row;
        }
    }

    static class DiagnosticResult {
        final String endpointKey;
        final String displayName;
        final String entityType;
        final String variant;
        final String apiPath;
        int apiCalls;
        int httpCode;
        long durationMs;
        long responseBytes;
        String arrayField = "";
        int itemCount;
        int emptyItemCount;
        List<String> stableIds = List.of();
        List<String> sampleKeys = List.of();
        String contentSignature = "";
        boolean repeated;
        boolean fieldsEffective;
        String recommendation = "";
        String warning = "";
        String error = "";
        String rawExcerpt = "";

        DiagnosticResult(EndpointSpec endpoint, DiagnosticVariant variant) {
            this.endpointKey = endpoint.key;
            this.displayName = endpoint.displayName;
            this.entityType = endpoint.entityType;
            this.variant = variant.name;
            this.apiPath = variant.path;
        }
    }

    private record DiagnosticVariant(String name, String path, String family) {
    }

    public static class SyncRunResult {
        long runId;
        final String mode;
        String status = "running";
        final String startedAt = Instant.now().toString();
        String finishedAt = "";
        String error = "";
        int apiCalls;
        int seen;
        int inserted;
        int updated;
        int unchanged;
        int downloaded;
        int downloadsSkipped;
        long fileBytes;
        String currentEndpoint = "";
        String currentEndpointKey = "";
        String currentEntityType = "";
        String currentApiPath = "";
        int currentPage;
        int currentEndpointSeen;
        int currentEndpointApiCalls;
        String lastWarning = "";
        String phase = "starting";
        int endpointIndex;
        int endpointCount;
        int callLimitPerHour = DEFAULT_CALL_LIMIT_PER_HOUR;
        RateBudget budget;
        long expectedTotalRecords = -1;
        boolean totalsKnown;
        int estimatedTotalCalls = -1;
        final List<Map<String, Object>> plannedEndpoints = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final List<Map<String, Object>> endpointRows = new ArrayList<>();

        SyncRunResult(String mode) {
            this.mode = mode;
        }

        void add(EndpointResult result) {
            apiCalls += result.apiCalls;
            seen += result.seen;
            inserted += result.inserted;
            updated += result.updated;
            unchanged += result.unchanged;
            downloaded += result.downloaded;
            downloadsSkipped += result.skippedExisting;
            fileBytes += result.fileBytes;
            warnings.addAll(result.warnings);
            if (!result.warnings.isEmpty()) lastWarning = lastOf(result.warnings);
            endpointRows.add(result.toMap());
            Map<String, Object> planned = plannedEndpointRow(result.endpointKey);
            if (planned != null) {
                planned.put("seen", result.seen);
                planned.put("apiCalls", result.apiCalls);
                // Ist-Werte ersetzen die Schätzung: abgeschlossene Endpoints zählen exakt.
                planned.put("expectedCalls", result.apiCalls);
                if (!(planned.get("expectedTotal") instanceof Number expected) || expected.intValue() < result.seen) {
                    planned.put("expectedTotal", result.seen);
                }
                if ("skip".equals(planned.get("estimateSource"))) {
                    planned.put("state", "skipped");
                } else {
                    planned.put("state", result.complete ? "done" : "warning");
                }
                recomputeEstimates();
            }
        }

        Map<String, Object> plannedEndpointRow(String endpointKey) {
            for (Map<String, Object> row : plannedEndpoints) {
                if (Objects.equals(endpointKey, row.get("endpointKey"))) return row;
            }
            return null;
        }

        void recomputeEstimates() {
            if (plannedEndpoints.isEmpty()) {
                expectedTotalRecords = -1;
                totalsKnown = false;
                estimatedTotalCalls = -1;
                return;
            }
            boolean recordsKnown = true;
            boolean callsKnown = true;
            long records = 0;
            int calls = 0;
            for (Map<String, Object> row : plannedEndpoints) {
                if (row.get("expectedTotal") instanceof Number expected) {
                    records += expected.longValue();
                } else {
                    recordsKnown = false;
                }
                if (row.get("expectedCalls") instanceof Number expectedCalls) {
                    calls += expectedCalls.intValue();
                } else {
                    callsKnown = false;
                }
            }
            expectedTotalRecords = recordsKnown ? records : -1;
            totalsKnown = recordsKnown;
            estimatedTotalCalls = callsKnown ? calls : -1;
        }

        /** Burst-ETA: freie Calls im Stundenfenster laufen im Spacing-Takt, der Rest im Fenstertakt. */
        private static long burstEta(long calls, int burst, double spacingSec, double windowSec) {
            if (calls <= burst) return Math.round(calls * spacingSec);
            return Math.round(burst * spacingSec + (calls - burst) * windowSec);
        }

        void markPendingEndpointsSkipped() {
            for (Map<String, Object> row : plannedEndpoints) {
                if ("pending".equals(row.get("state")) || "active".equals(row.get("state"))) {
                    row.put("state", "skipped");
                }
            }
        }

        void warning(String warning) {
            if (warning != null && !warning.isBlank()) {
                warnings.add(warning);
                lastWarning = warning;
            }
        }

        void setCurrentEndpoint(EndpointSpec endpoint, int page, String path, EndpointResult result) {
            currentEndpoint = endpoint.displayName;
            currentEndpointKey = endpoint.key;
            currentEntityType = endpoint.entityType;
            currentApiPath = path;
            currentPage = page;
            currentEndpointSeen = result.seen;
            currentEndpointApiCalls = result.apiCalls;
            String endpointWarning = lastOf(result.warnings);
            if (!endpointWarning.isBlank()) lastWarning = endpointWarning;
            Map<String, Object> planned = plannedEndpointRow(endpoint.key);
            if (planned != null) {
                planned.put("state", "active");
                planned.put("seen", result.seen);
                planned.put("apiCalls", result.apiCalls);
                boolean estimatesDirty = false;
                if (planned.get("expectedTotal") instanceof Number expected) {
                    // Drift-Korrektur: Remote kann seit der Schätzung gewachsen sein
                    if (expected.intValue() < result.seen) {
                        planned.put("expectedTotal", result.seen);
                        estimatesDirty = true;
                    }
                } else if (planned.get("expectedCalls") instanceof Number) {
                    // Total unbekannt: Call-Schätzung läuft dem Ist hinterher (mindestens eine Seite folgt noch)
                    planned.put("expectedCalls", result.apiCalls + 1);
                    estimatesDirty = true;
                }
                if (estimatesDirty) recomputeEstimates();
            }
        }

        void setApiTotal(String endpointKey, int total, int pageCallsForTotal) {
            Map<String, Object> planned = plannedEndpointRow(endpointKey);
            if (planned == null) return;
            int floor = planned.get("seen") instanceof Number seenValue ? seenValue.intValue() : 0;
            planned.put("expectedTotal", Math.max(total, floor));
            planned.put("estimateSource", "api");
            int extraCalls = planned.get("extraCalls") instanceof Number extra ? extra.intValue() : 0;
            int callsFloor = planned.get("apiCalls") instanceof Number calls ? calls.intValue() : 0;
            planned.put("expectedCalls", Math.max(pageCallsForTotal + extraCalls, callsFloor));
            recomputeEstimates();
        }

        void clearCurrentEndpoint() {
            currentEndpoint = "";
            currentEndpointKey = "";
            currentEntityType = "";
            currentApiPath = "";
            currentPage = 0;
            currentEndpointSeen = 0;
            currentEndpointApiCalls = 0;
        }

        Map<String, Object> toStatusMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("runId", runId);
            row.put("mode", mode);
            row.put("status", status);
            row.put("startedAt", startedAt);
            row.put("finishedAt", finishedAt);
            row.put("error", error);
            row.put("apiCalls", apiCalls);
            row.put("seen", seen);
            row.put("inserted", inserted);
            row.put("updated", updated);
            row.put("unchanged", unchanged);
            row.put("downloaded", downloaded);
            row.put("downloadsSkipped", downloadsSkipped);
            row.put("fileBytes", fileBytes);
            row.put("warningCount", warnings.size());
            row.put("warnings", warnings);
            row.put("endpointRows", endpointRows);
            row.put("currentEndpoint", currentEndpoint);
            row.put("currentEndpointKey", currentEndpointKey);
            row.put("currentEntityType", currentEntityType);
            row.put("currentApiPath", currentApiPath);
            row.put("currentPage", currentPage);
            row.put("currentEndpointSeen", currentEndpointSeen);
            row.put("currentEndpointApiCalls", currentEndpointApiCalls);
            row.put("lastWarning", lastWarning);
            row.put("phase", phase);
            row.put("endpointIndex", endpointIndex);
            row.put("endpointCount", endpointCount);
            row.put("expectedTotalRecords", expectedTotalRecords);
            row.put("totalsKnown", totalsKnown);
            row.put("processedRecords", seen);
            row.put("estimatedTotalCalls", estimatedTotalCalls);
            boolean runningStatus = "running".equals(status);
            int remainingCalls = estimatedTotalCalls > 0 ? Math.max(0, estimatedTotalCalls - apiCalls) : -1;
            row.put("estimatedRemainingCalls", remainingCalls);
            if (remainingCalls >= 0 && runningStatus) {
                double windowSecondsPerCall = Math.max(0.25, 3600.0 / Math.max(1, callLimitPerHour));
                int remainingEndpoints = Math.max(0, endpointCount - endpointIndex + 1);
                // 504-Retry-Marge: ein zusätzlicher Call je verbleibendem Endpoint,
                // analog zur Diagnose-Schätzung (estimatedDefaultMaxApiCalls)
                long etaSeconds;
                long etaMaxSeconds;
                RateBudget budgetRef = budget;
                if (budgetRef != null && budgetRef.isSlidingWindow()) {
                    double spacingSec = Math.max(0.3, budgetRef.minSpacingMs() / 1000.0);
                    int burst = budgetRef.burstAvailableNow();
                    etaSeconds = burstEta(remainingCalls, burst, spacingSec, windowSecondsPerCall);
                    etaMaxSeconds = burstEta(remainingCalls + remainingEndpoints, burst, spacingSec, windowSecondsPerCall);
                } else {
                    etaSeconds = Math.round(remainingCalls * windowSecondsPerCall);
                    etaMaxSeconds = Math.round((remainingCalls + remainingEndpoints) * windowSecondsPerCall);
                }
                row.put("etaSeconds", etaSeconds);
                row.put("etaMaxSeconds", etaMaxSeconds);
                row.put("etaAt", Instant.now().plusSeconds(etaSeconds).toString());
            } else {
                row.put("etaSeconds", -1L);
                row.put("etaMaxSeconds", -1L);
                row.put("etaAt", "");
            }
            int progressPercent = -1;
            String progressBasis = "endpoints";
            if (totalsKnown && expectedTotalRecords > 0) {
                progressPercent = (int) Math.round(100.0 * seen / expectedTotalRecords);
                progressBasis = "records";
            } else if (estimatedTotalCalls > 0) {
                progressPercent = (int) Math.round(100.0 * apiCalls / estimatedTotalCalls);
                progressBasis = "calls";
            }
            if (progressPercent >= 0) {
                progressPercent = runningStatus ? Math.min(progressPercent, 99) : Math.min(progressPercent, 100);
                if (!runningStatus && ("success".equals(status) || "warning".equals(status))) progressPercent = 100;
            }
            row.put("progressPercent", progressPercent);
            row.put("progressBasis", progressBasis);
            // Kopie, weil der Snapshot von HTTP-Threads gelesen wird, während der
            // Sync-Thread die Plan-Zeilen weiter mutiert
            List<Map<String, Object>> plannedCopy = new ArrayList<>(plannedEndpoints.size());
            for (Map<String, Object> planned : plannedEndpoints) {
                plannedCopy.add(new LinkedHashMap<>(planned));
            }
            row.put("plannedEndpoints", plannedCopy);
            return row;
        }
    }

    private static class EndpointResult {
        final String endpointKey;
        final String entityType;
        final String displayName;
        final String apiPath;
        boolean complete = true;
        int apiCalls;
        int seen;
        int inserted;
        int updated;
        int unchanged;
        int downloaded;
        int skippedExisting;
        long fileBytes;
        final List<String> warnings = new ArrayList<>();

        EndpointResult(String endpointKey, String entityType, String displayName, String apiPath) {
            this.endpointKey = endpointKey;
            this.entityType = entityType;
            this.displayName = displayName;
            this.apiPath = apiPath;
        }

        void add(UpsertResult result) {
            if (result == null) return;
            seen++;
            inserted += result.inserted;
            updated += result.updated;
            unchanged += result.unchanged;
        }

        void add(EndpointResult other) {
            if (other == null) return;
            apiCalls += other.apiCalls;
            seen += other.seen;
            inserted += other.inserted;
            updated += other.updated;
            unchanged += other.unchanged;
            downloaded += other.downloaded;
            skippedExisting += other.skippedExisting;
            fileBytes += other.fileBytes;
            warnings.addAll(other.warnings);
            complete = complete && other.complete;
        }

        void warning(String warning) {
            if (warning != null && !warning.isBlank()) warnings.add(warning);
        }

        Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("endpointKey", endpointKey);
            row.put("entityType", entityType);
            row.put("displayName", displayName);
            row.put("apiPath", apiPath);
            row.put("complete", complete);
            row.put("apiCalls", apiCalls);
            row.put("seen", seen);
            row.put("inserted", inserted);
            row.put("updated", updated);
            row.put("unchanged", unchanged);
            row.put("downloaded", downloaded);
            row.put("skippedExisting", skippedExisting);
            row.put("fileBytes", fileBytes);
            row.put("warnings", warnings);
            return row;
        }
    }

    public static class UpsertResult {
        final int inserted;
        final int updated;
        final int unchanged;

        private UpsertResult(int inserted, int updated, int unchanged) {
            this.inserted = inserted;
            this.updated = updated;
            this.unchanged = unchanged;
        }

        static UpsertResult inserted() {
            return new UpsertResult(1, 0, 0);
        }

        static UpsertResult updated() {
            return new UpsertResult(0, 1, 0);
        }

        static UpsertResult unchanged() {
            return new UpsertResult(0, 0, 1);
        }
    }

    static class EndpointSpec {
        final String key;
        final String displayName;
        final String entityType;
        final String path;
        final String arrayField;
        final boolean paginated;
        final boolean supportsCreatedFilter;
        final int limit;
        PaginationMode paginationMode = PaginationMode.PAGE_AND_OFFSET;
        String skipWarning = "";
        boolean storeRootAsSingleton;
        boolean fetchGroupMembers;
        boolean fetchAffiliateRelated;
        boolean fetchAssetFolderContents;

        EndpointSpec(String key, String displayName, String entityType, String path, String arrayField,
                     boolean paginated, boolean supportsCreatedFilter) {
            this(key, displayName, entityType, path, arrayField, paginated, supportsCreatedFilter, DEFAULT_LIMIT);
        }

        EndpointSpec(String key, String displayName, String entityType, String path, String arrayField,
                     boolean paginated, boolean supportsCreatedFilter, int limit) {
            this.key = key;
            this.displayName = displayName;
            this.entityType = entityType;
            this.path = path;
            this.arrayField = arrayField;
            this.paginated = paginated;
            this.supportsCreatedFilter = supportsCreatedFilter;
            this.limit = limit;
        }

        EndpointSpec pagination(PaginationMode mode) {
            this.paginationMode = mode == null ? PaginationMode.PAGE_AND_OFFSET : mode;
            return this;
        }

        EndpointSpec singlePagePartial(String warning) {
            this.paginationMode = PaginationMode.SINGLE_PAGE_PARTIAL;
            this.skipWarning = Objects.toString(warning, "");
            return this;
        }

        EndpointSpec skip(String warning) {
            this.paginationMode = PaginationMode.SKIP_WITH_WARNING;
            this.skipWarning = Objects.toString(warning, "");
            return this;
        }

        EndpointSpec singleton() {
            this.storeRootAsSingleton = true;
            return this;
        }

        EndpointSpec groupMembers(boolean value) {
            this.fetchGroupMembers = value;
            return this;
        }

        EndpointSpec affiliateRelated(boolean value) {
            this.fetchAffiliateRelated = value;
            return this;
        }

        EndpointSpec assetFolderContents(boolean value) {
            this.fetchAssetFolderContents = value;
            return this;
        }
    }
}
