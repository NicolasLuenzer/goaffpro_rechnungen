import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;

import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.awt.Color;
import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.security.MessageDigest;
import java.util.stream.Collectors;


public class WebUiServer {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[XXX]");
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Path CONFIG_PATH = Paths.get(
            System.getenv().getOrDefault("CONFIG_PATH", "src/main/java/config.properties"));
    private static final Path UI_PATH = Paths.get(
            System.getenv().getOrDefault("UI_PATH", "src/main/resources/ui/dashboard.html"));
    private static final Path HELP_DOC_PATH = Paths.get(
            System.getenv().getOrDefault("HELP_DOC_PATH", "docs/HILFE.md"));
    private static final String VEMMINA_LOGO_DATA_URI = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAMgAAAAwBAMAAACmtyjZAAAAMFBMVEUADCcEjM4chLcegrIld58cgrMofqgpfaef6/wDi85artcr"
            + "fqfL//9rzvgagbNWlLI1RuKaAAAAAXRSTlMAQObYZgAABfVJREFUeNqlmFFsFFUUhr+ZnXULlnZmqQFqA1vERI3KAAEVoQ6ExBCJ"
            + "KTXEFyMlxCcfWI1CUERM+mjMEoNJiQ8kPhiNDw3EAKJ13FZITAxr5IXE1A2WQIPQqYnQprM7PtzZmXtnFilwkmZn7t65/5zzn/Of"
            + "s9UGAb50UWx74l6xlkPc2fw3AZy+wz8UQLMAllXULd0e92srXTC6Cm0XL4IBwLi6Iadi7KNzj7qhq38gWm19o+WL8TSICfR8k+9d"
            + "tsRFtywgSPiq3C16ZfAqaGa8os3+G64C5tbFH5bSIBeAtkLp8nAR9LGxf6DuKBt6lLuBdVMc6Np/Zt+Sk6ev7TvNkn2nBloYWDfF"
            + "/q63zr0Pj1Un3luUBJkCOnB/8wdABw1Q4+OlPK+xZbx48NwwR6H+yOpti0U82PpX8QNuFhdvSSVDDdh28/IqNNDBLyRIMarK9gMf"
            + "nXKo0VIyZqI1u7G6vZQBiqcvJUGCIrzDH8+xEnTxpgopCYayV8vZ+kGftw/klviTwJ8f20G4agwXcOd/X1rxbIoUFzrnb/GMbQKk"
            + "nCBFpYTxpdqZI2tP7vh0Yqv9KC4TL2/NOuHqiTGt9qL77srWdGGNg7HuWkfGFYTk5quVskoNF5o5fcuaxPQ04aP4EKvWJID22Uwx"
            + "CaJfJ/dM2ej7WngyYyqkJCiBYPIWk+A14hhIq5Pi+nIKg7rDjLH2k04ECLbwqDklczHtiJlY8cCDYbOvRHi43i5kQNimCnrBFtcV"
            + "8VnBzlAjQy3Dr3YGqFUydo0MNaCyRmQsFVhDjacP0fL4eRaoGZezLKs3pkS+uRfTLGtp1rIWxvQQkjIqU3J/+liCfGBKGSsE0nZj"
            + "UjQwXHaLbMusL+31tEGA3RiDxs7Kqs8BY+fvv2DszBxl7/XMUSD6BIxDYPq2m3xV3bLyDdxNltUB3ZZlWZaVL7JKfJWzrIcwukUQ"
            + "9O78ELnufD+7wgd3WfnGYRnLWgibLatDCRdZCMxYqbrUTAs8YH2YNHUH2OMFUPeCChWCasi5VMs6jEgZK0BqptBmwHBD7ZPssiya"
            + "HhhD8re30p2kHQIT31FAfFtos6BET2qE75CLKvRK6Bq6qdkAs2qfMFx4UhxpKiB4YabDRqUwo7d/PrqedUJxqz21ugpSoKO3xBMN"
            + "ZVTJLs7OJyiWANqh628AJm/AzdcAuEF7TJNnCAL841ITjG3jeRGKkdbobfW4xQyFxd3wsprP57tE5GYNKYI3Ik8NJ2qCsbWHoQhM"
            + "ao4C4jshgTkPLTywP35wOiudMtsYAY4vLUQPx1YJs9O3o0AacUv2AWYh22zWkq6nw0dze6akySTyzmvcT0WJHXrCSNi4XoB5c5UP"
            + "r6HXo2pbDUMxEmW3LlWdJ/7sVLtONYCWJhtCG4NMuGo2Zis9LgXGb6eOHlCQD7yS6k+R9UaYvt34QpcOCiAQxQTAsejBB6S4mxVo"
            + "yTZ5i1jDNyiNSyKe0VbqjttTiX3/dkeBSxMuZL0nqgvccD4urWglqysIptS8A6C8PMqzcRVEkOJFpQgHAO11V6RPXzSJzhaqyydk"
            + "kE3nJVJ6KhBMJtjSkUkxqgl1FBLLSF6advMdqrb9DHWnv0k2R2zpclyzQRN1BPC7pZufXlK1aqYQk2K4zdiKwkW5jem6oo5WnFTf"
            + "y4iltLZXJXVMjngyiBY2rAYlnC+Ifn1HK7dFh9fA2NzI9vrYsCAlBvELVTWo/XOeToBwqt9zjIe/ivN5AXXHlTkRx+vu3U8nfiG6"
            + "HAJHaXaYCvGUVc/uwkxJHbWSSvuoCqLBXaijbGelssgobIlT9YTXd5wdi03WamY8qAQJtnxHAcEErXIvnvi9cXg2JNkyVZAyZObM"
            + "u+LR4dtNuENwQQXRVGePzd0VPaIkkZ3LYUrNJr9QjUsRTqyxiQbceJruBeqpJK6G6vjgdXUIA//VgqGmoqxvBxlDK5buIolNyKs/"
            + "SmY6Pb5TwkU5VYrBbcOjgosaq6QnXFtVYRr/zbmt1f9fWch4acHwUiD+tqYnzFPpbXa+7wgCUlt+BPgPQKv0SnQ63XcAAAAASUVO"
            + "RK5CYII=";
    private static final String COMMISSION_HISTORY_KEY = "lastImportedComissionHistory";
    private static final String COMMISSION_HISTORY_DATES_KEY = "lastImportedComissionHistoryDates";
    private static final String MAIL_LOG_KEY = "sentMailLogJson";
    private static final String REMINDER_LOG_KEY = "sentReminderLogJson";
    private static final String LEADER_WEEKLY_MAIL_LOG_KEY = "sentLeaderWeeklyMailLogJson";
    private static final ZoneId BERLIN_ZONE = ZoneId.of("Europe/Berlin");
    private static final int LEADER_NEW_CUSTOMER_MONTHLY_TARGET = 40;
    private static final DayOfWeek DEFAULT_LEADER_WEEKLY_MAIL_DAY = DayOfWeek.MONDAY;
    private static final LocalTime DEFAULT_LEADER_WEEKLY_MAIL_TIME = LocalTime.of(8, 0);
    private static ScheduledExecutorService leaderWeeklyMailScheduler;
    private static ScheduledExecutorService goaffproSyncScheduler;
    private static final GoAffProSyncService GOAFFPRO_SYNC_SERVICE = new GoAffProSyncService();
    private static final String DEFAULT_PDF_EXPORT_PATH =
            System.getenv().getOrDefault("PDF_EXPORT_PATH", "C:\\Users\\nluenzer\\Downloads\\goaffpro");
    private static final String UI_SETTINGS_FILENAME = "goaffpro_ui_settings.properties";
    private static final String DEFAULT_GOAFFPRO_API_KEY = "91bdb6e219f5b9ffeff929077b4badd5d7a26c235c672e20285885835683b845";
    private static final List<String> DEFAULT_COMMISSION_HISTORY = List.of("2103705", "2167905", "2190357", "2230376", "2336836", "2421355", "2497986", "2565325");
    private static final BuildInfo BUILD_INFO = detectBuildInfo();
    private static final String APP_VERSION = BUILD_INFO.version();
    private static final Object CONFIG_LOCK = new Object();

    // ── Datensicherung: Hintergrund-Job (Muster wie GoAffProSyncService.startAsync) ──
    private static final java.util.concurrent.atomic.AtomicBoolean BACKUP_RUNNING =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static volatile Map<String, Object> BACKUP_STATE = null;
    private static final java.util.concurrent.ExecutorService BACKUP_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "goaffpro-backup");
                t.setDaemon(true);
                return t;
            });

    public static void main(String[] args) throws IOException {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(8080), 0);
        } catch (java.net.BindException e) {
            System.err.println("Port 8080 ist bereits belegt.");
            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            if (isWindows) {
                System.err.println("Versuche, den bestehenden Prozess zu beenden...");
                try {
                    Process netstat = new ProcessBuilder("netstat", "-ano").start();
                    String output = new String(netstat.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    netstat.waitFor();
                    String pid = null;
                    for (String line : output.split("\n")) {
                        if (line.contains(":8080") && line.contains("LISTENING")) {
                            String[] parts = line.trim().split("\\s+");
                            pid = parts[parts.length - 1].trim();
                            break;
                        }
                    }
                    if (pid == null) {
                        System.err.println("Kein Prozess auf Port 8080 gefunden.");
                        throw e;
                    }
                    System.err.println("Beende Prozess PID " + pid + "...");
                    new ProcessBuilder("taskkill", "/F", "/PID", pid).start().waitFor();
                    Thread.sleep(1500);
                } catch (java.net.BindException be) {
                    throw be;
                } catch (Exception ex) {
                    System.err.println("Konnte Prozess nicht automatisch beenden: " + ex.getMessage());
                    System.err.println("Bitte manuell beenden: netstat -ano | findstr :8080, dann: taskkill /F /PID <PID>");
                    throw e;
                }
                server = HttpServer.create(new InetSocketAddress(8080), 0);
            } else {
                System.err.println("Bitte den blockierenden Prozess manuell beenden (z.B. lsof -i :8080).");
                throw e;
            }
        }
        server.createContext("/", new UiHandler());
        server.createContext("/api/executables", new ExecutablesHandler());
        server.createContext("/api/provisionen-goaffpro/poll", new PollGoaffproHandler());
        server.createContext("/api/settings", new SettingsHandler());
        server.createContext("/api/settings/recipient-mode", new RecipientModeHandler());
        server.createContext("/api/provisionen-goaffpro/export-pdf", new ExportPdfHandler());
        server.createContext("/api/provisionen-goaffpro/invoice-details-pdf", new InvoiceDetailsPdfHandler());
        server.createContext("/api/mail-log", new MailLogHandler());
        server.createContext("/api/mail-log/download", new MailLogDownloadHandler());
        server.createContext("/api/version", new VersionHandler());
        server.createContext("/api/version/history", new VersionHistoryHandler());
        server.createContext("/api/analytics/fetch", new AnalyticsFetchHandler());
        server.createContext("/api/analytics/advisor-detail", new AnalyticsAdvisorDetailHandler());
        server.createContext("/api/analytics/parties", new AnalyticsPartiesHandler());
        server.createContext("/api/analytics/new-customers", new AnalyticsNewCustomersHandler());
        server.createContext("/api/analytics/new-customers/leaders", new AnalyticsLeaderNewCustomersHandler());
        server.createContext("/api/analytics/new-customers/leaders/weekly-mails/preview", new LeaderWeeklyMailPreviewHandler());
        server.createContext("/api/analytics/new-customers/leaders/weekly-mails/send", new LeaderWeeklyMailSendHandler());
        server.createContext("/api/sync/status", new GoAffProSyncStatusHandler());
        server.createContext("/api/sync/inventory", new GoAffProSyncInventoryHandler());
        server.createContext("/api/sync/runs", new GoAffProSyncRunsHandler());
        server.createContext("/api/sync/run", new GoAffProSyncRunHandler());
        server.createContext("/api/sync/diagnostics/run", new GoAffProSyncDiagnosticsRunHandler());
        server.createContext("/api/sync/diagnostics/latest", new GoAffProSyncDiagnosticsLatestHandler());
        server.createContext("/api/sync/diagnostics/runs", new GoAffProSyncDiagnosticsRunsHandler());
        server.createContext("/api/sync/pause", new GoAffProSyncPauseHandler());
        server.createContext("/api/sync/resume", new GoAffProSyncResumeHandler());
        server.createContext("/api/commissions/add-latest", new AddLatestCommissionHandler());
        server.createContext("/api/commissions/remove", new RemoveCommissionHandler());
        server.createContext("/api/commissions/rebuild-from-payments", new RebuildCommissionHistoryHandler());
        server.createContext("/api/help", new HelpHandler());
        server.createContext("/api/validation/advisors", new ValidationAdvisorsHandler());
        server.createContext("/api/validation/advisors/tree", new ValidationAdvisorTreeHandler());
        server.createContext("/api/validation/send-reminder", new ValidationReminderMailHandler());
        server.createContext("/api/validation/reminder-log", new ValidationReminderLogHandler());
        server.createContext("/api/backup/status", new BackupStatusHandler());
        server.createContext("/api/backup/export", new BackupExportHandler());
        server.createContext("/api/backup/download", new BackupDownloadHandler());
        server.createContext("/api/backup/import/upload", new BackupImportUploadHandler());
        server.createContext("/api/backup/import/apply", new BackupImportApplyHandler());
        // Ohne Executor bearbeitet der Dispatcher-Thread alle Anfragen nacheinander - ein
        // grosser Download (Datensicherung) wuerde die gesamte Anwendung blockieren.
        server.setExecutor(Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "http-worker");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        startLeaderWeeklyMailScheduler();
        startGoAffProSyncScheduler();

        System.out.println("Web UI Server gestartet auf http://localhost:8080");
    }

    private static void startGoAffProSyncScheduler() {
        if (goaffproSyncScheduler != null) return;
        goaffproSyncScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "goaffpro-sync-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        goaffproSyncScheduler.scheduleAtFixedRate(() -> {
            try {
                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);
                String apiKey = getSecretOrConfig(config, "GOAFFPRO_API_KEY", "goaffproAPIKey", DEFAULT_GOAFFPRO_API_KEY).trim();
                if (GOAFFPRO_SYNC_SERVICE.shouldRunNightly(config)) {
                    GOAFFPRO_SYNC_SERVICE.startAsync(config, apiKey, "deep");
                } else if (GOAFFPRO_SYNC_SERVICE.shouldRunHourly(config)) {
                    GOAFFPRO_SYNC_SERVICE.startAsync(config, apiKey, "delta");
                }
            } catch (Exception e) {
                System.err.println("GoAffPro Sync Scheduler: " + e.getMessage());
            }
        }, 90, 60, TimeUnit.SECONDS);
    }

    private static void startLeaderWeeklyMailScheduler() {
        if (leaderWeeklyMailScheduler != null) return;
        leaderWeeklyMailScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "leader-weekly-mail-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        leaderWeeklyMailScheduler.scheduleAtFixedRate(() -> {
            try {
                runLeaderWeeklyMailSchedulerTick();
            } catch (Exception e) {
                System.err.println("Führungskräfte-Wochenmail-Scheduler: " + e.getMessage());
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    private static void runLeaderWeeklyMailSchedulerTick() throws Exception {
        Properties config = loadConfig();
        Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
        mergeUiSettingsIntoConfig(config, uiSettings);
        if (!Boolean.parseBoolean(Objects.toString(config.getProperty("leaderWeeklyMailSchedulerEnabled"), "false"))) {
            return;
        }
        if (!Boolean.parseBoolean(Objects.toString(config.getProperty("sendEmailsEnabled"), "true"))) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(BERLIN_ZONE);
        DayOfWeek scheduledDay = parseLeaderWeeklyMailScheduleDay(Objects.toString(config.getProperty("leaderWeeklyMailScheduleDay"), ""));
        LocalTime scheduledTime = parseLeaderWeeklyMailScheduleTime(Objects.toString(config.getProperty("leaderWeeklyMailScheduleTime"), ""));
        if (now.getDayOfWeek() != scheduledDay || now.toLocalTime().isBefore(scheduledTime)) {
            return;
        }
        LeaderWeeklyMailPeriods periods = leaderWeeklyMailPeriods(now.toLocalDate());
        String lastSentPeriodKey = Objects.toString(config.getProperty("leaderWeeklyMailLastSentPeriodKey"), "").trim();
        if (periods.periodKey().equals(lastSentPeriodKey)) {
            return;
        }
        sendLeaderWeeklyMails(config, now.toLocalDate(), true, true);
    }

    private static String normalizeLeaderWeeklyMailScheduleDay(String raw) {
        return parseLeaderWeeklyMailScheduleDay(raw).name();
    }

    private static String normalizePositiveInteger(String raw, String fallback) {
        try {
            int value = Integer.parseInt(Objects.toString(raw, "").trim());
            return String.valueOf(Math.max(1, value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String normalizeNonNegativeInteger(String raw, String fallback) {
        try {
            int value = Integer.parseInt(Objects.toString(raw, "").trim());
            return String.valueOf(Math.max(0, value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String normalizePositiveLong(String raw, String fallback) {
        try {
            long value = Long.parseLong(Objects.toString(raw, "").trim());
            return String.valueOf(Math.max(0L, value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static DayOfWeek parseLeaderWeeklyMailScheduleDay(String raw) {
        String value = Objects.toString(raw, "").trim().toUpperCase(java.util.Locale.ROOT);
        if (value.isBlank()) return DEFAULT_LEADER_WEEKLY_MAIL_DAY;
        return switch (value) {
            case "1", "MONDAY", "MONTAG" -> DayOfWeek.MONDAY;
            case "2", "TUESDAY", "DIENSTAG" -> DayOfWeek.TUESDAY;
            case "3", "WEDNESDAY", "MITTWOCH" -> DayOfWeek.WEDNESDAY;
            case "4", "THURSDAY", "DONNERSTAG" -> DayOfWeek.THURSDAY;
            case "5", "FRIDAY", "FREITAG" -> DayOfWeek.FRIDAY;
            case "6", "SATURDAY", "SAMSTAG", "SONNABEND" -> DayOfWeek.SATURDAY;
            case "7", "SUNDAY", "SONNTAG" -> DayOfWeek.SUNDAY;
            default -> DEFAULT_LEADER_WEEKLY_MAIL_DAY;
        };
    }

    private static String normalizeLeaderWeeklyMailScheduleTime(String raw) {
        return parseLeaderWeeklyMailScheduleTime(raw).format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private static LocalTime parseLeaderWeeklyMailScheduleTime(String raw) {
        String value = Objects.toString(raw, "").trim();
        if (value.isBlank()) return DEFAULT_LEADER_WEEKLY_MAIL_TIME;
        try {
            return LocalTime.parse(value.length() == 5 ? value : value.substring(0, Math.min(value.length(), 5)));
        } catch (Exception ignored) {
            return DEFAULT_LEADER_WEEKLY_MAIL_TIME;
        }
    }


    private static class UiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "text/plain", "");
                return;
            }

            URI requestUri = exchange.getRequestURI();
            if (!"/".equals(requestUri.getPath())) {
                sendResponse(exchange, 404, "text/plain", "Not found");
                return;
            }

            if (!Files.exists(UI_PATH)) {
                sendResponse(exchange, 404, "text/plain", "dashboard.html nicht gefunden: " + UI_PATH);
                return;
            }

            String html = Files.readString(UI_PATH, StandardCharsets.UTF_8);
            sendResponse(exchange, 200, "text/html; charset=utf-8", html);
        }
    }

    private static class ExecutablesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "[]");
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }

            List<Map<String, String>> executables = findExecutableJavaFiles();
            sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(executables));
        }

        private List<Map<String, String>> findExecutableJavaFiles() throws IOException {
            Path javaRoot = Paths.get("src/main/java");
            if (!Files.exists(javaRoot)) {
                return Collections.emptyList();
            }

            List<Map<String, String>> files = new ArrayList<>();
            try (var paths = Files.walk(javaRoot)) {
                List<Path> javaFiles = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .collect(Collectors.toList());

                for (Path file : javaFiles) {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    if (content.contains("public static void main(String[] args)")) {
                        String className = file.getFileName().toString().replace(".java", "");
                        Map<String, String> entry = new LinkedHashMap<>();
                        entry.put("id", className.toLowerCase());
                        entry.put("name", className);
                        entry.put("path", javaRoot.relativize(file).toString());
                        files.add(entry);
                    }
                }
            }

            files.sort((a, b) -> a.get("name").compareToIgnoreCase(b.get("name")));
            return files;
        }
    }

    private static class PollGoaffproHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }

            try {
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);

                String apiKey = getSecretOrConfig(config, "GOAFFPRO_API_KEY", "goaffproAPIKey", DEFAULT_GOAFFPRO_API_KEY).trim();
                String requestedCommission = asText(body, "sinceId").trim();
                String activeLastImportedComission = requestedCommission.isBlank()
                        ? Objects.toString(config.getProperty("lastImportedComission"), "0").trim()
                        : requestedCommission;

                String paymentsUrl = "https://api.goaffpro.com/v1/admin/payments?since_id=" + activeLastImportedComission
                        + "&fields=id,affiliate_id,amount,currency,payment_method,payment_details,affiliate_message,admin_note,created_at";

                JsonNode paymentRoot = requestJson(paymentsUrl, apiKey);
                JsonNode payments = paymentRoot.get("payments");

                if (payments == null || !payments.isArray() || payments.size() == 0) {
                    config.setProperty("lastImportedComission", activeLastImportedComission);
                    ensureCommissionInHistory(config, activeLastImportedComission);
                    persistSettings(config);

                    Map<String, Object> emptyResult = new HashMap<>();
                    emptyResult.put("payments", Collections.emptyList());
                    emptyResult.put("message", "Keine neuen Zahlungen gefunden.");
                    emptyResult.put("lastImportedComission", activeLastImportedComission);
                    emptyResult.put("lastImportedComissionHistory", getCommissionHistory(config));
                    emptyResult.put("commissionHistoryLabels", buildCommissionHistoryLabels(config));
                    sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(emptyResult));
                    return;
                }

                List<String> affiliateIds = new ArrayList<>();
                for (JsonNode payment : payments) {
                    String affiliateId = asText(payment, "affiliate_id");
                    if (!affiliateId.isBlank() && !affiliateIds.contains(affiliateId)) {
                        affiliateIds.add(affiliateId);
                    }
                }

                Map<String, JsonNode> affiliatesById = fetchAffiliatesById(apiKey, affiliateIds);

                String highestId = activeLastImportedComission;
                String highestDate = "";
                List<Map<String, String>> responsePayments = new ArrayList<>();
                for (JsonNode payment : payments) {
                    String paymentId = asText(payment, "id");
                    String createdAt = asText(payment, "created_at");
                    String affiliateId = asText(payment, "affiliate_id");
                    JsonNode affiliate = affiliatesById.get(affiliateId);

                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("paymentId", paymentId);
                    item.put("belegdatum", toGermanDate(createdAt));
                    item.put("affiliateName", affiliate != null ? asText(affiliate, "name") : "");
                    item.put("affiliateEmail", affiliate != null ? asText(affiliate, "email") : "");
                    item.put("affiliateAddress", formatAffiliateAddress(affiliate));
                    item.put("affiliateCountry", affiliate != null ? asText(affiliate, "country") : "");
                    item.put("affiliateSteuernummer", affiliate != null ? asText(affiliate, "tax_identification_number") : "");
                    item.put("affiliatePhone", affiliate != null ? asText(affiliate, "phone") : "");
                    item.put("affiliateCompany", affiliate != null ? asText(affiliate, "company_name") : "");
                    String iban = asText(payment.path("payment_details"), "account_number").trim();
                    item.put("iban", iban);
                    item.put("ibanBic", asText(payment.path("payment_details"), "branch_code"));
                    item.put("ibanOwner", asText(payment.path("payment_details"), "account_name"));
                    item.put("hasIban", iban.isBlank() ? "Nein" : "Ja");
                    item.put("hasValidIban", isValidIban(iban) ? "Ja" : "Nein");
                    item.put("amount", asText(payment, "amount"));
                    item.put("currency", asText(payment, "currency"));
                    responsePayments.add(item);

                    if (isGreaterNumeric(paymentId, highestId)) {
                        highestId = paymentId;
                        highestDate = toGermanDate(createdAt);
                    }
                }

                config.setProperty("lastImportedComission", activeLastImportedComission);
                ensureCommissionInHistory(config, activeLastImportedComission);
                ensureCommissionInHistory(config, highestId);
                if (!highestDate.isBlank()) {
                    setCommissionDate(config, highestId, highestDate);
                }
                persistSettings(config);

                Map<String, Object> result = new HashMap<>();
                result.put("payments", responsePayments);
                result.put("message", responsePayments.size() + " neue Zahlung(en) gefunden.");
                result.put("lastImportedComission", activeLastImportedComission);
                result.put("highestDiscoveredComission", highestId);
                result.put("lastImportedComissionHistory", getCommissionHistory(config));
                result.put("commissionHistoryLabels", buildCommissionHistoryLabels(config));
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(result));
            } catch (Exception e) {
                Map<String, String> err = new HashMap<>();
                err.put("error", e.getMessage());
                sendResponse(exchange, 500, "application/json", OBJECT_MAPPER.writeValueAsString(err));
            }
        }
    }

    private static class SettingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                Properties config = loadConfig();
                Path settingsDir = resolveSettingsDirectory(config);
                Properties uiSettings = loadUiSettings(settingsDir);
                mergeUiSettingsIntoConfig(config, uiSettings);

                String exportDir = Objects.toString(config.getProperty("pdfExportPath"), DEFAULT_PDF_EXPORT_PATH);
                String activeCommission = Objects.toString(config.getProperty("lastImportedComission"), "0").trim();
                String goaffproAPIKey = getSecretOrConfig(config, "GOAFFPRO_API_KEY", "goaffproAPIKey", DEFAULT_GOAFFPRO_API_KEY).trim();
                String contactEmail = Objects.toString(config.getProperty("contactEmail"), "").trim();
                String smtpHost = Objects.toString(config.getProperty("smtpHost"), "").trim();
                String smtpPort = Objects.toString(config.getProperty("smtpPort"), "587").trim();
                String smtpUsername = Objects.toString(config.getProperty("smtpUsername"), "").trim();
                String emailBcc = Objects.toString(config.getProperty("emailBcc"), "").trim();
                boolean smtpTls = Boolean.parseBoolean(Objects.toString(config.getProperty("smtpTls"), "false"));
                boolean hasSmtpPassword = !getSecretOrConfig(config, "SMTP_PASSWORD", "smtpPassword", "").trim().isBlank();
                boolean sendEmailsEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("sendEmailsEnabled"), "true"));
                String emailRecipientMode = Objects.toString(config.getProperty("emailRecipientMode"), "contact").trim();
                String emailTemplateHtml = Objects.toString(config.getProperty("emailTemplateHtml"), "");
                String validationReminderTemplateHtml = Objects.toString(config.getProperty("validationReminderTemplateHtml"), "");
                String eInvoicePdfTemplateHtml = Objects.toString(config.getProperty("eInvoicePdfTemplateHtml"), "");
                String eInvoicePdfTemplateHtmlRechnung = Objects.toString(config.getProperty("eInvoicePdfTemplateHtmlRechnung"), "");
                String emailTemplateHtmlRechnung = Objects.toString(config.getProperty("emailTemplateHtmlRechnung"), "");
                String leaderWeeklyReportTemplateHtml = Objects.toString(config.getProperty("leaderWeeklyReportTemplateHtml"), "");
                boolean leaderWeeklyMailSchedulerEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("leaderWeeklyMailSchedulerEnabled"), "false"));
                boolean leaderWeeklyMailProductionEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("leaderWeeklyMailProductionEnabled"), "false"));
                String leaderWeeklyMailScheduleDay = normalizeLeaderWeeklyMailScheduleDay(Objects.toString(config.getProperty("leaderWeeklyMailScheduleDay"), ""));
                String leaderWeeklyMailScheduleTime = normalizeLeaderWeeklyMailScheduleTime(Objects.toString(config.getProperty("leaderWeeklyMailScheduleTime"), ""));
                String leaderWeeklyMailLastSentPeriodKey = Objects.toString(config.getProperty("leaderWeeklyMailLastSentPeriodKey"), "").trim();
                boolean goaffproSyncEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("goaffproSyncEnabled"), "true"));
                boolean goaffproSyncHourlyEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("goaffproSyncHourlyEnabled"), "false"));
                boolean goaffproSyncDeepEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("goaffproSyncDeepEnabled"), "false"));
                boolean goaffproSyncAssetDownloadEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("goaffproSyncAssetDownloadEnabled"), "true"));
                String goaffproSyncMaxCallsPerHour = Objects.toString(config.getProperty("goaffproSyncMaxCallsPerHour"), "60").trim();
                boolean goaffproSyncSlidingWindowEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("goaffproSyncSlidingWindowEnabled"), "true"));
                String goaffproSyncMinCallSpacingMs = Objects.toString(config.getProperty("goaffproSyncMinCallSpacingMs"), "1500").trim();
                boolean goaffproSyncDownloadSkipExistingEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("goaffproSyncDownloadSkipExistingEnabled"), "true"));
                boolean goaffproSyncDeltaDownloadsEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("goaffproSyncDeltaDownloadsEnabled"), "false"));
                String goaffproSyncDeltaLookbackDays = Objects.toString(config.getProperty("goaffproSyncDeltaLookbackDays"), "14").trim();
                String goaffproSyncMinFreeBytes = Objects.toString(config.getProperty("goaffproSyncMinFreeBytes"), String.valueOf(512L * 1024L * 1024L)).trim();
                String goaffproSyncDataPath = Objects.toString(config.getProperty("goaffproSyncDataPath"), GoAffProSyncService.resolveDataDir(config).toString()).trim();
                boolean eInvoiceEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("eInvoiceEnabled"), "true"));
                boolean eInvoiceAttachAndStoreEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("eInvoiceAttachAndStoreEnabled"), "true"));
                String eInvoiceBuyerName = Objects.toString(config.getProperty("eInvoiceBuyerName"), "S+R linear technology gmbh").trim();
                String eInvoiceBuyerStreet = Objects.toString(config.getProperty("eInvoiceBuyerStreet"), "").trim();
                String eInvoiceBuyerZip = Objects.toString(config.getProperty("eInvoiceBuyerZip"), "").trim();
                String eInvoiceBuyerCity = Objects.toString(config.getProperty("eInvoiceBuyerCity"), "").trim();
                String eInvoiceBuyerCountry = Objects.toString(config.getProperty("eInvoiceBuyerCountry"), "DE").trim();
                String eInvoiceBuyerVatId = Objects.toString(config.getProperty("eInvoiceBuyerVatId"), "").trim();
                String eInvoiceBuyerTaxNumber = Objects.toString(config.getProperty("eInvoiceBuyerTaxNumber"), "").trim();
                String eInvoiceBankIban = Objects.toString(config.getProperty("eInvoiceBankIban"), "").trim();
                String eInvoiceBankBic = Objects.toString(config.getProperty("eInvoiceBankBic"), "").trim();
                String eInvoiceBankAccountHolder = Objects.toString(config.getProperty("eInvoiceBankAccountHolder"), "").trim();
                String eInvoicePaymentTerms = Objects.toString(config.getProperty("eInvoicePaymentTerms"), "Zahlbar sofort ohne Abzug").trim();
                String nachweisFirmenname = Objects.toString(config.getProperty("nachweisFirmenname"), "S+R Linear Technology GmbH").trim();
                String rechnungCutoffDate = rechnungCutoffDateRaw(config);
                String legacyBuyerName = Objects.toString(config.getProperty("legacyBuyerName"), DEFAULT_LEGACY_BUYER_NAME).trim();
                String legacyBuyerStreet = Objects.toString(config.getProperty("legacyBuyerStreet"), "").trim();
                String legacyBuyerZip = Objects.toString(config.getProperty("legacyBuyerZip"), "").trim();
                String legacyBuyerCity = Objects.toString(config.getProperty("legacyBuyerCity"), "").trim();
                String legacyBuyerCountry = Objects.toString(config.getProperty("legacyBuyerCountry"), "DE").trim();
                String legacyBuyerVatId = Objects.toString(config.getProperty("legacyBuyerVatId"), "").trim();
                String legacyBuyerTaxNumber = Objects.toString(config.getProperty("legacyBuyerTaxNumber"), "").trim();
                String legacyNachweisFirmenname = Objects.toString(config.getProperty("legacyNachweisFirmenname"), DEFAULT_LEGACY_BUYER_NAME).trim();
                ensureCommissionInHistory(config, activeCommission);
                persistSettings(config);

                Map<String, Object> payload = new HashMap<>();
                payload.put("pdfExportPath", exportDir);
                payload.put("settingsDirectory", resolveSettingsDirectory(config).toString());
                payload.put("lastImportedComission", activeCommission);
                payload.put("goaffproAPIKey", goaffproAPIKey);
                payload.put("contactEmail", contactEmail);
                payload.put("smtpHost", smtpHost);
                payload.put("smtpPort", smtpPort);
                payload.put("smtpUsername", smtpUsername);
                payload.put("emailBcc", emailBcc);
                payload.put("smtpTls", smtpTls);
                payload.put("hasSmtpPassword", hasSmtpPassword);
                payload.put("sendEmailsEnabled", sendEmailsEnabled);
                payload.put("emailRecipientMode", emailRecipientMode);
                payload.put("emailTemplateHtml", emailTemplateHtml.isBlank() ? getDefaultInvoiceMailHtmlTemplate() : emailTemplateHtml);
                payload.put("emailTemplateHtmlDefault", getDefaultInvoiceMailHtmlTemplate());
                payload.put("validationReminderTemplateHtml", validationReminderTemplateHtml.isBlank() ? getDefaultValidationReminderHtmlTemplate() : validationReminderTemplateHtml);
                payload.put("validationReminderTemplateHtmlDefault", getDefaultValidationReminderHtmlTemplate());
                payload.put("eInvoicePdfTemplateHtml", eInvoicePdfTemplateHtml.isBlank() ? getDefaultEInvoicePdfViewHtmlTemplate() : eInvoicePdfTemplateHtml);
                payload.put("eInvoicePdfTemplateHtmlDefault", getDefaultEInvoicePdfViewHtmlTemplate());
                payload.put("eInvoicePdfTemplateHtmlRechnung", eInvoicePdfTemplateHtmlRechnung.isBlank() ? getDefaultRechnungPdfViewHtmlTemplate() : eInvoicePdfTemplateHtmlRechnung);
                payload.put("eInvoicePdfTemplateHtmlRechnungDefault", getDefaultRechnungPdfViewHtmlTemplate());
                payload.put("emailTemplateHtmlRechnung", emailTemplateHtmlRechnung.isBlank() ? getDefaultRechnungMailHtmlTemplate() : emailTemplateHtmlRechnung);
                payload.put("emailTemplateHtmlRechnungDefault", getDefaultRechnungMailHtmlTemplate());
                payload.put("leaderWeeklyReportTemplateHtml", leaderWeeklyReportTemplateHtml.isBlank() ? getDefaultLeaderWeeklyReportHtmlTemplate() : leaderWeeklyReportTemplateHtml);
                payload.put("leaderWeeklyReportTemplateHtmlDefault", getDefaultLeaderWeeklyReportHtmlTemplate());
                payload.put("leaderWeeklyMailSchedulerEnabled", leaderWeeklyMailSchedulerEnabled);
                payload.put("leaderWeeklyMailProductionEnabled", leaderWeeklyMailProductionEnabled);
                payload.put("leaderWeeklyMailScheduleDay", leaderWeeklyMailScheduleDay);
                payload.put("leaderWeeklyMailScheduleTime", leaderWeeklyMailScheduleTime);
                payload.put("leaderWeeklyMailLastSentPeriodKey", leaderWeeklyMailLastSentPeriodKey);
                payload.put("goaffproSyncEnabled", goaffproSyncEnabled);
                payload.put("goaffproSyncHourlyEnabled", goaffproSyncHourlyEnabled);
                payload.put("goaffproSyncDeepEnabled", goaffproSyncDeepEnabled);
                payload.put("goaffproSyncAssetDownloadEnabled", goaffproSyncAssetDownloadEnabled);
                payload.put("goaffproSyncMaxCallsPerHour", goaffproSyncMaxCallsPerHour);
                payload.put("goaffproSyncSlidingWindowEnabled", goaffproSyncSlidingWindowEnabled);
                payload.put("goaffproSyncMinCallSpacingMs", goaffproSyncMinCallSpacingMs);
                payload.put("goaffproSyncDownloadSkipExistingEnabled", goaffproSyncDownloadSkipExistingEnabled);
                payload.put("goaffproSyncDeltaDownloadsEnabled", goaffproSyncDeltaDownloadsEnabled);
                payload.put("goaffproSyncDeltaLookbackDays", goaffproSyncDeltaLookbackDays);
                payload.put("goaffproSyncMinFreeBytes", goaffproSyncMinFreeBytes);
                payload.put("goaffproSyncDataPath", goaffproSyncDataPath);
                payload.put("goaffproSyncDbPath", GoAffProSyncService.resolveDbPath(config).toString());
                payload.put("eInvoiceEnabled", eInvoiceEnabled);
                payload.put("eInvoiceAttachAndStoreEnabled", eInvoiceAttachAndStoreEnabled);
                payload.put("eInvoiceBuyerName", eInvoiceBuyerName);
                payload.put("eInvoiceBuyerStreet", eInvoiceBuyerStreet);
                payload.put("eInvoiceBuyerZip", eInvoiceBuyerZip);
                payload.put("eInvoiceBuyerCity", eInvoiceBuyerCity);
                payload.put("eInvoiceBuyerCountry", eInvoiceBuyerCountry);
                payload.put("eInvoiceBuyerVatId", eInvoiceBuyerVatId);
                payload.put("eInvoiceBuyerTaxNumber", eInvoiceBuyerTaxNumber);
                payload.put("eInvoiceBankIban", eInvoiceBankIban);
                payload.put("eInvoiceBankBic", eInvoiceBankBic);
                payload.put("eInvoiceBankAccountHolder", eInvoiceBankAccountHolder);
                payload.put("eInvoicePaymentTerms", eInvoicePaymentTerms);
                payload.put("nachweisFirmenname", nachweisFirmenname);
                payload.put("rechnungCutoffDate", rechnungCutoffDate);
                payload.put("legacyBuyerName", legacyBuyerName);
                payload.put("legacyBuyerStreet", legacyBuyerStreet);
                payload.put("legacyBuyerZip", legacyBuyerZip);
                payload.put("legacyBuyerCity", legacyBuyerCity);
                payload.put("legacyBuyerCountry", legacyBuyerCountry);
                payload.put("legacyBuyerVatId", legacyBuyerVatId);
                payload.put("legacyBuyerTaxNumber", legacyBuyerTaxNumber);
                payload.put("legacyNachweisFirmenname", legacyNachweisFirmenname);
                payload.put("lastImportedComissionHistory", getCommissionHistory(config));
                payload.put("commissionHistoryLabels", buildCommissionHistoryLabels(config));
                payload.put("commissionDaySummary", buildCommissionDaySummary(config));
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    String newPath = asText(body, "pdfExportPath").trim();
                    String selectedCommission = asText(body, "lastImportedComission").trim();
                    String goaffproAPIKey = asText(body, "goaffproAPIKey").trim();
                    String contactEmail = asText(body, "contactEmail").trim();
                    String smtpHost = asText(body, "smtpHost").trim();
                    String smtpPort = asText(body, "smtpPort").trim();
                    String smtpUsername = asText(body, "smtpUsername").trim();
                    String emailBcc = asText(body, "emailBcc").trim();
                    String smtpPassword = asText(body, "smtpPassword").trim();
                    boolean smtpTls = body.has("smtpTls") && body.get("smtpTls").asBoolean(false);
                    boolean sendEmailsEnabled = !body.has("sendEmailsEnabled") || body.get("sendEmailsEnabled").asBoolean(true);
                    String emailRecipientMode = asText(body, "emailRecipientMode").trim();
                    String emailTemplateHtml = asText(body, "emailTemplateHtml");
                    String validationReminderTemplateHtml = asText(body, "validationReminderTemplateHtml");
                    String eInvoicePdfTemplateHtml = asText(body, "eInvoicePdfTemplateHtml");
                    String eInvoicePdfTemplateHtmlRechnung = asText(body, "eInvoicePdfTemplateHtmlRechnung");
                    String emailTemplateHtmlRechnung = asText(body, "emailTemplateHtmlRechnung");
                    String leaderWeeklyReportTemplateHtml = asText(body, "leaderWeeklyReportTemplateHtml");
                    boolean leaderWeeklyMailSchedulerEnabled = body.has("leaderWeeklyMailSchedulerEnabled") && body.get("leaderWeeklyMailSchedulerEnabled").asBoolean(false);
                    boolean leaderWeeklyMailProductionEnabled = body.has("leaderWeeklyMailProductionEnabled") && body.get("leaderWeeklyMailProductionEnabled").asBoolean(false);
                    String leaderWeeklyMailScheduleDay = normalizeLeaderWeeklyMailScheduleDay(asText(body, "leaderWeeklyMailScheduleDay"));
                    String leaderWeeklyMailScheduleTime = normalizeLeaderWeeklyMailScheduleTime(asText(body, "leaderWeeklyMailScheduleTime"));
                    boolean goaffproSyncEnabled = !body.has("goaffproSyncEnabled") || body.get("goaffproSyncEnabled").asBoolean(true);
                    boolean goaffproSyncHourlyEnabled = body.has("goaffproSyncHourlyEnabled") && body.get("goaffproSyncHourlyEnabled").asBoolean(false);
                    boolean goaffproSyncDeepEnabled = body.has("goaffproSyncDeepEnabled") && body.get("goaffproSyncDeepEnabled").asBoolean(false);
                    boolean goaffproSyncAssetDownloadEnabled = !body.has("goaffproSyncAssetDownloadEnabled") || body.get("goaffproSyncAssetDownloadEnabled").asBoolean(true);
                    String goaffproSyncMaxCallsPerHour = normalizePositiveInteger(asText(body, "goaffproSyncMaxCallsPerHour"), "60");
                    boolean goaffproSyncSlidingWindowEnabled = !body.has("goaffproSyncSlidingWindowEnabled") || body.get("goaffproSyncSlidingWindowEnabled").asBoolean(true);
                    String goaffproSyncMinCallSpacingMs = normalizeNonNegativeInteger(asText(body, "goaffproSyncMinCallSpacingMs"), "1500");
                    boolean goaffproSyncDownloadSkipExistingEnabled = !body.has("goaffproSyncDownloadSkipExistingEnabled") || body.get("goaffproSyncDownloadSkipExistingEnabled").asBoolean(true);
                    boolean goaffproSyncDeltaDownloadsEnabled = body.has("goaffproSyncDeltaDownloadsEnabled") && body.get("goaffproSyncDeltaDownloadsEnabled").asBoolean(false);
                    String goaffproSyncDeltaLookbackDays = normalizePositiveInteger(asText(body, "goaffproSyncDeltaLookbackDays"), "14");
                    String goaffproSyncMinFreeBytes = normalizePositiveLong(asText(body, "goaffproSyncMinFreeBytes"), String.valueOf(512L * 1024L * 1024L));
                    String goaffproSyncDataPath = asText(body, "goaffproSyncDataPath").trim();
                    boolean eInvoiceEnabled = !body.has("eInvoiceEnabled") || body.get("eInvoiceEnabled").asBoolean(true);
                    boolean eInvoiceAttachAndStoreEnabled = !body.has("eInvoiceAttachAndStoreEnabled") || body.get("eInvoiceAttachAndStoreEnabled").asBoolean(true);
                    String eInvoiceBuyerName = asText(body, "eInvoiceBuyerName").trim();
                    String eInvoiceBuyerStreet = asText(body, "eInvoiceBuyerStreet").trim();
                    String eInvoiceBuyerZip = asText(body, "eInvoiceBuyerZip").trim();
                    String eInvoiceBuyerCity = asText(body, "eInvoiceBuyerCity").trim();
                    String eInvoiceBuyerCountry = asText(body, "eInvoiceBuyerCountry").trim();
                    String eInvoiceBuyerVatId = asText(body, "eInvoiceBuyerVatId").trim();
                    String eInvoiceBuyerTaxNumber = asText(body, "eInvoiceBuyerTaxNumber").trim();
                    String eInvoiceBankIban = asText(body, "eInvoiceBankIban").trim();
                    String eInvoiceBankBic = asText(body, "eInvoiceBankBic").trim();
                    String eInvoiceBankAccountHolder = asText(body, "eInvoiceBankAccountHolder").trim();
                    String eInvoicePaymentTerms = asText(body, "eInvoicePaymentTerms").trim();
                    String nachweisFirmenname = asText(body, "nachweisFirmenname").trim();
                    String rechnungCutoffDate = normalizeIsoDate(asText(body, "rechnungCutoffDate"), DEFAULT_RECHNUNG_CUTOFF_DATE);
                    String legacyBuyerName = asText(body, "legacyBuyerName").trim();
                    String legacyBuyerStreet = asText(body, "legacyBuyerStreet").trim();
                    String legacyBuyerZip = asText(body, "legacyBuyerZip").trim();
                    String legacyBuyerCity = asText(body, "legacyBuyerCity").trim();
                    String legacyBuyerCountry = asText(body, "legacyBuyerCountry").trim();
                    String legacyBuyerVatId = asText(body, "legacyBuyerVatId").trim();
                    String legacyBuyerTaxNumber = asText(body, "legacyBuyerTaxNumber").trim();
                    String legacyNachweisFirmenname = asText(body, "legacyNachweisFirmenname").trim();
                    if (!"advisor".equals(emailRecipientMode)) emailRecipientMode = "contact";

                    Properties config = loadConfig();
                    Path chosenDir = newPath.isEmpty() ? resolveSettingsDirectory(config) : Paths.get(newPath).toAbsolutePath();
                    Files.createDirectories(chosenDir);

                    config.setProperty("pdfExportPath", chosenDir.toString());
                    if (!goaffproAPIKey.isEmpty()) {
                        config.setProperty("goaffproAPIKey", goaffproAPIKey);
                    }
                    if (!selectedCommission.isEmpty()) {
                        config.setProperty("lastImportedComission", selectedCommission);
                        ensureCommissionInHistory(config, selectedCommission);
                    }
                    config.setProperty("contactEmail", contactEmail);
                    config.setProperty("smtpHost", smtpHost);
                    config.setProperty("smtpPort", smtpPort.isBlank() ? "587" : smtpPort);
                    config.setProperty("smtpUsername", smtpUsername);
                    config.setProperty("emailBcc", emailBcc);
                    config.setProperty("smtpTls", String.valueOf(smtpTls));
                    if (!smtpPassword.isBlank()) {
                        config.setProperty("smtpPassword", smtpPassword);
                    }
                    config.setProperty("sendEmailsEnabled", String.valueOf(sendEmailsEnabled));
                    config.setProperty("emailRecipientMode", emailRecipientMode);
                    if (!emailTemplateHtml.isBlank()) {
                        config.setProperty("emailTemplateHtml", emailTemplateHtml);
                    } else {
                        config.remove("emailTemplateHtml");
                    }
                    if (!validationReminderTemplateHtml.isBlank()) {
                        config.setProperty("validationReminderTemplateHtml", validationReminderTemplateHtml);
                    } else {
                        config.remove("validationReminderTemplateHtml");
                    }
                    if (!eInvoicePdfTemplateHtml.isBlank()) {
                        config.setProperty("eInvoicePdfTemplateHtml", eInvoicePdfTemplateHtml);
                    } else {
                        config.remove("eInvoicePdfTemplateHtml");
                    }
                    if (!eInvoicePdfTemplateHtmlRechnung.isBlank()) {
                        config.setProperty("eInvoicePdfTemplateHtmlRechnung", eInvoicePdfTemplateHtmlRechnung);
                    } else {
                        config.remove("eInvoicePdfTemplateHtmlRechnung");
                    }
                    if (!emailTemplateHtmlRechnung.isBlank()) {
                        config.setProperty("emailTemplateHtmlRechnung", emailTemplateHtmlRechnung);
                    } else {
                        config.remove("emailTemplateHtmlRechnung");
                    }
                    if (!leaderWeeklyReportTemplateHtml.isBlank()) {
                        config.setProperty("leaderWeeklyReportTemplateHtml", leaderWeeklyReportTemplateHtml);
                    } else {
                        config.remove("leaderWeeklyReportTemplateHtml");
                    }
                    config.setProperty("leaderWeeklyMailSchedulerEnabled", String.valueOf(leaderWeeklyMailSchedulerEnabled));
                    config.setProperty("leaderWeeklyMailProductionEnabled", String.valueOf(leaderWeeklyMailProductionEnabled));
                    config.setProperty("leaderWeeklyMailScheduleDay", leaderWeeklyMailScheduleDay);
                    config.setProperty("leaderWeeklyMailScheduleTime", leaderWeeklyMailScheduleTime);
                    config.setProperty("goaffproSyncEnabled", String.valueOf(goaffproSyncEnabled));
                    config.setProperty("goaffproSyncHourlyEnabled", String.valueOf(goaffproSyncHourlyEnabled));
                    config.setProperty("goaffproSyncDeepEnabled", String.valueOf(goaffproSyncDeepEnabled));
                    config.setProperty("goaffproSyncAssetDownloadEnabled", String.valueOf(goaffproSyncAssetDownloadEnabled));
                    config.setProperty("goaffproSyncMaxCallsPerHour", goaffproSyncMaxCallsPerHour);
                    config.setProperty("goaffproSyncSlidingWindowEnabled", String.valueOf(goaffproSyncSlidingWindowEnabled));
                    config.setProperty("goaffproSyncMinCallSpacingMs", goaffproSyncMinCallSpacingMs);
                    config.setProperty("goaffproSyncDownloadSkipExistingEnabled", String.valueOf(goaffproSyncDownloadSkipExistingEnabled));
                    config.setProperty("goaffproSyncDeltaDownloadsEnabled", String.valueOf(goaffproSyncDeltaDownloadsEnabled));
                    config.setProperty("goaffproSyncDeltaLookbackDays", goaffproSyncDeltaLookbackDays);
                    config.setProperty("goaffproSyncMinFreeBytes", goaffproSyncMinFreeBytes);
                    if (!goaffproSyncDataPath.isBlank()) {
                        config.setProperty("goaffproSyncDataPath", goaffproSyncDataPath);
                    }
                    config.setProperty("eInvoiceEnabled", String.valueOf(eInvoiceEnabled));
                    config.setProperty("eInvoiceAttachAndStoreEnabled", String.valueOf(eInvoiceAttachAndStoreEnabled));
                    config.setProperty("eInvoiceBuyerName", eInvoiceBuyerName);
                    config.setProperty("eInvoiceBuyerStreet", eInvoiceBuyerStreet);
                    config.setProperty("eInvoiceBuyerZip", eInvoiceBuyerZip);
                    config.setProperty("eInvoiceBuyerCity", eInvoiceBuyerCity);
                    config.setProperty("eInvoiceBuyerCountry", eInvoiceBuyerCountry);
                    config.setProperty("eInvoiceBuyerVatId", eInvoiceBuyerVatId);
                    config.setProperty("eInvoiceBuyerTaxNumber", eInvoiceBuyerTaxNumber);
                    config.setProperty("eInvoiceBankIban", eInvoiceBankIban);
                    config.setProperty("eInvoiceBankBic", eInvoiceBankBic);
                    config.setProperty("eInvoiceBankAccountHolder", eInvoiceBankAccountHolder);
                    config.setProperty("eInvoicePaymentTerms", eInvoicePaymentTerms);
                    config.setProperty("nachweisFirmenname", nachweisFirmenname);
                    config.setProperty("rechnungCutoffDate", rechnungCutoffDate);
                    config.setProperty("legacyBuyerName", legacyBuyerName);
                    config.setProperty("legacyBuyerStreet", legacyBuyerStreet);
                    config.setProperty("legacyBuyerZip", legacyBuyerZip);
                    config.setProperty("legacyBuyerCity", legacyBuyerCity);
                    config.setProperty("legacyBuyerCountry", legacyBuyerCountry);
                    config.setProperty("legacyBuyerVatId", legacyBuyerVatId);
                    config.setProperty("legacyBuyerTaxNumber", legacyBuyerTaxNumber);
                    config.setProperty("legacyNachweisFirmenname", legacyNachweisFirmenname);

                    persistSettings(config);

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("message", "Einstellungen gespeichert.");
                    payload.put("pdfExportPath", Objects.toString(config.getProperty("pdfExportPath"), DEFAULT_PDF_EXPORT_PATH));
                    payload.put("settingsDirectory", resolveSettingsDirectory(config).toString());
                    payload.put("lastImportedComission", Objects.toString(config.getProperty("lastImportedComission"), "0"));
                    payload.put("goaffproAPIKey", getSecretOrConfig(config, "GOAFFPRO_API_KEY", "goaffproAPIKey", DEFAULT_GOAFFPRO_API_KEY));
                    payload.put("contactEmail", Objects.toString(config.getProperty("contactEmail"), ""));
                    payload.put("smtpHost", Objects.toString(config.getProperty("smtpHost"), ""));
                    payload.put("smtpPort", Objects.toString(config.getProperty("smtpPort"), "587"));
                    payload.put("smtpUsername", Objects.toString(config.getProperty("smtpUsername"), ""));
                    payload.put("emailBcc", Objects.toString(config.getProperty("emailBcc"), ""));
                    payload.put("smtpTls", Boolean.parseBoolean(Objects.toString(config.getProperty("smtpTls"), "false")));
                    payload.put("hasSmtpPassword", !getSecretOrConfig(config, "SMTP_PASSWORD", "smtpPassword", "").trim().isBlank());
                    payload.put("sendEmailsEnabled", Boolean.parseBoolean(Objects.toString(config.getProperty("sendEmailsEnabled"), "true")));
                    payload.put("emailRecipientMode", Objects.toString(config.getProperty("emailRecipientMode"), "contact"));
                    payload.put("emailTemplateHtml", Objects.toString(config.getProperty("emailTemplateHtml"), "").isBlank() ? getDefaultInvoiceMailHtmlTemplate() : Objects.toString(config.getProperty("emailTemplateHtml"), ""));
                    payload.put("emailTemplateHtmlDefault", getDefaultInvoiceMailHtmlTemplate());
                    payload.put("validationReminderTemplateHtml", Objects.toString(config.getProperty("validationReminderTemplateHtml"), "").isBlank() ? getDefaultValidationReminderHtmlTemplate() : Objects.toString(config.getProperty("validationReminderTemplateHtml"), ""));
                    payload.put("validationReminderTemplateHtmlDefault", getDefaultValidationReminderHtmlTemplate());
                    payload.put("eInvoicePdfTemplateHtml", Objects.toString(config.getProperty("eInvoicePdfTemplateHtml"), "").isBlank() ? getDefaultEInvoicePdfViewHtmlTemplate() : Objects.toString(config.getProperty("eInvoicePdfTemplateHtml"), ""));
                    payload.put("eInvoicePdfTemplateHtmlDefault", getDefaultEInvoicePdfViewHtmlTemplate());
                    payload.put("eInvoicePdfTemplateHtmlRechnung", Objects.toString(config.getProperty("eInvoicePdfTemplateHtmlRechnung"), "").isBlank() ? getDefaultRechnungPdfViewHtmlTemplate() : Objects.toString(config.getProperty("eInvoicePdfTemplateHtmlRechnung"), ""));
                    payload.put("eInvoicePdfTemplateHtmlRechnungDefault", getDefaultRechnungPdfViewHtmlTemplate());
                    payload.put("emailTemplateHtmlRechnung", Objects.toString(config.getProperty("emailTemplateHtmlRechnung"), "").isBlank() ? getDefaultRechnungMailHtmlTemplate() : Objects.toString(config.getProperty("emailTemplateHtmlRechnung"), ""));
                    payload.put("emailTemplateHtmlRechnungDefault", getDefaultRechnungMailHtmlTemplate());
                    payload.put("leaderWeeklyReportTemplateHtml", Objects.toString(config.getProperty("leaderWeeklyReportTemplateHtml"), "").isBlank() ? getDefaultLeaderWeeklyReportHtmlTemplate() : Objects.toString(config.getProperty("leaderWeeklyReportTemplateHtml"), ""));
                    payload.put("leaderWeeklyReportTemplateHtmlDefault", getDefaultLeaderWeeklyReportHtmlTemplate());
                    payload.put("leaderWeeklyMailSchedulerEnabled", Boolean.parseBoolean(Objects.toString(config.getProperty("leaderWeeklyMailSchedulerEnabled"), "false")));
                    payload.put("leaderWeeklyMailProductionEnabled", Boolean.parseBoolean(Objects.toString(config.getProperty("leaderWeeklyMailProductionEnabled"), "false")));
                    payload.put("leaderWeeklyMailScheduleDay", normalizeLeaderWeeklyMailScheduleDay(Objects.toString(config.getProperty("leaderWeeklyMailScheduleDay"), "")));
                    payload.put("leaderWeeklyMailScheduleTime", normalizeLeaderWeeklyMailScheduleTime(Objects.toString(config.getProperty("leaderWeeklyMailScheduleTime"), "")));
                    payload.put("leaderWeeklyMailLastSentPeriodKey", Objects.toString(config.getProperty("leaderWeeklyMailLastSentPeriodKey"), ""));
                    payload.put("goaffproSyncEnabled", Boolean.parseBoolean(Objects.toString(config.getProperty("goaffproSyncEnabled"), "true")));
                    payload.put("goaffproSyncHourlyEnabled", Boolean.parseBoolean(Objects.toString(config.getProperty("goaffproSyncHourlyEnabled"), "false")));
                    payload.put("goaffproSyncDeepEnabled", Boolean.parseBoolean(Objects.toString(config.getProperty("goaffproSyncDeepEnabled"), "false")));
                    payload.put("goaffproSyncAssetDownloadEnabled", Boolean.parseBoolean(Objects.toString(config.getProperty("goaffproSyncAssetDownloadEnabled"), "true")));
                    payload.put("goaffproSyncMaxCallsPerHour", Objects.toString(config.getProperty("goaffproSyncMaxCallsPerHour"), "60"));
                    payload.put("goaffproSyncSlidingWindowEnabled", Boolean.parseBoolean(Objects.toString(config.getProperty("goaffproSyncSlidingWindowEnabled"), "true")));
                    payload.put("goaffproSyncMinCallSpacingMs", Objects.toString(config.getProperty("goaffproSyncMinCallSpacingMs"), "1500"));
                    payload.put("goaffproSyncDownloadSkipExistingEnabled", Boolean.parseBoolean(Objects.toString(config.getProperty("goaffproSyncDownloadSkipExistingEnabled"), "true")));
                    payload.put("goaffproSyncDeltaDownloadsEnabled", Boolean.parseBoolean(Objects.toString(config.getProperty("goaffproSyncDeltaDownloadsEnabled"), "false")));
                    payload.put("goaffproSyncDeltaLookbackDays", Objects.toString(config.getProperty("goaffproSyncDeltaLookbackDays"), "14"));
                    payload.put("goaffproSyncMinFreeBytes", Objects.toString(config.getProperty("goaffproSyncMinFreeBytes"), String.valueOf(512L * 1024L * 1024L)));
                    payload.put("goaffproSyncDataPath", Objects.toString(config.getProperty("goaffproSyncDataPath"), GoAffProSyncService.resolveDataDir(config).toString()));
                    payload.put("goaffproSyncDbPath", GoAffProSyncService.resolveDbPath(config).toString());
                    payload.put("eInvoiceEnabled", Boolean.parseBoolean(Objects.toString(config.getProperty("eInvoiceEnabled"), "true")));
                    payload.put("eInvoiceAttachAndStoreEnabled", Boolean.parseBoolean(Objects.toString(config.getProperty("eInvoiceAttachAndStoreEnabled"), "true")));
                    payload.put("eInvoiceBuyerName", Objects.toString(config.getProperty("eInvoiceBuyerName"), "S+R linear technology gmbh"));
                    payload.put("eInvoiceBuyerStreet", Objects.toString(config.getProperty("eInvoiceBuyerStreet"), ""));
                    payload.put("eInvoiceBuyerZip", Objects.toString(config.getProperty("eInvoiceBuyerZip"), ""));
                    payload.put("eInvoiceBuyerCity", Objects.toString(config.getProperty("eInvoiceBuyerCity"), ""));
                    payload.put("eInvoiceBuyerCountry", Objects.toString(config.getProperty("eInvoiceBuyerCountry"), "DE"));
                    payload.put("eInvoiceBuyerVatId", Objects.toString(config.getProperty("eInvoiceBuyerVatId"), ""));
                    payload.put("eInvoiceBuyerTaxNumber", Objects.toString(config.getProperty("eInvoiceBuyerTaxNumber"), ""));
                    payload.put("eInvoiceBankIban", Objects.toString(config.getProperty("eInvoiceBankIban"), ""));
                    payload.put("eInvoiceBankBic", Objects.toString(config.getProperty("eInvoiceBankBic"), ""));
                    payload.put("eInvoiceBankAccountHolder", Objects.toString(config.getProperty("eInvoiceBankAccountHolder"), ""));
                    payload.put("eInvoicePaymentTerms", Objects.toString(config.getProperty("eInvoicePaymentTerms"), "Zahlbar sofort ohne Abzug"));
                    payload.put("nachweisFirmenname", Objects.toString(config.getProperty("nachweisFirmenname"), "S+R Linear Technology GmbH"));
                    payload.put("rechnungCutoffDate", rechnungCutoffDateRaw(config));
                    payload.put("legacyBuyerName", Objects.toString(config.getProperty("legacyBuyerName"), DEFAULT_LEGACY_BUYER_NAME));
                    payload.put("legacyBuyerStreet", Objects.toString(config.getProperty("legacyBuyerStreet"), ""));
                    payload.put("legacyBuyerZip", Objects.toString(config.getProperty("legacyBuyerZip"), ""));
                    payload.put("legacyBuyerCity", Objects.toString(config.getProperty("legacyBuyerCity"), ""));
                    payload.put("legacyBuyerCountry", Objects.toString(config.getProperty("legacyBuyerCountry"), "DE"));
                    payload.put("legacyBuyerVatId", Objects.toString(config.getProperty("legacyBuyerVatId"), ""));
                    payload.put("legacyBuyerTaxNumber", Objects.toString(config.getProperty("legacyBuyerTaxNumber"), ""));
                    payload.put("legacyNachweisFirmenname", Objects.toString(config.getProperty("legacyNachweisFirmenname"), DEFAULT_LEGACY_BUYER_NAME));
                    payload.put("lastImportedComissionHistory", getCommissionHistory(config));
                payload.put("commissionHistoryLabels", buildCommissionHistoryLabels(config));
                payload.put("commissionDaySummary", buildCommissionDaySummary(config));
                    sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
                } catch (Exception e) {
                    sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
                return;
            }

            sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
        }
    }

    private static class RecipientModeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String mode = asText(body, "emailRecipientMode").trim();
                if (!"advisor".equals(mode)) mode = "contact";
                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);
                config.setProperty("emailRecipientMode", mode);
                persistSettings(config);
                sendResponse(exchange, 200, "application/json", "{\"emailRecipientMode\":\"" + mode + "\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class AddLatestCommissionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }

            try {
                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);
                String apiKey = getSecretOrConfig(config, "GOAFFPRO_API_KEY", "goaffproAPIKey", DEFAULT_GOAFFPRO_API_KEY).trim();

                String latestUrl = "https://api.goaffpro.com/v1/admin/payments?created_at_min=2025-12-18T07%3A48%3A36.000Z&fields=id,created_at";
                JsonNode root = requestJson(latestUrl, apiKey);
                JsonNode payments = root.get("payments");
                if (payments == null || !payments.isArray() || payments.size() == 0) {
                    sendResponse(exchange, 404, "application/json", "{\"error\":\"Keine Zahlläufe gefunden\"}");
                    return;
                }

                String maxId = "";
                String maxCreatedAt = "";
                for (JsonNode payment : payments) {
                    String id = asText(payment, "id").trim();
                    if (id.isBlank()) continue;
                    if (maxId.isBlank() || isGreaterNumeric(id, maxId)) {
                        maxId = id;
                        maxCreatedAt = asText(payment, "created_at");
                    }
                }
                if (maxId.isBlank()) {
                    sendResponse(exchange, 404, "application/json", "{\"error\":\"Keine gültige Payment-ID gefunden\"}");
                    return;
                }

                List<String> before = getCommissionHistory(config);
                boolean alreadyPresent = before.contains(maxId);
                ensureCommissionInHistory(config, maxId);
                if (!maxCreatedAt.isBlank()) {
                    setCommissionDate(config, maxId, toGermanDate(maxCreatedAt));
                }
                persistSettings(config);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("message", alreadyPresent ? "Neuester Zahllauf war bereits vorhanden." : "Neuester Zahllauf wurde hinzugefügt.");
                payload.put("latestId", maxId);
                payload.put("latestCreatedAt", maxCreatedAt);
                payload.put("lastImportedComissionHistory", getCommissionHistory(config));
                payload.put("commissionHistoryLabels", buildCommissionHistoryLabels(config));
                payload.put("commissionDaySummary", buildCommissionDaySummary(config));
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class RemoveCommissionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String commission = asText(body, "commission").trim();
                if (commission.isBlank()) {
                    sendResponse(exchange, 400, "application/json", "{\"error\":\"commission fehlt\"}");
                    return;
                }

                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);

                boolean removed = removeCommissionFromHistory(config, commission);
                removeCommissionDate(config, commission);

                String active = Objects.toString(config.getProperty("lastImportedComission"), "0").trim();
                if (commission.equals(active)) {
                    List<String> history = getCommissionHistory(config);
                    config.setProperty("lastImportedComission", history.isEmpty() ? "0" : history.get(0));
                }

                persistSettings(config);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("message", removed ? "Zahllauf entfernt." : "Zahllauf war nicht in der Liste.");
                payload.put("removed", removed);
                payload.put("lastImportedComission", Objects.toString(config.getProperty("lastImportedComission"), "0"));
                payload.put("lastImportedComissionHistory", getCommissionHistory(config));
                payload.put("commissionHistoryLabels", buildCommissionHistoryLabels(config));
                payload.put("commissionDaySummary", buildCommissionDaySummary(config));
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }


    private static class RebuildCommissionHistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);
                String apiKey = getSecretOrConfig(config, "GOAFFPRO_API_KEY", "goaffproAPIKey", DEFAULT_GOAFFPRO_API_KEY).trim();

                rebuildCommissionHistoryFromPayments(config, apiKey);
                persistSettings(config);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("message", "Zahllauf-Liste aus Payments neu aufgebaut.");
                payload.put("lastImportedComission", Objects.toString(config.getProperty("lastImportedComission"), "0"));
                payload.put("lastImportedComissionHistory", getCommissionHistory(config));
                payload.put("commissionHistoryLabels", buildCommissionHistoryLabels(config));
                payload.put("commissionDaySummary", buildCommissionDaySummary(config));
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class ValidationAdvisorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);
                String apiKey = getSecretOrConfig(config, "GOAFFPRO_API_KEY", "goaffproAPIKey", DEFAULT_GOAFFPRO_API_KEY).trim();

                List<Map<String, String>> rows = fetchAdvisorValidationRows(apiKey);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("message", rows.size() + " Beraterinnen geladen.");
                payload.put("rows", rows);
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class ValidationAdvisorTreeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);
                String apiKey = getSecretOrConfig(config, "GOAFFPRO_API_KEY", "goaffproAPIKey", DEFAULT_GOAFFPRO_API_KEY).trim();

                List<Map<String, String>> rows = fetchAdvisorTreeValidationRows(apiKey);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("message", rows.size() + " Knoten im Beraterinnen-Baum geladen.");
                payload.put("rows", rows);
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class HelpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                String text = Files.exists(HELP_DOC_PATH)
                        ? Files.readString(HELP_DOC_PATH, StandardCharsets.UTF_8)
                        : "Hilfe-Dokumentation konnte nicht gefunden werden.";
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("title", "Hilfe- und Funktionsdokumentation");
                payload.put("content", text);
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class GoAffProSyncStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                Properties config = loadConfigWithUiSettings();
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(GOAFFPRO_SYNC_SERVICE.status(config)));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class GoAffProSyncInventoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                Properties config = loadConfigWithUiSettings();
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(GOAFFPRO_SYNC_SERVICE.inventory(config)));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class GoAffProSyncRunsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                Properties config = loadConfigWithUiSettings();
                int limit = 25;
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.contains("limit=")) {
                    try {
                        limit = Integer.parseInt(query.replaceAll(".*(?:^|&)limit=([0-9]+).*", "$1"));
                    } catch (Exception ignored) {
                    }
                }
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(GOAFFPRO_SYNC_SERVICE.runs(config, limit)));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    // ══════════════ DATENSICHERUNG UND UMZUG ─ BEGIN ══════════════

    private static BackupService.BackupLocations backupLocations(Properties config) {
        return new BackupService.BackupLocations(
                CONFIG_PATH.toAbsolutePath(),
                GoAffProSyncService.resolveDataDir(config),
                resolveSettingsDirectory(config));
    }

    /** Meldet Fortschritt in die von der Oberfläche gepollte Momentaufnahme. */
    private static BackupService.ProgressSink backupProgress(String job) {
        return new BackupService.ProgressSink() {
            @Override public void phase(String key, String label) { updateBackupState(job, key, label, -1, -1, null); }
            @Override public void progress(long done, long total) { updateBackupState(job, null, null, done, total, null); }
            @Override public void note(String message) { updateBackupState(job, null, null, -1, -1, message); }
        };
    }

    private static synchronized void updateBackupState(String job, String phaseKey, String label,
                                                       long done, long total, String note) {
        Map<String, Object> state = new LinkedHashMap<>();
        if (BACKUP_STATE != null) state.putAll(BACKUP_STATE);
        state.put("job", job);
        state.put("running", true);
        state.put("status", "running");
        if (phaseKey != null) state.put("phase", phaseKey);
        if (label != null) state.put("phaseLabel", label);
        if (done >= 0) state.put("doneBytes", done);
        if (total >= 0) state.put("totalBytes", total);
        if (note != null) {
            List<String> notes = new ArrayList<>();
            Object existing = state.get("notes");
            if (existing instanceof List<?> list) list.forEach(n -> notes.add(String.valueOf(n)));
            notes.add(note);
            state.put("notes", notes);
        }
        BACKUP_STATE = state;
    }

    private static void finishBackupState(String job, String status, String message, Map<String, Object> extra) {
        Map<String, Object> state = new LinkedHashMap<>();
        if (BACKUP_STATE != null) state.putAll(BACKUP_STATE);
        state.put("job", job);
        state.put("running", false);
        state.put("status", status);
        state.put("message", message);
        state.put("finishedAt", Instant.now().toString());
        if (extra != null) state.putAll(extra);
        BACKUP_STATE = state;
    }

    private static List<Map<String, Object>> listBackupArchives(Properties config) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Path dir = backupLocations(config).backupDir();
        if (!Files.isDirectory(dir)) return rows;
        try (var list = Files.list(dir)) {
            List<Path> files = list.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".zip"))
                    .sorted(java.util.Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
            for (Path file : files) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", file.getFileName().toString());
                row.put("bytes", Files.size(file));
                row.put("modifiedAt", Files.getLastModifiedTime(file).toInstant().toString());
                rows.add(row);
            }
        } catch (Exception ignored) {
        }
        return rows;
    }

    private static Map<String, Object> backupStatusPayload(Properties config) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("running", BACKUP_RUNNING.get());
        payload.put("job", BACKUP_STATE);
        payload.put("archives", listBackupArchives(config));
        payload.put("syncBusy", GOAFFPRO_SYNC_SERVICE.isBusy());
        payload.put("backupDir", backupLocations(config).backupDir().toString());
        return payload;
    }

    private static class BackupStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            try {
                sendResponse(exchange, 200, "application/json",
                        OBJECT_MAPPER.writeValueAsString(backupStatusPayload(loadConfigWithUiSettings())));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class BackupExportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                boolean includeSecrets = body.has("includeSecrets") && body.get("includeSecrets").asBoolean(false);
                Properties config = loadConfigWithUiSettings();

                if (!BACKUP_RUNNING.compareAndSet(false, true)) {
                    sendResponse(exchange, 409, "application/json",
                            "{\"error\":\"Es läuft bereits eine Sicherung oder ein Import.\",\"code\":\"BACKUP_BUSY\"}");
                    return;
                }
                if (!GOAFFPRO_SYNC_SERVICE.beginMaintenance()) {
                    BACKUP_RUNNING.set(false);
                    sendResponse(exchange, 409, "application/json",
                            "{\"error\":\"Ein GoAffPro Sync läuft gerade. Bitte den Sync pausieren oder das Ende abwarten.\","
                                    + "\"code\":\"SYNC_BUSY\",\"syncRunning\":true}");
                    return;
                }

                BACKUP_STATE = new LinkedHashMap<>(Map.of("job", "export", "running", true, "status", "running"));
                BackupService.BackupLocations loc = backupLocations(config);
                BACKUP_EXECUTOR.submit(() -> {
                    try {
                        Files.createDirectories(loc.backupDir());
                        BackupService.pruneOldArchives(loc.backupDir(), "export_", 2);
                        Path target = loc.backupDir().resolve(BackupService.archiveFileName("export", includeSecrets));
                        BackupService.createArchive(loc, includeSecrets, target, backupProgress("export"));
                        finishBackupState("export", "success", "Sicherung erstellt: " + target.getFileName(),
                                Map.of("file", target.getFileName().toString(), "bytes", Files.size(target)));
                    } catch (Throwable t) {
                        System.err.println("GoAffPro Sicherung fehlgeschlagen: " + GoAffProSyncService.describeThrowable(t));
                        t.printStackTrace();
                        finishBackupState("export", "error", GoAffProSyncService.describeThrowable(t), null);
                    } finally {
                        GOAFFPRO_SYNC_SERVICE.endMaintenance();
                        BACKUP_RUNNING.set(false);
                    }
                });
                Map<String, Object> payload = backupStatusPayload(config);
                payload.put("message", "Sicherung wird erstellt.");
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    /** Liefert ein Archiv aus. Nimmt bewusst nur einen Dateinamen, nie einen Pfad vom Client. */
    private static class BackupDownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            try {
                Properties config = loadConfigWithUiSettings();
                Path dir = backupLocations(config).backupDir();
                String name = sanitizeFilename(Objects.toString(parseQueryParams(exchange.getRequestURI()).get("file"), ""));
                Path file = dir.resolve(name).normalize();
                if (name.isBlank() || !file.startsWith(dir.toAbsolutePath().normalize()) || !Files.isRegularFile(file)) {
                    sendResponse(exchange, 404, "application/json", "{\"error\":\"Archiv nicht gefunden\"}");
                    return;
                }
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Content-Type", "application/zip");
                exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + name + "\"");
                exchange.sendResponseHeaders(200, Files.size(file));
                try (OutputStream os = exchange.getResponseBody()) {
                    Files.copy(file, os);
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    /** Nimmt das Archiv als rohen Body entgegen (kein multipart) und liest nur das Manifest. */
    private static class BackupImportUploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            Path tmp = null;
            try {
                Properties config = loadConfigWithUiSettings();
                Path dir = backupLocations(config).backupDir();
                Files.createDirectories(dir);
                String token = "upload_" + FILE_TIMESTAMP.format(LocalDateTime.now()) + ".zip";
                tmp = dir.resolve(token);

                long written = 0;
                long max = 4L * 1024 * 1024 * 1024;
                byte[] buffer = new byte[64 * 1024];
                try (InputStream in = exchange.getRequestBody();
                     OutputStream out = Files.newOutputStream(tmp)) {
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        written += n;
                        if (written > max) throw new IOException("Archiv zu groß (Grenze 4 GB).");
                        out.write(buffer, 0, n);
                    }
                }
                JsonNode manifest = BackupService.readManifest(tmp);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("token", token);
                payload.put("bytes", written);
                payload.put("manifest", manifest);
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                if (tmp != null) try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
                sendResponse(exchange, 400, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class BackupImportApplyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String token = sanitizeFilename(asText(body, "token").trim());
                // Bestätigung serverseitig prüfen: der Schutz darf nicht am Browser hängen.
                if (!"IMPORTIEREN".equals(asText(body, "confirm").trim())) {
                    sendResponse(exchange, 400, "application/json",
                            "{\"error\":\"Bestätigung fehlt. Bitte IMPORTIEREN eingeben.\"}");
                    return;
                }
                Properties config = loadConfigWithUiSettings();
                BackupService.BackupLocations loc = backupLocations(config);
                Path archive = loc.backupDir().resolve(token).normalize();
                if (token.isBlank() || !archive.startsWith(loc.backupDir().toAbsolutePath().normalize())
                        || !Files.isRegularFile(archive)) {
                    sendResponse(exchange, 404, "application/json", "{\"error\":\"Hochgeladenes Archiv nicht gefunden.\"}");
                    return;
                }

                if (!BACKUP_RUNNING.compareAndSet(false, true)) {
                    sendResponse(exchange, 409, "application/json",
                            "{\"error\":\"Es läuft bereits eine Sicherung oder ein Import.\",\"code\":\"BACKUP_BUSY\"}");
                    return;
                }
                if (!GOAFFPRO_SYNC_SERVICE.beginMaintenance()) {
                    BACKUP_RUNNING.set(false);
                    sendResponse(exchange, 409, "application/json",
                            "{\"error\":\"Ein GoAffPro Sync läuft gerade. Bitte den Sync pausieren oder das Ende abwarten.\","
                                    + "\"code\":\"SYNC_BUSY\",\"syncRunning\":true}");
                    return;
                }

                BACKUP_STATE = new LinkedHashMap<>(Map.of("job", "import", "running", true, "status", "running"));
                Path staging = loc.backupDir().resolve("import_" + FILE_TIMESTAMP.format(LocalDateTime.now()));
                BACKUP_EXECUTOR.submit(() -> {
                    try {
                        updateBackupState("import", "sicherung", "Sicherung des bisherigen Standes", -1, -1, null);
                        BackupService.pruneOldArchives(loc.backupDir(), "pre-import_", 2);
                        // Die Vorab-Sicherung enthält immer die Zugangsdaten - sie bleibt lokal.
                        BackupService.createArchive(loc, true,
                                loc.backupDir().resolve(BackupService.archiveFileName("pre-import", false)), null);

                        BackupService.ImportReport report =
                                BackupService.applyArchive(archive, loc, staging, backupProgress("import"));
                        persistSettings(report.settings());
                        GOAFFPRO_SYNC_SERVICE.resetAfterRestore();
                        BackupService.deleteRecursively(staging);
                        try { Files.deleteIfExists(archive); } catch (IOException ignored) { }

                        Map<String, Object> extra = new LinkedHashMap<>();
                        extra.put("filePathsRebased", report.filePathsRebased());
                        extra.put("filePathsMissing", report.filePathsMissing());
                        extra.put("filePathsForeign", report.filePathsForeign());
                        extra.put("mailLogRebased", report.mailLogRebased());
                        extra.put("mailLogDropped", report.mailLogDropped());
                        extra.put("countersTaken", report.countersTaken());
                        extra.put("secretsIncluded", report.secretsIncluded());
                        extra.put("notes", report.notes());
                        finishBackupState("import", "success", "Import abgeschlossen.", extra);
                    } catch (Throwable t) {
                        System.err.println("GoAffPro Import fehlgeschlagen: " + GoAffProSyncService.describeThrowable(t));
                        t.printStackTrace();
                        finishBackupState("import", "error", GoAffProSyncService.describeThrowable(t), null);
                    } finally {
                        GOAFFPRO_SYNC_SERVICE.endMaintenance();
                        BACKUP_RUNNING.set(false);
                    }
                });
                Map<String, Object> payload = backupStatusPayload(config);
                payload.put("message", "Import wird ausgeführt.");
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }
    // ══════════════ DATENSICHERUNG ─ END ══════════════

    private static class GoAffProSyncRunHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String mode = asText(body, "mode").trim();
                Properties config = loadConfigWithUiSettings();
                String apiKey = getSecretOrConfig(config, "GOAFFPRO_API_KEY", "goaffproAPIKey", DEFAULT_GOAFFPRO_API_KEY).trim();
                Map<String, Object> payload = GOAFFPRO_SYNC_SERVICE.startAsync(config, apiKey, mode);
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class GoAffProSyncDiagnosticsRunHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                List<String> endpoints = new ArrayList<>();
                JsonNode endpointNode = body.get("endpoints");
                if (endpointNode != null && endpointNode.isArray()) {
                    for (JsonNode item : endpointNode) {
                        String value = item.asText("").trim();
                        if (!value.isBlank()) endpoints.add(value);
                    }
                }
                Properties config = loadConfigWithUiSettings();
                String apiKey = getSecretOrConfig(config, "GOAFFPRO_API_KEY", "goaffproAPIKey", DEFAULT_GOAFFPRO_API_KEY).trim();
                Map<String, Object> payload = GOAFFPRO_SYNC_SERVICE.startDiagnosticsAsync(config, apiKey, endpoints);
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class GoAffProSyncDiagnosticsLatestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                Properties config = loadConfigWithUiSettings();
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(GOAFFPRO_SYNC_SERVICE.diagnosticsLatest(config)));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class GoAffProSyncDiagnosticsRunsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                Properties config = loadConfigWithUiSettings();
                int limit = 25;
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.contains("limit=")) {
                    try {
                        limit = Integer.parseInt(query.replaceAll(".*(?:^|&)limit=([0-9]+).*", "$1"));
                    } catch (Exception ignored) {
                    }
                }
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(GOAFFPRO_SYNC_SERVICE.diagnosticRuns(config, limit)));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class GoAffProSyncPauseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            updateSyncEnabled(exchange, false);
        }
    }

    private static class GoAffProSyncResumeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            updateSyncEnabled(exchange, true);
        }
    }

    private static void updateSyncEnabled(HttpExchange exchange, boolean enabled) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 200, "application/json", "{}");
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
            return;
        }
        try {
            Properties config = loadConfigWithUiSettings();
            GOAFFPRO_SYNC_SERVICE.setEnabled(config, enabled);
            persistSettings(config);
            Map<String, Object> payload = GOAFFPRO_SYNC_SERVICE.status(config);
            payload.put("message", enabled ? "GoAffPro Sync fortgesetzt." : "GoAffPro Sync pausiert.");
            sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
        } catch (Exception e) {
            sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static class AnalyticsFetchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }

            try {
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String sinceId = asText(body, "sinceId").trim();
                if (sinceId.isBlank()) sinceId = "0";
                LocalDate fromDate = parseIsoDate(asText(body, "fromDate"));
                LocalDate toDate = parseIsoDate(asText(body, "toDate"));

                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);
                String apiKey = getSecretOrConfig(config, "GOAFFPRO_API_KEY", "goaffproAPIKey", DEFAULT_GOAFFPRO_API_KEY).trim();

                String paymentsUrl = "https://api.goaffpro.com/v1/admin/payments?since_id=" + sinceId
                        + "&fields=id,affiliate_id,amount,currency,payment_method,payment_details,affiliate_message,admin_note,transactions,created_at";
                JsonNode paymentRoot = requestJson(paymentsUrl, apiKey);
                JsonNode payments = paymentRoot.get("payments");
                if (payments == null || !payments.isArray()) {
                    payments = OBJECT_MAPPER.createArrayNode();
                }

                List<JsonNode> filteredPayments = new ArrayList<>();
                for (JsonNode payment : payments) {
                    LocalDate paymentDate = parseIsoDateTimeToLocalDate(asText(payment, "created_at"));
                    if (paymentDate == null) continue;
                    if (fromDate != null && paymentDate.isBefore(fromDate)) continue;
                    if (toDate != null && paymentDate.isAfter(toDate)) continue;
                    filteredPayments.add(payment);
                }

                List<String> affiliateIds = new ArrayList<>();
                for (JsonNode payment : filteredPayments) {
                    String affiliateId = asText(payment, "affiliate_id");
                    if (!affiliateId.isBlank() && !affiliateIds.contains(affiliateId)) affiliateIds.add(affiliateId);
                }
                Map<String, JsonNode> affiliatesById = fetchAffiliatesById(apiKey, affiliateIds);

                List<String> leaderIds = new ArrayList<>();
                for (JsonNode affiliate : affiliatesById.values()) {
                    String leaderId = resolveLeaderId(affiliate);
                    if (!leaderId.isBlank() && !leaderIds.contains(leaderId)) leaderIds.add(leaderId);
                }
                Map<String, JsonNode> leadersById = fetchAffiliatesById(apiKey, leaderIds);

                double totalAmount = 0.0;
                int totalTransactions = 0;
                double totalSelfCommission = 0.0;
                double totalTeamCommission = 0.0;
                Map<String, Map<String, Object>> advisorAgg = new LinkedHashMap<>();
                Map<String, Integer> countryAgg = new LinkedHashMap<>();
                Map<String, Map<String, Object>> leaderAgg = new LinkedHashMap<>();
                List<Map<String, Object>> paymentRows = new ArrayList<>();
                List<Map<String, Object>> pendingRows = new ArrayList<>();
                List<Map<String, Object>> trafficSourceRows = new ArrayList<>();
                List<Map<String, Object>> orderStatusRows = new ArrayList<>();
                List<Map<String, Object>> rewardStatusRows = new ArrayList<>();
                double pendingDueTotal = 0.0;
                double rewardAmountTotal = 0.0;
                for (JsonNode payment : filteredPayments) {
                    String paymentId = asText(payment, "id");
                    String affiliateId = asText(payment, "affiliate_id");
                    JsonNode affiliate = affiliatesById.get(affiliateId);
                    String advisorName = affiliate != null ? asText(affiliate, "name") : "Unbekannt";
                    String country = affiliate != null ? asText(affiliate, "country") : "";
                    double amount = parseDoubleSafeStatic(asText(payment, "amount"));
                    int txCount = payment.has("transactions") && payment.get("transactions").isArray() ? payment.get("transactions").size() : 0;
                    TransactionSplit split = splitTransactions(payment.get("transactions"));

                    totalAmount += amount;
                    totalTransactions += txCount;
                    totalSelfCommission += split.selfCommission;
                    totalTeamCommission += split.teamCommission;

                    String advisorKey = affiliateId.isBlank() ? advisorName : affiliateId;
                    Map<String, Object> agg = advisorAgg.computeIfAbsent(advisorKey, k -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("affiliateId", affiliateId);
                        m.put("advisorName", advisorName);
                        m.put("country", country);
                        m.put("paymentCount", 0);
                        m.put("totalAmount", 0.0);
                        m.put("totalTransactions", 0);
                        m.put("selfCommission", 0.0);
                        m.put("teamCommission", 0.0);
                        return m;
                    });
                    agg.put("paymentCount", ((Integer) agg.get("paymentCount")) + 1);
                    agg.put("totalAmount", ((Double) agg.get("totalAmount")) + amount);
                    agg.put("totalTransactions", ((Integer) agg.get("totalTransactions")) + txCount);
                    agg.put("selfCommission", ((Double) agg.get("selfCommission")) + split.selfCommission);
                    agg.put("teamCommission", ((Double) agg.get("teamCommission")) + split.teamCommission);

                    String leaderId = resolveLeaderId(affiliate);
                    if (!leaderId.isBlank()) {
                        JsonNode leader = leadersById.get(leaderId);
                        String leaderName = leader != null ? asText(leader, "name") : ("ID " + leaderId);
                        Map<String, Object> la = leaderAgg.computeIfAbsent(leaderId, k -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("leaderId", leaderId);
                            m.put("leaderName", leaderName);
                            m.put("advisorCount", 0);
                            m.put("paymentCount", 0);
                            m.put("teamTotalAmount", 0.0);
                            m.put("teamSelfCommission", 0.0);
                            m.put("teamTeamCommission", 0.0);
                            m.put("advisorIds", new LinkedHashSet<String>());
                            return m;
                        });
                        @SuppressWarnings("unchecked")
                        Set<String> advisorIds = (Set<String>) la.get("advisorIds");
                        if (!affiliateId.isBlank()) advisorIds.add(affiliateId);
                        la.put("paymentCount", ((Integer) la.get("paymentCount")) + 1);
                        la.put("teamTotalAmount", ((Double) la.get("teamTotalAmount")) + amount);
                        la.put("teamSelfCommission", ((Double) la.get("teamSelfCommission")) + split.selfCommission);
                        la.put("teamTeamCommission", ((Double) la.get("teamTeamCommission")) + split.teamCommission);
                    }

                    if (!country.isBlank()) {
                        countryAgg.put(country, countryAgg.getOrDefault(country, 0) + 1);
                    }

                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("paymentId", paymentId);
                    row.put("advisorName", advisorName);
                    row.put("country", country);
                    row.put("amount", amount);
                    row.put("selfCommission", split.selfCommission);
                    row.put("teamCommission", split.teamCommission);
                    row.put("transactionCount", txCount);
                    row.put("createdAt", toGermanDate(asText(payment, "created_at")));
                    paymentRows.add(row);
                }

                List<Map<String, Object>> advisorRows = new ArrayList<>(advisorAgg.values());
                advisorRows.sort((a, b) -> Double.compare((Double) b.get("totalAmount"), (Double) a.get("totalAmount")));

                List<String> labels = new ArrayList<>();
                List<Double> amountSeries = new ArrayList<>();
                List<Integer> txSeries = new ArrayList<>();
                for (int i = 0; i < Math.min(10, advisorRows.size()); i++) {
                    Map<String, Object> row = advisorRows.get(i);
                    labels.add(Objects.toString(row.get("advisorName"), "n/a"));
                    amountSeries.add((Double) row.get("totalAmount"));
                    txSeries.add((Integer) row.get("totalTransactions"));
                }

                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("sinceId", sinceId);
                summary.put("fromDate", fromDate != null ? fromDate.toString() : "");
                summary.put("toDate", toDate != null ? toDate.toString() : "");
                summary.put("paymentsCount", filteredPayments.size());
                summary.put("totalAmount", totalAmount);
                summary.put("totalTransactions", totalTransactions);
                summary.put("advisorCount", advisorRows.size());
                summary.put("selfCommission", totalSelfCommission);
                summary.put("teamCommission", totalTeamCommission);
                
                List<Map<String, Object>> countryRows = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : countryAgg.entrySet()) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("country", entry.getKey());
                    c.put("payments", entry.getValue());
                    countryRows.add(c);
                }
                countryRows.sort((a,b)->Integer.compare((Integer)b.get("payments"),(Integer)a.get("payments")));

                List<Map<String, Object>> leaderRows = new ArrayList<>();
                for (Map<String, Object> row : leaderAgg.values()) {
                    @SuppressWarnings("unchecked")
                    Set<String> advisorIds = (Set<String>) row.get("advisorIds");
                    row.put("advisorCount", advisorIds.size());
                    row.remove("advisorIds");
                    leaderRows.add(row);
                }
                leaderRows.sort((a,b)->Double.compare((Double)b.get("teamTotalAmount"),(Double)a.get("teamTotalAmount")));

                try {
                    JsonNode pendingRoot = requestJson("https://api.goaffpro.com/v1/admin/payments/pending?limit=100", apiKey);
                    JsonNode pending = pendingRoot.get("pending");
                    if (pending != null && pending.isArray()) {
                        for (JsonNode item : pending) {
                            double total = parseDoubleSafeStatic(asText(item, "total"));
                            double paid = parseDoubleSafeStatic(asText(item, "paid_out"));
                            double due = Math.max(0.0, total - paid);
                            pendingDueTotal += due;
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("affiliateId", asText(item, "affiliate_id"));
                            row.put("name", asText(item, "name"));
                            row.put("total", total);
                            row.put("paidOut", paid);
                            row.put("due", due);
                            pendingRows.add(row);
                        }
                        pendingRows.sort((a,b)->Double.compare((Double)b.get("due"),(Double)a.get("due")));
                        if (pendingRows.size() > 15) pendingRows = new ArrayList<>(pendingRows.subList(0, 15));
                    }
                } catch (Exception ignored) {
                }

                try {
                    JsonNode trafficRoot = requestJson("https://api.goaffpro.com/v1/admin/traffic?limit=250", apiKey);
                    JsonNode traffic = trafficRoot.get("traffic");
                    Map<String, Map<String, Object>> sourceAgg = new LinkedHashMap<>();
                    if (traffic != null && traffic.isArray()) {
                        for (JsonNode visit : traffic) {
                            String source = asText(visit, "source");
                            if (source.isBlank()) source = "(ohne Quelle)";
                            final String sourceKey = source;
                            int pageViews = (int) parseDoubleSafeStatic(asText(visit, "page_views"));
                            Map<String, Object> agg = sourceAgg.computeIfAbsent(sourceKey, k -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("source", sourceKey);
                                m.put("visits", 0);
                                m.put("pageViews", 0);
                                return m;
                            });
                            agg.put("visits", ((Integer) agg.get("visits")) + 1);
                            agg.put("pageViews", ((Integer) agg.get("pageViews")) + pageViews);
                        }
                    }
                    trafficSourceRows = new ArrayList<>(sourceAgg.values());
                    trafficSourceRows.sort((a,b)->Integer.compare((Integer)b.get("visits"),(Integer)a.get("visits")));
                    if (trafficSourceRows.size() > 15) trafficSourceRows = new ArrayList<>(trafficSourceRows.subList(0, 15));
                } catch (Exception ignored) {
                }

                try {
                    JsonNode orderRoot = requestJson("https://api.goaffpro.com/v1/admin/orders?limit=250&fields=order_id,affiliate_id,status,created_at", apiKey);
                    JsonNode orders = orderRoot.get("orders");
                    Map<String, Integer> statusAgg = new LinkedHashMap<>();
                    if (orders != null && orders.isArray()) {
                        for (JsonNode order : orders) {
                            String status = asText(order, "status");
                            if (status.isBlank()) status = "(ohne Status)";
                            statusAgg.put(status, statusAgg.getOrDefault(status, 0) + 1);
                        }
                    }
                    for (Map.Entry<String, Integer> e : statusAgg.entrySet()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("status", e.getKey());
                        row.put("count", e.getValue());
                        orderStatusRows.add(row);
                    }
                    orderStatusRows.sort((a,b)->Integer.compare((Integer)b.get("count"),(Integer)a.get("count")));
                } catch (Exception ignored) {
                }

                try {
                    JsonNode rewardRoot = requestJson("https://api.goaffpro.com/v1/admin/rewards?limit=250&fields=id,affiliate_id,amount,status,created_at", apiKey);
                    JsonNode rewards = rewardRoot.get("rewards");
                    Map<String, Map<String, Object>> rewardAgg = new LinkedHashMap<>();
                    if (rewards != null && rewards.isArray()) {
                        for (JsonNode reward : rewards) {
                            String status = asText(reward, "status");
                            if (status.isBlank()) status = "(ohne Status)";
                            final String rewardStatus = status;
                            double amount = parseDoubleSafeStatic(asText(reward, "amount"));
                            rewardAmountTotal += amount;
                            Map<String, Object> agg = rewardAgg.computeIfAbsent(rewardStatus, k -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("status", rewardStatus);
                                m.put("count", 0);
                                m.put("amount", 0.0);
                                return m;
                            });
                            agg.put("count", ((Integer) agg.get("count")) + 1);
                            agg.put("amount", ((Double) agg.get("amount")) + amount);
                        }
                    }
                    rewardStatusRows = new ArrayList<>(rewardAgg.values());
                    rewardStatusRows.sort((a,b)->Double.compare((Double)b.get("amount"),(Double)a.get("amount")));
                } catch (Exception ignored) {
                }

                summary.put("pendingDueTotal", pendingDueTotal);
                summary.put("trafficSources", trafficSourceRows.size());
                summary.put("rewardAmountTotal", rewardAmountTotal);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("summary", summary);
                payload.put("advisorRows", advisorRows);
                payload.put("leaderRows", leaderRows);
                payload.put("countryRows", countryRows);
                payload.put("paymentRows", paymentRows);
                payload.put("pendingRows", pendingRows);
                payload.put("trafficSourceRows", trafficSourceRows);
                payload.put("orderStatusRows", orderStatusRows);
                payload.put("rewardStatusRows", rewardStatusRows);
                Map<String, Object> chartData = new LinkedHashMap<>();
                chartData.put("labels", labels);
                chartData.put("amountSeries", amountSeries);
                chartData.put("txSeries", txSeries);
                payload.put("chartData", chartData);

                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class AnalyticsAdvisorDetailHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String advisorId = asText(body, "advisorId").trim();
                if (advisorId.isBlank()) {
                    sendResponse(exchange, 400, "application/json", "{\"error\":\"advisorId fehlt\"}");
                    return;
                }
                String sinceId = asText(body, "sinceId").trim();
                if (sinceId.isBlank()) sinceId = "0";
                LocalDate fromDate = parseIsoDate(asText(body, "fromDate"));
                LocalDate toDate = parseIsoDate(asText(body, "toDate"));

                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);
                String apiKey = getSecretOrConfig(config, "GOAFFPRO_API_KEY", "goaffproAPIKey", DEFAULT_GOAFFPRO_API_KEY).trim();

                JsonNode advisor = fetchAffiliatesById(apiKey, List.of(advisorId)).get(advisorId);

                String paymentsUrl = "https://api.goaffpro.com/v1/admin/payments?since_id=" + sinceId
                        + "&fields=id,affiliate_id,amount,currency,payment_method,payment_details,affiliate_message,admin_note,transactions,created_at";
                JsonNode paymentRoot = requestJson(paymentsUrl, apiKey);
                JsonNode payments = paymentRoot.get("payments");
                if (payments == null || !payments.isArray()) payments = OBJECT_MAPPER.createArrayNode();

                List<JsonNode> advisorPayments = new ArrayList<>();
                for (JsonNode payment : payments) {
                    if (!advisorId.equals(asText(payment, "affiliate_id").trim())) continue;
                    LocalDate paymentDate = parseIsoDateTimeToLocalDate(asText(payment, "created_at"));
                    if (paymentDate == null) continue;
                    if (fromDate != null && paymentDate.isBefore(fromDate)) continue;
                    if (toDate != null && paymentDate.isAfter(toDate)) continue;
                    advisorPayments.add(payment);
                }

                double payoutSum = 0.0;
                int directSalesCount = 0;
                int indirectSalesCount = 0;
                double directCommission = 0.0;
                double indirectCommission = 0.0;
                int totalTx = 0;
                Set<String> orderIds = new LinkedHashSet<>();
                List<Map<String, Object>> payoutRows = new ArrayList<>();

                for (JsonNode payment : advisorPayments) {
                    String paymentId = asText(payment, "id");
                    double payout = parseDoubleSafeStatic(asText(payment, "amount"));
                    payoutSum += payout;
                    JsonNode txArray = payment.get("transactions");
                    int txCount = (txArray != null && txArray.isArray()) ? txArray.size() : 0;
                    totalTx += txCount;
                    if (txArray != null && txArray.isArray()) {
                        for (JsonNode tx : txArray) {
                            String entityType = asText(tx, "entity_type");
                            double txAmount = parseDoubleSafeStatic(asText(tx, "amount"));
                            if ("orders".equalsIgnoreCase(entityType)) {
                                directSalesCount++;
                                directCommission += txAmount;
                            } else {
                                indirectSalesCount++;
                                indirectCommission += txAmount;
                            }
                            String orderId = asText(tx.path("metadata"), "order_id").trim();
                            if (orderId.isBlank()) orderId = asText(tx, "entity_id").trim();
                            if (!orderId.isBlank()) orderIds.add(orderId);
                        }
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("paymentId", paymentId);
                    row.put("created", formatDateTimeEuropeBerlinStatic(asText(payment, "created_at")));
                    row.put("amount", payout);
                    row.put("currency", asText(payment, "currency"));
                    row.put("method", asText(payment, "payment_method"));
                    row.put("txCount", txCount);
                    row.put("periodLabel", buildPaymentPeriodLabel(payment));
                    payoutRows.add(row);
                }

                Map<String, JsonNode> ordersById = fetchOrdersById(apiKey, new ArrayList<>(orderIds));
                Map<String, Map<String, Object>> productAgg = new LinkedHashMap<>();
                int totalUnits = 0;
                for (JsonNode order : ordersById.values()) {
                    JsonNode lineItems = order.get("line_items");
                    if (lineItems == null || !lineItems.isArray()) continue;
                    for (JsonNode li : lineItems) {
                        String name = asText(li, "title");
                        if (name.isBlank()) name = asText(li, "name");
                        if (name.isBlank()) name = "(ohne Titel)";
                        int qty = parseIntSafe(asText(li, "quantity"));
                        double price = parseDoubleSafeStatic(asText(li, "price"));
                        totalUnits += Math.max(qty, 0);
                        Map<String, Object> agg = productAgg.computeIfAbsent(name, k -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("productName", k);
                            m.put("quantity", 0);
                            m.put("salesValue", 0.0);
                            return m;
                        });
                        agg.put("quantity", ((Integer) agg.get("quantity")) + Math.max(qty, 0));
                        agg.put("salesValue", ((Double) agg.get("salesValue")) + Math.max(price, 0.0) * Math.max(qty, 0));
                    }
                }
                List<Map<String, Object>> productRows = new ArrayList<>(productAgg.values());
                productRows.sort((a, b) -> Integer.compare((Integer) b.get("quantity"), (Integer) a.get("quantity")));

                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("advisorId", advisorId);
                summary.put("advisorName", advisor != null ? asText(advisor, "name") : "");
                summary.put("advisorEmail", advisor != null ? asText(advisor, "email") : "");
                summary.put("advisorCountry", advisor != null ? asText(advisor, "country") : "");
                summary.put("advisorStatus", advisor != null ? asText(advisor, "status") : "");
                summary.put("payoutCount", advisorPayments.size());
                summary.put("payoutSum", payoutSum);
                summary.put("currency", advisorPayments.isEmpty() ? "EUR" : asText(advisorPayments.get(0), "currency"));
                summary.put("totalTransactions", totalTx);
                summary.put("directSalesCount", directSalesCount);
                summary.put("indirectSalesCount", indirectSalesCount);
                summary.put("directCommission", directCommission);
                summary.put("indirectCommission", indirectCommission);
                summary.put("orderCount", ordersById.size());
                summary.put("soldUnits", totalUnits);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("summary", summary);
                payload.put("payoutRows", payoutRows);
                payload.put("productRows", productRows);
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class AnalyticsPartiesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                LocalDate[] range = normalizePartyDateRange(parseIsoDate(asText(body, "fromDate")), parseIsoDate(asText(body, "toDate")));
                LocalDate fromDate = range[0];
                LocalDate toDate = range[1];

                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);
                String apiKey = getSecretOrConfig(config, "GOAFFPRO_API_KEY", "goaffproAPIKey", DEFAULT_GOAFFPRO_API_KEY).trim();

                JsonNode showcaseRoot;
                JsonNode orderRoot;
                if (hasSyncedData(config, "showcases") && hasSyncedData(config, "orders")) {
                    showcaseRoot = loadSyncedRoot(config, "showcases", "showcases");
                    orderRoot = loadSyncedRoot(config, "orders", "orders");
                } else {
                    String showcaseUrl = "https://api.goaffpro.com/v1/admin/showcases?limit=500";
                    String orderFields = "id,number,total,status,affiliate_id,created_at,customer_email,shipping_address,line_items,conversion_source,sub_id";
                    String ordersUrl = "https://api.goaffpro.com/v1/admin/orders?limit=500"
                            + "&created_at_min=" + fromDate + "T00:00:00.000Z"
                            + "&created_at_max=" + toDate + "T23:59:59.999Z"
                            + "&fields=" + orderFields;
                    showcaseRoot = requestJson(showcaseUrl, apiKey);
                    orderRoot = requestJson(ordersUrl, apiKey);
                }
                Map<String, Object> payload = buildPartyAnalyticsPayload(showcaseRoot, orderRoot, fromDate, toDate);
                attachDataSource(payload, config, "showcases");
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class AnalyticsNewCustomersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                LocalDate[] range = normalizePartyDateRange(parseIsoDate(asText(body, "fromDate")), parseIsoDate(asText(body, "toDate")));
                LocalDate fromDate = range[0];
                LocalDate toDate = range[1];

                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);
                String apiKey = getSecretOrConfig(config, "GOAFFPRO_API_KEY", "goaffproAPIKey", DEFAULT_GOAFFPRO_API_KEY).trim();

                List<JsonNode> allOrders = new ArrayList<>();
                Set<String> limitedWeeks = new LinkedHashSet<>();
                if (hasSyncedData(config, "orders")) {
                    allOrders.addAll(jsonArrayToList(loadSyncedRoot(config, "orders", "orders").get("orders")));
                } else {
                    String orderFields = "id,number,total,status,affiliate_id,created_at,is_new_customer,customer";
                    for (LocalDate weekStart = isoWeekStart(fromDate); !weekStart.isAfter(toDate); weekStart = weekStart.plusWeeks(1)) {
                        LocalDate requestFrom = weekStart.isBefore(fromDate) ? fromDate : weekStart;
                        LocalDate requestTo = weekStart.plusDays(6).isAfter(toDate) ? toDate : weekStart.plusDays(6);
                        String weekKey = isoWeekKey(requestFrom);
                        String ordersUrl = "https://api.goaffpro.com/v1/admin/orders?limit=500"
                                + "&created_at_min=" + requestFrom + "T00:00:00.000Z"
                                + "&created_at_max=" + requestTo + "T23:59:59.999Z"
                                + "&fields=" + orderFields;
                        JsonNode orderRoot = requestJson(ordersUrl, apiKey);
                        List<JsonNode> weekOrders = jsonArrayToList(orderRoot.get("orders"));
                        allOrders.addAll(weekOrders);
                        if (weekOrders.size() >= 500) {
                            limitedWeeks.add(weekKey);
                        }
                    }
                }

                Set<String> affiliateIds = new LinkedHashSet<>();
                for (JsonNode order : allOrders) {
                    String affiliateId = asText(order, "affiliate_id").trim();
                    if (!affiliateId.isBlank()) affiliateIds.add(affiliateId);
                }
                Map<String, JsonNode> affiliatesById = hasSyncedData(config, "affiliates")
                        ? loadSyncedEntityMapFiltered(config, "affiliates", new ArrayList<>(affiliateIds))
                        : fetchAffiliatesById(apiKey, new ArrayList<>(affiliateIds));

                Map<String, Object> payload = buildNewCustomerAnalyticsPayload(allOrders, affiliatesById, fromDate, toDate, limitedWeeks);
                attachDataSource(payload, config, "orders");
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class AnalyticsLeaderNewCustomersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                LocalDate[] range = normalizeLeaderNewCustomerDateRange(parseIsoDate(asText(body, "fromDate")), parseIsoDate(asText(body, "toDate")));
                LocalDate fromDate = range[0];
                LocalDate toDate = range[1];

                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);
                String apiKey = getSecretOrConfig(config, "GOAFFPRO_API_KEY", "goaffproAPIKey", DEFAULT_GOAFFPRO_API_KEY).trim();

                List<JsonNode> allOrders = new ArrayList<>();
                Set<String> limitedWeeks = new LinkedHashSet<>();
                if (hasSyncedData(config, "orders")) {
                    allOrders.addAll(jsonArrayToList(loadSyncedRoot(config, "orders", "orders").get("orders")));
                } else {
                    String orderFields = "id,number,total,status,affiliate_id,created_at,is_new_customer,customer";
                    for (LocalDate weekStart = isoWeekStart(fromDate); !weekStart.isAfter(toDate); weekStart = weekStart.plusWeeks(1)) {
                        LocalDate requestFrom = weekStart.isBefore(fromDate) ? fromDate : weekStart;
                        LocalDate requestTo = weekStart.plusDays(6).isAfter(toDate) ? toDate : weekStart.plusDays(6);
                        String weekKey = isoWeekKey(requestFrom);
                        String ordersUrl = "https://api.goaffpro.com/v1/admin/orders?limit=500"
                                + "&created_at_min=" + requestFrom + "T00:00:00.000Z"
                                + "&created_at_max=" + requestTo + "T23:59:59.999Z"
                                + "&fields=" + orderFields;
                        JsonNode orderRoot = requestJson(ordersUrl, apiKey);
                        List<JsonNode> weekOrders = jsonArrayToList(orderRoot.get("orders"));
                        allOrders.addAll(weekOrders);
                        if (weekOrders.size() >= 500) {
                            limitedWeeks.add(weekKey);
                        }
                    }
                }

                Map<String, JsonNode> affiliatesById = hasSyncedData(config, "affiliates")
                        ? loadSyncedEntityMap(config, "affiliates")
                        : fetchAllAffiliatesForTeamAnalytics(apiKey);
                JsonNode treeRoot = hasSyncedData(config, "mlm_tree")
                        ? loadSyncedRoot(config, "mlm_tree", "tree").path("tree")
                        : requestJson("https://api.goaffpro.com/v1/admin/mlm/tree", apiKey);
                Map<String, List<String>> childrenByParent = buildChildrenByParentFromTreeAndAffiliates(treeRoot, affiliatesById);

                Map<String, Object> payload = buildLeaderNewCustomerAnalyticsPayload(
                        allOrders, affiliatesById, childrenByParent, fromDate, toDate, limitedWeeks, LocalDate.now(ZoneId.of("Europe/Berlin")));
                Map<String, Object> weeklyPayload = buildNewCustomerAnalyticsPayload(allOrders, affiliatesById, fromDate, toDate, limitedWeeks);
                payload.put("weekRows", weeklyPayload.get("weekRows"));
                payload.put("advisorWeekRows", weeklyPayload.get("advisorWeekRows"));
                payload.put("advisorRows", weeklyPayload.get("advisorRows"));
                mergeWarnings(payload, weeklyPayload);
                attachDataSource(payload, config, "orders");

                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class LeaderWeeklyMailPreviewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                LocalDate referenceDate = parseIsoDate(asText(body, "referenceDate"));
                if (referenceDate == null) referenceDate = LocalDate.now(BERLIN_ZONE);

                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);

                boolean productionMode = Boolean.parseBoolean(Objects.toString(config.getProperty("sendEmailsEnabled"), "true"))
                        && Boolean.parseBoolean(Objects.toString(config.getProperty("leaderWeeklyMailProductionEnabled"), "false"));
                Map<String, Object> payload = buildLeaderWeeklyMailPayloadFromApi(config, referenceDate, productionMode);
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class LeaderWeeklyMailSendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                LocalDate referenceDate = parseIsoDate(asText(body, "referenceDate"));
                if (referenceDate == null) referenceDate = LocalDate.now(BERLIN_ZONE);
                boolean productionRequested = body.has("production") && body.get("production").asBoolean(false);

                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);

                Map<String, Object> payload = sendLeaderWeeklyMails(config, referenceDate, productionRequested, false);
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private record LeaderWeeklyMailPeriods(LocalDate currentStart, LocalDate currentEnd,
                                           LocalDate previousStart, LocalDate previousEnd,
                                           LocalDate secondPreviousStart, LocalDate secondPreviousEnd,
                                           String periodKey, String periodLabel) {
    }

    private static Map<String, Object> buildLeaderWeeklyMailPayloadFromApi(Properties config,
                                                                           LocalDate referenceDate,
                                                                           boolean productionRequested) throws Exception {
        LeaderWeeklyMailPeriods periods = leaderWeeklyMailPeriods(referenceDate);
        LocalDate monthStart = YearMonth.from(periods.currentEnd()).atDay(1);
        LocalDate fetchFrom = periods.secondPreviousStart().isBefore(monthStart) ? periods.secondPreviousStart() : monthStart;
        Set<String> limitedWeeks = new LinkedHashSet<>();
        String apiKey = getSecretOrConfig(config, "GOAFFPRO_API_KEY", "goaffproAPIKey", DEFAULT_GOAFFPRO_API_KEY).trim();
        List<JsonNode> allOrders = hasSyncedData(config, "orders")
                ? jsonArrayToList(loadSyncedRoot(config, "orders", "orders").get("orders"))
                : fetchGoaffproNewCustomerOrders(apiKey, fetchFrom, periods.currentEnd(), limitedWeeks);
        Map<String, JsonNode> affiliatesById = hasSyncedData(config, "affiliates")
                ? loadSyncedEntityMap(config, "affiliates")
                : fetchAllAffiliatesForTeamAnalytics(apiKey);
        JsonNode treeRoot = hasSyncedData(config, "mlm_tree")
                ? loadSyncedRoot(config, "mlm_tree", "tree").path("tree")
                : requestJson("https://api.goaffpro.com/v1/admin/mlm/tree", apiKey);
        Map<String, List<String>> childrenByParent = buildChildrenByParentFromTreeAndAffiliates(treeRoot, affiliatesById);
        Map<String, Object> payload = buildLeaderWeeklyMailPayload(allOrders, affiliatesById, childrenByParent, config, referenceDate, productionRequested, limitedWeeks);
        attachDataSource(payload, config, "orders");
        return payload;
    }

    private static Map<String, Object> sendLeaderWeeklyMails(Properties config,
                                                             LocalDate referenceDate,
                                                             boolean productionRequested,
                                                             boolean automated) throws Exception {
        boolean sendEmailsEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("sendEmailsEnabled"), "true"));
        if (!sendEmailsEnabled) {
            throw new IOException("E-Mail-Versand ist deaktiviert.");
        }

        Map<String, Object> payload = buildLeaderWeeklyMailPayloadFromApi(config, referenceDate, productionRequested);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reportRows = (List<Map<String, Object>>) payload.getOrDefault("reportRows", Collections.emptyList());
        String bcc = Objects.toString(config.getProperty("emailBcc"), "").trim();
        SmtpConfig smtpConfig = resolveSmtpConfig(config);
        int sentCount = 0;
        int skippedCount = 0;

        for (Map<String, Object> row : reportRows) {
            String recipientMode = Objects.toString(row.get("recipientMode"), "test");
            String toEmail = Objects.toString(row.get("toEmail"), "").trim();
            String subject = Objects.toString(row.get("subject"), "");
            if ("test".equals(recipientMode) && !subject.startsWith("[TEST]")) {
                subject = "[TEST] " + subject;
            }
            String status = "production".equals(recipientMode) ? "sent" : "test";
            if (toEmail.isBlank()) {
                status = "skipped";
                skippedCount++;
            } else {
                sendSimpleHtmlMail(
                        toEmail,
                        bcc,
                        subject,
                        Objects.toString(row.get("plainText"), ""),
                        Objects.toString(row.get("renderedHtml"), ""),
                        smtpConfig);
                sentCount++;
            }
            row.put("sendStatus", status);
            appendLeaderWeeklyMailLogEntry(
                    config,
                    Objects.toString(row.get("leaderId"), ""),
                    Objects.toString(row.get("leaderName"), ""),
                    Objects.toString(row.get("periodKey"), ""),
                    recipientMode,
                    toEmail,
                    status,
                    subject);
        }

        if (automated) {
            Map<String, Object> summary = castMap(payload.get("summary"));
            config.setProperty("leaderWeeklyMailLastSentPeriodKey", Objects.toString(summary.get("periodKey"), ""));
        }
        persistSettings(config);

        Map<String, Object> summary = castMap(payload.get("summary"));
        summary.put("sentCount", sentCount);
        summary.put("skippedCount", skippedCount);
        summary.put("automated", automated);
        payload.put("message", sentCount + " Führungskräfte-Wochenmail(s) verarbeitet.");
        return payload;
    }

    private static Map<String, Object> buildLeaderWeeklyMailPayload(List<JsonNode> orders,
                                                                    Map<String, JsonNode> affiliatesById,
                                                                    Map<String, List<String>> childrenByParent,
                                                                    Properties config,
                                                                    LocalDate referenceDate,
                                                                    boolean productionRequested,
                                                                    Set<String> limitedWeeks) {
        LocalDate effectiveReferenceDate = referenceDate != null ? referenceDate : LocalDate.now(BERLIN_ZONE);
        LeaderWeeklyMailPeriods periods = leaderWeeklyMailPeriods(effectiveReferenceDate);
        YearMonth reportMonth = YearMonth.from(periods.currentEnd());
        LocalDate monthStart = reportMonth.atDay(1);
        Map<String, JsonNode> affiliates = affiliatesById != null ? affiliatesById : Collections.emptyMap();
        Map<String, List<String>> children = childrenByParent != null ? childrenByParent : Collections.emptyMap();
        Set<String> approvedIds = affiliates.entrySet().stream()
                .filter(e -> isApprovedAffiliate(e.getValue()))
                .map(Map.Entry::getKey)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Set<String>> teamMembersByLeader = buildApprovedTeamsByLeader(approvedIds, affiliates, children);
        Map<String, List<String>> leadersByMember = buildLeadersByMember(teamMembersByLeader);
        Map<String, Map<String, Object>> leaderCurrentAgg = new LinkedHashMap<>();
        Map<String, Map<String, Object>> leaderPreviousAgg = new LinkedHashMap<>();
        Map<String, Map<String, Object>> leaderSecondPreviousAgg = new LinkedHashMap<>();
        Map<String, Map<String, Object>> leaderMonthAgg = new LinkedHashMap<>();
        Map<String, Map<String, Object>> contributionAgg = new LinkedHashMap<>();
        int skippedWithoutDate = 0;

        if (orders != null) {
            for (JsonNode order : orders) {
                LocalDate orderDate = firstLocalDate(asText(order, "created_at"));
                if (orderDate == null) {
                    skippedWithoutDate++;
                    continue;
                }
                String advisorId = asText(order, "affiliate_id").trim();
                if (!approvedIds.contains(advisorId)) continue;
                List<String> leaderIds = leadersByMember.getOrDefault(advisorId, Collections.emptyList());
                if (leaderIds.isEmpty()) continue;
                boolean newCustomer = isNewCustomerOrder(order);
                double total = partyOrderTotal(order);

                for (String leaderId : leaderIds) {
                    JsonNode leader = affiliates.get(leaderId);
                    String leaderName = affiliateDisplayName(leader, leaderId);
                    JsonNode advisor = affiliates.get(advisorId);
                    String advisorName = affiliateDisplayName(advisor, advisorId);
                    String contributionKey = leaderId + "|" + advisorId;
                    Map<String, Object> contribution = contributionAgg.computeIfAbsent(contributionKey, k ->
                            newLeaderWeeklyContributionRow(leaderId, leaderName, advisorId, advisorName, leaderId.equals(advisorId)));

                    if (!orderDate.isBefore(periods.currentStart()) && !orderDate.isAfter(periods.currentEnd())) {
                        incrementNewCustomerAgg(leaderCurrentAgg.computeIfAbsent(leaderId, k ->
                                newCustomerPeriodAgg("week", periods.periodKey(), periods.periodLabel(), periods.currentStart(), periods.currentEnd(), leaderId, leaderName, "", "")), newCustomer, total);
                        incrementLeaderWeeklyContribution(contribution, "current", newCustomer, total);
                    } else if (!orderDate.isBefore(periods.previousStart()) && !orderDate.isAfter(periods.previousEnd())) {
                        incrementNewCustomerAgg(leaderPreviousAgg.computeIfAbsent(leaderId, k ->
                                newCustomerPeriodAgg("week", previousLeaderWeeklyPeriodKey(periods), formatDateRange(periods.previousStart(), periods.previousEnd()), periods.previousStart(), periods.previousEnd(), leaderId, leaderName, "", "")), newCustomer, total);
                        incrementLeaderWeeklyContribution(contribution, "previous", newCustomer, total);
                    } else if (!orderDate.isBefore(periods.secondPreviousStart()) && !orderDate.isAfter(periods.secondPreviousEnd())) {
                        incrementNewCustomerAgg(leaderSecondPreviousAgg.computeIfAbsent(leaderId, k ->
                                newCustomerPeriodAgg("week", secondPreviousLeaderWeeklyPeriodKey(periods), formatDateRange(periods.secondPreviousStart(), periods.secondPreviousEnd()), periods.secondPreviousStart(), periods.secondPreviousEnd(), leaderId, leaderName, "", "")), newCustomer, total);
                        incrementLeaderWeeklyContribution(contribution, "secondPrevious", newCustomer, total);
                    }

                    if (!orderDate.isBefore(monthStart) && !orderDate.isAfter(periods.currentEnd())) {
                        incrementNewCustomerAgg(leaderMonthAgg.computeIfAbsent(leaderId, k ->
                                newCustomerPeriodAgg("month", monthKey(reportMonth), monthKey(reportMonth), monthStart, reportMonth.atEndOfMonth(), leaderId, leaderName, "", "")), newCustomer, total);
                    }
                }
            }
        }

        List<Map<String, Object>> reportRows = new ArrayList<>();
        String contactEmail = Objects.toString(config.getProperty("contactEmail"), "").trim();
        boolean sendEmailsEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("sendEmailsEnabled"), "true"));
        boolean productionEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("leaderWeeklyMailProductionEnabled"), "false"));
        boolean productionMode = sendEmailsEnabled && productionEnabled && productionRequested;
        String template = Objects.toString(config.getProperty("leaderWeeklyReportTemplateHtml"), "").trim();
        if (template.isBlank()) template = getDefaultLeaderWeeklyReportHtmlTemplate();

        int totalNewCustomers = 0;
        int totalLeadersOk = 0;
        int totalLeadersAttention = 0;
        int totalLeadersSupport = 0;

        for (Map.Entry<String, Set<String>> entry : teamMembersByLeader.entrySet()) {
            String leaderId = entry.getKey();
            JsonNode leader = affiliates.get(leaderId);
            String leaderName = affiliateDisplayName(leader, leaderId);
            String leaderEmail = asText(leader, "email").trim();
            Map<String, Object> current = finalizeSingleAgg(leaderCurrentAgg.get(leaderId));
            Map<String, Object> previous = finalizeSingleAgg(leaderPreviousAgg.get(leaderId));
            Map<String, Object> secondPrevious = finalizeSingleAgg(leaderSecondPreviousAgg.get(leaderId));
            Map<String, Object> month = finalizeSingleAgg(leaderMonthAgg.get(leaderId));
            int currentNewCustomers = intValue(current.get("newCustomerOrders"));
            int previousNewCustomers = intValue(previous.get("newCustomerOrders"));
            int secondPreviousNewCustomers = intValue(secondPrevious.get("newCustomerOrders"));
            int monthNewCustomers = intValue(month.get("newCustomerOrders"));
            totalNewCustomers += currentNewCustomers;

            String status;
            String statusLabel;
            String actionText;
            if (monthNewCustomers >= LEADER_NEW_CUSTOMER_MONTHLY_TARGET) {
                status = "OK";
                statusLabel = "Ziel erreicht";
                actionText = "Das Monatsziel ist erreicht. Fokus auf stabile Fortsetzung.";
                totalLeadersOk++;
            } else if (currentNewCustomers >= 10) {
                status = "AUFMERKSAMKEIT";
                statusLabel = "Aufmerksamkeit";
                actionText = "Die Woche liegt auf Kurs, der Monatsfortschritt sollte aktiv beobachtet werden.";
                totalLeadersAttention++;
            } else {
                status = "UNTERSTUETZUNG";
                statusLabel = "Unterstützung nötig";
                actionText = "Die letzte Woche liegt unter dem Wochenrichtwert. Bitte Teamaktivität und Neukundenansprache priorisieren.";
                totalLeadersSupport++;
            }

            List<Map<String, Object>> teamRows = buildLeaderWeeklyTeamRows(leaderId, entry.getValue(), affiliates, contributionAgg);
            String teamRowsHtml = renderLeaderWeeklyTeamRowsHtml(teamRows);
            String recipientMode = productionMode ? "production" : "test";
            String toEmail = productionMode ? leaderEmail : contactEmail;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("leaderId", leaderId);
            row.put("leaderName", leaderName);
            row.put("leaderEmail", leaderEmail);
            row.put("toEmail", toEmail);
            row.put("recipientMode", recipientMode);
            row.put("recipientModeLabel", productionMode ? "PRODUKTIV an Führungskraft" : "TEST an Kontakt-E-Mail");
            row.put("periodKey", periods.periodKey());
            row.put("periodLabel", periods.periodLabel());
            row.put("periodStart", periods.currentStart().toString());
            row.put("periodEnd", periods.currentEnd().toString());
            row.put("previousPeriodLabel", formatDateRange(periods.previousStart(), periods.previousEnd()));
            row.put("secondPreviousPeriodLabel", formatDateRange(periods.secondPreviousStart(), periods.secondPreviousEnd()));
            row.put("currentWeekNewCustomers", currentNewCustomers);
            row.put("previousWeekNewCustomers", previousNewCustomers);
            row.put("secondPreviousWeekNewCustomers", secondPreviousNewCustomers);
            row.put("teamSize", entry.getValue().size());
            row.put("monthlyTarget", LEADER_NEW_CUSTOMER_MONTHLY_TARGET);
            row.put("monthKey", monthKey(reportMonth));
            row.put("monthNewCustomers", monthNewCustomers);
            row.put("monthProgressPercent", ratio(monthNewCustomers, LEADER_NEW_CUSTOMER_MONTHLY_TARGET));
            row.put("status", status);
            row.put("statusLabel", statusLabel);
            row.put("actionText", actionText);
            row.put("teamContributionRows", teamRows);
            row.put("subject", "Neukundenreport " + periods.periodLabel() + " - " + leaderName);
            row.put("plainText", buildLeaderWeeklyPlainText(row));
            row.put("renderedHtml", renderLeaderWeeklyReportHtml(template, row, teamRowsHtml));
            reportRows.add(row);
        }

        reportRows.sort((a, b) -> {
            int rank = Integer.compare(leaderWeeklyStatusRank(Objects.toString(a.get("status"), "")), leaderWeeklyStatusRank(Objects.toString(b.get("status"), "")));
            if (rank != 0) return rank;
            int current = Integer.compare(intValue(b.get("currentWeekNewCustomers")), intValue(a.get("currentWeekNewCustomers")));
            if (current != 0) return current;
            return Objects.toString(a.get("leaderName"), "").compareToIgnoreCase(Objects.toString(b.get("leaderName"), ""));
        });

        List<String> warnings = new ArrayList<>();
        if (limitedWeeks != null && !limitedWeeks.isEmpty()) {
            warnings.add("Order-Limit in " + String.join(", ", limitedWeeks) + " erreicht: Daten ggf. unvollständig, Zeitraum enger prüfen.");
        }
        if (skippedWithoutDate > 0) {
            warnings.add(skippedWithoutDate + " Order(s) ohne verwertbares Datum wurden nicht ausgewertet.");
        }
        if (!productionMode && contactEmail.isBlank()) {
            warnings.add("Kontakt-E-Mail fehlt: Testversand ist erst nach Pflege der Kontakt-E-Mail möglich.");
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("periodKey", periods.periodKey());
        summary.put("periodLabel", periods.periodLabel());
        summary.put("periodStart", periods.currentStart().toString());
        summary.put("periodEnd", periods.currentEnd().toString());
        summary.put("previousPeriodLabel", formatDateRange(periods.previousStart(), periods.previousEnd()));
        summary.put("secondPreviousPeriodLabel", formatDateRange(periods.secondPreviousStart(), periods.secondPreviousEnd()));
        summary.put("target", LEADER_NEW_CUSTOMER_MONTHLY_TARGET);
        summary.put("leaderCount", reportRows.size());
        summary.put("okCount", totalLeadersOk);
        summary.put("attentionCount", totalLeadersAttention);
        summary.put("supportCount", totalLeadersSupport);
        summary.put("newCustomerOrders", totalNewCustomers);
        summary.put("recipientMode", productionMode ? "production" : "test");
        summary.put("recipientModeLabel", productionMode ? "PRODUKTIV an Führungskraft" : "TEST an Kontakt-E-Mail");
        summary.put("sendEmailsEnabled", sendEmailsEnabled);
        summary.put("productionEnabled", productionEnabled);
        summary.put("productionRequested", productionRequested);
        summary.put("scheduleDay", normalizeLeaderWeeklyMailScheduleDay(Objects.toString(config.getProperty("leaderWeeklyMailScheduleDay"), "")));
        summary.put("scheduleTime", normalizeLeaderWeeklyMailScheduleTime(Objects.toString(config.getProperty("leaderWeeklyMailScheduleTime"), "")));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("reportRows", reportRows);
        payload.put("warnings", warnings);
        return payload;
    }

    private static List<JsonNode> fetchGoaffproNewCustomerOrders(String apiKey, LocalDate fromDate, LocalDate toDate, Set<String> limitedWeeks) throws Exception {
        List<JsonNode> allOrders = new ArrayList<>();
        String orderFields = "id,number,total,status,affiliate_id,created_at,is_new_customer,customer";
        for (LocalDate weekStart = isoWeekStart(fromDate); !weekStart.isAfter(toDate); weekStart = weekStart.plusWeeks(1)) {
            LocalDate requestFrom = weekStart.isBefore(fromDate) ? fromDate : weekStart;
            LocalDate requestTo = weekStart.plusDays(6).isAfter(toDate) ? toDate : weekStart.plusDays(6);
            String weekKey = isoWeekKey(requestFrom);
            String ordersUrl = "https://api.goaffpro.com/v1/admin/orders?limit=500"
                    + "&created_at_min=" + requestFrom + "T00:00:00.000Z"
                    + "&created_at_max=" + requestTo + "T23:59:59.999Z"
                    + "&fields=" + orderFields;
            JsonNode orderRoot = requestJson(ordersUrl, apiKey);
            List<JsonNode> weekOrders = jsonArrayToList(orderRoot.get("orders"));
            allOrders.addAll(weekOrders);
            if (weekOrders.size() >= 500 && limitedWeeks != null) {
                limitedWeeks.add(weekKey);
            }
        }
        return allOrders;
    }

    private static LeaderWeeklyMailPeriods leaderWeeklyMailPeriods(LocalDate referenceDate) {
        LocalDate ref = referenceDate != null ? referenceDate : LocalDate.now(BERLIN_ZONE);
        LocalDate currentEnd = ref.minusDays(1);
        LocalDate currentStart = currentEnd.minusDays(6);
        LocalDate previousEnd = currentStart.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(6);
        LocalDate secondPreviousEnd = previousStart.minusDays(1);
        LocalDate secondPreviousStart = secondPreviousEnd.minusDays(6);
        String periodKey = currentStart + "_" + currentEnd;
        return new LeaderWeeklyMailPeriods(
                currentStart,
                currentEnd,
                previousStart,
                previousEnd,
                secondPreviousStart,
                secondPreviousEnd,
                periodKey,
                formatDateRange(currentStart, currentEnd));
    }

    private static String previousLeaderWeeklyPeriodKey(LeaderWeeklyMailPeriods periods) {
        return periods.previousStart() + "_" + periods.previousEnd();
    }

    private static String secondPreviousLeaderWeeklyPeriodKey(LeaderWeeklyMailPeriods periods) {
        return periods.secondPreviousStart() + "_" + periods.secondPreviousEnd();
    }

    private static String formatDateRange(LocalDate fromDate, LocalDate toDate) {
        return formatGermanDate(fromDate) + " bis " + formatGermanDate(toDate);
    }

    private static String formatGermanDate(LocalDate date) {
        return date == null ? "" : date.format(OUTPUT_FORMATTER);
    }

    private static Map<String, Set<String>> buildApprovedTeamsByLeader(Set<String> approvedIds,
                                                                       Map<String, JsonNode> affiliates,
                                                                       Map<String, List<String>> children) {
        Map<String, Set<String>> teamMembersByLeader = new LinkedHashMap<>();
        for (String leaderId : approvedIds) {
            Set<String> descendants = new LinkedHashSet<>();
            collectApprovedDescendants(leaderId, children, affiliates, descendants, new LinkedHashSet<>());
            if (!descendants.isEmpty()) {
                Set<String> team = new LinkedHashSet<>();
                team.add(leaderId);
                team.addAll(descendants);
                teamMembersByLeader.put(leaderId, team);
            }
        }
        return teamMembersByLeader;
    }

    private static Map<String, List<String>> buildLeadersByMember(Map<String, Set<String>> teamMembersByLeader) {
        Map<String, List<String>> leadersByMember = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : teamMembersByLeader.entrySet()) {
            for (String memberId : entry.getValue()) {
                leadersByMember.computeIfAbsent(memberId, k -> new ArrayList<>()).add(entry.getKey());
            }
        }
        return leadersByMember;
    }

    private static Map<String, Object> newLeaderWeeklyContributionRow(String leaderId,
                                                                      String leaderName,
                                                                      String advisorId,
                                                                      String advisorName,
                                                                      boolean isLeaderSelf) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("leaderId", leaderId);
        row.put("leaderName", leaderName);
        row.put("advisorId", advisorId);
        row.put("advisorName", advisorName);
        row.put("isLeaderSelf", isLeaderSelf);
        row.put("currentTotalOrders", 0);
        row.put("currentNewCustomers", 0);
        row.put("currentNewCustomerRevenue", 0.0);
        row.put("previousTotalOrders", 0);
        row.put("previousNewCustomers", 0);
        row.put("secondPreviousTotalOrders", 0);
        row.put("secondPreviousNewCustomers", 0);
        return row;
    }

    private static void incrementLeaderWeeklyContribution(Map<String, Object> row, String prefix, boolean newCustomer, double total) {
        String totalKey = prefix + "TotalOrders";
        String newKey = prefix + "NewCustomers";
        row.put(totalKey, intValue(row.get(totalKey)) + 1);
        if (newCustomer) {
            row.put(newKey, intValue(row.get(newKey)) + 1);
            if ("current".equals(prefix)) {
                row.put("currentNewCustomerRevenue", doubleValue(row.get("currentNewCustomerRevenue")) + total);
            }
        }
    }

    private static Map<String, Object> finalizeSingleAgg(Map<String, Object> row) {
        if (row == null) return Collections.emptyMap();
        finalizeNewCustomerRows(List.of(row));
        return row;
    }

    private static List<Map<String, Object>> buildLeaderWeeklyTeamRows(String leaderId,
                                                                       Set<String> teamIds,
                                                                       Map<String, JsonNode> affiliates,
                                                                       Map<String, Map<String, Object>> contributionAgg) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String advisorId : teamIds) {
            JsonNode advisor = affiliates.get(advisorId);
            String advisorName = affiliateDisplayName(advisor, advisorId);
            Map<String, Object> row = new LinkedHashMap<>(contributionAgg.getOrDefault(
                    leaderId + "|" + advisorId,
                    newLeaderWeeklyContributionRow(leaderId, affiliateDisplayName(affiliates.get(leaderId), leaderId), advisorId, advisorName, leaderId.equals(advisorId))));
            rows.add(row);
        }
        rows.sort((a, b) -> {
            int current = Integer.compare(intValue(b.get("currentNewCustomers")), intValue(a.get("currentNewCustomers")));
            if (current != 0) return current;
            int previous = Integer.compare(intValue(b.get("previousNewCustomers")), intValue(a.get("previousNewCustomers")));
            if (previous != 0) return previous;
            return Objects.toString(a.get("advisorName"), "").compareToIgnoreCase(Objects.toString(b.get("advisorName"), ""));
        });
        return rows;
    }

    private static String renderLeaderWeeklyTeamRowsHtml(List<Map<String, Object>> teamRows) {
        if (teamRows == null || teamRows.isEmpty()) {
            return "<tr><td colspan=\"5\" style=\"padding:10px;border:1px solid #e2e8f0;\">Keine Teamdaten vorhanden.</td></tr>";
        }
        StringBuilder html = new StringBuilder();
        for (Map<String, Object> row : teamRows) {
            html.append("<tr>")
                    .append("<td style=\"padding:8px;border:1px solid #e2e8f0;\">")
                    .append(escapeHtmlEmail(Objects.toString(row.get("advisorName"), "")))
                    .append(row.get("isLeaderSelf") instanceof Boolean && (Boolean) row.get("isLeaderSelf") ? " <span style=\"color:#64748b;\">(selbst)</span>" : "")
                    .append("</td>")
                    .append("<td style=\"padding:8px;border:1px solid #e2e8f0;text-align:right;\">").append(intValue(row.get("currentNewCustomers"))).append("</td>")
                    .append("<td style=\"padding:8px;border:1px solid #e2e8f0;text-align:right;\">").append(intValue(row.get("previousNewCustomers"))).append("</td>")
                    .append("<td style=\"padding:8px;border:1px solid #e2e8f0;text-align:right;\">").append(intValue(row.get("secondPreviousNewCustomers"))).append("</td>")
                    .append("<td style=\"padding:8px;border:1px solid #e2e8f0;text-align:right;\">").append(euroStatic(doubleValue(row.get("currentNewCustomerRevenue")))).append("</td>")
                    .append("</tr>");
        }
        return html.toString();
    }

    private static String buildLeaderWeeklyPlainText(Map<String, Object> row) {
        return "Neukundenreport " + Objects.toString(row.get("periodLabel"), "") + "\n"
                + "Führungskraft: " + Objects.toString(row.get("leaderName"), "") + "\n"
                + "Neukunden letzte 7 Tage: " + Objects.toString(row.get("currentWeekNewCustomers"), "0") + "\n"
                + "Vorwoche: " + Objects.toString(row.get("previousWeekNewCustomers"), "0") + "\n"
                + "Monatsfortschritt: " + Objects.toString(row.get("monthNewCustomers"), "0") + "/"
                + Objects.toString(row.get("monthlyTarget"), "40") + "\n"
                + "Status: " + Objects.toString(row.get("statusLabel"), "") + "\n"
                + Objects.toString(row.get("actionText"), "");
    }

    private static String renderLeaderWeeklyReportHtml(String template, Map<String, Object> row, String teamRowsHtml) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("leaderName", escapeHtmlEmail(Objects.toString(row.get("leaderName"), "")));
        values.put("leaderEmail", escapeHtmlEmail(Objects.toString(row.get("leaderEmail"), "")));
        values.put("toEmail", escapeHtmlEmail(Objects.toString(row.get("toEmail"), "")));
        values.put("recipientMode", escapeHtmlEmail(Objects.toString(row.get("recipientModeLabel"), "")));
        values.put("periodLabel", escapeHtmlEmail(Objects.toString(row.get("periodLabel"), "")));
        values.put("reportFrom", escapeHtmlEmail(formatGermanDate(parseIsoDate(Objects.toString(row.get("periodStart"), "")))));
        values.put("reportTo", escapeHtmlEmail(formatGermanDate(parseIsoDate(Objects.toString(row.get("periodEnd"), "")))));
        values.put("previousPeriodLabel", escapeHtmlEmail(Objects.toString(row.get("previousPeriodLabel"), "")));
        values.put("secondPreviousPeriodLabel", escapeHtmlEmail(Objects.toString(row.get("secondPreviousPeriodLabel"), "")));
        values.put("teamSize", escapeHtmlEmail(Objects.toString(row.get("teamSize"), "0")));
        values.put("currentWeekNewCustomers", escapeHtmlEmail(Objects.toString(row.get("currentWeekNewCustomers"), "0")));
        values.put("previousWeekNewCustomers", escapeHtmlEmail(Objects.toString(row.get("previousWeekNewCustomers"), "0")));
        values.put("secondPreviousWeekNewCustomers", escapeHtmlEmail(Objects.toString(row.get("secondPreviousWeekNewCustomers"), "0")));
        values.put("monthlyTarget", escapeHtmlEmail(Objects.toString(row.get("monthlyTarget"), "40")));
        values.put("monthKey", escapeHtmlEmail(Objects.toString(row.get("monthKey"), "")));
        values.put("monthNewCustomers", escapeHtmlEmail(Objects.toString(row.get("monthNewCustomers"), "0")));
        values.put("monthProgressPercent", escapeHtmlEmail(percentPlain(doubleValue(row.get("monthProgressPercent")))));
        values.put("status", escapeHtmlEmail(Objects.toString(row.get("status"), "")));
        values.put("statusLabel", escapeHtmlEmail(Objects.toString(row.get("statusLabel"), "")));
        values.put("actionText", escapeHtmlEmail(Objects.toString(row.get("actionText"), "")));
        values.put("teamRows", teamRowsHtml);

        String rendered = template == null || template.isBlank() ? getDefaultLeaderWeeklyReportHtmlTemplate() : template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return rendered;
    }

    private static int leaderWeeklyStatusRank(String status) {
        return switch (status) {
            case "UNTERSTUETZUNG" -> 0;
            case "AUFMERKSAMKEIT" -> 1;
            case "OK" -> 2;
            default -> 3;
        };
    }

    private static int intValue(Object value) {
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(Objects.toString(value, "0"));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static double doubleValue(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(Objects.toString(value, "0").replace(",", "."));
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private static String percentPlain(double ratioValue) {
        return String.format(java.util.Locale.GERMANY, "%.0f %%", ratioValue * 100.0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?>) return (Map<String, Object>) value;
        return new LinkedHashMap<>();
    }


    private static LocalDate[] normalizePartyDateRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Berlin"));
        LocalDate effectiveTo = toDate != null ? toDate : today;
        LocalDate effectiveFrom = fromDate != null ? fromDate : effectiveTo.minusDays(90);
        if (effectiveFrom.isAfter(effectiveTo)) {
            effectiveTo = effectiveFrom;
        }
        return new LocalDate[]{effectiveFrom, effectiveTo};
    }

    private static LocalDate[] normalizeLeaderNewCustomerDateRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Berlin"));
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate defaultFrom = currentMonth.minusMonths(2).atDay(1);
        LocalDate effectiveTo = toDate != null ? toDate : today;
        LocalDate effectiveFrom = fromDate != null ? fromDate : defaultFrom;
        if (fromDate == null && toDate != null) {
            effectiveFrom = YearMonth.from(effectiveTo).minusMonths(2).atDay(1);
        }
        if (effectiveFrom.isAfter(effectiveTo)) {
            effectiveTo = effectiveFrom;
        }
        return new LocalDate[]{effectiveFrom, effectiveTo};
    }

    private static Map<String, Object> buildNewCustomerAnalyticsPayload(List<JsonNode> orders,
                                                                        Map<String, JsonNode> affiliatesById,
                                                                        LocalDate fromDate, LocalDate toDate,
                                                                        Set<String> limitedWeeks) {
        LocalDate[] range = normalizePartyDateRange(fromDate, toDate);
        fromDate = range[0];
        toDate = range[1];

        Map<String, Map<String, Object>> weekAgg = new LinkedHashMap<>();
        Map<String, Map<String, Object>> advisorWeekAgg = new LinkedHashMap<>();
        Map<String, Map<String, Object>> advisorAgg = new LinkedHashMap<>();
        int skippedWithoutDate = 0;

        if (orders != null) {
            for (JsonNode order : orders) {
                LocalDate orderDate = firstLocalDate(asText(order, "created_at"));
                if (orderDate == null) {
                    skippedWithoutDate++;
                    continue;
                }
                if (orderDate.isBefore(fromDate) || orderDate.isAfter(toDate)) continue;

                String affiliateId = asText(order, "affiliate_id").trim();
                JsonNode affiliate = affiliatesById != null ? affiliatesById.get(affiliateId) : null;
                String advisorName = affiliate != null ? asText(affiliate, "name") : (affiliateId.isBlank() ? "Unbekannt" : "ID " + affiliateId);
                String weekKey = isoWeekKey(orderDate);
                boolean newCustomer = isNewCustomerOrder(order);
                double total = partyOrderTotal(order);

                incrementNewCustomerAgg(weekAgg.computeIfAbsent(weekKey, k -> newCustomerAgg(k, "", "")), newCustomer, total);

                String advisorWeekKey = (affiliateId.isBlank() ? advisorName : affiliateId) + "|" + weekKey;
                Map<String, Object> advisorWeek = advisorWeekAgg.computeIfAbsent(advisorWeekKey, k -> newCustomerAgg(weekKey, affiliateId, advisorName));
                advisorWeek.put("advisorId", affiliateId);
                advisorWeek.put("advisorName", advisorName);
                incrementNewCustomerAgg(advisorWeek, newCustomer, total);

                Map<String, Object> advisor = advisorAgg.computeIfAbsent(affiliateId.isBlank() ? advisorName : affiliateId, k -> newCustomerAgg("", affiliateId, advisorName));
                advisor.put("advisorId", affiliateId);
                advisor.put("advisorName", advisorName);
                incrementNewCustomerAgg(advisor, newCustomer, total);
            }
        }

        List<Map<String, Object>> weekRows = finalizeNewCustomerRows(new ArrayList<>(weekAgg.values()));
        weekRows.sort((a, b) -> Objects.toString(a.get("weekKey"), "").compareTo(Objects.toString(b.get("weekKey"), "")));

        List<Map<String, Object>> advisorWeekRows = finalizeNewCustomerRows(new ArrayList<>(advisorWeekAgg.values()));
        advisorWeekRows.sort((a, b) -> {
            int week = Objects.toString(a.get("weekKey"), "").compareTo(Objects.toString(b.get("weekKey"), ""));
            if (week != 0) return week;
            int newCustomers = Integer.compare((Integer) b.get("newCustomerOrders"), (Integer) a.get("newCustomerOrders"));
            if (newCustomers != 0) return newCustomers;
            return Objects.toString(a.get("advisorName"), "").compareToIgnoreCase(Objects.toString(b.get("advisorName"), ""));
        });

        List<Map<String, Object>> advisorRows = finalizeNewCustomerRows(new ArrayList<>(advisorAgg.values()));
        advisorRows.sort((a, b) -> {
            int newCustomers = Integer.compare((Integer) b.get("newCustomerOrders"), (Integer) a.get("newCustomerOrders"));
            if (newCustomers != 0) return newCustomers;
            return Double.compare((Double) b.get("newCustomerRevenue"), (Double) a.get("newCustomerRevenue"));
        });

        int totalOrders = weekRows.stream().mapToInt(r -> (Integer) r.get("totalOrders")).sum();
        int newCustomerOrders = weekRows.stream().mapToInt(r -> (Integer) r.get("newCustomerOrders")).sum();
        double totalRevenue = weekRows.stream().mapToDouble(r -> (Double) r.get("totalRevenue")).sum();
        double newCustomerRevenue = weekRows.stream().mapToDouble(r -> (Double) r.get("newCustomerRevenue")).sum();

        List<String> warnings = new ArrayList<>();
        if (limitedWeeks != null && !limitedWeeks.isEmpty()) {
            warnings.add("Order-Limit in " + String.join(", ", limitedWeeks) + " erreicht: Daten ggf. unvollständig, Zeitraum enger prüfen.");
        }
        if (skippedWithoutDate > 0) {
            warnings.add(skippedWithoutDate + " Order(s) ohne verwertbares Datum wurden nicht ausgewertet.");
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fromDate", fromDate.toString());
        summary.put("toDate", toDate.toString());
        summary.put("totalOrders", totalOrders);
        summary.put("newCustomerOrders", newCustomerOrders);
        summary.put("returningCustomerOrders", Math.max(0, totalOrders - newCustomerOrders));
        summary.put("newCustomerRate", ratio(newCustomerOrders, totalOrders));
        summary.put("totalRevenue", totalRevenue);
        summary.put("newCustomerRevenue", newCustomerRevenue);
        summary.put("advisorCount", advisorRows.size());
        summary.put("weekCount", weekRows.size());
        summary.put("limited", limitedWeeks != null && !limitedWeeks.isEmpty());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("weekRows", weekRows);
        payload.put("advisorWeekRows", advisorWeekRows);
        payload.put("advisorRows", advisorRows);
        payload.put("warnings", warnings);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private static void mergeWarnings(Map<String, Object> target, Map<String, Object> source) {
        Object targetWarningsRaw = target.get("warnings");
        List<String> targetWarnings;
        if (targetWarningsRaw instanceof List<?>) {
            targetWarnings = (List<String>) targetWarningsRaw;
        } else {
            targetWarnings = new ArrayList<>();
            target.put("warnings", targetWarnings);
        }
        Object sourceWarningsRaw = source != null ? source.get("warnings") : null;
        if (sourceWarningsRaw instanceof List<?>) {
            for (Object warning : (List<?>) sourceWarningsRaw) {
                String text = Objects.toString(warning, "");
                if (!text.isBlank() && !targetWarnings.contains(text)) {
                    targetWarnings.add(text);
                }
            }
        }
    }

    private static Map<String, Object> buildLeaderNewCustomerAnalyticsPayload(List<JsonNode> orders,
                                                                              Map<String, JsonNode> affiliatesById,
                                                                              Map<String, List<String>> childrenByParent,
                                                                              LocalDate fromDate, LocalDate toDate,
                                                                              Set<String> limitedWeeks,
                                                                              LocalDate today) {
        LocalDate[] range = normalizeLeaderNewCustomerDateRange(fromDate, toDate);
        fromDate = range[0];
        toDate = range[1];
        LocalDate effectiveToday = today != null ? today : LocalDate.now(ZoneId.of("Europe/Berlin"));
        YearMonth currentMonth = YearMonth.from(effectiveToday);
        YearMonth rangeToMonth = YearMonth.from(toDate);
        YearMonth lastClosedMonth = rangeToMonth.isBefore(currentMonth) ? rangeToMonth : currentMonth.minusMonths(1);
        YearMonth previousClosedMonth = lastClosedMonth.minusMonths(1);
        YearMonth liveMonth = monthOverlaps(currentMonth, fromDate, toDate) ? currentMonth : null;

        Map<String, JsonNode> affiliates = affiliatesById != null ? affiliatesById : Collections.emptyMap();
        Map<String, List<String>> children = childrenByParent != null ? childrenByParent : Collections.emptyMap();
        Set<String> approvedIds = affiliates.entrySet().stream()
                .filter(e -> isApprovedAffiliate(e.getValue()))
                .map(Map.Entry::getKey)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Set<String>> teamMembersByLeader = new LinkedHashMap<>();
        for (String leaderId : approvedIds) {
            Set<String> descendants = new LinkedHashSet<>();
            collectApprovedDescendants(leaderId, children, affiliates, descendants, new LinkedHashSet<>());
            if (!descendants.isEmpty()) {
                Set<String> team = new LinkedHashSet<>();
                team.add(leaderId);
                team.addAll(descendants);
                teamMembersByLeader.put(leaderId, team);
            }
        }

        Map<String, List<String>> leadersByMember = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : teamMembersByLeader.entrySet()) {
            for (String memberId : entry.getValue()) {
                leadersByMember.computeIfAbsent(memberId, k -> new ArrayList<>()).add(entry.getKey());
            }
        }

        List<YearMonth> months = monthsBetween(fromDate, toDate);
        Map<String, Map<String, Object>> leaderMonthAgg = new LinkedHashMap<>();
        Map<String, Map<String, Object>> leaderWeekAgg = new LinkedHashMap<>();
        Map<String, Map<String, Object>> advisorContributionAgg = new LinkedHashMap<>();
        int skippedWithoutDate = 0;
        int totalOrders = 0;
        int newCustomerOrders = 0;
        double totalRevenue = 0.0;
        double newCustomerRevenue = 0.0;

        if (orders != null) {
            for (JsonNode order : orders) {
                LocalDate orderDate = firstLocalDate(asText(order, "created_at"));
                if (orderDate == null) {
                    skippedWithoutDate++;
                    continue;
                }
                if (orderDate.isBefore(fromDate) || orderDate.isAfter(toDate)) continue;

                String advisorId = asText(order, "affiliate_id").trim();
                if (!approvedIds.contains(advisorId)) continue;
                boolean newCustomer = isNewCustomerOrder(order);
                double total = partyOrderTotal(order);
                totalOrders++;
                totalRevenue += total;
                if (newCustomer) {
                    newCustomerOrders++;
                    newCustomerRevenue += total;
                }

                List<String> leaderIds = leadersByMember.getOrDefault(advisorId, Collections.emptyList());
                if (leaderIds.isEmpty()) continue;
                JsonNode advisor = affiliates.get(advisorId);
                String advisorName = affiliateDisplayName(advisor, advisorId);
                YearMonth month = YearMonth.from(orderDate);
                String monthKey = monthKey(month);
                String weekKey = isoWeekKey(orderDate);

                for (String leaderId : leaderIds) {
                    JsonNode leader = affiliates.get(leaderId);
                    String leaderName = affiliateDisplayName(leader, leaderId);

                    String leaderMonthKey = leaderId + "|" + monthKey;
                    Map<String, Object> monthRow = leaderMonthAgg.computeIfAbsent(leaderMonthKey, k ->
                            newCustomerPeriodAgg("month", monthKey, monthKey, month.atDay(1), month.atEndOfMonth(), leaderId, leaderName, "", ""));
                    incrementNewCustomerAgg(monthRow, newCustomer, total);

                    LocalDate weekStart = isoWeekStart(orderDate);
                    String leaderWeekKey = leaderId + "|" + weekKey;
                    Map<String, Object> weekRow = leaderWeekAgg.computeIfAbsent(leaderWeekKey, k ->
                            newCustomerPeriodAgg("week", weekKey, weekKey, weekStart, weekStart.plusDays(6), leaderId, leaderName, "", ""));
                    incrementNewCustomerAgg(weekRow, newCustomer, total);

                    String contributionKey = leaderId + "|" + advisorId;
                    Map<String, Object> contributionRow = advisorContributionAgg.computeIfAbsent(contributionKey, k ->
                            newCustomerPeriodAgg("advisor", "", "", null, null, leaderId, leaderName, advisorId, advisorName));
                    contributionRow.put("isLeaderSelf", leaderId.equals(advisorId));
                    incrementNewCustomerAgg(contributionRow, newCustomer, total);
                }
            }
        }

        for (String leaderId : teamMembersByLeader.keySet()) {
            JsonNode leader = affiliates.get(leaderId);
            String leaderName = affiliateDisplayName(leader, leaderId);
            for (YearMonth month : months) {
                String mKey = monthKey(month);
                leaderMonthAgg.computeIfAbsent(leaderId + "|" + mKey, k ->
                        newCustomerPeriodAgg("month", mKey, mKey, month.atDay(1), month.atEndOfMonth(), leaderId, leaderName, "", ""));
            }
        }

        List<Map<String, Object>> leaderMonthRows = finalizeNewCustomerRows(new ArrayList<>(leaderMonthAgg.values()));
        leaderMonthRows.sort((a, b) -> {
            int leader = Objects.toString(a.get("leaderName"), "").compareToIgnoreCase(Objects.toString(b.get("leaderName"), ""));
            if (leader != 0) return leader;
            return Objects.toString(a.get("periodKey"), "").compareTo(Objects.toString(b.get("periodKey"), ""));
        });

        List<Map<String, Object>> leaderWeekRows = finalizeNewCustomerRows(new ArrayList<>(leaderWeekAgg.values()));
        leaderWeekRows.sort((a, b) -> {
            int leader = Objects.toString(a.get("leaderName"), "").compareToIgnoreCase(Objects.toString(b.get("leaderName"), ""));
            if (leader != 0) return leader;
            return Objects.toString(a.get("periodKey"), "").compareTo(Objects.toString(b.get("periodKey"), ""));
        });

        List<Map<String, Object>> advisorContributionRows = finalizeNewCustomerRows(new ArrayList<>(advisorContributionAgg.values()));
        advisorContributionRows.sort((a, b) -> {
            int leader = Objects.toString(a.get("leaderName"), "").compareToIgnoreCase(Objects.toString(b.get("leaderName"), ""));
            if (leader != 0) return leader;
            int newCustomers = Integer.compare((Integer) b.get("newCustomerOrders"), (Integer) a.get("newCustomerOrders"));
            if (newCustomers != 0) return newCustomers;
            return Objects.toString(a.get("advisorName"), "").compareToIgnoreCase(Objects.toString(b.get("advisorName"), ""));
        });

        Map<String, Map<String, Object>> monthRowsByLeaderAndMonth = new LinkedHashMap<>();
        for (Map<String, Object> row : leaderMonthRows) {
            monthRowsByLeaderAndMonth.put(Objects.toString(row.get("leaderId"), "") + "|" + Objects.toString(row.get("periodKey"), ""), row);
        }

        List<Map<String, Object>> leaderRows = new ArrayList<>();
        int okCount = 0;
        int yellowCount = 0;
        int degradeCount = 0;
        for (Map.Entry<String, Set<String>> entry : teamMembersByLeader.entrySet()) {
            String leaderId = entry.getKey();
            JsonNode leader = affiliates.get(leaderId);
            String leaderName = affiliateDisplayName(leader, leaderId);
            int previousNewCustomers = newCustomerCountForMonth(monthRowsByLeaderAndMonth, leaderId, previousClosedMonth);
            int lastNewCustomers = newCustomerCountForMonth(monthRowsByLeaderAndMonth, leaderId, lastClosedMonth);
            int liveNewCustomers = liveMonth != null ? newCustomerCountForMonth(monthRowsByLeaderAndMonth, leaderId, liveMonth) : 0;

            String status;
            String statusLabel;
            String actionText;
            if (lastNewCustomers >= LEADER_NEW_CUSTOMER_MONTHLY_TARGET) {
                status = "OK";
                statusLabel = "OK";
                actionText = "Ziel erreicht; kein akuter Handlungsbedarf.";
                okCount++;
            } else if (previousNewCustomers < LEADER_NEW_CUSTOMER_MONTHLY_TARGET) {
                status = "DEGRADIERUNG";
                statusLabel = "Degradierungswürdig";
                actionText = "Zwei abgeschlossene Monate unter Ziel; Degradierung prüfen und Gespräch vorbereiten.";
                degradeCount++;
            } else {
                status = "GELB";
                statusLabel = "Gelbe Karte";
                actionText = "Letzter abgeschlossener Monat unter Ziel; Führungskraft aktiv unterstützen.";
                yellowCount++;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("leaderId", leaderId);
            row.put("leaderName", leaderName);
            row.put("status", status);
            row.put("statusLabel", statusLabel);
            row.put("actionText", actionText);
            row.put("teamSize", entry.getValue().size());
            row.put("target", LEADER_NEW_CUSTOMER_MONTHLY_TARGET);
            row.put("previousMonth", monthKey(previousClosedMonth));
            row.put("previousMonthNewCustomers", previousNewCustomers);
            row.put("previousMonthGap", Math.max(0, LEADER_NEW_CUSTOMER_MONTHLY_TARGET - previousNewCustomers));
            row.put("lastClosedMonth", monthKey(lastClosedMonth));
            row.put("lastClosedMonthNewCustomers", lastNewCustomers);
            row.put("lastClosedMonthGap", Math.max(0, LEADER_NEW_CUSTOMER_MONTHLY_TARGET - lastNewCustomers));
            row.put("liveMonth", liveMonth != null ? monthKey(liveMonth) : "");
            row.put("liveMonthNewCustomers", liveNewCustomers);
            row.put("liveMonthGap", liveMonth != null ? Math.max(0, LEADER_NEW_CUSTOMER_MONTHLY_TARGET - liveNewCustomers) : 0);
            leaderRows.add(row);
        }

        leaderRows.sort((a, b) -> {
            int rank = Integer.compare(leaderStatusRank(Objects.toString(a.get("status"), "")), leaderStatusRank(Objects.toString(b.get("status"), "")));
            if (rank != 0) return rank;
            int gap = Integer.compare((Integer) b.get("lastClosedMonthGap"), (Integer) a.get("lastClosedMonthGap"));
            if (gap != 0) return gap;
            return Objects.toString(a.get("leaderName"), "").compareToIgnoreCase(Objects.toString(b.get("leaderName"), ""));
        });

        List<String> warnings = new ArrayList<>();
        if (limitedWeeks != null && !limitedWeeks.isEmpty()) {
            warnings.add("Order-Limit in " + String.join(", ", limitedWeeks) + " erreicht: Daten ggf. unvollständig, Zeitraum enger prüfen.");
        }
        if (skippedWithoutDate > 0) {
            warnings.add(skippedWithoutDate + " Order(s) ohne verwertbares Datum wurden nicht ausgewertet.");
        }
        if (!rangeCoversMonth(fromDate, toDate, previousClosedMonth) || !rangeCoversMonth(fromDate, toDate, lastClosedMonth)) {
            warnings.add("Die Gelb/Rot-Bewertung benötigt den letzten und vorletzten abgeschlossenen Monat vollständig im Zeitraum.");
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fromDate", fromDate.toString());
        summary.put("toDate", toDate.toString());
        summary.put("target", LEADER_NEW_CUSTOMER_MONTHLY_TARGET);
        summary.put("leaderCount", leaderRows.size());
        summary.put("okCount", okCount);
        summary.put("yellowCount", yellowCount);
        summary.put("degradeCount", degradeCount);
        summary.put("currentMonth", monthKey(currentMonth));
        summary.put("lastClosedMonth", monthKey(lastClosedMonth));
        summary.put("previousMonth", monthKey(previousClosedMonth));
        summary.put("liveMonth", liveMonth != null ? monthKey(liveMonth) : "");
        summary.put("totalOrders", totalOrders);
        summary.put("newCustomerOrders", newCustomerOrders);
        summary.put("returningCustomerOrders", Math.max(0, totalOrders - newCustomerOrders));
        summary.put("newCustomerRate", ratio(newCustomerOrders, totalOrders));
        summary.put("totalRevenue", totalRevenue);
        summary.put("newCustomerRevenue", newCustomerRevenue);
        summary.put("limited", limitedWeeks != null && !limitedWeeks.isEmpty());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("leaderRows", leaderRows);
        payload.put("leaderMonthRows", leaderMonthRows);
        payload.put("leaderWeekRows", leaderWeekRows);
        payload.put("advisorContributionRows", advisorContributionRows);
        payload.put("warnings", warnings);
        return payload;
    }

    private static Map<String, Object> newCustomerPeriodAgg(String periodType, String periodKey, String periodLabel,
                                                            LocalDate periodStart, LocalDate periodEnd,
                                                            String leaderId, String leaderName,
                                                            String advisorId, String advisorName) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("periodType", periodType);
        row.put("periodKey", periodKey);
        row.put("periodLabel", periodLabel);
        row.put("periodStart", periodStart != null ? periodStart.toString() : "");
        row.put("periodEnd", periodEnd != null ? periodEnd.toString() : "");
        row.put("leaderId", leaderId);
        row.put("leaderName", leaderName);
        row.put("advisorId", advisorId);
        row.put("advisorName", advisorName);
        row.put("isLeaderSelf", false);
        row.put("totalOrders", 0);
        row.put("newCustomerOrders", 0);
        row.put("returningCustomerOrders", 0);
        row.put("totalRevenue", 0.0);
        row.put("newCustomerRevenue", 0.0);
        row.put("newCustomerRate", 0.0);
        return row;
    }

    private static int newCustomerCountForMonth(Map<String, Map<String, Object>> rows, String leaderId, YearMonth month) {
        Map<String, Object> row = rows.get(leaderId + "|" + monthKey(month));
        return row != null ? (Integer) row.get("newCustomerOrders") : 0;
    }

    private static int leaderStatusRank(String status) {
        return switch (status) {
            case "DEGRADIERUNG" -> 0;
            case "GELB" -> 1;
            case "OK" -> 2;
            default -> 3;
        };
    }

    private static boolean rangeCoversMonth(LocalDate fromDate, LocalDate toDate, YearMonth month) {
        return !fromDate.isAfter(month.atDay(1)) && !toDate.isBefore(month.atEndOfMonth());
    }

    private static boolean monthOverlaps(YearMonth month, LocalDate fromDate, LocalDate toDate) {
        return !month.atDay(1).isAfter(toDate) && !month.atEndOfMonth().isBefore(fromDate);
    }

    private static List<YearMonth> monthsBetween(LocalDate fromDate, LocalDate toDate) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth cursor = YearMonth.from(fromDate);
        YearMonth end = YearMonth.from(toDate);
        while (!cursor.isAfter(end)) {
            months.add(cursor);
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    private static String monthKey(YearMonth month) {
        return month != null ? month.toString() : "";
    }

    private static boolean isApprovedAffiliate(JsonNode affiliate) {
        return "approved".equalsIgnoreCase(asText(affiliate, "status").trim());
    }

    private static String affiliateDisplayName(JsonNode affiliate, String fallbackId) {
        String name = affiliate != null ? asText(affiliate, "name").trim() : "";
        return !name.isBlank() ? name : (fallbackId == null || fallbackId.isBlank() ? "Unbekannt" : "ID " + fallbackId);
    }

    private static void collectApprovedDescendants(String parentId,
                                                   Map<String, List<String>> childrenByParent,
                                                   Map<String, JsonNode> affiliatesById,
                                                   Set<String> approvedDescendants,
                                                   Set<String> visited) {
        if (parentId == null || parentId.isBlank() || visited.contains(parentId)) return;
        visited.add(parentId);
        for (String childId : childrenByParent.getOrDefault(parentId, Collections.emptyList())) {
            JsonNode child = affiliatesById.get(childId);
            if (isApprovedAffiliate(child)) {
                approvedDescendants.add(childId);
            }
            collectApprovedDescendants(childId, childrenByParent, affiliatesById, approvedDescendants, visited);
        }
    }

    private static Map<String, Object> newCustomerAgg(String weekKey, String advisorId, String advisorName) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("weekKey", weekKey);
        row.put("weekLabel", weekKey);
        row.put("weekStart", weekKey.isBlank() ? "" : isoWeekStartFromKey(weekKey).toString());
        row.put("weekEnd", weekKey.isBlank() ? "" : isoWeekStartFromKey(weekKey).plusDays(6).toString());
        row.put("advisorId", advisorId);
        row.put("advisorName", advisorName);
        row.put("totalOrders", 0);
        row.put("newCustomerOrders", 0);
        row.put("returningCustomerOrders", 0);
        row.put("totalRevenue", 0.0);
        row.put("newCustomerRevenue", 0.0);
        row.put("newCustomerRate", 0.0);
        return row;
    }

    private static void incrementNewCustomerAgg(Map<String, Object> row, boolean newCustomer, double total) {
        row.put("totalOrders", ((Integer) row.get("totalOrders")) + 1);
        row.put("totalRevenue", ((Double) row.get("totalRevenue")) + total);
        if (newCustomer) {
            row.put("newCustomerOrders", ((Integer) row.get("newCustomerOrders")) + 1);
            row.put("newCustomerRevenue", ((Double) row.get("newCustomerRevenue")) + total);
        } else {
            row.put("returningCustomerOrders", ((Integer) row.get("returningCustomerOrders")) + 1);
        }
    }

    private static List<Map<String, Object>> finalizeNewCustomerRows(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            int totalOrders = (Integer) row.get("totalOrders");
            int newCustomerOrders = (Integer) row.get("newCustomerOrders");
            row.put("returningCustomerOrders", Math.max(0, totalOrders - newCustomerOrders));
            row.put("newCustomerRate", ratio(newCustomerOrders, totalOrders));
        }
        return rows;
    }

    private static boolean isNewCustomerOrder(JsonNode order) {
        JsonNode direct = order != null ? order.get("is_new_customer") : null;
        if (direct != null && !direct.isNull()) return isTruthy(direct);
        return isTruthy(order != null ? order.path("customer").get("is_new_customer") : null);
    }

    private static boolean isTruthy(JsonNode node) {
        if (node == null || node.isNull()) return false;
        if (node.isBoolean()) return node.asBoolean();
        if (node.isNumber()) return node.asInt(0) != 0;
        String text = node.asText("").trim().toLowerCase();
        return "1".equals(text) || "true".equals(text) || "yes".equals(text) || "new customer".equals(text);
    }

    private static double ratio(int numerator, int denominator) {
        return denominator > 0 ? (double) numerator / (double) denominator : 0.0;
    }

    private static String isoWeekKey(LocalDate date) {
        if (date == null) return "";
        int weekYear = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%d-KW%02d", weekYear, week);
    }

    private static LocalDate isoWeekStart(LocalDate date) {
        if (date == null) return LocalDate.now(ZoneId.of("Europe/Berlin"));
        return date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }

    private static LocalDate isoWeekStartFromKey(String weekKey) {
        try {
            String[] parts = weekKey.split("-KW");
            int year = Integer.parseInt(parts[0]);
            int week = Integer.parseInt(parts[1]);
            return LocalDate.of(year, 1, 4)
                    .with(IsoFields.WEEK_BASED_YEAR, year)
                    .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
                    .minusDays(LocalDate.of(year, 1, 4)
                            .with(IsoFields.WEEK_BASED_YEAR, year)
                            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
                            .getDayOfWeek().getValue() - 1L);
        } catch (Exception e) {
            return LocalDate.now(ZoneId.of("Europe/Berlin"));
        }
    }

    private static Map<String, Object> buildPartyAnalyticsPayload(JsonNode showcaseRoot, JsonNode orderRoot,
                                                                  LocalDate fromDate, LocalDate toDate) {
        LocalDate[] range = normalizePartyDateRange(fromDate, toDate);
        fromDate = range[0];
        toDate = range[1];

        List<JsonNode> showcases = jsonArrayToList(showcaseRoot != null ? showcaseRoot.get("showcases") : null);
        List<JsonNode> orders = jsonArrayToList(orderRoot != null ? orderRoot.get("orders") : null);

        Map<String, JsonNode> showcaseBySubId = new LinkedHashMap<>();
        for (JsonNode showcase : showcases) {
            String partyId = partyId(showcase);
            String subId = asText(showcase, "sub_id").trim();
            if (!subId.isBlank()) showcaseBySubId.put(subId, showcase);
        }

        Map<String, List<JsonNode>> ordersByPartyId = new LinkedHashMap<>();
        int partyOrderCandidates = 0;
        int unmatchedPartyOrders = 0;
        for (JsonNode order : orders) {
            if (!"party-link".equalsIgnoreCase(asText(order, "conversion_source").trim())) continue;
            partyOrderCandidates++;
            String subId = asText(order, "sub_id").trim();
            JsonNode showcase = showcaseBySubId.get(subId);
            if (showcase == null) {
                unmatchedPartyOrders++;
                continue;
            }
            String partyId = partyId(showcase);
            if (!partyId.isBlank()) {
                ordersByPartyId.computeIfAbsent(partyId, k -> new ArrayList<>()).add(order);
            }
        }

        List<JsonNode> relevantShowcases = new ArrayList<>();
        for (JsonNode showcase : showcases) {
            String id = partyId(showcase);
            if (ordersByPartyId.containsKey(id) || partyOverlapsPeriod(showcase, fromDate, toDate)) {
                relevantShowcases.add(showcase);
            }
        }

        Map<String, Set<String>> advisorCustomerParties = new LinkedHashMap<>();
        for (JsonNode showcase : relevantShowcases) {
            String advisorId = showcaseAdvisorId(showcase);
            String id = partyId(showcase);
            for (JsonNode order : ordersByPartyId.getOrDefault(id, Collections.emptyList())) {
                String customerKey = customerIdentityKey(order);
                if (advisorId.isBlank() || customerKey.isBlank()) continue;
                advisorCustomerParties.computeIfAbsent(advisorId + "|" + customerKey, k -> new LinkedHashSet<>()).add(id);
            }
        }

        List<Map<String, Object>> partyRows = new ArrayList<>();
        Map<String, Map<String, Object>> advisorAgg = new LinkedHashMap<>();
        int activePartyCount = 0;
        int closedPartyCount = 0;
        int matchedOrderCount = 0;
        double matchedOrderTotal = 0.0;
        int reviewPartyCount = 0;
        int hintPartyCount = 0;

        for (JsonNode showcase : relevantShowcases) {
            String id = partyId(showcase);
            String advisorId = showcaseAdvisorId(showcase);
            String advisorName = showcaseAdvisorName(showcase);
            String closedAt = asText(showcase, "closed_at");
            boolean closed = !closedAt.isBlank();
            if (closed) closedPartyCount++; else activePartyCount++;

            List<JsonNode> partyOrders = ordersByPartyId.getOrDefault(id, Collections.emptyList());
            matchedOrderCount += partyOrders.size();

            Map<String, Integer> customerCounts = new LinkedHashMap<>();
            Map<String, Double> customerSales = new LinkedHashMap<>();
            Map<String, Integer> cartCounts = new LinkedHashMap<>();
            Map<String, Map<String, Object>> productAgg = new LinkedHashMap<>();
            List<Map<String, Object>> orderRows = new ArrayList<>();
            int outsidePartyPeriod = 0;
            int affiliateMismatch = 0;
            double partyMatchedTotal = 0.0;
            int productUnits = 0;

            for (JsonNode order : partyOrders) {
                double orderTotal = partyOrderTotal(order);
                partyMatchedTotal += orderTotal;
                matchedOrderTotal += orderTotal;
                String customerKey = customerIdentityKey(order);
                if (!customerKey.isBlank()) {
                    customerCounts.put(customerKey, customerCounts.getOrDefault(customerKey, 0) + 1);
                    customerSales.put(customerKey, customerSales.getOrDefault(customerKey, 0.0) + orderTotal);
                }
                String cartSignature = cartSignature(order);
                if (!cartSignature.isBlank()) {
                    cartCounts.put(cartSignature, cartCounts.getOrDefault(cartSignature, 0) + 1);
                }
                if (orderOutsidePartyPeriod(order, showcase)) outsidePartyPeriod++;
                String orderAffiliateId = asText(order, "affiliate_id").trim();
                if (!advisorId.isBlank() && !orderAffiliateId.isBlank() && !advisorId.equals(orderAffiliateId)) {
                    affiliateMismatch++;
                }
                JsonNode lineItems = order.get("line_items");
                if (lineItems != null && lineItems.isArray()) {
                    for (JsonNode item : lineItems) {
                        String productName = firstNonBlank(asText(item, "name"), asText(item, "title"), asText(item, "sku"), "(ohne Artikel)");
                        int qty = Math.max(parseIntSafe(asText(item, "quantity")), 0);
                        double value = lineItemTotal(item);
                        productUnits += qty;
                        Map<String, Object> product = productAgg.computeIfAbsent(productName, k -> {
                            Map<String, Object> p = new LinkedHashMap<>();
                            p.put("productName", k);
                            p.put("quantity", 0);
                            p.put("salesValue", 0.0);
                            return p;
                        });
                        product.put("quantity", ((Integer) product.get("quantity")) + qty);
                        product.put("salesValue", ((Double) product.get("salesValue")) + value);
                    }
                }
                Map<String, Object> orderRow = new LinkedHashMap<>();
                orderRow.put("orderId", firstNonBlank(asText(order, "number"), asText(order, "id")));
                orderRow.put("createdAt", formatDateTimeEuropeBerlinStatic(asText(order, "created_at")));
                orderRow.put("status", asText(order, "status"));
                orderRow.put("total", orderTotal);
                orderRow.put("customer", maskedCustomerLabel(order));
                orderRow.put("cartSignature", cartSignature);
                orderRows.add(orderRow);
            }

            List<Map<String, Object>> productRows = new ArrayList<>(productAgg.values());
            productRows.sort((a, b) -> Integer.compare((Integer) b.get("quantity"), (Integer) a.get("quantity")));
            List<Map<String, Object>> topProducts = productRows.size() > 5 ? new ArrayList<>(productRows.subList(0, 5)) : productRows;

            int aggregateOrderCount = showcase.path("orders").path("num_orders").asInt(0);
            double aggregateTotal = parseDoubleSafeStatic(asText(showcase.path("orders"), "total"));
            double aggregateCommission = parseDoubleSafeStatic(asText(showcase.path("orders"), "commission"));

            List<String> reasons = new ArrayList<>();
            int score = 0;
            long repeatedCustomers = customerCounts.values().stream().filter(c -> c > 1).count();
            if (repeatedCustomers > 0) {
                score += 25;
                reasons.add(repeatedCustomers + " Kundenidentitaet mehrfach in derselben Party");
            }
            long crossPartyCustomers = customerCounts.keySet().stream()
                    .filter(k -> advisorCustomerParties.getOrDefault(advisorId + "|" + k, Collections.emptySet()).size() > 1)
                    .count();
            if (crossPartyCustomers > 0) {
                score += 20;
                reasons.add(crossPartyCustomers + " Kundenidentitaet auch in anderen Partys dieser Beraterin");
            }
            long repeatedCarts = cartCounts.entrySet().stream()
                    .filter(e -> !"no-items".equals(e.getKey()) && e.getValue() > 1)
                    .count();
            if (repeatedCarts > 0) {
                score += 20;
                reasons.add(repeatedCarts + " identischer Warenkorb mehrfach");
            }
            double topCustomerShare = partyMatchedTotal > 0.0
                    ? customerSales.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0) / partyMatchedTotal
                    : 0.0;
            if (partyOrders.size() >= 3 && topCustomerShare >= 0.80) {
                score += 20;
                reasons.add("wenige Kunden dominieren " + Math.round(topCustomerShare * 100.0) + "% des Umsatzes");
            }
            if (outsidePartyPeriod > 0) {
                score += 20;
                reasons.add(outsidePartyPeriod + " Order(s) ausserhalb des Party-Zeitraums");
            }
            if (affiliateMismatch > 0) {
                score += 25;
                reasons.add(affiliateMismatch + " Order(s) mit abweichender Affiliate-ID");
            }
            if (aggregateOrderCount > 0 && partyOrders.isEmpty()) {
                score += 10;
                reasons.add("GoAffPro meldet Orders, aber im Zeitraum wurden keine Detailorders gematcht");
            }
            score = Math.min(score, 100);
            String riskLevel = score >= 50 ? "Pr\u00fcfen" : (score >= 20 ? "Hinweis" : "OK");
            if ("Pr\u00fcfen".equals(riskLevel)) reviewPartyCount++;
            if ("Hinweis".equals(riskLevel)) hintPartyCount++;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("partyId", id);
            row.put("subId", asText(showcase, "sub_id"));
            row.put("refCode", asText(showcase, "ref_code"));
            row.put("partyTitle", firstNonBlank(asText(showcase, "partyTitle"), asText(showcase, "title"), "(ohne Titel)"));
            row.put("hostName", asText(showcase, "hostName"));
            row.put("hostEmail", maskEmail(asText(showcase, "hostEmail")));
            row.put("hostUrl", asText(showcase, "hostUrl"));
            row.put("advisorId", advisorId);
            row.put("advisorName", advisorName);
            row.put("startsAt", formatDateTimeEuropeBerlinStatic(asText(showcase, "starts_at")));
            row.put("endsAt", formatDateTimeEuropeBerlinStatic(asText(showcase, "ends_at")));
            row.put("closedAt", formatDateTimeEuropeBerlinStatic(closedAt));
            row.put("createdAt", formatDateTimeEuropeBerlinStatic(asText(showcase, "created_at")));
            row.put("status", closed ? "geschlossen" : "aktiv");
            row.put("aggregateOrderCount", aggregateOrderCount);
            row.put("aggregateTotal", aggregateTotal);
            row.put("aggregateCommission", aggregateCommission);
            row.put("matchedOrderCount", partyOrders.size());
            row.put("matchedTotal", partyMatchedTotal);
            row.put("customerCount", customerCounts.size());
            row.put("productUnits", productUnits);
            row.put("productCount", productAgg.size());
            row.put("riskScore", score);
            row.put("riskLevel", riskLevel);
            row.put("riskReasons", reasons);
            row.put("orderRows", orderRows);
            row.put("productRows", productRows);
            row.put("topProducts", topProducts);
            partyRows.add(row);

            Map<String, Object> advisor = advisorAgg.computeIfAbsent(advisorId.isBlank() ? advisorName : advisorId, k -> {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("advisorId", advisorId);
                a.put("advisorName", advisorName);
                a.put("partyCount", 0);
                a.put("activePartyCount", 0);
                a.put("closedPartyCount", 0);
                a.put("matchedOrderCount", 0);
                a.put("customerCount", 0);
                a.put("matchedTotal", 0.0);
                a.put("riskPartyCount", 0);
                a.put("maxRiskScore", 0);
                a.put("customerKeys", new LinkedHashSet<String>());
                return a;
            });
            advisor.put("partyCount", ((Integer) advisor.get("partyCount")) + 1);
            advisor.put("activePartyCount", ((Integer) advisor.get("activePartyCount")) + (closed ? 0 : 1));
            advisor.put("closedPartyCount", ((Integer) advisor.get("closedPartyCount")) + (closed ? 1 : 0));
            advisor.put("matchedOrderCount", ((Integer) advisor.get("matchedOrderCount")) + partyOrders.size());
            advisor.put("matchedTotal", ((Double) advisor.get("matchedTotal")) + partyMatchedTotal);
            advisor.put("riskPartyCount", ((Integer) advisor.get("riskPartyCount")) + (score >= 20 ? 1 : 0));
            advisor.put("maxRiskScore", Math.max((Integer) advisor.get("maxRiskScore"), score));
            @SuppressWarnings("unchecked")
            Set<String> advisorCustomers = (Set<String>) advisor.get("customerKeys");
            advisorCustomers.addAll(customerCounts.keySet());
        }

        List<Map<String, Object>> advisorRows = new ArrayList<>(advisorAgg.values());
        for (Map<String, Object> advisor : advisorRows) {
            @SuppressWarnings("unchecked")
            Set<String> customerKeys = (Set<String>) advisor.get("customerKeys");
            advisor.put("customerCount", customerKeys.size());
            advisor.remove("customerKeys");
        }

        partyRows.sort((a, b) -> {
            int risk = Integer.compare((Integer) b.get("riskScore"), (Integer) a.get("riskScore"));
            if (risk != 0) return risk;
            return Double.compare((Double) b.get("matchedTotal"), (Double) a.get("matchedTotal"));
        });
        advisorRows.sort((a, b) -> {
            int risk = Integer.compare((Integer) b.get("maxRiskScore"), (Integer) a.get("maxRiskScore"));
            if (risk != 0) return risk;
            return Double.compare((Double) b.get("matchedTotal"), (Double) a.get("matchedTotal"));
        });

        List<String> warnings = new ArrayList<>();
        if (showcases.size() >= 500) warnings.add("Party-Limit erreicht: Daten ggf. unvollständig, Zeitraum verkleinern.");
        if (orders.size() >= 500) warnings.add("Order-Limit erreicht: Daten ggf. unvollständig, Zeitraum verkleinern.");
        if (unmatchedPartyOrders > 0) warnings.add(unmatchedPartyOrders + " Party-Order(s) konnten keiner Showcase zugeordnet werden.");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fromDate", fromDate.toString());
        summary.put("toDate", toDate.toString());
        summary.put("showcaseCount", showcases.size());
        summary.put("partyCount", partyRows.size());
        summary.put("activePartyCount", activePartyCount);
        summary.put("closedPartyCount", closedPartyCount);
        summary.put("partyOrderCandidates", partyOrderCandidates);
        summary.put("matchedOrderCount", matchedOrderCount);
        summary.put("matchedTotal", matchedOrderTotal);
        summary.put("advisorCount", advisorRows.size());
        summary.put("hintPartyCount", hintPartyCount);
        summary.put("reviewPartyCount", reviewPartyCount);
        summary.put("limited", showcases.size() >= 500 || orders.size() >= 500);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("advisorRows", advisorRows);
        payload.put("partyRows", partyRows);
        payload.put("warnings", warnings);
        return payload;
    }

    private static List<JsonNode> jsonArrayToList(JsonNode node) {
        if (node == null || !node.isArray()) return Collections.emptyList();
        List<JsonNode> list = new ArrayList<>();
        node.forEach(list::add);
        return list;
    }

    private static String partyId(JsonNode showcase) {
        return firstNonBlank(asText(showcase, "_id"), asText(showcase, "id"), asText(showcase, "sub_id"), asText(showcase, "ref_code"));
    }

    private static String showcaseAdvisorId(JsonNode showcase) {
        return firstNonBlank(asText(showcase, "affiliate_id"), asText(showcase.path("affiliate"), "id"));
    }

    private static String showcaseAdvisorName(JsonNode showcase) {
        return firstNonBlank(asText(showcase.path("affiliate"), "name"), asText(showcase, "affiliate_name"), "Unbekannt");
    }

    private static boolean partyOverlapsPeriod(JsonNode showcase, LocalDate fromDate, LocalDate toDate) {
        LocalDate start = firstLocalDate(asText(showcase, "starts_at"), asText(showcase, "created_at"));
        LocalDate end = firstLocalDate(asText(showcase, "closed_at"), asText(showcase, "ends_at"));
        if (start == null && end == null) return true;
        if (start == null) start = end;
        if (end == null) end = start;
        return !start.isAfter(toDate) && !end.isBefore(fromDate);
    }

    private static LocalDate firstLocalDate(String... values) {
        for (String value : values) {
            LocalDate date = parseIsoDateTimeToLocalDate(value);
            if (date == null) date = parseIsoDate(value);
            if (date != null) return date;
        }
        return null;
    }

    private static boolean orderOutsidePartyPeriod(JsonNode order, JsonNode showcase) {
        LocalDate orderDate = firstLocalDate(asText(order, "created_at"));
        if (orderDate == null) return false;
        LocalDate start = firstLocalDate(asText(showcase, "starts_at"), asText(showcase, "created_at"));
        LocalDate end = firstLocalDate(asText(showcase, "closed_at"), asText(showcase, "ends_at"));
        return (start != null && orderDate.isBefore(start)) || (end != null && orderDate.isAfter(end));
    }

    private static double partyOrderTotal(JsonNode order) {
        return parseDoubleSafeStatic(firstNonBlank(asText(order, "total"), asText(order, "subtotal")));
    }

    private static double lineItemTotal(JsonNode item) {
        double total = parseDoubleSafeStatic(firstNonBlank(asText(item, "total_price"), asText(item, "total")));
        if (total > 0.0) return total;
        int qty = Math.max(parseIntSafe(asText(item, "quantity")), 0);
        double price = parseDoubleSafeStatic(asText(item, "price"));
        return price * qty;
    }

    private static String customerIdentityKey(JsonNode order) {
        String email = asText(order, "customer_email").trim().toLowerCase();
        if (!email.isBlank()) return "email:" + email;
        JsonNode address = order.path("shipping_address");
        String phoneDigits = asText(address, "phone").replaceAll("[^0-9]", "");
        if (phoneDigits.length() >= 6) return "phone:" + phoneDigits;
        String addressKey = normalizeKey(firstNonBlank(asText(address, "name"), asText(address, "first_name") + " " + asText(address, "last_name"))
                + "|" + asText(address, "address_1") + "|" + asText(address, "zip"));
        return addressKey.isBlank() || "||".equals(addressKey) ? "" : "addr:" + addressKey;
    }

    private static String maskedCustomerLabel(JsonNode order) {
        String email = asText(order, "customer_email").trim();
        if (!email.isBlank()) return maskEmail(email);
        JsonNode address = order.path("shipping_address");
        String phoneDigits = asText(address, "phone").replaceAll("[^0-9]", "");
        if (phoneDigits.length() >= 6) return "Telefon ***" + phoneDigits.substring(Math.max(0, phoneDigits.length() - 3));
        String name = firstNonBlank(asText(address, "name"), asText(address, "first_name") + " " + asText(address, "last_name")).trim();
        String zip = asText(address, "zip").trim();
        if (!name.isBlank() || !zip.isBlank()) {
            return "Adresse " + (!name.isBlank() ? name.substring(0, 1).toUpperCase() + "." : "") + (!zip.isBlank() ? " " + zip : "");
        }
        return "(Kunde unbekannt)";
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "";
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        if (at <= 0) return "***";
        String local = trimmed.substring(0, at);
        String domain = trimmed.substring(at + 1);
        return local.substring(0, 1) + "***@" + domain;
    }

    private static String cartSignature(JsonNode order) {
        JsonNode lineItems = order.get("line_items");
        if (lineItems == null || !lineItems.isArray() || lineItems.size() == 0) return "no-items";
        List<String> parts = new ArrayList<>();
        for (JsonNode item : lineItems) {
            String product = normalizeKey(firstNonBlank(asText(item, "sku"), asText(item, "product_id"), asText(item, "name"), asText(item, "title")));
            int qty = Math.max(parseIntSafe(asText(item, "quantity")), 0);
            parts.add(product + ":" + qty);
        }
        Collections.sort(parts);
        return String.join("|", parts);
    }

    private static String normalizeKey(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9|@._+-]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return normalized;
    }

    private static class MailLogHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);
                List<Map<String, String>> entries = readMailLogEntries(config);
                Map<String, List<Map<String, String>>> byPayment = new LinkedHashMap<>();
                for (Map<String, String> row : entries) {
                    String paymentId = Objects.toString(row.get("paymentId"), "").trim();
                    if (paymentId.isBlank()) continue;
                    byPayment.computeIfAbsent(paymentId, k -> new ArrayList<>()).add(row);
                }
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("entries", entries);
                payload.put("byPayment", byPayment);
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class MailLogDownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                Map<String, String> query = parseQueryParams(exchange.getRequestURI());
                String filePath = Objects.toString(query.get("path"), "").trim();
                if (filePath.isBlank()) {
                    sendResponse(exchange, 400, "application/json", "{\"error\":\"path fehlt\"}");
                    return;
                }
                Path p = Paths.get(filePath).toAbsolutePath().normalize();
                if (!Files.exists(p) || !Files.isRegularFile(p)) {
                    sendResponse(exchange, 404, "application/json", "{\"error\":\"Datei nicht gefunden\"}");
                    return;
                }
                // Nur Dateien aus dem Belegverzeichnis ausliefern. Ohne diese Prüfung wäre über
                // ?path=… jede lesbare Datei abrufbar (config.properties, .env, die Sync-Datenbank).
                Path allowedRoot = resolveSettingsDirectory(loadConfigWithUiSettings());
                if (!isUnderRoot(p, allowedRoot)) {
                    sendResponse(exchange, 403, "application/json",
                            "{\"error\":\"Die Datei liegt außerhalb des Exportordners.\"}");
                    return;
                }
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + p.getFileName().toString().replace("\"", "") + "\"");
                exchange.sendResponseHeaders(200, Files.size(p));
                try (OutputStream os = exchange.getResponseBody()) { Files.copy(p, os); }
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class ExportPdfHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }

            try {
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                List<Map<String, String>> selectedRows = OBJECT_MAPPER.convertValue(body.get("rows"), new TypeReference<List<Map<String, String>>>() {});

                if (selectedRows == null || selectedRows.isEmpty()) {
                    sendResponse(exchange, 400, "application/json", "{\"error\":\"Keine Tabellenzeilen selektiert\"}");
                    return;
                }

                String requestedDir = asText(body, "pdfExportPath").trim();
                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);

                String exportDirValue = requestedDir.isEmpty()
                        ? Objects.toString(config.getProperty("pdfExportPath"), DEFAULT_PDF_EXPORT_PATH)
                        : requestedDir;

                if (exportDirValue.isBlank()) {
                    sendResponse(exchange, 400, "application/json", "{\"error\":\"Kein Exportpfad gesetzt\"}");
                    return;
                }

                Path exportDir = Paths.get(exportDirValue).toAbsolutePath();
                Files.createDirectories(exportDir);

                String highestPaymentId = selectedRows.stream()
                        .map(r -> safe(r.get("paymentId"), "0"))
                        .max((a, b) -> isGreaterNumeric(a, b) ? 1 : (isGreaterNumeric(b, a) ? -1 : 0))
                        .orElse("0");
                String maxBelegdatum = selectedRows.stream()
                        .map(r -> safe(r.get("belegdatum"), ""))
                        .map(WebUiServer::parseGermanDate)
                        .filter(Objects::nonNull)
                        .max(LocalDate::compareTo)
                        .map(d -> d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                        .orElse("unbekanntes-datum");
                String advisorFolderToken = selectedRows.stream()
                        .map(r -> safe(r.get("affiliateName"), ""))
                        .map(String::trim)
                        .filter(v -> !v.isBlank())
                        .distinct()
                        .sorted(String::compareToIgnoreCase)
                        .collect(Collectors.collectingAndThen(Collectors.toList(), names -> {
                            if (names.isEmpty()) return "ohne-beraterin";
                            if (names.size() == 1) return names.get(0);
                            return names.get(0) + "_und_weitere";
                        }));
                Path runExportDir = exportDir.resolve("export_" + sanitizeFilename(maxBelegdatum + "_" + highestPaymentId + "_" + advisorFolderToken));
                Files.createDirectories(runExportDir);

                List<String> exportedFiles = new ArrayList<>();
                for (Map<String, String> row : selectedRows) {
                    String paymentId = safe(row.get("paymentId"), "unbekannt");
                    String filename = "payment_" + sanitizeFilename(paymentId) + "_" + FILE_TIMESTAMP.format(LocalDateTime.now()) + ".pdf";
                    Path pdfPath = runExportDir.resolve(filename);
                    createPdfForPayment(pdfPath, row);
                    exportedFiles.add(pdfPath.toString());
                }

                config.setProperty("pdfExportPath", exportDir.toString());
                persistSettings(config);

                Map<String, Object> payload = new HashMap<>();
                payload.put("message", exportedFiles.size() + " PDF-Datei(en) exportiert.");
                payload.put("files", exportedFiles);
                payload.put("pdfExportPath", exportDir.toString());
                payload.put("settingsDirectory", resolveSettingsDirectory(config).toString());
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }

        private void createPdfForPayment(Path pdfPath, Map<String, String> row) throws IOException {
            try (PDDocument document = new PDDocument()) {
                PDPage page = new PDPage();
                document.addPage(page);

                List<String> lines = List.of(
                        "Payment-ID: " + safe(row.get("paymentId"), ""),
                        "Belegdatum: " + safe(row.get("belegdatum"), ""),
                        "Affiliate-Name: " + safe(row.get("affiliateName"), ""),
                        "Affiliate-Land: " + safe(row.get("affiliateCountry"), ""),
                        "Affiliate-Steuernummer: " + safe(row.get("affiliateSteuernummer"), ""),
                        "Provision: " + safe(row.get("amount"), ""),
                        "Waehrung: " + safe(row.get("currency"), "")
                );
                String contentHash = sha256Hex(String.join("\n", lines));

                try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
                    cs.newLineAtOffset(50, 750);
                    cs.showText("GoAffPro Zahlungsexport");
                    cs.endText();

                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, 11);
                    cs.newLineAtOffset(50, 720);

                    boolean first = true;
                    for (String line : lines) {
                        if (!first) {
                            cs.newLineAtOffset(0, -18);
                        }
                        cs.showText(line);
                        first = false;
                    }
                    cs.newLineAtOffset(0, -22);
                    cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 9);
                    cs.showText("Inhalts-Hash (SHA-256): " + contentHash);
                    cs.endText();
                }

                document.save(pdfPath.toFile());
            }
        }
    }


    private static class InvoiceDetailsPdfHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }

            try {
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String paymentId = asText(body, "paymentId").trim();
                String requestedDir = asText(body, "pdfExportPath").trim();
                Boolean includeEInvoiceArtifactsRequest = body.has("includeEInvoiceArtifacts") ? body.get("includeEInvoiceArtifacts").asBoolean() : null;
                if (paymentId.isEmpty()) {
                    sendResponse(exchange, 400, "application/json", "{\"error\":\"paymentId fehlt\"}");
                    return;
                }

                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);

                String apiKey = getSecretOrConfig(config, "GOAFFPRO_API_KEY", "goaffproAPIKey", DEFAULT_GOAFFPRO_API_KEY).trim();
                String detailsUrl = "https://api.goaffpro.com/v1/admin/payments?id=" + paymentId
                        + "&fields=id,affiliate_id,amount,currency,payment_method,payment_details,affiliate_message,admin_note,transactions,created_at";
                JsonNode response = requestJson(detailsUrl, apiKey);
                JsonNode payments = response.get("payments");
                if (payments == null || !payments.isArray() || payments.size() == 0) {
                    sendResponse(exchange, 404, "application/json", "{\"error\":\"Keine Payment-Details gefunden\"}");
                    return;
                }
                JsonNode payment = payments.get(0);
                String affiliateId = asText(payment, "affiliate_id");
                JsonNode affiliate = null;
                if (!affiliateId.isBlank()) {
                    affiliate = fetchAffiliatesById(apiKey, List.of(affiliateId)).get(affiliateId);
                }

                String exportDirValue = requestedDir.isEmpty()
                        ? Objects.toString(config.getProperty("pdfExportPath"), DEFAULT_PDF_EXPORT_PATH)
                        : requestedDir;
                Path exportDir = Paths.get(exportDirValue).toAbsolutePath();
                Files.createDirectories(exportDir);

                String belegdatum = toGermanDate(asText(payment, "created_at"));
                LocalDate belegDate = parseGermanDate(belegdatum);
                String belegFolder = belegDate != null ? belegDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "unbekanntes-datum";
                String advisorFolderToken = affiliate != null ? asText(affiliate, "name") : "ohne-beraterin";
                Path runExportDir = exportDir.resolve("export_" + sanitizeFilename(belegFolder + "_" + paymentId + "_" + advisorFolderToken));

                // Zeitraum und Dokumentart ZUERST bestimmen (rein lesend). Erst danach darf eine
                // Belegnummer gezogen werden – generateNextDocumentNumber persistiert sofort.
                String periodLabel = buildPaymentPeriodLabel(payment);
                DocumentKindDecision decision = resolveDocumentKind(payment, config);
                DocumentKind kind;
                boolean kindOverridden = false;
                if (decision.mixed()) {
                    DocumentKind override = DocumentKind.fromWireValue(asText(body, "documentKind").trim(), null);
                    if (override == null) {
                        Map<String, Object> conflict = new LinkedHashMap<>();
                        conflict.put("error", "Der Zahllauf enthält Provisionen vor und ab dem Stichtag "
                                + rechnungCutoffDateRaw(config)
                                + ". Es wurde kein Beleg erzeugt und keine Belegnummer vergeben. "
                                + "Bitte manuell entscheiden, ob eine Gutschrift oder eine Rechnung ausgestellt wird.");
                        conflict.put("code", "MIXED_PERIOD");
                        conflict.put("paymentId", paymentId);
                        conflict.put("periodLabel", periodLabel);
                        conflict.put("cutoffDate", rechnungCutoffDateRaw(config));
                        conflict.put("beforeCutoffCount", decision.beforeCutoffCount());
                        conflict.put("fromCutoffCount", decision.fromCutoffCount());
                        conflict.put("beforeCutoffAmount", decision.beforeCutoffAmount());
                        conflict.put("fromCutoffAmount", decision.fromCutoffAmount());
                        conflict.put("suggestedDocumentKind",
                                decision.fromCutoffAmount() >= decision.beforeCutoffAmount() ? "gutschrift" : "rechnung");
                        sendResponse(exchange, 409, "application/json", OBJECT_MAPPER.writeValueAsString(conflict));
                        return;
                    }
                    kind = override;
                    kindOverridden = true;
                } else {
                    // Außerhalb des Mixed-Falls wird ein mitgesendetes documentKind bewusst ignoriert.
                    kind = decision.kind();
                }

                Files.createDirectories(runExportDir);

                String timestamp = FILE_TIMESTAMP.format(LocalDateTime.now());
                String gutschriftNr = generateNextDocumentNumber(config, kind);
                boolean isKleinunternehmer = affiliate == null || asText(affiliate, "tax_identification_number").isBlank();
                String baseFilename = "provisionsnachweis_" + sanitizeFilename(gutschriftNr) + "_" + timestamp;
                Path pdfPath = runExportDir.resolve(baseFilename + ".pdf");
                Path jsonPath = runExportDir.resolve(baseFilename + ".json");
                boolean eInvoiceAttachAndStoreEnabled = includeEInvoiceArtifactsRequest != null
                        ? includeEInvoiceArtifactsRequest
                        : Boolean.parseBoolean(Objects.toString(config.getProperty("eInvoiceAttachAndStoreEnabled"), "true"));
                Path zugferdPath = eInvoiceAttachAndStoreEnabled ? runExportDir.resolve(kind.filePrefix + "_" + sanitizeFilename(gutschriftNr) + "_" + timestamp + ".xml") : null;
                Path eInvoicePdfPath = eInvoiceAttachAndStoreEnabled ? runExportDir.resolve(kind.filePrefix + "_" + sanitizeFilename(gutschriftNr) + "_" + timestamp + ".pdf") : null;
                createInvoiceDetailsPdf(pdfPath, response, affiliate, config, gutschriftNr, kind);
                writeOriginalJson(jsonPath, response);
                if (eInvoiceAttachAndStoreEnabled) {
                    createZugferdInvoiceXml(zugferdPath, payment, affiliate, config, gutschriftNr, periodLabel, isKleinunternehmer, kind);
                    createEInvoicePdfWithEmbeddedXml(eInvoicePdfPath, zugferdPath, payment, affiliate, config, gutschriftNr, periodLabel, isKleinunternehmer, kind);
                }

                String contactEmail = Objects.toString(config.getProperty("contactEmail"), "").trim();
                boolean sendEmailsEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("sendEmailsEnabled"), "true"));
                String emailRecipientMode = Objects.toString(config.getProperty("emailRecipientMode"), "contact").trim();
                String advisorEmail = affiliate != null ? asText(affiliate, "email").trim() : "";
                String targetEmail = "advisor".equals(emailRecipientMode) ? advisorEmail : contactEmail;
                if (sendEmailsEnabled && targetEmail.isBlank()) {
                    String errorText = "advisor".equals(emailRecipientMode)
                            ? "Beraterinnen-E-Mail ist leer. Bitte Affiliate-E-Mail prüfen oder Versandziel umstellen."
                            : "Kontakt-E-Mail ist nicht gesetzt. Bitte in den Einstellungen hinterlegen.";
                    sendResponse(exchange, 400, "application/json", "{\"error\":\"" + escapeJson(errorText) + "\"}");
                    return;
                }
                if (sendEmailsEnabled) {
                    String affiliateNameForMail = affiliate != null ? asText(affiliate, "name") : "";
                    sendInvoiceMailWithAttachment(targetEmail, Objects.toString(config.getProperty("emailBcc"), "").trim(), pdfPath, jsonPath, zugferdPath, eInvoicePdfPath, eInvoiceAttachAndStoreEnabled, affiliateNameForMail, periodLabel, payment, affiliate, Objects.toString(config.getProperty(kind.mailTemplateKey), ""), resolveSmtpConfig(config), gutschriftNr, kind);
                    String subject = documentMailSubject(kind, gutschriftNr, periodLabel);
                    appendMailLogEntry(config, paymentId, emailRecipientMode, targetEmail, subject, periodLabel, pdfPath, jsonPath, zugferdPath, eInvoicePdfPath, kind, gutschriftNr, kindOverridden);
                }

                boolean opened = false;
                String openMessage = "";
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(pdfPath.toFile());
                        opened = true;
                    } else {
                        openMessage = "Automatisches Öffnen nicht unterstützt.";
                    }
                } catch (Exception ex) {
                    openMessage = "PDF konnte nicht automatisch geöffnet werden: " + ex.getMessage();
                }

                config.setProperty("pdfExportPath", exportDir.toString());
                persistSettings(config);

                Map<String, Object> payload = new HashMap<>();
                payload.put("message", sendEmailsEnabled ? ("advisor".equals(emailRecipientMode) ? kind.label + "-PDF erstellt und an Beraterinnen-E-Mail versendet." : kind.label + "-PDF erstellt und an Kontakt-E-Mail versendet.") : kind.label + "-PDF erstellt (E-Mail-Versand deaktiviert).");
                payload.put("documentKind", kind.wireValue());
                payload.put("documentKindLabel", kind.label);
                payload.put("documentNumber", gutschriftNr);
                payload.put("documentKindSource", decision.source());
                payload.put("documentKindOverridden", kindOverridden);
                payload.put("requestUrl", detailsUrl);
                payload.put("file", pdfPath.toString());
                payload.put("jsonFile", jsonPath.toString());
                payload.put("zugferdFile", zugferdPath != null ? zugferdPath.toString() : "");
                payload.put("eInvoicePdfFile", eInvoicePdfPath != null ? eInvoicePdfPath.toString() : "");
                payload.put("eInvoiceViewPdfFile", eInvoicePdfPath != null ? eInvoicePdfPath.toString() : "");
                payload.put("opened", opened);
                payload.put("openMessage", openMessage);
                payload.put("pdfExportPath", exportDir.toString());
                payload.put("includeEInvoiceArtifacts", eInvoiceAttachAndStoreEnabled);
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }

        private void createInvoiceDetailsPdf(Path pdfPath, JsonNode apiResponse, JsonNode affiliate, Properties config, String gutschriftNr, DocumentKind kind) throws IOException {
            try (PDDocument document = new PDDocument()) {
                JsonNode payments = apiResponse.get("payments");
                JsonNode payment = (payments != null && payments.isArray() && payments.size() > 0) ? payments.get(0) : null;
                String documentHash = sha256Hex(toCanonicalJson(apiResponse) + "|affiliate=" + toCanonicalJson(affiliate));
                if (payment == null) {
                    PDPage page = new PDPage();
                    document.addPage(page);
                    try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
                        cs.newLineAtOffset(40, 770);
                        cs.showText("Provisionsnachweis konnte nicht erstellt werden (keine Daten)");
                        cs.endText();
                    }
                document.save(pdfPath.toFile());
                }

                List<JsonNode> txList = new ArrayList<>();
                JsonNode transactions = payment.get("transactions");
                if (transactions != null && transactions.isArray()) {
                    for (JsonNode tx : transactions) txList.add(tx);
                }

                int totalCount = txList.size();
                int directCount = 0;
                int teamCount = 0;
                double sumOrderDirect = 0.0, sumOrderTeam = 0.0;
                double sumBmgDirect = 0.0, sumBmgTeam = 0.0;
                double sumProvDirect = 0.0, sumProvTeam = 0.0;
                OffsetDateTime minDate = null, maxDate = null;

                for (JsonNode tx : txList) {
                    String entityType = asText(tx, "entity_type");
                    double orderValue = parseDoubleSafe(asText(tx.path("metadata"), "order_value"));
                    double bmgValue = parseDoubleSafe(asText(tx.path("metadata"), "commission_on"));
                    double provValue = parseDoubleSafe(asText(tx, "amount"));

                    if ("orders".equalsIgnoreCase(entityType)) {
                        directCount++;
                        sumOrderDirect += orderValue;
                        sumBmgDirect += bmgValue;
                        sumProvDirect += provValue;
                    } else {
                        teamCount++;
                        sumOrderTeam += orderValue;
                        sumBmgTeam += bmgValue;
                        sumProvTeam += provValue;
                    }

                    try {
                        OffsetDateTime dt = OffsetDateTime.parse(asText(tx, "created_at"));
                        if (minDate == null || dt.isBefore(minDate)) minDate = dt;
                        if (maxDate == null || dt.isAfter(maxDate)) maxDate = dt;
                    } catch (Exception ignored) {
                    }
                }

                double sumOrderAll = sumOrderDirect + sumOrderTeam;
                double sumBmgAll = sumBmgDirect + sumBmgTeam;
                double sumProvAll = sumProvDirect + sumProvTeam;
                double payout = parseDoubleSafe(asText(payment, "amount"));
                double rounding = payout - sumProvAll;

                List<String[]> advisorRows = List.of(
                        new String[]{"Name", affiliate != null ? asText(affiliate, "name") : ""},
                        new String[]{"E-Mail", affiliate != null ? asText(affiliate, "email") : ""},
                        new String[]{"Telefon", affiliate != null ? asText(affiliate, "phone") : ""},
                        new String[]{"Unternehmen", affiliate != null ? asText(affiliate, "company_name") : ""},
                        new String[]{"Adresse", formatAffiliateAddress(affiliate)},
                        new String[]{"Steuernummer", affiliate != null ? asText(affiliate, "tax_identification_number") : ""},
                        new String[]{"Referenzcode", affiliate != null ? asText(affiliate, "ref_code") : ""},
                        new String[]{"Status", affiliate != null ? asText(affiliate, "status") : ""}
                );

                PDPage summaryPage = new PDPage();
                document.addPage(summaryPage);
                try (PDPageContentStream cs = new PDPageContentStream(document, summaryPage)) {
                    float margin = 52f;
                    float pageWidth = summaryPage.getMediaBox().getWidth();
                    float pageHeight = summaryPage.getMediaBox().getHeight();
                    float x = margin;
                    float y = pageHeight - margin;
                    float totalWidth = pageWidth - (2 * margin);
                    float keyWidth = totalWidth * 0.30f;
                    float valueWidth = totalWidth * 0.70f;

                    String titleText = "Provisionsübersicht zur " + kind.label + " " + gutschriftNr;
                    List<String> titleLines = wrapForPdf(titleText, 46);
                    float titleLineHeight = 22f;
                    float heroHeight = Math.max(62f, 18f + (titleLines.size() * titleLineHeight));
                    cs.setNonStrokingColor(new Color(38, 93, 171));
                    cs.addRect(x, y - heroHeight, totalWidth, heroHeight);
                    cs.fill();

                    cs.setNonStrokingColor(Color.WHITE);
                    for (int i = 0; i < titleLines.size(); i++) {
                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA_BOLD, 19);
                        cs.newLineAtOffset(x + 14, y - 24 - (i * titleLineHeight));
                        cs.showText(shortenForPdf(titleLines.get(i), 120));
                        cs.endText();
                    }
                    y -= heroHeight + 18;

                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
                    cs.setNonStrokingColor(new Color(44, 52, 64));
                    cs.newLineAtOffset(x, y);
                    cs.showText("Zahllauf-ID: " + asText(payment, "id") + "   |   Affiliate-ID: " + asText(payment, "affiliate_id"));
                    cs.endText();
                    y -= 18;

                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, 11);
                    cs.newLineAtOffset(x, y);
                    cs.showText("Auszahlungsdatum (System): " + formatDateTimeEuropeBerlin(asText(payment, "created_at")) + " (Europe/Berlin)");
                    cs.endText();
                    y -= 22;

                    String ibanRaw = asText(payment.path("payment_details"), "account_number");
                    String ibanMasked = maskIban(ibanRaw);
                    String period = (minDate == null || maxDate == null)
                            ? "k.A."
                            : formatDateTimeEuropeBerlin(minDate.toString()) + " bis " + formatDateTimeEuropeBerlin(maxDate.toString()) + " (Europe/Berlin)";

                    List<String[]> summaryRows = List.of(
                            new String[]{"Empfänger (Kontoinhaber)", asText(payment.path("payment_details"), "account_name")},
                            new String[]{"Zahlmethode", "SEPA (via " + asText(payment.path("payment_details"), "paid_via") + ")"},
                            new String[]{"IBAN (maskiert)", ibanMasked},
                            new String[]{"BIC", asText(payment.path("payment_details"), "branch_code")},
                            new String[]{"Zeitraum der Transaktionen", period},
                            new String[]{"Anzahl Transaktionen im Zahllauf", String.valueOf(totalCount)},
                            new String[]{"Davon Direkt (Order)", String.valueOf(directCount)},
                            new String[]{"Davon Team (Reward)", String.valueOf(teamCount)},
                            new String[]{"Summe Bestellwert* (gesamt)", euro(sumOrderAll)},
                            new String[]{"Summe Bemessungsgrundlage (gesamt)", euro(sumBmgAll)},
                            new String[]{"Summe Provision (gesamt)", euro(sumProvAll)},
                            new String[]{"Auszahlungsbetrag", euro(payout)},
                            new String[]{"Rundungsdifferenz (Auszahlung - Summe Provision)", euro(rounding)}
                    );

                    cs.setNonStrokingColor(new Color(44, 52, 64));
                    for (String[] row : summaryRows) {
                        float used = drawTableRow(cs, x, y, 18f, keyWidth, valueWidth, row[0], row[1]);
                        y -= used;
                    }

                    y -= 16;
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, 10);
                    cs.newLineAtOffset(x, y);
                    cs.showText("* Bestellwert exkl. abgezogener Rabatte (so wie im System/Export übergeben).");
                    cs.endText();
                    y -= 28;

                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                    cs.setNonStrokingColor(new Color(38, 93, 171));
                    cs.newLineAtOffset(x, y);
                    cs.showText("Aufteilung der Provisionen");
                    cs.endText();
                    y -= 20;

                    float x2 = x;
                    float c1 = totalWidth * 0.22f;
                    float c2 = totalWidth * 0.26f;
                    float c3 = totalWidth * 0.30f;
                    float c4 = totalWidth - (c1 + c2 + c3);
                    float h = 20f;
                    drawSimpleCell(cs, x2, y, c1, h, "Typ", true, new Color(235, 242, 252));
                    drawSimpleCell(cs, x2 + c1, y, c2, h, "Summe Bestellwert*", true, new Color(235, 242, 252));
                    drawSimpleCell(cs, x2 + c1 + c2, y, c3, h, "Summe Bemessungsgrundlage", true, new Color(235, 242, 252));
                    drawSimpleCell(cs, x2 + c1 + c2 + c3, y, c4, h, "Summe Provision", true, new Color(235, 242, 252));
                    y -= h;
                    drawSimpleCell(cs, x2, y, c1, h, "Direkt (Order)", false, null);
                    drawSimpleCell(cs, x2 + c1, y, c2, h, euro(sumOrderDirect), false, null);
                    drawSimpleCell(cs, x2 + c1 + c2, y, c3, h, euro(sumBmgDirect), false, null);
                    drawSimpleCell(cs, x2 + c1 + c2 + c3, y, c4, h, euro(sumProvDirect), false, null);
                    y -= h;
                    drawSimpleCell(cs, x2, y, c1, h, "Team (Reward)", false, new Color(247, 249, 252));
                    drawSimpleCell(cs, x2 + c1, y, c2, h, euro(sumOrderTeam), false, new Color(247, 249, 252));
                    drawSimpleCell(cs, x2 + c1 + c2, y, c3, h, euro(sumBmgTeam), false, new Color(247, 249, 252));
                    drawSimpleCell(cs, x2 + c1 + c2 + c3, y, c4, h, euro(sumProvTeam), false, new Color(247, 249, 252));
                    y -= (h + 18);

                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
                    cs.newLineAtOffset(x, y);
                    cs.showText("Hinweise für Beraterinnen (für die eigene Nachweisführung)");
                    cs.endText();
                    y -= 16;
                    String[] notes = new String[]{
                            "• Diesen Provisionsnachweis zusammen mit dem Kontoauszug (Zahlungseingang/SEPA-Gutschrift) ablegen.",
                            "• Falls ihr umsatzsteuerpflichtig seid: prüfen, ob die Provision netto/brutto ausgewiesen werden muss.",
                            "• Bei Kleinunternehmerregelung (§ 19 UStG): sicherstellen, dass die " + kind.label + " korrekt ausgestellt wurde.",
                            "• Team-/Downline-Provisionen: Referenzen im System aufbewahren und bei Bedarf nachreichen.",
                            "• Aufbewahrung: Unterlagen nach Jahr/Monat/Zahllauf archivieren.",
                            "• Stammdaten aktuell halten (Name/IBAN/Adresse/Steuernummer), damit Zuordnung eindeutig bleibt."
                    };
                    for (String n : notes) {
                        for (String line : wrapForPdf(n, 98)) {
                            cs.beginText();
                            cs.setFont(PDType1Font.HELVETICA, 10);
                            cs.newLineAtOffset(x, y);
                            cs.showText(line);
                            cs.endText();
                            y -= 13;
                        }
                    }

                    y -= 8;
                    String nachweisFirmenname = documentProviderName(config, kind);
                    String providerNote = "Diese Provisionsübersicht wurde von der " + nachweisFirmenname + " als Anlage zur " + kind.label + " " + gutschriftNr
                            + (kind.selfBilling ? " gemäß § 14 UStG" : "") + " erstellt. " +
                            "Bei Rückfragen wenden Sie sich bitte an info@vemmina.com. " +
                            "Die zugrundeliegenden Rohdaten können bei Bedarf angefragt werden.";
                    for (String line : wrapForPdf(providerNote, 100)) {
                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 9);
                        cs.setNonStrokingColor(new Color(72, 78, 85));
                        cs.newLineAtOffset(x, y);
                        cs.showText(line);
                        cs.endText();
                        y -= 12;
                    }

                    y -= 8;
                    for (String line : wrapForPdf("Inhalts-Hash (SHA-256): " + documentHash, 100)) {
                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 9);
                        cs.setNonStrokingColor(new Color(72, 78, 85));
                        cs.newLineAtOffset(x, y);
                        cs.showText(line);
                        cs.endText();
                        y -= 12;
                    }
                }

                PDPage advisorPage = new PDPage();
                document.addPage(advisorPage);
                try (PDPageContentStream cs = new PDPageContentStream(document, advisorPage)) {
                    float margin = 52f;
                    float pageWidth = advisorPage.getMediaBox().getWidth();
                    float pageHeight = advisorPage.getMediaBox().getHeight();
                    float x = margin;
                    float y = pageHeight - margin;
                    float totalWidth = pageWidth - (2 * margin);
                    float keyWidth = totalWidth * 0.30f;
                    float valueWidth = totalWidth * 0.70f;

                    cs.setNonStrokingColor(new Color(38, 93, 171));
                    cs.addRect(x, y - 36f, totalWidth, 36f);
                    cs.fill();

                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                    cs.setNonStrokingColor(Color.WHITE);
                    cs.newLineAtOffset(x + 12, y - 22);
                    cs.showText("Beraterin / Affiliate (Stammdaten)");
                    cs.endText();
                    y -= 52f;

                    cs.setNonStrokingColor(new Color(44, 52, 64));
                    for (String[] row : advisorRows) {
                        if (row[1] == null || row[1].isBlank()) continue;
                        float used = drawTableRow(cs, x, y, 20f, keyWidth, valueWidth, row[0], row[1]);
                        y -= used;
                    }
                }

                float detailMargin = 52f;
                float detailPageHeight = summaryPage.getMediaBox().getHeight();
                float detailRowHeight = 19f;
                int rowsPerPage = Math.max(12, (int) ((detailPageHeight - (2 * detailMargin) - 120f) / detailRowHeight));
                int totalPages = Math.max(1, (txList.size() + rowsPerPage - 1) / rowsPerPage);
                int pageNo = 1;
                for (int start = 0; start < txList.size(); start += rowsPerPage) {
                    int end = Math.min(start + rowsPerPage, txList.size());
                    PDPage detailPage = new PDPage();
                    document.addPage(detailPage);
                    try (PDPageContentStream cs = new PDPageContentStream(document, detailPage)) {
                        float pageWidth = detailPage.getMediaBox().getWidth();
                        float pageHeight = detailPage.getMediaBox().getHeight();
                        float x = detailMargin;
                        float y = pageHeight - detailMargin;

                        cs.setNonStrokingColor(new Color(38, 93, 171));
                        cs.addRect(x, y - 28f, pageWidth - (2 * detailMargin), 28f);
                        cs.fill();

                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                        cs.setNonStrokingColor(Color.WHITE);
                        cs.newLineAtOffset(x + 10, y - 18);
                        cs.showText("Detailnachweis – Einzeltransaktionen (Seite " + pageNo + " von " + totalPages + ")");
                        cs.endText();
                        y -= 42;

                        float totalTableWidth = pageWidth - (2 * detailMargin);
                        float cZeit = totalTableWidth * 0.20f;
                        float cTyp = totalTableWidth * 0.17f;
                        float cOrder = totalTableWidth * 0.16f;
                        float cOrderW = totalTableWidth * 0.20f;
                        float cBmg = totalTableWidth * 0.14f;
                        float cProv = totalTableWidth - (cZeit + cTyp + cOrder + cOrderW + cBmg);
                        float h=detailRowHeight;
                        drawSimpleCell(cs, x, y, cZeit, h, "Zeitpunkt", true, new Color(235, 242, 252));
                        drawSimpleCell(cs, x+cZeit, y, cTyp, h, "Typ", true, new Color(235, 242, 252));
                        drawSimpleCell(cs, x+cZeit+cTyp, y, cOrder, h, "Bestellnummer", true, new Color(235, 242, 252));
                        drawSimpleCell(cs, x+cZeit+cTyp+cOrder, y, cOrderW, h, "Bestellwert*", true, new Color(235, 242, 252));
                        drawSimpleCell(cs, x+cZeit+cTyp+cOrder+cOrderW, y, cBmg, h, "BMG", true, new Color(235, 242, 252));
                        drawSimpleCell(cs, x+cZeit+cTyp+cOrder+cOrderW+cBmg, y, cProv, h, "Provision", true, new Color(235, 242, 252));
                        y -= h;

                        for (int i=start; i<end; i++) {
                            JsonNode tx = txList.get(i);
                            JsonNode md = tx.get("metadata");
                            String t = formatDateTimeEuropeBerlin(asText(tx, "created_at"));
                            String typ = "rewards".equalsIgnoreCase(asText(tx, "entity_type")) ? "Team (Reward)" : "Direkt (Order)";
                            String orderNo = md != null ? asText(md, "order_number") : "";
                            String orderVal = md != null ? euro(parseDoubleSafe(asText(md, "order_value"))) : "";
                            String bmgVal = md != null ? euro(parseDoubleSafe(asText(md, "commission_on"))) : "";
                            String provVal = euro(parseDoubleSafe(asText(tx, "amount")));

                            Color rowBg = (i % 2 == 0) ? null : new Color(249, 251, 255);
                            drawSimpleCell(cs, x, y, cZeit, h, t, false, rowBg);
                            drawSimpleCell(cs, x+cZeit, y, cTyp, h, typ, false, rowBg);
                            drawSimpleCell(cs, x+cZeit+cTyp, y, cOrder, h, orderNo, false, rowBg);
                            drawSimpleCell(cs, x+cZeit+cTyp+cOrder, y, cOrderW, h, orderVal, false, rowBg);
                            drawSimpleCell(cs, x+cZeit+cTyp+cOrder+cOrderW, y, cBmg, h, bmgVal, false, rowBg);
                            drawSimpleCell(cs, x+cZeit+cTyp+cOrder+cOrderW+cBmg, y, cProv, h, provVal, false, rowBg);
                            y -= h;
                        }

                        y -= 14;
                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA, 9);
                        cs.setNonStrokingColor(new Color(72, 78, 85));
                        cs.newLineAtOffset(x, y);
                        cs.showText("* Bestellwert exkl. abgezogener Rabatte (so wie im System/Export übergeben).");
                        cs.endText();

                        y -= 12;
                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 8);
                        cs.newLineAtOffset(x, y);
                        cs.showText(shortenForPdf("Inhalts-Hash (SHA-256): " + documentHash, 120));
                        cs.endText();
                    }
                    pageNo++;
                }

                document.save(pdfPath.toFile());
            }
        }

        private String maskIban(String iban) {
            if (iban == null || iban.length() < 8) {
                return iban == null ? "" : iban;
            }
            String compact = iban.replaceAll("\\s+", "");
            if (compact.length() <= 8) return compact;
            return compact.substring(0, 4) + " **** **** **** " + compact.substring(compact.length() - 4);
        }

        private String euro(double value) {
            return euroPdf(value);
        }

        private double parseDoubleSafe(String raw) {
            if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) return 0.0;
            try {
                return Double.parseDouble(raw.replace(",", "."));
            } catch (Exception e) {
                return 0.0;
            }
        }

        private String formatDateTimeEuropeBerlin(String input) {
            if (input == null || input.isBlank()) return "";
            try {
                OffsetDateTime dt = OffsetDateTime.parse(input);
                return dt.atZoneSameInstant(ZoneId.of("Europe/Berlin")).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            } catch (Exception ignored) {
                return input;
            }
        }

        private void drawSimpleCell(PDPageContentStream cs, float x, float y, float width, float height, String text, boolean bold) throws IOException {
            drawSimpleCell(cs, x, y, width, height, text, bold, null);
        }

        private void drawSimpleCell(PDPageContentStream cs, float x, float y, float width, float height, String text, boolean bold, Color background) throws IOException {
            if (background != null) {
                cs.setNonStrokingColor(background);
                cs.addRect(x, y - height, width, height);
                cs.fill();
            }

            cs.setStrokingColor(new Color(196, 205, 217));
            cs.setLineWidth(0.45f);
            cs.addRect(x, y - height, width, height);
            cs.stroke();

            List<String> lines = wrapForPdf(text == null ? "" : text, Math.max(8, (int)(width / 5.2f)));
            float ty = y - 12f;
            for (int i = 0; i < Math.min(lines.size(), 2); i++) {
                cs.beginText();
                cs.setFont(bold ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA, 9);
                cs.setNonStrokingColor(new Color(44, 52, 64));
                cs.newLineAtOffset(x + 4, ty - (i * 9f));
                cs.showText(shortenForPdf(lines.get(i), 80));
                cs.endText();
            }
        }
    }


    private static void flattenJsonForPdf(String prefix, JsonNode node, List<String[]> rows) {
        if (node == null || node.isNull()) {
            rows.add(new String[]{prefix, "null"});
            return;
        }

        if (node.isObject()) {
            node.fieldNames().forEachRemaining(field -> {
                JsonNode child = node.get(field);
                String nextPrefix = prefix.isEmpty() ? field : prefix + "." + field;
                flattenJsonForPdf(nextPrefix, child, rows);
            });
            return;
        }

        if (node.isArray()) {
            if (node.size() == 0) {
                rows.add(new String[]{prefix, "[]"});
                return;
            }
            for (int i = 0; i < node.size(); i++) {
                flattenJsonForPdf(prefix + "[" + i + "]", node.get(i), rows);
            }
            return;
        }

        rows.add(new String[]{prefix, node.asText()});
    }

    private static float drawTableRow(PDPageContentStream cs, float x, float y, float minRowHeight, float keyWidth, float valueWidth, String key, String value) throws IOException {
            List<String> keyLines = wrapForPdf(key, Math.max(8, (int)(keyWidth / 4.8f)));
            List<String> valueLines = valueWidth > 0 ? wrapForPdf(value, Math.max(8, (int)(valueWidth / 4.8f))) : List.of("");
            int lines = Math.max(keyLines.size(), valueLines.size());
            float rowHeight = Math.max(minRowHeight, lines * 10f + 8f);

            cs.setLineWidth(0.5f);
            cs.addRect(x, y - rowHeight, keyWidth, rowHeight);
            if (valueWidth > 0f) {
                cs.addRect(x + keyWidth, y - rowHeight, valueWidth, rowHeight);
            }
            cs.stroke();

            float textY = y - 12f;
            for (int i = 0; i < lines; i++) {
                String kl = i < keyLines.size() ? keyLines.get(i) : "";
                String vl = i < valueLines.size() ? valueLines.get(i) : "";

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 9);
                cs.newLineAtOffset(x + 4, textY - (i * 10f));
                cs.showText(shortenForPdf(kl, 200));
                cs.endText();

                if (valueWidth > 0f) {
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, 9);
                    cs.newLineAtOffset(x + keyWidth + 4, textY - (i * 10f));
                    cs.showText(shortenForPdf(vl, 400));
                    cs.endText();
                }
            }
            return rowHeight;
        }

    private static float drawTableRowBold(PDPageContentStream cs, float x, float y, float minRowHeight, float keyWidth, float valueWidth, String key, String value) throws IOException {
            List<String> keyLines = wrapForPdf(key, Math.max(8, (int)(keyWidth / 4.8f)));
            List<String> valueLines = valueWidth > 0 ? wrapForPdf(value, Math.max(8, (int)(valueWidth / 4.8f))) : List.of("");
            int lines = Math.max(keyLines.size(), valueLines.size());
            float rowHeight = Math.max(minRowHeight, lines * 10f + 8f);

            cs.setLineWidth(0.8f);
            cs.addRect(x, y - rowHeight, keyWidth, rowHeight);
            if (valueWidth > 0f) {
                cs.addRect(x + keyWidth, y - rowHeight, valueWidth, rowHeight);
            }
            cs.stroke();

            float textY = y - 12f;
            for (int i = 0; i < lines; i++) {
                String kl = i < keyLines.size() ? keyLines.get(i) : "";
                String vl = i < valueLines.size() ? valueLines.get(i) : "";

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 9);
                cs.newLineAtOffset(x + 4, textY - (i * 10f));
                cs.showText(shortenForPdf(kl, 200));
                cs.endText();

                if (valueWidth > 0f) {
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 9);
                    cs.newLineAtOffset(x + keyWidth + 4, textY - (i * 10f));
                    cs.showText(shortenForPdf(vl, 400));
                    cs.endText();
                }
            }
            return rowHeight;
        }

    private static List<String> wrapForPdf(String text, int maxChars) {
            String safe = text == null ? "" : text.replaceAll("[\r\n]+", " ");
            List<String> lines = new ArrayList<>();
            if (safe.isBlank()) {
                lines.add("");
                return lines;
            }

            StringBuilder current = new StringBuilder();
            for (String word : safe.split("\s+")) {
                if (current.length() == 0) {
                    current.append(word);
                } else if (current.length() + 1 + word.length() <= maxChars) {
                    current.append(" ").append(word);
                } else {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                }
            }
            if (current.length() > 0) lines.add(current.toString());
            return lines;
        }

    private static String resolveGermanLabel(String originalName) {
            return switch (originalName) {
                case "id" -> "Zahlungs-ID";
                case "affiliate_id" -> "Affiliate-ID";
                case "amount" -> "Provision";
                case "payment_method" -> "Zahlungsart";
                case "affiliate_message" -> "Affiliate-Nachricht";
                case "admin_note" -> "Admin-Notiz";
                case "created_at" -> "Bestelldatum";
                case "tx_id" -> "Transaktions-ID";
                case "entity_type" -> "Entitätstyp";
                case "order_number" -> "Bestellnummer";
                case "order_id" -> "Bestell-ID";
                case "status" -> "Status";
                case "order_value" -> "Bestellwert abzgl. Rabatte";
                case "commission_on" -> "Provisionsberechtigter Rechnungsbetrag";
                case "affiliate_commission" -> "Affiliate-Provision";
                default -> "Feld";
            };
        }

    private static String translateFieldValue(String fieldName, String rawValue) {
            if (rawValue == null || rawValue.isBlank() || "null".equalsIgnoreCase(rawValue)) {
                return rawValue == null ? "" : rawValue;
            }

            if ("status".equals(fieldName) && "approved".equalsIgnoreCase(rawValue)) {
                return "freigegeben";
            }
            if ("entity_type".equals(fieldName) && "rewards".equalsIgnoreCase(rawValue)) {
                return "Team-Provision";
            }

            if ("amount".equals(fieldName)
                    || "affiliate_commission".equals(fieldName)
                    || "commission_on".equals(fieldName)
                    || "order_value".equals(fieldName)) {
                return formatAmountEuro(rawValue);
            }
            return rawValue;
        }

    private static String label(String germanName, String originalName, int indentLevel) {
            String indent = "  ".repeat(Math.max(0, indentLevel));
            return indent + germanName + " (" + originalName + ")";
        }

    private static String formatAmountEuro(String raw) {
            if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
                return raw == null ? "" : raw;
            }
            try {
                double v = Double.parseDouble(raw.replace(",", "."));
                return String.format(java.util.Locale.GERMANY, "%.2f €", v);
            } catch (Exception e) {
                return raw;
            }
        }

    private static String shortenForPdf(String text, int maxLen) {
            String safe = sanitizePdfText(text == null ? "" : text).replaceAll("[\r\n]+", " ");
            return safe.length() > maxLen ? safe.substring(0, Math.max(0, maxLen - 3)) + "..." : safe;
        }
    private static class VersionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("version", APP_VERSION);
            payload.put("commit", BUILD_INFO.commit());
            payload.put("branch", BUILD_INFO.branch());
            payload.put("source", BUILD_INFO.source());
            payload.put("sequenceKnown", BUILD_INFO.sequenceKnown());
            sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
        }
    }

    private static class VersionHistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }

            Map<String, Object> payload = readRecentVersions();
            sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
        }
    }

    private static class ValidationReminderLogHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);
                List<Map<String, String>> entries = readReminderLogEntries(config);
                Map<String, List<Map<String, String>>> byAdvisor = new LinkedHashMap<>();
                for (Map<String, String> row : entries) {
                    String advisorId = Objects.toString(row.get("advisorId"), "").trim();
                    if (advisorId.isBlank()) continue;
                    byAdvisor.computeIfAbsent(advisorId, k -> new ArrayList<>()).add(row);
                }
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("entries", entries);
                payload.put("byAdvisor", byAdvisor);
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class ValidationReminderMailHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "application/json", "{}");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String advisorId = asText(body, "advisorId").trim();
                String advisorName = asText(body, "advisorName").trim();
                String advisorEmail = asText(body, "advisorEmail").trim();
                String missingFields = asText(body, "missingFields").trim();
                String recipientMode = asText(body, "recipientMode").trim();
                if (!"advisor".equals(recipientMode)) recipientMode = "contact";

                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);
                boolean sendEmailsEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("sendEmailsEnabled"), "true"));
                String contactEmail = Objects.toString(config.getProperty("contactEmail"), "").trim();
                String toEmail = "advisor".equals(recipientMode) ? advisorEmail : contactEmail;
                if (sendEmailsEnabled && toEmail.isBlank()) {
                    sendResponse(exchange, 400, "application/json", "{\"error\":\"Keine Empfänger-E-Mail vorhanden\"}");
                    return;
                }
                String subject = "Bitte fehlende Stammdaten ergänzen";
                if (sendEmailsEnabled) {
                    String plain = buildValidationReminderMailBody(advisorName, missingFields);
                    String html = buildValidationReminderMailHtml(advisorName, missingFields, Objects.toString(config.getProperty("validationReminderTemplateHtml"), ""));
                    sendSimpleHtmlMail(toEmail, Objects.toString(config.getProperty("emailBcc"), "").trim(), subject, plain, html, resolveSmtpConfig(config));
                }
                appendReminderLogEntry(config, advisorId, advisorName, recipientMode, toEmail, subject, missingFields, sendEmailsEnabled ? "sent" : "skipped");
                persistSettings(config);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("message", sendEmailsEnabled ? "Erinnerungs-E-Mail versendet." : "E-Mail-Versand ist deaktiviert.");
                payload.put("toEmail", toEmail);
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static Map<String, JsonNode> fetchAffiliatesById(String apiKey, List<String> affiliateIds) throws Exception {
        if (affiliateIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String ids = String.join(",", affiliateIds);
        String url = "https://api.goaffpro.com/v1/admin/affiliates?id=" + ids + "&fields=id,name,email,phone,company_name,ref_code,status,address_1,address_2,city,state,zip,country,tax_identification_number,payment_method,payment_details,parent_id,upline_affiliate_id,upline_id,parent_affiliate_id";
        JsonNode root = requestJson(url, apiKey);
        JsonNode affiliates = root.get("affiliates");
        if (affiliates == null || !affiliates.isArray()) {
            return Collections.emptyMap();
        }

        Map<String, JsonNode> map = new HashMap<>();
        for (JsonNode affiliate : affiliates) {
            map.put(asText(affiliate, "id"), affiliate);
        }
        return map;
    }

    private static Map<String, JsonNode> loadSyncedEntityMap(Properties config, String entityType) {
        try {
            return GOAFFPRO_SYNC_SERVICE.entityMap(config, entityType);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static Map<String, JsonNode> loadSyncedEntityMapFiltered(Properties config, String entityType, List<String> ids) {
        Map<String, JsonNode> all = loadSyncedEntityMap(config, entityType);
        if (ids == null || ids.isEmpty()) return all;
        Map<String, JsonNode> filtered = new LinkedHashMap<>();
        for (String id : ids) {
            JsonNode node = all.get(id);
            if (node != null) filtered.put(id, node);
        }
        return filtered;
    }

    private static JsonNode loadSyncedRoot(Properties config, String entityType, String arrayField) {
        return GOAFFPRO_SYNC_SERVICE.rootFromStore(config, entityType, arrayField);
    }

    private static boolean hasSyncedData(Properties config, String entityType) {
        return GOAFFPRO_SYNC_SERVICE.hasEntityData(config, entityType);
    }

    private static void attachDataSource(Map<String, Object> payload, Properties config, String entityType) {
        payload.put("dataSource", GOAFFPRO_SYNC_SERVICE.dataSourceInfo(config, entityType));
    }

    private static Map<String, JsonNode> fetchAllAffiliatesForTeamAnalytics(String apiKey) throws Exception {
        String fields = "id,name,email,status,parent_id,upline_affiliate_id,upline_id,parent_affiliate_id";
        String url = "https://api.goaffpro.com/v1/admin/affiliates?fields=" + fields;
        JsonNode root = requestJson(url, apiKey);
        JsonNode affiliates = root.get("affiliates");
        if (affiliates == null || !affiliates.isArray()) {
            return Collections.emptyMap();
        }
        Map<String, JsonNode> map = new LinkedHashMap<>();
        for (JsonNode affiliate : affiliates) {
            String id = asText(affiliate, "id").trim();
            if (!id.isBlank()) {
                map.put(id, affiliate);
            }
        }
        return map;
    }

    private static Map<String, List<String>> buildChildrenByParentFromTreeAndAffiliates(JsonNode treeRoot,
                                                                                        Map<String, JsonNode> affiliatesById) {
        Map<String, List<String>> childrenByParent = new LinkedHashMap<>();
        Set<String> seenIds = new LinkedHashSet<>();
        collectTreeStructure(treeRoot, "", seenIds, childrenByParent);
        if (affiliatesById != null) {
            for (Map.Entry<String, JsonNode> entry : affiliatesById.entrySet()) {
                String id = entry.getKey();
                String parentId = resolveLeaderId(entry.getValue());
                if (!id.isBlank()) seenIds.add(id);
                if (!id.isBlank() && !parentId.isBlank() && !Objects.equals(id, parentId)) {
                    addChildLink(childrenByParent, parentId, id);
                }
            }
        }
        return childrenByParent;
    }

    private static void addChildLink(Map<String, List<String>> childrenByParent, String parentId, String childId) {
        List<String> children = childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>());
        if (!children.contains(childId)) {
            children.add(childId);
        }
    }


    private static Map<String, JsonNode> fetchOrdersById(String apiKey, List<String> orderIds) throws Exception {
        if (orderIds == null || orderIds.isEmpty()) return Collections.emptyMap();
        String ids = String.join(",", orderIds);
        String url = "https://api.goaffpro.com/v1/admin/orders?id=" + ids + "&fields=id,number,currency,total,status,affiliate_id,created_at,line_items";
        JsonNode root = requestJson(url, apiKey);
        JsonNode orders = root.get("orders");
        if (orders == null || !orders.isArray()) return Collections.emptyMap();
        Map<String, JsonNode> map = new LinkedHashMap<>();
        for (JsonNode order : orders) {
            String id = asText(order, "id").trim();
            if (!id.isBlank()) map.put(id, order);
        }
        return map;
    }

    private static int parseIntSafe(String value) {
        try {
            if (value == null || value.isBlank()) return 0;
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static void createZugferdInvoiceXml(Path xmlPath, JsonNode payment, JsonNode affiliate, Properties config,
                                                String documentNumber, String periodLabel, boolean isKleinunternehmer) throws IOException {
        createZugferdInvoiceXml(xmlPath, payment, affiliate, config, documentNumber, periodLabel, isKleinunternehmer, DocumentKind.GUTSCHRIFT);
    }

    private static void createZugferdInvoiceXml(Path xmlPath, JsonNode payment, JsonNode affiliate, Properties config,
                                                String documentNumber, String periodLabel, boolean isKleinunternehmer,
                                                DocumentKind kind) throws IOException {
        boolean enabled = Boolean.parseBoolean(Objects.toString(config.getProperty("eInvoiceEnabled"), "true"));
        if (!enabled) {
            Files.writeString(xmlPath, "<!-- ZUGFeRD/E-" + kind.label + " deaktiviert -->", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return;
        }

        String buyerName = buyerProperty(config, kind, "Name", kind.defaultBuyerName);
        String buyerStreet = buyerProperty(config, kind, "Street", "");
        String buyerZip = buyerProperty(config, kind, "Zip", "");
        String buyerCity = buyerProperty(config, kind, "City", "");
        String buyerCountry = buyerProperty(config, kind, "Country", "DE");
        String buyerVatId = buyerProperty(config, kind, "VatId", "");
        String buyerTaxNumber = buyerProperty(config, kind, "TaxNumber", "");
        String paymentTerms = Objects.toString(config.getProperty("eInvoicePaymentTerms"), "Zahlbar sofort ohne Abzug").trim();

        String sellerName = affiliate != null ? asText(affiliate, "name") : "Beraterin";
        String sellerStreet = affiliate != null ? asText(affiliate, "address_1") : "";
        String sellerCity = affiliate != null ? asText(affiliate, "city") : "";
        String sellerZip = affiliate != null ? asText(affiliate, "zip") : "";
        String sellerCountry = affiliate != null ? asText(affiliate, "country") : "";
        String sellerTaxNumber = affiliate != null ? asText(affiliate, "tax_identification_number") : "";

        String bankIban = parseAffiliatePaymentField(affiliate, "iban");
        String bankBic = parseAffiliatePaymentField(affiliate, "bic");
        String bankAccountHolder = parseAffiliatePaymentField(affiliate, "account_holder");
        if (bankAccountHolder.isBlank()) bankAccountHolder = parseAffiliatePaymentField(affiliate, "name");

        // Altfall-Rechnungen tragen als Rechnungsdatum den Ausstellungstag (passend zum Nummernjahr);
        // Auszahlungsdatum und Leistungszeitraum werden im Dokument separat ausgewiesen.
        String issueDate = kind == DocumentKind.RECHNUNG
                ? LocalDate.now(BERLIN_ZONE).format(DateTimeFormatter.BASIC_ISO_DATE)
                : formatDateYmd(asText(payment, "created_at"));
        String currency = asText(payment, "currency");
        if (currency.isBlank()) currency = "EUR";
        double netAmount = parseDoubleSafeStatic(asText(payment, "amount"));
        double vatAmount = calculateVat(netAmount, isKleinunternehmer);
        double grossAmount = netAmount + vatAmount;

        // Tax block: E = exempt (§19 Kleinunternehmer), S = standard 19%
        String taxCategoryCode = isKleinunternehmer ? "E" : "S";
        String taxRatePercent = isKleinunternehmer ? "0" : "19";
        String taxExemptionReason = isKleinunternehmer
                ? "<ram:ExemptionReason>Steuerbefreiung gem. § 19 UStG (Kleinunternehmerregelung)</ram:ExemptionReason>" : "";

        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rsm:CrossIndustryInvoice xmlns:rsm="urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100"
                                          xmlns:ram="urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100"
                                          xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100">
                  <rsm:ExchangedDocumentContext>
                    <ram:GuidelineSpecifiedDocumentContextParameter>
                      <ram:ID>urn:cen.eu:en16931:2017#compliant#urn:zugferd.de:2p2:basic</ram:ID>
                    </ram:GuidelineSpecifiedDocumentContextParameter>
                  </rsm:ExchangedDocumentContext>
                  <rsm:ExchangedDocument>
                    <ram:ID>{{gutschriftNr}}</ram:ID>
                    <ram:TypeCode>{{typeCode}}</ram:TypeCode>
                    <ram:IssueDateTime><udt:DateTimeString format="102">{{issueDate}}</udt:DateTimeString></ram:IssueDateTime>
                  </rsm:ExchangedDocument>
                  <rsm:SupplyChainTradeTransaction>
                    <ram:IncludedSupplyChainTradeLineItem>
                      <ram:AssociatedDocumentLineDocument><ram:LineID>1</ram:LineID></ram:AssociatedDocumentLineDocument>
                      <ram:SpecifiedTradeProduct><ram:Name>Vermittlungsprovision {{periodLabel}}</ram:Name></ram:SpecifiedTradeProduct>
                      <ram:SpecifiedLineTradeSettlement>
                        <ram:ApplicableTradeTax>
                          <ram:TypeCode>VAT</ram:TypeCode>
                          <ram:CategoryCode>{{taxCategoryCode}}</ram:CategoryCode>
                          <ram:RateApplicablePercent>{{taxRatePercent}}</ram:RateApplicablePercent>
                        </ram:ApplicableTradeTax>
                        <ram:SpecifiedTradeSettlementLineMonetarySummation>
                          <ram:LineTotalAmount>{{netAmount}}</ram:LineTotalAmount>
                        </ram:SpecifiedTradeSettlementLineMonetarySummation>
                      </ram:SpecifiedLineTradeSettlement>
                    </ram:IncludedSupplyChainTradeLineItem>
                    <ram:ApplicableHeaderTradeAgreement>
                      <ram:SellerTradeParty>
                        <ram:Name>{{sellerName}}</ram:Name>
                        <ram:PostalTradeAddress><ram:PostcodeCode>{{sellerZip}}</ram:PostcodeCode><ram:LineOne>{{sellerStreet}}</ram:LineOne><ram:CityName>{{sellerCity}}</ram:CityName><ram:CountryID>{{sellerCountry}}</ram:CountryID></ram:PostalTradeAddress>
                        <ram:SpecifiedTaxRegistration><ram:ID schemeID="FC">{{sellerTaxNumber}}</ram:ID></ram:SpecifiedTaxRegistration>
                      </ram:SellerTradeParty>
                      <ram:BuyerTradeParty>
                        <ram:Name>{{buyerName}}</ram:Name>
                        <ram:PostalTradeAddress><ram:PostcodeCode>{{buyerZip}}</ram:PostcodeCode><ram:LineOne>{{buyerStreet}}</ram:LineOne><ram:CityName>{{buyerCity}}</ram:CityName><ram:CountryID>{{buyerCountry}}</ram:CountryID></ram:PostalTradeAddress>
                        <ram:SpecifiedTaxRegistration><ram:ID schemeID="VA">{{buyerVatId}}</ram:ID></ram:SpecifiedTaxRegistration>
                        <ram:SpecifiedTaxRegistration><ram:ID schemeID="FC">{{buyerTaxNumber}}</ram:ID></ram:SpecifiedTaxRegistration>
                      </ram:BuyerTradeParty>
                    </ram:ApplicableHeaderTradeAgreement>
                    <ram:ApplicableHeaderTradeDelivery/>
                    <ram:ApplicableHeaderTradeSettlement>
                      <ram:InvoiceCurrencyCode>{{currency}}</ram:InvoiceCurrencyCode>
                      <ram:ApplicableTradeTax>
                        <ram:CalculatedAmount>{{vatAmount}}</ram:CalculatedAmount>
                        <ram:TypeCode>VAT</ram:TypeCode>
                        {{taxExemptionReason}}
                        <ram:BasisAmount>{{netAmount}}</ram:BasisAmount>
                        <ram:CategoryCode>{{taxCategoryCode}}</ram:CategoryCode>
                        <ram:RateApplicablePercent>{{taxRatePercent}}</ram:RateApplicablePercent>
                      </ram:ApplicableTradeTax>
                      <ram:SpecifiedTradeSettlementPaymentMeans>
                        <ram:TypeCode>58</ram:TypeCode>
                        <ram:PayeePartyCreditorFinancialAccount><ram:IBANID>{{bankIban}}</ram:IBANID><ram:AccountName>{{bankAccountHolder}}</ram:AccountName></ram:PayeePartyCreditorFinancialAccount>
                        <ram:PayeeSpecifiedCreditorFinancialInstitution><ram:BICID>{{bankBic}}</ram:BICID></ram:PayeeSpecifiedCreditorFinancialInstitution>
                      </ram:SpecifiedTradeSettlementPaymentMeans>
                      <ram:SpecifiedTradePaymentTerms><ram:Description>{{paymentTerms}}</ram:Description></ram:SpecifiedTradePaymentTerms>
                      <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
                        <ram:LineTotalAmount>{{netAmount}}</ram:LineTotalAmount>
                        <ram:TaxTotalAmount currencyID="{{currency}}">{{vatAmount}}</ram:TaxTotalAmount>
                        <ram:GrandTotalAmount>{{grossAmount}}</ram:GrandTotalAmount>
                        <ram:DuePayableAmount>{{grossAmount}}</ram:DuePayableAmount>
                      </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
                    </ram:ApplicableHeaderTradeSettlement>
                  </rsm:SupplyChainTradeTransaction>
                </rsm:CrossIndustryInvoice>
                """;
        xml = xml.replace("{{typeCode}}", kind.zugferdTypeCode)
                .replace("{{gutschriftNr}}", escapeXml(documentNumber))
                .replace("{{issueDate}}", escapeXml(issueDate))
                .replace("{{periodLabel}}", escapeXml(periodLabel))
                .replace("{{sellerName}}", escapeXml(sellerName))
                .replace("{{sellerStreet}}", escapeXml(sellerStreet))
                .replace("{{sellerZip}}", escapeXml(sellerZip))
                .replace("{{sellerCity}}", escapeXml(sellerCity))
                .replace("{{sellerCountry}}", escapeXml(sellerCountry))
                .replace("{{sellerTaxNumber}}", escapeXml(sellerTaxNumber))
                .replace("{{buyerName}}", escapeXml(buyerName))
                .replace("{{buyerStreet}}", escapeXml(buyerStreet))
                .replace("{{buyerZip}}", escapeXml(buyerZip))
                .replace("{{buyerCity}}", escapeXml(buyerCity))
                .replace("{{buyerCountry}}", escapeXml(buyerCountry))
                .replace("{{buyerVatId}}", escapeXml(buyerVatId))
                .replace("{{buyerTaxNumber}}", escapeXml(buyerTaxNumber))
                .replace("{{currency}}", escapeXml(currency))
                .replace("{{bankIban}}", escapeXml(bankIban))
                .replace("{{bankAccountHolder}}", escapeXml(bankAccountHolder))
                .replace("{{bankBic}}", escapeXml(bankBic))
                .replace("{{paymentTerms}}", escapeXml(paymentTerms))
                .replace("{{taxCategoryCode}}", taxCategoryCode)
                .replace("{{taxRatePercent}}", taxRatePercent)
                .replace("{{taxExemptionReason}}", taxExemptionReason)
                .replace("{{netAmount}}", String.format(java.util.Locale.US, "%.2f", netAmount))
                .replace("{{vatAmount}}", String.format(java.util.Locale.US, "%.2f", vatAmount))
                .replace("{{grossAmount}}", String.format(java.util.Locale.US, "%.2f", grossAmount));

        Files.writeString(xmlPath, xml, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static String parseAffiliatePaymentField(JsonNode affiliate, String key) {
        if (affiliate == null || affiliate.isMissingNode() || affiliate.isNull()) return "";
        JsonNode paymentDetails = affiliate.get("payment_details");
        if (paymentDetails == null || paymentDetails.isMissingNode() || paymentDetails.isNull()) return "";
        if (paymentDetails.isObject()) {
            return asText(paymentDetails, key).trim();
        }
        String raw = paymentDetails.asText("").trim();
        if (raw.isBlank()) return "";
        try {
            JsonNode parsed = OBJECT_MAPPER.readTree(raw);
            if (parsed != null && parsed.isObject()) return asText(parsed, key).trim();
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String formatDateYmd(String isoDateTime) {
        try {
            OffsetDateTime dt = OffsetDateTime.parse(isoDateTime);
            return dt.atZoneSameInstant(ZoneId.of("Europe/Berlin")).toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        } catch (Exception e) {
            return LocalDate.now(ZoneId.of("Europe/Berlin")).format(DateTimeFormatter.BASIC_ISO_DATE);
        }
    }

    private static String escapeXml(String value) {
        String safe = value == null ? "" : value;
        return safe.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static List<Map<String, String>> fetchAdvisorValidationRows(String apiKey) throws Exception {
        String url = "https://api.goaffpro.com/v1/admin/affiliates?fields=id,avatar,honorific,date_of_birth,gender,name,first_name,last_name,email,ref_code,company_name,ref_codes,coupon,coupons,phone,website,facebook,twitter,instagram,address_1,address_2,city,state,zip,country,phone,admin_note,extra_1,extra_2,extra_3,group_id,registration_ip,personal_message,payment_method,payment_details,commission,status,last_login,total_referral_earnings,total_network_earnings,total_amount_paid,total_amount_pending,total_other_earnings,number_of_orders,tax_identification_number,login_token,signup_page,comments,tags,approved_at,blocked_at,created_at,updated_at";
        JsonNode root = requestJson(url, apiKey);
        JsonNode affiliates = root.get("affiliates");
        if (affiliates == null || !affiliates.isArray()) return List.of();

        List<Map<String, String>> rows = new ArrayList<>();
        for (JsonNode a : affiliates) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("id", asText(a, "id"));
            row.put("name", asText(a, "name"));
            row.put("email", asText(a, "email"));
            row.put("phone", asText(a, "phone"));
            row.put("company", asText(a, "company_name"));
            row.put("address", formatAffiliateAddress(a));
            row.put("country", asText(a, "country"));
            row.put("dateOfBirth", asText(a, "date_of_birth"));
            row.put("taxNumber", asText(a, "tax_identification_number"));
            row.put("status", asText(a, "status"));
            row.put("paymentMethod", asText(a, "payment_method"));
            String iban = asText(a.path("payment_details"), "account_number").trim();
            row.put("iban", iban);
            row.put("ibanOwner", asText(a.path("payment_details"), "account_name").trim());
            row.put("ibanValid", isValidIban(iban) ? "Ja" : "Nein");
            if (isValidationRowRelevant(row)) rows.add(row);
        }
        rows.sort((a, b) -> Objects.toString(a.get("name"), "").compareToIgnoreCase(Objects.toString(b.get("name"), "")));
        return rows;
    }

    private static List<Map<String, String>> fetchAdvisorTreeValidationRows(String apiKey) throws Exception {
        JsonNode treeRoot = requestJson("https://api.goaffpro.com/v1/admin/mlm/tree", apiKey);

        String affiliatesUrl = "https://api.goaffpro.com/v1/admin/affiliates?fields=id,avatar,honorific,date_of_birth,gender,name,first_name,last_name,email,ref_code,company_name,ref_codes,coupon,coupons,phone,website,facebook,twitter,instagram,address_1,address_2,city,state,zip,country,phone,admin_note,extra_1,extra_2,extra_3,group_id,registration_ip,personal_message,payment_method,payment_details,commission,status,last_login,total_referral_earnings,total_network_earnings,total_amount_paid,total_amount_pending,total_other_earnings,number_of_orders,tax_identification_number,login_token,signup_page,comments,tags,approved_at,blocked_at,created_at,updated_at";
        JsonNode affiliateRoot = requestJson(affiliatesUrl, apiKey);
        JsonNode affiliates = affiliateRoot.get("affiliates");

        Map<String, JsonNode> affiliatesById = new LinkedHashMap<>();
        if (affiliates != null && affiliates.isArray()) {
            for (JsonNode affiliate : affiliates) {
                String id = asText(affiliate, "id").trim();
                if (!id.isBlank()) affiliatesById.put(id, affiliate);
            }
        }

        Map<String, List<String>> childrenByParent = new LinkedHashMap<>();
        Set<String> seenIds = new LinkedHashSet<>();
        collectTreeStructure(treeRoot, "", seenIds, childrenByParent);

        List<String> roots = seenIds.stream().filter(id -> {
            for (List<String> children : childrenByParent.values()) {
                if (children.contains(id)) return false;
            }
            return true;
        }).collect(Collectors.toCollection(ArrayList::new));

        if (roots.isEmpty()) roots.addAll(seenIds);
        sortAffiliateIdsByName(roots, affiliatesById);

        List<Map<String, String>> rows = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        for (String rootId : roots) {
            appendTreeRows(rootId, "", 0, childrenByParent, affiliatesById, visited, rows);
        }

        for (String orphan : seenIds) {
            if (!visited.contains(orphan)) {
                appendTreeRows(orphan, "", 0, childrenByParent, affiliatesById, visited, rows);
            }
        }
        return rows;
    }

    private static void collectTreeStructure(JsonNode node, String currentParentId, Set<String> seenIds, Map<String, List<String>> childrenByParent) {
        if (node == null || node.isNull() || node.isMissingNode()) return;

        if (node.isArray()) {
            for (JsonNode item : node) collectTreeStructure(item, currentParentId, seenIds, childrenByParent);
            return;
        }

        if (!node.isObject()) return;

        String nodeId = extractAffiliateId(node);
        String parentId = extractParentAffiliateId(node);
        if (parentId.isBlank()) parentId = currentParentId;
        String activeId = nodeId.isBlank() ? currentParentId : nodeId;

        if (!nodeId.isBlank()) seenIds.add(nodeId);
        if (!nodeId.isBlank() && !parentId.isBlank() && !Objects.equals(nodeId, parentId)) {
            childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>());
            if (!childrenByParent.get(parentId).contains(nodeId)) childrenByParent.get(parentId).add(nodeId);
        }

        for (String key : List.of("children", "childs", "downline", "tree", "affiliates", "members", "nodes")) {
            JsonNode children = node.get(key);
            if (children != null && children.isArray()) {
                for (JsonNode child : children) collectTreeStructure(child, activeId, seenIds, childrenByParent);
            }
        }
    }

    private static String extractAffiliateId(JsonNode node) {
        for (String key : List.of("id", "affiliate_id", "affiliateId", "user_id")) {
            String value = asText(node, key).trim();
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static String extractParentAffiliateId(JsonNode node) {
        for (String key : List.of("parent", "parent_id", "parent_affiliate_id", "upline_affiliate_id", "upline_id", "parentId")) {
            String value = asText(node, key).trim();
            if (!value.isBlank() && !"0".equals(value)) return value;
        }
        return "";
    }

    private static void appendTreeRows(String nodeId,
                                       String parentId,
                                       int level,
                                       Map<String, List<String>> childrenByParent,
                                       Map<String, JsonNode> affiliatesById,
                                       Set<String> visited,
                                       List<Map<String, String>> rows) {
        if (nodeId == null || nodeId.isBlank() || visited.contains(nodeId)) return;
        visited.add(nodeId);

        JsonNode affiliate = affiliatesById.get(nodeId);
        Map<String, String> row = new LinkedHashMap<>();
        row.put("id", nodeId);
        row.put("parentId", parentId);
        row.put("level", String.valueOf(level));
        row.put("name", affiliate != null ? asText(affiliate, "name") : ("ID " + nodeId));
        row.put("email", affiliate != null ? asText(affiliate, "email") : "");
        row.put("status", affiliate != null ? asText(affiliate, "status") : "");
        row.put("company", affiliate != null ? asText(affiliate, "company_name") : "");
        List<String> children = new ArrayList<>(childrenByParent.getOrDefault(nodeId, List.of()));
        row.put("childrenCount", String.valueOf(children.size()));
        rows.add(row);

        sortAffiliateIdsByName(children, affiliatesById);
        for (String childId : children) {
            appendTreeRows(childId, nodeId, level + 1, childrenByParent, affiliatesById, visited, rows);
        }
    }

    private static void sortAffiliateIdsByName(List<String> ids, Map<String, JsonNode> affiliatesById) {
        ids.sort((a, b) -> {
            JsonNode affA = affiliatesById.get(a);
            JsonNode affB = affiliatesById.get(b);
            String nameA = affA != null ? asText(affA, "name") : ("ID " + a);
            String nameB = affB != null ? asText(affB, "name") : ("ID " + b);
            return nameA.compareToIgnoreCase(nameB);
        });
    }

    private static boolean isValidationRowRelevant(Map<String, String> row) {
        String[] keys = new String[]{"name", "email", "phone", "address", "country", "dateOfBirth", "taxNumber", "iban", "ibanOwner", "paymentMethod"};
        for (String key : keys) {
            if (!Objects.toString(row.get(key), "").isBlank()) return true;
        }
        return false;
    }

    private static String formatAffiliateAddress(JsonNode affiliate) {
        if (affiliate == null || affiliate.isMissingNode() || affiliate.isNull()) return "";

        String address1 = asText(affiliate, "address_1");
        String address2 = asText(affiliate, "address_2");
        String zip = asText(affiliate, "zip");
        String city = asText(affiliate, "city");
        String state = asText(affiliate, "state");
        String country = asText(affiliate, "country");

        List<String> parts = new ArrayList<>();
        if (!address1.isBlank()) parts.add(address1);
        if (!address2.isBlank()) parts.add(address2);

        StringBuilder cityLine = new StringBuilder();
        if (!zip.isBlank()) cityLine.append(zip.trim());
        if (!city.isBlank()) {
            if (cityLine.length() > 0) cityLine.append(" ");
            cityLine.append(city.trim());
        }
        if (!state.isBlank()) {
            if (cityLine.length() > 0) cityLine.append(", ");
            cityLine.append(state.trim());
        }
        if (cityLine.length() > 0) parts.add(cityLine.toString());

        if (!country.isBlank()) parts.add(country);
        return String.join(", ", parts);
    }

    private static class TransactionSplit {
        final double selfCommission;
        final double teamCommission;

        private TransactionSplit(double selfCommission, double teamCommission) {
            this.selfCommission = selfCommission;
            this.teamCommission = teamCommission;
        }
    }

    private static TransactionSplit splitTransactions(JsonNode transactions) {
        if (transactions == null || !transactions.isArray()) {
            return new TransactionSplit(0.0, 0.0);
        }
        double self = 0.0;
        double team = 0.0;
        for (JsonNode tx : transactions) {
            String entityType = asText(tx, "entity_type");
            double commission = parseDoubleSafeStatic(asText(tx, "amount"));
            if ("orders".equalsIgnoreCase(entityType)) {
                self += commission;
            } else {
                team += commission;
            }
        }
        return new TransactionSplit(self, team);
    }

    private static String resolveLeaderId(JsonNode affiliate) {
        if (affiliate == null || affiliate.isNull() || affiliate.isMissingNode()) return "";
        String[] fields = new String[]{"parent_id", "upline_affiliate_id", "upline_id", "parent_affiliate_id"};
        for (String field : fields) {
            String value = asText(affiliate, field).trim();
            if (!value.isBlank() && !"0".equals(value)) return value;
        }
        return "";
    }

    /** Min/Max der Transaktions-Zeitstempel eines Zahllaufs. Einzige Quelle für Zeitraum UND Stichtag. */
    private record TransactionDateRange(OffsetDateTime min, OffsetDateTime max, int datedCount, int totalCount) {
        boolean hasDates() {
            return min != null && max != null;
        }
    }

    private static TransactionDateRange extractTransactionDateRange(JsonNode payment) {
        JsonNode transactions = payment != null ? payment.get("transactions") : null;
        if (transactions == null || !transactions.isArray() || transactions.size() == 0) {
            return new TransactionDateRange(null, null, 0, 0);
        }
        OffsetDateTime minDate = null;
        OffsetDateTime maxDate = null;
        int dated = 0;
        for (JsonNode tx : transactions) {
            try {
                OffsetDateTime dt = OffsetDateTime.parse(asText(tx, "created_at"));
                if (minDate == null || dt.isBefore(minDate)) minDate = dt;
                if (maxDate == null || dt.isAfter(maxDate)) maxDate = dt;
                dated++;
            } catch (Exception ignored) {
            }
        }
        return new TransactionDateRange(minDate, maxDate, dated, transactions.size());
    }

    private static String buildPaymentPeriodLabel(JsonNode payment) {
        TransactionDateRange range = extractTransactionDateRange(payment);
        if (!range.hasDates()) return "ohne Zeitraum";
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        return range.min().atZoneSameInstant(BERLIN_ZONE).format(f)
                + " bis "
                + range.max().atZoneSameInstant(BERLIN_ZONE).format(f);
    }

    private static void writeOriginalJson(Path jsonPath, JsonNode response) throws IOException {
        String pretty = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response);
        Files.writeString(jsonPath, pretty, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void sendInvoiceMailWithAttachment(String toEmail, String bccEmail, Path pdfPath, Path jsonPath, Path zugferdPath, Path eInvoicePdfPath, boolean includeEInvoiceAttachments, String affiliateName, String periodLabel, JsonNode payment, JsonNode affiliate, String configuredEmailTemplateHtml, SmtpConfig smtpConfig, String documentNumber) throws Exception {
        sendInvoiceMailWithAttachment(toEmail, bccEmail, pdfPath, jsonPath, zugferdPath, eInvoicePdfPath, includeEInvoiceAttachments,
                affiliateName, periodLabel, payment, affiliate, configuredEmailTemplateHtml, smtpConfig, documentNumber, DocumentKind.GUTSCHRIFT);
    }

    private static void sendInvoiceMailWithAttachment(String toEmail, String bccEmail, Path pdfPath, Path jsonPath, Path zugferdPath, Path eInvoicePdfPath, boolean includeEInvoiceAttachments, String affiliateName, String periodLabel, JsonNode payment, JsonNode affiliate, String configuredEmailTemplateHtml, SmtpConfig smtpConfig, String gutschriftNr, DocumentKind kind) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpConfig.host);
        props.put("mail.smtp.port", String.valueOf(smtpConfig.port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(smtpConfig.tls));
        props.put("mail.smtp.ssl.enable", "false");

        Session session = Session.getInstance(props);

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(smtpConfig.username));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail, false));
        if (bccEmail != null && !bccEmail.isBlank()) {
            message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(bccEmail, false));
        }

        String displayName = (affiliateName == null || affiliateName.isBlank()) ? "Beraterin" : affiliateName.trim();
        String subject = documentMailSubject(kind, gutschriftNr, periodLabel);
        message.setSubject(subject, StandardCharsets.UTF_8.name());

        String plainTextBody = buildInvoiceMailBody(payment, affiliate, periodLabel, gutschriftNr, kind);
        String htmlBody = buildInvoiceMailHtml(payment, affiliate, periodLabel, configuredEmailTemplateHtml, gutschriftNr, kind);

        MimeBodyPart contentPart = new MimeBodyPart();
        MimeMultipart alternative = new MimeMultipart("alternative");

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(plainTextBody, StandardCharsets.UTF_8.name());
        alternative.addBodyPart(textPart);

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");
        alternative.addBodyPart(htmlPart);

        contentPart.setContent(alternative);

        MimeBodyPart attachmentPart = new MimeBodyPart();
        FileDataSource fds = new FileDataSource(pdfPath.toFile());
        attachmentPart.setDataHandler(new DataHandler(fds));
        attachmentPart.setFileName(pdfPath.getFileName().toString());

        MimeBodyPart jsonAttachmentPart = new MimeBodyPart();
        FileDataSource jsonDs = new FileDataSource(jsonPath.toFile());
        jsonAttachmentPart.setDataHandler(new DataHandler(jsonDs));
        jsonAttachmentPart.setFileName(jsonPath.getFileName().toString());

        MimeMultipart multipart = new MimeMultipart("mixed");
        multipart.addBodyPart(contentPart);
        multipart.addBodyPart(attachmentPart);
        multipart.addBodyPart(jsonAttachmentPart);
        if (includeEInvoiceAttachments && zugferdPath != null && eInvoicePdfPath != null) {
            MimeBodyPart zugferdAttachmentPart = new MimeBodyPart();
            FileDataSource zugferdDs = new FileDataSource(zugferdPath.toFile());
            zugferdAttachmentPart.setDataHandler(new DataHandler(zugferdDs));
            zugferdAttachmentPart.setFileName(zugferdPath.getFileName().toString());
            zugferdAttachmentPart.setHeader("Content-Type", "application/xml; charset=UTF-8");

            MimeBodyPart eInvoiceViewAttachmentPart = new MimeBodyPart();
            FileDataSource eInvoiceViewDs = new FileDataSource(eInvoicePdfPath.toFile());
            eInvoiceViewAttachmentPart.setDataHandler(new DataHandler(eInvoiceViewDs));
            eInvoiceViewAttachmentPart.setFileName(eInvoicePdfPath.getFileName().toString());

            multipart.addBodyPart(zugferdAttachmentPart);
            multipart.addBodyPart(eInvoiceViewAttachmentPart);
        }
        message.setContent(multipart);

        Transport transport = session.getTransport("smtp");
        try {
            System.out.println("[SMTP] Connecting to " + smtpConfig.host + ":" + smtpConfig.port + " as " + smtpConfig.username + " (tls=" + smtpConfig.tls + ")");
            transport.connect(smtpConfig.host, smtpConfig.port, smtpConfig.username, smtpConfig.password);
            transport.sendMessage(message, message.getAllRecipients());
            System.out.println("[SMTP] Mail sent: to=" + toEmail + " subject=" + subject);
        } catch (Exception e) {
            System.err.println("[SMTP] Send failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            transport.close();
        }
    }


    private static String buildInvoiceMailBody(JsonNode payment, JsonNode affiliate, String periodLabel, String documentNumber) {
        return buildInvoiceMailBody(payment, affiliate, periodLabel, documentNumber, DocumentKind.GUTSCHRIFT);
    }

    private static String buildInvoiceMailBody(JsonNode payment, JsonNode affiliate, String periodLabel, String gutschriftNr, DocumentKind kind) {
        String affiliateName = affiliate != null ? asText(affiliate, "name") : "";
        String salutationName = (affiliateName == null || affiliateName.isBlank()) ? "liebe Beraterin" : ("liebe " + affiliateName.trim());
        String paymentId = payment != null ? asText(payment, "id") : "";
        String payout = euroStatic(parseDoubleSafeStatic(payment != null ? asText(payment, "amount") : "0"));
        String method = payment != null ? asText(payment, "payment_method") : "";
        String created = formatDateTimeEuropeBerlinStatic(payment != null ? asText(payment, "created_at") : "");

        int txCount = 0;
        JsonNode transactions = payment != null ? payment.get("transactions") : null;
        if (transactions != null && transactions.isArray()) txCount = transactions.size();

        if (kind == DocumentKind.RECHNUNG) {
            return """
                Hallo %s,

                der Provisionslauf für %s wurde abgeschlossen.

                Im Anhang finden Sie Ihre Rechnung %s (PDF) über die Vermittlungsprovision sowie die Provisionsübersicht mit den vermittelten Aufträgen.

                Kurze Übersicht:
                - Rechnungsnummer: %s
                - Zeitraum: %s
                - Auszahlungsbetrag: %s
                - Zahlungsmethode: %s
                - Auszahlungsdatum (System): %s
                - Anzahl Transaktionen: %s

                Bitte prüfen Sie die Unterlagen. Falls Ihnen Abweichungen auffallen, melden Sie sich bitte zeitnah bei uns.

                Viele Grüße
                Ihr VEMMiNA Team
                """.formatted(salutationName, periodLabel, gutschriftNr, gutschriftNr, periodLabel, payout, method, created, txCount);
        }

        return """
                Hallo %s,

                der Provisionslauf für %s wurde abgeschlossen.

                Im Anhang finden Sie Ihre Gutschrift %s (PDF) gemäß § 14 UStG sowie die Provisionsübersicht mit den vermittelten Aufträgen.

                Kurze Übersicht:
                - Gutschriftnummer: %s
                - Zeitraum: %s
                - Auszahlungsbetrag: %s
                - Zahlungsmethode: %s
                - Auszahlungsdatum (System): %s
                - Anzahl Transaktionen: %s

                Bitte prüfen Sie die Unterlagen. Falls Ihnen Abweichungen auffallen, melden Sie sich bitte zeitnah bei uns.

                Viele Grüße
                Ihr VEMMiNA Team
                """.formatted(salutationName, periodLabel, gutschriftNr, gutschriftNr, periodLabel, payout, method, created, txCount);
    }

    private static String buildInvoiceMailHtml(JsonNode payment, JsonNode affiliate, String periodLabel, String configuredTemplateHtml, String documentNumber) {
        return buildInvoiceMailHtml(payment, affiliate, periodLabel, configuredTemplateHtml, documentNumber, DocumentKind.GUTSCHRIFT);
    }

    private static String buildInvoiceMailHtml(JsonNode payment, JsonNode affiliate, String periodLabel, String configuredTemplateHtml, String gutschriftNr, DocumentKind kind) {
        String affiliateName = affiliate != null ? asText(affiliate, "name") : "";
        String salutationName = (affiliateName == null || affiliateName.isBlank()) ? "Beraterin" : affiliateName.trim();
        String paymentId = payment != null ? asText(payment, "id") : "-";
        String payout = euroStatic(parseDoubleSafeStatic(payment != null ? asText(payment, "amount") : "0"));
        String method = payment != null ? asText(payment, "payment_method") : "-";
        String created = formatDateTimeEuropeBerlinStatic(payment != null ? asText(payment, "created_at") : "-");

        int txCount = 0;
        JsonNode transactions = payment != null ? payment.get("transactions") : null;
        if (transactions != null && transactions.isArray()) txCount = transactions.size();

        String template = (configuredTemplateHtml == null || configuredTemplateHtml.isBlank())
                ? defaultMailTemplate(kind)
                : configuredTemplateHtml;

        return template
                .replace("{{salutationName}}", escapeHtmlEmail(salutationName))
                .replace("{{periodLabel}}", escapeHtmlEmail(periodLabel))
                .replace("{{documentNumber}}", escapeHtmlEmail(gutschriftNr))
                .replace("{{gutschriftNr}}", escapeHtmlEmail(gutschriftNr))
                .replace("{{paymentId}}", escapeHtmlEmail(paymentId))
                .replace("{{payout}}", escapeHtmlEmail(payout))
                .replace("{{method}}", escapeHtmlEmail(method))
                .replace("{{created}}", escapeHtmlEmail(created))
                .replace("{{txCount}}", String.valueOf(txCount));
    }

    private static String getDefaultInvoiceMailHtmlTemplate() {
        return """
                <!doctype html>
                <html lang="de">
                <body style="margin:0;padding:0;background:#eef4f8;font-family:Arial,sans-serif;color:#1f2937;">
                  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background:#eef4f8;padding:22px 0;">
                    <tr>
                      <td align="center">
                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="680" style="max-width:680px;width:100%;background:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #dbe3ef;box-shadow:0 10px 24px rgba(15,23,42,0.08);">
                          <tr>
                            <td style="padding:26px 28px;background:linear-gradient(135deg,#6FA3C4 0%,#5c8fb1 100%);color:#ffffff;">
                              <p style="margin:0;font-size:13px;letter-spacing:1.2px;text-transform:uppercase;opacity:0.88;">VEMMiNA</p>
                              <h1 style="margin:8px 0 6px 0;font-size:34px;line-height:1.2;">Ihre Provisionsgutschrift</h1>
                              <p style="margin:0;font-size:16px;line-height:1.5;opacity:0.95;">Der Provisionslauf für {{periodLabel}} wurde abgeschlossen.</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:28px;">
                              <p style="margin:0 0 14px 0;font-size:24px;line-height:1.35;color:#1e293b;">Hallo {{salutationName}},</p>
                              <p style="margin:0 0 18px 0;font-size:16px;line-height:1.7;color:#334155;">im Anhang finden Sie Ihre Gutschrift gemäß § 14 UStG sowie die Provisionsübersicht mit den vermittelten Aufträgen. Bitte prüfen Sie die Unterlagen.</p>

                              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="border-collapse:separate;border-spacing:0;background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;overflow:hidden;">
                                <tr><td style="padding:12px 14px;font-size:15px;color:#1f2937;border-bottom:1px solid #e2e8f0;"><strong>Gutschriftnummer</strong><br/>{{gutschriftNr}}</td></tr>
                                <tr><td style="padding:12px 14px;font-size:15px;color:#1f2937;border-bottom:1px solid #e2e8f0;"><strong>Zeitraum</strong><br/>{{periodLabel}}</td></tr>
                                <tr><td style="padding:12px 14px;font-size:15px;color:#1f2937;border-bottom:1px solid #e2e8f0;"><strong>Auszahlungsbetrag</strong><br/><span style="font-size:20px;font-weight:700;color:#108474;">{{payout}}</span></td></tr>
                                <tr><td style="padding:12px 14px;font-size:15px;color:#1f2937;border-bottom:1px solid #e2e8f0;"><strong>Zahlungsmethode</strong><br/>{{method}}</td></tr>
                                <tr><td style="padding:12px 14px;font-size:15px;color:#1f2937;border-bottom:1px solid #e2e8f0;"><strong>Ausstellungsdatum</strong><br/>{{created}}</td></tr>
                                <tr><td style="padding:12px 14px;font-size:15px;color:#1f2937;"><strong>Anzahl Transaktionen</strong><br/>{{txCount}}</td></tr>
                              </table>

                              <p style="margin:18px 0 0 0;font-size:15px;line-height:1.7;color:#334155;">Falls Ihnen Abweichungen auffallen, melden Sie sich bitte zeitnah bei uns.</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:20px 28px;background:#f8fafc;border-top:1px solid #e2e8f0;">
                              <p style="margin:0;font-size:14px;color:#64748b;">Viele Grüße<br/><strong style="color:#0f172a;">Ihr VEMMiNA Team</strong></p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """;
    }

    // LEGACY-RECHNUNG: Mailvorlage für Altfälle. Layoutgleich, aber Rechnungs-Wortlaut ohne § 14.
    private static String getDefaultRechnungMailHtmlTemplate() {
        return """
                <!doctype html>
                <html lang="de">
                <body style="margin:0;padding:0;background:#eef4f8;font-family:Arial,sans-serif;color:#1f2937;">
                  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background:#eef4f8;padding:22px 0;">
                    <tr>
                      <td align="center">
                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="680" style="max-width:680px;width:100%;background:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #dbe3ef;box-shadow:0 10px 24px rgba(15,23,42,0.08);">
                          <tr>
                            <td style="padding:26px 28px;background:linear-gradient(135deg,#6FA3C4 0%,#5c8fb1 100%);color:#ffffff;">
                              <p style="margin:0;font-size:13px;letter-spacing:1.2px;text-transform:uppercase;opacity:0.88;">VEMMiNA</p>
                              <h1 style="margin:8px 0 6px 0;font-size:34px;line-height:1.2;">Ihre Provisionsrechnung</h1>
                              <p style="margin:0;font-size:16px;line-height:1.5;opacity:0.95;">Der Provisionslauf für {{periodLabel}} wurde abgeschlossen.</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:28px;">
                              <p style="margin:0 0 14px 0;font-size:24px;line-height:1.35;color:#1e293b;">Hallo {{salutationName}},</p>
                              <p style="margin:0 0 18px 0;font-size:16px;line-height:1.7;color:#334155;">im Anhang finden Sie Ihre Rechnung über die Vermittlungsprovision sowie die Provisionsübersicht mit den vermittelten Aufträgen. Bitte prüfen Sie die Unterlagen.</p>

                              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="border-collapse:separate;border-spacing:0;background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;overflow:hidden;">
                                <tr><td style="padding:12px 14px;font-size:15px;color:#1f2937;border-bottom:1px solid #e2e8f0;"><strong>Rechnungsnummer</strong><br/>{{documentNumber}}</td></tr>
                                <tr><td style="padding:12px 14px;font-size:15px;color:#1f2937;border-bottom:1px solid #e2e8f0;"><strong>Zeitraum</strong><br/>{{periodLabel}}</td></tr>
                                <tr><td style="padding:12px 14px;font-size:15px;color:#1f2937;border-bottom:1px solid #e2e8f0;"><strong>Auszahlungsbetrag</strong><br/><span style="font-size:20px;font-weight:700;color:#108474;">{{payout}}</span></td></tr>
                                <tr><td style="padding:12px 14px;font-size:15px;color:#1f2937;border-bottom:1px solid #e2e8f0;"><strong>Zahlungsmethode</strong><br/>{{method}}</td></tr>
                                <tr><td style="padding:12px 14px;font-size:15px;color:#1f2937;border-bottom:1px solid #e2e8f0;"><strong>Auszahlungsdatum</strong><br/>{{created}}</td></tr>
                                <tr><td style="padding:12px 14px;font-size:15px;color:#1f2937;"><strong>Anzahl Transaktionen</strong><br/>{{txCount}}</td></tr>
                              </table>

                              <p style="margin:18px 0 0 0;font-size:15px;line-height:1.7;color:#334155;">Falls Ihnen Abweichungen auffallen, melden Sie sich bitte zeitnah bei uns.</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:20px 28px;background:#f8fafc;border-top:1px solid #e2e8f0;">
                              <p style="margin:0;font-size:14px;color:#64748b;">Viele Grüße<br/><strong style="color:#0f172a;">Ihr VEMMiNA Team</strong></p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """;
    }

    private static List<Map<String, String>> readMailLogEntries(Properties config) {
        String raw = Objects.toString(config.getProperty(MAIL_LOG_KEY), "").trim();
        if (raw.isBlank()) return new ArrayList<>();
        try {
            List<Map<String, String>> list = OBJECT_MAPPER.readValue(raw, new TypeReference<List<Map<String, String>>>() {});
            return list == null ? new ArrayList<>() : list;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private static List<Map<String, String>> readReminderLogEntries(Properties config) {
        String raw = Objects.toString(config.getProperty(REMINDER_LOG_KEY), "").trim();
        if (raw.isBlank()) return new ArrayList<>();
        try {
            List<Map<String, String>> list = OBJECT_MAPPER.readValue(raw, new TypeReference<List<Map<String, String>>>() {});
            return list == null ? new ArrayList<>() : list;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private static List<Map<String, String>> readLeaderWeeklyMailLogEntries(Properties config) {
        String raw = Objects.toString(config.getProperty(LEADER_WEEKLY_MAIL_LOG_KEY), "").trim();
        if (raw.isBlank()) return new ArrayList<>();
        try {
            List<Map<String, String>> list = OBJECT_MAPPER.readValue(raw, new TypeReference<List<Map<String, String>>>() {});
            return list == null ? new ArrayList<>() : list;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private static void appendReminderLogEntry(Properties config,
                                               String advisorId,
                                               String advisorName,
                                               String recipientMode,
                                               String toEmail,
                                               String subject,
                                               String missingFields,
                                               String status) {
        List<Map<String, String>> entries = readReminderLogEntries(config);
        Map<String, String> row = new LinkedHashMap<>();
        row.put("advisorId", Objects.toString(advisorId, ""));
        row.put("advisorName", Objects.toString(advisorName, ""));
        row.put("recipientMode", Objects.toString(recipientMode, "contact"));
        row.put("toEmail", Objects.toString(toEmail, ""));
        row.put("subject", Objects.toString(subject, ""));
        row.put("missingFields", Objects.toString(missingFields, ""));
        row.put("status", Objects.toString(status, "sent"));
        row.put("sentAt", ZonedDateTime.now(ZoneId.of("Europe/Berlin")).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));
        entries.add(0, row);
        if (entries.size() > 1000) entries = new ArrayList<>(entries.subList(0, 1000));
        try {
            config.setProperty(REMINDER_LOG_KEY, OBJECT_MAPPER.writeValueAsString(entries));
        } catch (Exception ignored) {
        }
    }

    private static void appendLeaderWeeklyMailLogEntry(Properties config,
                                                       String leaderId,
                                                       String leaderName,
                                                       String periodKey,
                                                       String recipientMode,
                                                       String toEmail,
                                                       String status,
                                                       String subject) {
        List<Map<String, String>> entries = readLeaderWeeklyMailLogEntries(config);
        Map<String, String> row = new LinkedHashMap<>();
        row.put("leaderId", Objects.toString(leaderId, ""));
        row.put("leaderName", Objects.toString(leaderName, ""));
        row.put("periodKey", Objects.toString(periodKey, ""));
        row.put("recipientMode", Objects.toString(recipientMode, "test"));
        row.put("toEmail", Objects.toString(toEmail, ""));
        row.put("status", Objects.toString(status, "test"));
        row.put("subject", Objects.toString(subject, ""));
        row.put("sentAt", ZonedDateTime.now(BERLIN_ZONE).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));
        entries.add(0, row);
        if (entries.size() > 1000) entries = new ArrayList<>(entries.subList(0, 1000));
        try {
            config.setProperty(LEADER_WEEKLY_MAIL_LOG_KEY, OBJECT_MAPPER.writeValueAsString(entries));
        } catch (Exception ignored) {
        }
    }

    private static void appendMailLogEntry(Properties config, String paymentId, String recipientMode, String toEmail, String subject, String periodLabel, Path pdfPath, Path jsonPath, Path zugferdPath, Path eInvoiceViewPdfPath) {
        appendMailLogEntry(config, paymentId, recipientMode, toEmail, subject, periodLabel, pdfPath, jsonPath, zugferdPath, eInvoiceViewPdfPath,
                DocumentKind.GUTSCHRIFT, "", false);
    }

    private static void appendMailLogEntry(Properties config, String paymentId, String recipientMode, String toEmail, String subject, String periodLabel, Path pdfPath, Path jsonPath, Path zugferdPath, Path eInvoiceViewPdfPath,
                                           DocumentKind kind, String documentNumber, boolean kindOverridden) {
        List<Map<String, String>> entries = readMailLogEntries(config);
        Map<String, String> row = new LinkedHashMap<>();
        row.put("paymentId", Objects.toString(paymentId, ""));
        row.put("documentKind", (kind != null ? kind : DocumentKind.GUTSCHRIFT).wireValue());
        row.put("documentNumber", Objects.toString(documentNumber, ""));
        if (kindOverridden) row.put("documentKindOverridden", "true");
        row.put("recipientMode", Objects.toString(recipientMode, "contact"));
        row.put("toEmail", Objects.toString(toEmail, ""));
        row.put("subject", Objects.toString(subject, ""));
        row.put("periodLabel", Objects.toString(periodLabel, ""));
        row.put("pdfFile", pdfPath != null ? pdfPath.getFileName().toString() : "");
        row.put("jsonFile", jsonPath != null ? jsonPath.getFileName().toString() : "");
        row.put("pdfPath", pdfPath != null ? pdfPath.toAbsolutePath().toString() : "");
        row.put("jsonPath", jsonPath != null ? jsonPath.toAbsolutePath().toString() : "");
        row.put("zugferdFile", zugferdPath != null ? zugferdPath.getFileName().toString() : "");
        row.put("zugferdPath", zugferdPath != null ? zugferdPath.toAbsolutePath().toString() : "");
        row.put("eInvoiceViewPdfFile", eInvoiceViewPdfPath != null ? eInvoiceViewPdfPath.getFileName().toString() : "");
        row.put("eInvoiceViewPdfPath", eInvoiceViewPdfPath != null ? eInvoiceViewPdfPath.toAbsolutePath().toString() : "");
        row.put("sentAt", ZonedDateTime.now(ZoneId.of("Europe/Berlin")).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));
        entries.add(0, row);
        if (entries.size() > 1000) entries = new ArrayList<>(entries.subList(0, 1000));
        try {
            config.setProperty(MAIL_LOG_KEY, OBJECT_MAPPER.writeValueAsString(entries));
        } catch (Exception ignored) {
        }
    }

    private static void sendSimpleHtmlMail(String toEmail, String bccEmail, String subject, String plainTextBody, String htmlBody, SmtpConfig smtpConfig) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpConfig.host);
        props.put("mail.smtp.port", String.valueOf(smtpConfig.port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(smtpConfig.tls));
        props.put("mail.smtp.ssl.enable", "false");

        Session session = Session.getInstance(props);
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(smtpConfig.username));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail, false));
        if (bccEmail != null && !bccEmail.isBlank()) {
            message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(bccEmail, false));
        }
        message.setSubject(subject, StandardCharsets.UTF_8.name());

        MimeMultipart alternative = new MimeMultipart("alternative");
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(plainTextBody, StandardCharsets.UTF_8.name());
        alternative.addBodyPart(textPart);
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");
        alternative.addBodyPart(htmlPart);
        message.setContent(alternative);

        Transport transport = session.getTransport("smtp");
        try {
            System.out.println("[SMTP] Connecting to " + smtpConfig.host + ":" + smtpConfig.port + " as " + smtpConfig.username + " (tls=" + smtpConfig.tls + ")");
            transport.connect(smtpConfig.host, smtpConfig.port, smtpConfig.username, smtpConfig.password);
            transport.sendMessage(message, message.getAllRecipients());
            System.out.println("[SMTP] Mail sent: to=" + toEmail + " subject=" + subject);
        } catch (Exception e) {
            System.err.println("[SMTP] Send failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            transport.close();
        }
    }

    private static void createEInvoicePdfWithEmbeddedXml(Path pdfPath, Path xmlPath, JsonNode payment, JsonNode affiliate, Properties config,
                                                         String documentNumber, String periodLabel, boolean isKleinunternehmer) throws IOException {
        createEInvoicePdfWithEmbeddedXml(pdfPath, xmlPath, payment, affiliate, config,
                documentNumber, periodLabel, isKleinunternehmer, DocumentKind.GUTSCHRIFT);
    }

    private static void createEInvoicePdfWithEmbeddedXml(Path pdfPath, Path xmlPath, JsonNode payment, JsonNode affiliate, Properties config,
                                                         String gutschriftNr, String periodLabel, boolean isKleinunternehmer,
                                                         DocumentKind kind) throws IOException {
        if (!Boolean.getBoolean("goaffpro.legacyEInvoicePdfRenderer")) {
            createEInvoicePdfFromHtmlTemplate(pdfPath, xmlPath, payment, affiliate, config, gutschriftNr, periodLabel, isKleinunternehmer, kind);
            return;
        }

        // Extract variables
        String advisorName = affiliate != null ? asText(affiliate, "name") : "Beraterin";
        String advisorAddressOneLiner = formatAffiliateAddress(affiliate);
        String advisorEmail = affiliate != null ? asText(affiliate, "email") : "";
        String advisorPhone = affiliate != null ? asText(affiliate, "phone") : "";
        String advisorTaxNumber = affiliate != null ? asText(affiliate, "tax_identification_number") : "";
        String advisorIban = parseAffiliatePaymentField(affiliate, "iban");
        String advisorBic = parseAffiliatePaymentField(affiliate, "bic");
        String advisorAccountHolder = parseAffiliatePaymentField(affiliate, "account_holder");
        if (advisorAccountHolder.isBlank()) advisorAccountHolder = advisorName;
        String paymentId = payment != null ? asText(payment, "id") : "-";
        String created = formatDateTimeEuropeBerlinStatic(payment != null ? asText(payment, "created_at") : "");
        double netAmount = parseDoubleSafeStatic(payment != null ? asText(payment, "amount") : "0");
        double vatAmount = calculateVat(netAmount, isKleinunternehmer);
        double grossAmount = netAmount + vatAmount;
        String amount = euroPdf(netAmount);
        String vatAmountStr = euroPdf(vatAmount);
        String grossAmountStr = euroPdf(grossAmount);
        String buyerCompanyName = firstNonBlank(
                Objects.toString(config.getProperty("eInvoiceBuyerName"), ""),
                Objects.toString(config.getProperty("nachweisFirmenname"), ""),
                "S+R Linear Technology GmbH");
        String buyerStreet = Objects.toString(config.getProperty("eInvoiceBuyerStreet"), "").trim();
        String buyerZip = Objects.toString(config.getProperty("eInvoiceBuyerZip"), "").trim();
        String buyerCity = Objects.toString(config.getProperty("eInvoiceBuyerCity"), "").trim();
        String buyerCountry = Objects.toString(config.getProperty("eInvoiceBuyerCountry"), "DE").trim();
        String buyerVatId = Objects.toString(config.getProperty("eInvoiceBuyerVatId"), "").trim();
        String buyerTaxNumber = Objects.toString(config.getProperty("eInvoiceBuyerTaxNumber"), "").trim();
        String buyerAddressOneLiner = String.join(", ", List.of(
                buyerStreet,
                (buyerZip + " " + buyerCity).trim(),
                buyerCountry
        ).stream().filter(v -> v != null && !v.isBlank()).toList());
        String contactEmail = Objects.toString(config.getProperty("contactEmail"), "").trim();
        String paymentTerms = Objects.toString(config.getProperty("eInvoicePaymentTerms"), "Zahlbar sofort ohne Abzug").trim();

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();
            float left = 45f;
            float right = pageWidth - 45f;
            float usableW = right - left;

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float y = pageHeight - 38f;

                // Header: issuing company as sender line.
                String hdrLine = buyerCompanyName + (buyerAddressOneLiner.isBlank() ? "" : " - " + buyerAddressOneLiner);
                cs.setNonStrokingColor(new Color(100, 100, 100));
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 8f);
                cs.newLineAtOffset(left, y);
                cs.showText(sanitizePdfText(shortenForPdf(hdrLine, 95)));
                cs.endText();
                y -= 7f;
                cs.setStrokingColor(new Color(180, 180, 180)); cs.setLineWidth(0.4f);
                cs.moveTo(left, y); cs.lineTo(right, y); cs.stroke();
                y -= 20f;

                // ── TWO COLUMNS: Bill To (left) | Invoice Meta (right) ──
                float colL = left;
                float colR = left + usableW * 0.55f;
                float startY2col = y;

                // LEFT: recipient address block (affiliate).
                cs.setNonStrokingColor(new Color(130, 130, 130));
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 8f);
                cs.newLineAtOffset(colL, y); cs.showText("Gutschriftempfängerin (Leistungserbringerin)"); cs.endText();
                y -= 14f;
                cs.setNonStrokingColor(new Color(15, 15, 15));
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA_BOLD, 12f);
                cs.newLineAtOffset(colL, y);
                cs.showText(sanitizePdfText(shortenForPdf(advisorName, 38))); cs.endText();
                y -= 14f;
                List<String> advisorLines = new ArrayList<>();
                if (affiliate != null) {
                    String address1 = asText(affiliate, "address_1").trim();
                    String address2 = asText(affiliate, "address_2").trim();
                    String zip = asText(affiliate, "zip").trim();
                    String city = asText(affiliate, "city").trim();
                    String state = asText(affiliate, "state").trim();
                    String country = asText(affiliate, "country").trim();
                    if (!address1.isBlank()) advisorLines.add(address1);
                    if (!address2.isBlank()) advisorLines.add(address2);
                    String advisorCityLine = (zip + " " + city).trim();
                    if (!state.isBlank()) advisorCityLine = advisorCityLine.isBlank() ? state : advisorCityLine + ", " + state;
                    if (!advisorCityLine.isBlank()) advisorLines.add(advisorCityLine);
                    if (!country.isBlank()) advisorLines.add(country);
                }
                for (String bl : advisorLines) {
                    cs.setNonStrokingColor(new Color(40, 40, 40));
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 10f);
                    cs.newLineAtOffset(colL, y); cs.showText(sanitizePdfText(bl)); cs.endText();
                    y -= 13f;
                }
                if (!advisorTaxNumber.isBlank()) {
                    cs.setNonStrokingColor(new Color(100, 100, 100));
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 8.5f);
                    cs.newLineAtOffset(colL, y); cs.showText("Steuernummer: " + sanitizePdfText(advisorTaxNumber)); cs.endText();
                    y -= 11f;
                }
                if (!advisorEmail.isBlank()) {
                    cs.setNonStrokingColor(new Color(100, 100, 100));
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 8.5f);
                    cs.newLineAtOffset(colL, y); cs.showText("E-Mail: " + sanitizePdfText(shortenForPdf(advisorEmail, 46))); cs.endText();
                    y -= 11f;
                }
                float leftEndY = y;

                // RIGHT: issuer contact + document metadata.
                float ry = startY2col;
                List<String[]> metaRows = new ArrayList<>();
                if (!contactEmail.isBlank()) metaRows.add(new String[]{"E-Mail:", contactEmail});
                if (!buyerVatId.isBlank()) metaRows.add(new String[]{"USt-IdNr:", buyerVatId});
                if (!buyerTaxNumber.isBlank()) metaRows.add(new String[]{"Steuernummer:", buyerTaxNumber});
                metaRows.add(new String[]{"Gutschriftnummer:", gutschriftNr});
                metaRows.add(new String[]{"Datum:", created});
                float lblW = 82f;
                for (String[] row : metaRows) {
                    cs.setNonStrokingColor(new Color(130, 130, 130));
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 9f);
                    cs.newLineAtOffset(colR, ry); cs.showText(sanitizePdfText(row[0])); cs.endText();
                    cs.setNonStrokingColor(new Color(20, 20, 20));
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 9f);
                    cs.newLineAtOffset(colR + lblW, ry); cs.showText(sanitizePdfText(shortenForPdf(row[1], 28))); cs.endText();
                    ry -= 13f;
                }
                y = Math.min(leftEndY, ry) - 14f;

                // ── GUTSCHRIFT TITLE BOX ──
                float boxH = 28f;
                cs.setNonStrokingColor(new Color(235, 235, 235));
                cs.addRect(left, y - boxH, usableW, boxH); cs.fill();
                cs.setStrokingColor(new Color(200, 200, 200)); cs.setLineWidth(0.4f);
                cs.addRect(left, y - boxH, usableW, boxH); cs.stroke();
                cs.setNonStrokingColor(new Color(15, 15, 15));
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA_BOLD, 13f);
                cs.newLineAtOffset(left + 8f, y - 19f);
                cs.showText("GUTSCHRIFT  Nr. " + sanitizePdfText(gutschriftNr)); cs.endText();
                cs.setNonStrokingColor(new Color(80, 80, 80));
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 9.5f);
                cs.newLineAtOffset(right - 135f, y - 19f);
                cs.showText("Datum: " + sanitizePdfText(created)); cs.endText();
                y -= boxH + 6f;
                // §14-Untertitel
                cs.setNonStrokingColor(new Color(80, 80, 80));
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 8f);
                cs.newLineAtOffset(left + 8f, y - 2f);
                cs.showText("Gutschrift gemäß § 14 Abs. 2 Satz 5 UStG - Provisionszeitraum: " + sanitizePdfText(periodLabel));
                cs.endText();
                y -= 16f;

                // ── ITEM TABLE ──
                float rowH = 22f;
                float cW0 = 28f;   // Pos
                float cW2 = 105f;  // Betrag
                float cW1 = usableW - cW0 - cW2; // Beschreibung
                float[] cx = {left, left + cW0, left + cW0 + cW1};

                // Table header row
                cs.setNonStrokingColor(new Color(245, 245, 245));
                cs.addRect(left, y - rowH, usableW, rowH); cs.fill();
                cs.setStrokingColor(new Color(200, 200, 200)); cs.setLineWidth(0.4f);
                cs.addRect(left, y - rowH, usableW, rowH); cs.stroke();
                String[] hdrs = {"Pos.", "Beschreibung", "Betrag"};
                cs.setNonStrokingColor(new Color(60, 60, 60));
                for (int i = 0; i < 3; i++) {
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA_BOLD, 9.5f);
                    cs.newLineAtOffset(cx[i] + 4f, y - 15f); cs.showText(hdrs[i]); cs.endText();
                }
                y -= rowH;

                // Data row
                float dRowH = 24f;
                cs.setStrokingColor(new Color(210, 210, 210)); cs.setLineWidth(0.4f);
                cs.addRect(left, y - dRowH, usableW, dRowH); cs.stroke();
                cs.setNonStrokingColor(new Color(30, 30, 30));
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 10f);
                cs.newLineAtOffset(cx[0] + 4f, y - 16f); cs.showText("1"); cs.endText();
                String desc = "Vermittlungsprovision - " + sanitizePdfText(periodLabel);
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 10f);
                cs.newLineAtOffset(cx[1] + 4f, y - 16f);
                cs.showText(sanitizePdfText(shortenForPdf(desc, 48))); cs.endText();
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 10f);
                cs.newLineAtOffset(cx[2] + 4f, y - 16f);
                cs.showText(sanitizePdfText(amount)); cs.endText();
                y -= dRowH + 14f;

                // ── TOTALS (right-aligned) ──
                float tX = left + usableW * 0.52f;
                float tW = usableW * 0.48f;
                float tLblW = tW * 0.52f;
                float tValX = tX + tLblW;

                // Nettobetrag
                cs.setStrokingColor(new Color(200, 200, 200)); cs.setLineWidth(0.4f);
                cs.addRect(tX, y - 20f, tW, 20f); cs.stroke();
                cs.setNonStrokingColor(new Color(40, 40, 40));
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 10f);
                cs.newLineAtOffset(tX + 4f, y - 14f); cs.showText("Nettobetrag"); cs.endText();
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 10f);
                cs.newLineAtOffset(tValX, y - 14f); cs.showText(sanitizePdfText(amount)); cs.endText();
                y -= 20f;

                // VAT row
                if (isKleinunternehmer) {
                    cs.setNonStrokingColor(new Color(242, 242, 242));
                    cs.addRect(tX, y - 26f, tW, 26f); cs.fill();
                    cs.setStrokingColor(new Color(200, 200, 200));
                    cs.addRect(tX, y - 26f, tW, 26f); cs.stroke();
                    cs.setNonStrokingColor(new Color(80, 80, 80));
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 7f);
                    cs.newLineAtOffset(tX + 4f, y - 11f);
                    cs.showText("Gem. \u00a7 19 UStG wird keine Umsatzsteuer"); cs.endText();
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 7f);
                    cs.newLineAtOffset(tX + 4f, y - 20f); cs.showText("berechnet."); cs.endText();
                    y -= 26f;
                } else {
                    cs.setStrokingColor(new Color(200, 200, 200)); cs.setLineWidth(0.4f);
                    cs.addRect(tX, y - 20f, tW, 20f); cs.stroke();
                    cs.setNonStrokingColor(new Color(40, 40, 40));
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 10f);
                    cs.newLineAtOffset(tX + 4f, y - 14f); cs.showText("Umsatzsteuer (19 %)"); cs.endText();
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 10f);
                    cs.newLineAtOffset(tValX, y - 14f); cs.showText(sanitizePdfText(vatAmountStr)); cs.endText();
                    y -= 20f;
                }

                // Grand Total
                cs.setNonStrokingColor(new Color(225, 225, 225));
                cs.addRect(tX, y - 24f, tW, 24f); cs.fill();
                cs.setStrokingColor(new Color(180, 180, 180));
                cs.addRect(tX, y - 24f, tW, 24f); cs.stroke();
                cs.setNonStrokingColor(new Color(15, 15, 15));
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA_BOLD, 11f);
                cs.newLineAtOffset(tX + 4f, y - 16f); cs.showText("Auszahlungsbetrag"); cs.endText();
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA_BOLD, 11f);
                cs.newLineAtOffset(tValX, y - 16f); cs.showText(sanitizePdfText(grossAmountStr)); cs.endText();
                y -= 24f + 20f;

                // ── PAYMENT TERMS ──
                if (!paymentTerms.isBlank()) {
                    cs.setNonStrokingColor(new Color(60, 60, 60));
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 9f);
                    cs.newLineAtOffset(left, y);
                    cs.showText("Zahlungsbedingungen: " + sanitizePdfText(paymentTerms)); cs.endText();
                    y -= 14f;
                }

                // ── BANK DETAILS ──
                y -= 10f;
                cs.setStrokingColor(new Color(180, 180, 180)); cs.setLineWidth(0.4f);
                cs.moveTo(left, y); cs.lineTo(right, y); cs.stroke();
                y -= 14f;
                cs.setNonStrokingColor(new Color(40, 40, 40));
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA_BOLD, 9f);
                cs.newLineAtOffset(left, y); cs.showText("Bankverbindung der Gutschriftempfängerin"); cs.endText();
                y -= 12f;
                List<String[]> bankRows = new ArrayList<>();
                bankRows.add(new String[]{"Kontoinhaber:", advisorAccountHolder});
                if (!advisorIban.isBlank()) bankRows.add(new String[]{"IBAN:", advisorIban});
                if (!advisorBic.isBlank()) bankRows.add(new String[]{"BIC:", advisorBic});
                for (String[] br : bankRows) {
                    cs.setNonStrokingColor(new Color(100, 100, 100));
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 8.5f);
                    cs.newLineAtOffset(left, y); cs.showText(sanitizePdfText(br[0])); cs.endText();
                    cs.setNonStrokingColor(new Color(30, 30, 30));
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 8.5f);
                    cs.newLineAtOffset(left + 55f, y); cs.showText(sanitizePdfText(br[1])); cs.endText();
                    y -= 12f;
                }

                // ── WIDERSPRUCHSHINWEIS ──
                y -= 8f;
                cs.setStrokingColor(new Color(200, 200, 200)); cs.setLineWidth(0.4f);
                cs.moveTo(left, y); cs.lineTo(right, y); cs.stroke();
                y -= 12f;
                String[] widerspruchLines = {
                    "Bitte prüfen Sie diese Gutschrift. Abweichungen teilen Sie uns bitte unverzüglich mit.",
                    "Die Gutschrift verliert ihre Wirkung als Rechnung, soweit ihr widersprochen wird."
                };
                for (String wl : widerspruchLines) {
                    cs.setNonStrokingColor(new Color(80, 80, 80));
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 7.5f);
                    cs.newLineAtOffset(left, y); cs.showText(sanitizePdfText(wl)); cs.endText();
                    y -= 10f;
                }

                // ── BOTTOM FOOTER ──
                float footerY = 35f;
                cs.setStrokingColor(new Color(180, 180, 180)); cs.setLineWidth(0.4f);
                cs.moveTo(left, footerY + 12f); cs.lineTo(right, footerY + 12f); cs.stroke();
                cs.setNonStrokingColor(new Color(120, 120, 120));
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 7f);
                cs.newLineAtOffset(left, footerY);
                cs.showText(sanitizePdfText(shortenForPdf(hdrLine, 95))); cs.endText();
                if (!buyerTaxNumber.isBlank()) {
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 7f);
                    cs.newLineAtOffset(left, footerY - 9f);
                    cs.showText("Steuernummer: " + sanitizePdfText(buyerTaxNumber)); cs.endText();
                }
            }
            if (xmlPath != null && Files.exists(xmlPath)) {
                attachZugferdXmlToPdf(document, xmlPath);
            }
            document.save(pdfPath.toFile());
        }
    }

    private static void createEInvoicePdfFromHtmlTemplate(Path pdfPath, Path xmlPath, JsonNode payment, JsonNode affiliate, Properties config,
                                                         String documentNumber, String periodLabel, boolean isKleinunternehmer,
                                                         DocumentKind kind) throws IOException {
        String template = documentPdfTemplateHtml(config, kind);
        String renderedHtml = renderEInvoicePdfViewHtml(template, payment, affiliate, config, documentNumber, periodLabel, isKleinunternehmer, kind);
        String xhtml = normalizeHtmlForPdf(renderedHtml);
        String baseUri = pdfPath.toAbsolutePath().getParent() == null
                ? Paths.get(".").toAbsolutePath().toUri().toString()
                : pdfPath.toAbsolutePath().getParent().toUri().toString();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, baseUri);
            builder.toStream(out);
            builder.run();

            try (PDDocument document = PDDocument.load(out.toByteArray())) {
                if (xmlPath != null && Files.exists(xmlPath)) {
                    attachZugferdXmlToPdf(document, xmlPath);
                }
                document.save(pdfPath.toFile());
            }
        } catch (Exception e) {
            throw new IOException("E-Gutschrift-PDF konnte nicht aus der gespeicherten HTML-Vorlage gerendert werden: " + e.getMessage(), e);
        }
    }

    private static String normalizeHtmlForPdf(String html) {
        Document document = Jsoup.parse(html == null ? "" : html);
        document.outputSettings(new Document.OutputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .charset(StandardCharsets.UTF_8)
                .prettyPrint(false));
        return document.html();
    }


    private static void attachZugferdXmlToPdf(PDDocument document, Path xmlPath) throws IOException {
        byte[] xmlBytes = Files.readAllBytes(xmlPath);
        PDComplexFileSpecification fs = new PDComplexFileSpecification();
        fs.setFile(xmlPath.getFileName().toString());

        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(xmlBytes)) {
            PDEmbeddedFile ef = new PDEmbeddedFile(document, bais);
            ef.setSubtype("application/xml");
            ef.setSize(xmlBytes.length);
            ef.setCreationDate(new java.util.GregorianCalendar());
            ef.setModDate(new java.util.GregorianCalendar());
            fs.setEmbeddedFile(ef);
        }

        COSDictionary fsDict = fs.getCOSObject();
        fsDict.setName(COSName.AF_RELATIONSHIP, "Alternative");

        PDDocumentCatalog catalog = document.getDocumentCatalog();
        PDDocumentNameDictionary names = catalog.getNames();
        if (names == null) names = new PDDocumentNameDictionary(catalog);

        PDEmbeddedFilesNameTreeNode efTree = names.getEmbeddedFiles();
        if (efTree == null) efTree = new PDEmbeddedFilesNameTreeNode();

        java.util.Map<String, PDComplexFileSpecification> map = efTree.getNames();
        if (map == null) map = new java.util.HashMap<>();
        map.put(xmlPath.getFileName().toString(), fs);
        efTree.setNames(map);
        names.setEmbeddedFiles(efTree);
        catalog.setNames(names);

        COSArray afArray = new COSArray();
        afArray.add(fsDict);
        catalog.getCOSObject().setItem(COSName.AF, afArray);
    }

    private static String sanitizePdfText(String value) {
        if (value == null || value.isEmpty()) return "";
        String normalized = value
                .replace("\u20ac", "EUR")
                .replace("\u2010", "-")
                .replace("\u2011", "-")
                .replace("\u2012", "-")
                .replace("\u2013", "-")
                .replace("\u2014", "-")
                .replace("\u2212", "-")
                .replace("\u2026", "...")
                .replace("\u2018", "'")
                .replace("\u2019", "'")
                .replace("\u201c", "\"")
                .replace("\u201d", "\"")
                .replace("\u2022", "-")
                .replace('\u00a0', ' ');
        StringBuilder out = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(cp -> {
            if (cp == '\n' || cp == '\r' || cp == '\t') {
                out.append(' ');
                return;
            }
            if (cp < 32 || (cp >= 127 && cp <= 159)) {
                out.append(' ');
                return;
            }
            if (cp > 255) {
                String fallback = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFKD)
                        .replaceAll("\\p{M}", "");
                boolean appended = false;
                for (int i = 0; i < fallback.length(); i++) {
                    char ch = fallback.charAt(i);
                    if (ch >= 32 && ch <= 255) {
                        out.append(ch);
                        appended = true;
                    }
                }
                if (!appended) out.append(' ');
                return;
            }
            out.append((char) cp);
        });
        return out.toString();
    }

    private static List<String> wrapText(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return List.of("");
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() == 0) {
                current.append(word);
                continue;
            }
            if (current.length() + 1 + word.length() <= maxChars) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    private static String renderEInvoicePdfViewHtml(String template, JsonNode payment, JsonNode affiliate, Properties config,
                                                    String documentNumber, String periodLabel, boolean isKleinunternehmer) {
        return renderEInvoicePdfViewHtml(template, payment, affiliate, config, documentNumber, periodLabel, isKleinunternehmer, DocumentKind.GUTSCHRIFT);
    }

    private static String renderEInvoicePdfViewHtml(String template, JsonNode payment, JsonNode affiliate, Properties config,
                                                    String gutschriftNr, String periodLabel, boolean isKleinunternehmer,
                                                    DocumentKind kind) {
        String advisorName = affiliate != null ? asText(affiliate, "name") : "Beraterin";
        String advisorAddress = formatAffiliateAddress(affiliate);
        String advisorEmail = affiliate != null ? asText(affiliate, "email") : "";
        String advisorPhone = affiliate != null ? asText(affiliate, "phone") : "";
        String tax = affiliate != null ? asText(affiliate, "tax_identification_number") : "";
        String paymentId = payment != null ? asText(payment, "id") : "-";
        String created = formatDateTimeEuropeBerlinStatic(payment != null ? asText(payment, "created_at") : "");
        double netAmountVal = parseDoubleSafeStatic(payment != null ? asText(payment, "amount") : "0");
        double vatAmountVal = calculateVat(netAmountVal, isKleinunternehmer);
        double grossAmountVal = netAmountVal + vatAmountVal;
        String amount = euroStatic(netAmountVal);
        String vatAmountFormatted = euroStatic(vatAmountVal);
        String grossAmountFormatted = euroStatic(grossAmountVal);
        String vatLine = isKleinunternehmer ? "Gem. § 19 UStG keine USt." : "Umsatzsteuer (19 %)";
        String currency = payment != null ? asText(payment, "currency") : "EUR";
        String buyerCompanyName = buyerProperty(config, kind, "Name", kind.defaultBuyerName);
        String buyerStreet = buyerProperty(config, kind, "Street", "");
        String buyerZip = buyerProperty(config, kind, "Zip", "");
        String buyerCity = buyerProperty(config, kind, "City", "");
        String buyerCountry = buyerProperty(config, kind, "Country", "DE");
        String buyerVatId = buyerProperty(config, kind, "VatId", "");
        String buyerTaxNumber = buyerProperty(config, kind, "TaxNumber", "");
        String buyerAddress = String.join(", ", List.of(
                buyerStreet,
                (buyerZip + " " + buyerCity).trim(),
                buyerCountry
        ).stream().filter(v -> v != null && !v.isBlank()).toList());
        String advisorIban = parseAffiliatePaymentField(affiliate, "iban");
        String advisorBic = parseAffiliatePaymentField(affiliate, "bic");
        String advisorAccountHolder = parseAffiliatePaymentField(affiliate, "account_holder");
        if (advisorAccountHolder.isBlank()) advisorAccountHolder = advisorName;

        return template
                .replace("{{advisorName}}", escapeHtmlEmail(advisorName))
                .replace("{{advisorAddress}}", escapeHtmlEmail(advisorAddress))
                .replace("{{advisorEmail}}", escapeHtmlEmail(advisorEmail))
                .replace("{{advisorPhone}}", escapeHtmlEmail(advisorPhone))
                .replace("{{advisorTaxNumber}}", escapeHtmlEmail(tax))
                .replace("{{advisorIban}}", escapeHtmlEmail(advisorIban))
                .replace("{{advisorBic}}", escapeHtmlEmail(advisorBic))
                .replace("{{advisorAccountHolder}}", escapeHtmlEmail(advisorAccountHolder))
                .replace("{{buyerCompanyName}}", escapeHtmlEmail(buyerCompanyName))
                .replace("{{buyerAddress}}", escapeHtmlEmail(buyerAddress))
                .replace("{{buyerVatId}}", escapeHtmlEmail(buyerVatId))
                .replace("{{buyerTaxNumber}}", escapeHtmlEmail(buyerTaxNumber))
                .replace("{{gutschriftNr}}", escapeHtmlEmail(gutschriftNr))
                .replace("{{invoiceNumber}}", escapeHtmlEmail(gutschriftNr))
                .replace("{{documentNumber}}", escapeHtmlEmail(gutschriftNr))
                .replace("{{issueDate}}", escapeHtmlEmail(kind == DocumentKind.RECHNUNG
                        ? LocalDate.now(BERLIN_ZONE).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                        : created))
                .replace("{{paymentId}}", escapeHtmlEmail(paymentId))
                .replace("{{periodLabel}}", escapeHtmlEmail(periodLabel))
                .replace("{{vatLine}}", escapeHtmlEmail(vatLine))
                .replace("{{vatAmount}}", escapeHtmlEmail(vatAmountFormatted))
                .replace("{{grossAmount}}", escapeHtmlEmail(grossAmountFormatted))
                .replace("{{created}}", escapeHtmlEmail(created))
                .replace("{{amount}}", escapeHtmlEmail(amount))
                .replace("{{currency}}", escapeHtmlEmail(currency));
    }

    // LEGACY-RECHNUNG: Vorlage für Altfälle. Layoutgleich zur Gutschrift, aber Rechnungs-Wortlaut,
    // ohne § 14 / Widerspruchshinweis, mit getrenntem Rechnungs- und Auszahlungsdatum.
    private static String getDefaultRechnungPdfViewHtmlTemplate() {
        return """
                <!doctype html>
                <html lang="de">
                <head>
                  <meta charset="UTF-8" />
                  <style>
                    @page { size: A4; margin: 18mm 16mm; }
                    body { font-family: Arial, sans-serif; color:#111827; font-size:10px; line-height:1.35; }
                    table { border-collapse: collapse; }
                    .muted { color:#5f6b7a; }
                    .rule { border-top:1px solid #d6dbe2; }
                  </style>
                </head>
                <body>
                  <table style="width:100%;margin-bottom:42px;">
                    <tr>
                      <td style="vertical-align:top;width:48%;">
                        <img src="{{vemminaLogoDataUri}}" alt="VEMMiNA" style="width:150px;height:auto;" />
                      </td>
                      <td style="vertical-align:top;text-align:right;width:52%;font-size:10px;">
                        <div style="font-weight:700;">{{buyerCompanyName}}</div>
                        <div>{{buyerAddress}}</div>
                        <div style="margin-top:8px;font-weight:700;">Rechnungsempf&auml;ngerin (Leistungsempf&auml;ngerin)</div>
                        <div>USt-IdNr: {{buyerVatId}}</div>
                        <div>Steuernummer: {{buyerTaxNumber}}</div>
                      </td>
                    </tr>
                  </table>

                  <div class="muted" style="font-size:8px;border-bottom:1px solid #cfd5dd;padding-bottom:4px;width:64%;margin-bottom:10px;">
                    {{buyerCompanyName}}, {{buyerAddress}}
                  </div>

                  <table style="width:100%;margin-bottom:42px;">
                    <tr>
                      <td style="vertical-align:top;width:58%;font-size:10px;">
                        <div style="font-weight:700;">{{advisorName}}</div>
                        <div>{{advisorAddress}}</div>
                        <div style="margin-top:8px;" class="muted">Rechnungsstellerin (Leistungserbringerin)</div>
                        <div class="muted">E-Mail: {{advisorEmail}}</div>
                        <div class="muted">Telefon: {{advisorPhone}}</div>
                        <div class="muted">Steuernummer: {{advisorTaxNumber}}</div>
                      </td>
                      <td style="vertical-align:top;width:42%;">
                        <table style="width:100%;font-size:10px;">
                          <tr><td class="muted" style="padding:0 0 5px 0;">Rechnungsnummer</td><td style="text-align:right;padding:0 0 5px 0;">{{documentNumber}}</td></tr>
                          <tr><td class="muted" style="padding:0 0 5px 0;">Rechnungsdatum</td><td style="text-align:right;padding:0 0 5px 0;">{{issueDate}}</td></tr>
                          <tr><td class="muted" style="padding:0 0 5px 0;">Auszahlungsdatum</td><td style="text-align:right;padding:0 0 5px 0;">{{created}}</td></tr>
                          <tr><td class="muted" style="padding:0 0 5px 0;">Zahllauf-ID</td><td style="text-align:right;padding:0 0 5px 0;">{{paymentId}}</td></tr>
                          <tr><td class="muted" style="padding:0 0 5px 0;">Leistungszeitraum</td><td style="text-align:right;padding:0 0 5px 0;">{{periodLabel}}</td></tr>
                        </table>
                      </td>
                    </tr>
                  </table>

                  <div style="font-size:18px;margin-bottom:30px;">Rechnung</div>

                  <table style="width:100%;font-size:10px;margin-bottom:34px;">
                    <thead>
                      <tr class="rule">
                        <th style="text-align:left;padding:8px 8px;border-bottom:1px solid #d6dbe2;width:48px;">Pos</th>
                        <th style="text-align:left;padding:8px 8px;border-bottom:1px solid #d6dbe2;">Beschreibung</th>
                        <th style="text-align:right;padding:8px 8px;border-bottom:1px solid #d6dbe2;width:140px;">Betrag</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td style="padding:9px 8px;border-bottom:1px solid #e5e7eb;">1</td>
                        <td style="padding:9px 8px;border-bottom:1px solid #e5e7eb;">Vermittlungsprovision - Provisionszeitraum {{periodLabel}}</td>
                        <td style="padding:9px 8px;border-bottom:1px solid #e5e7eb;text-align:right;">{{amount}} ({{currency}})</td>
                      </tr>
                    </tbody>
                  </table>

                  <table style="width:42%;margin-left:58%;font-size:10px;margin-bottom:28px;">
                    <tr><td style="padding:4px 0;">Zwischensumme (netto)</td><td style="padding:4px 0;text-align:right;">{{amount}}</td></tr>
                    <tr><td style="padding:4px 0;">{{vatLine}}</td><td style="padding:4px 0;text-align:right;">{{vatAmount}}</td></tr>
                    <tr><td style="padding:4px 0;border-top:1px solid #d6dbe2;">Gesamtsumme</td><td style="padding:4px 0;border-top:1px solid #d6dbe2;text-align:right;">{{grossAmount}}</td></tr>
                    <tr><td style="padding:5px 0;font-weight:700;">Auszahlungsbetrag</td><td style="padding:5px 0;text-align:right;font-weight:700;">{{grossAmount}}</td></tr>
                  </table>

                  <div style="margin-bottom:20px;">
                    <div>Zahlbar sofort ohne Abzug auf das unten genannte Konto.</div>
                    <div>Diese Rechnung wurde maschinell erstellt und ist ohne Unterschrift g&uuml;ltig.</div>
                  </div>

                  <div style="margin-top:18px;border-top:1px solid #d6dbe2;padding-top:10px;font-size:10px;">
                    <div style="font-weight:700;">Bankverbindung der Rechnungsstellerin</div>
                    <div>Kontoinhaber: {{advisorAccountHolder}}</div>
                    <div>IBAN: {{advisorIban}}</div>
                    <div>BIC: {{advisorBic}}</div>
                  </div>

                  <div style="margin-top:42px;border-top:1px solid #d6dbe2;padding-top:10px;text-align:center;font-size:9px;line-height:1.45;" class="muted">
                    <div>{{buyerCompanyName}}</div>
                    <div>{{buyerAddress}}</div>
                    <div>USt-IdNr: {{buyerVatId}}</div>
                    <div>Steuernummer: {{buyerTaxNumber}}</div>
                  </div>
                </body>
                </html>
                """.replace("{{vemminaLogoDataUri}}", VEMMINA_LOGO_DATA_URI);
    }

    private static String getDefaultEInvoicePdfViewHtmlTemplate() {
        return """
                <!doctype html>
                <html lang="de">
                <head>
                  <meta charset="UTF-8" />
                  <style>
                    @page { size: A4; margin: 18mm 16mm; }
                    body { font-family: Arial, sans-serif; color:#111827; font-size:10px; line-height:1.35; }
                    table { border-collapse: collapse; }
                    .muted { color:#5f6b7a; }
                    .rule { border-top:1px solid #d6dbe2; }
                  </style>
                </head>
                <body>
                  <table style="width:100%;margin-bottom:42px;">
                    <tr>
                      <td style="vertical-align:top;width:48%;">
                        <img src="{{vemminaLogoDataUri}}" alt="VEMMiNA" style="width:150px;height:auto;" />
                      </td>
                      <td style="vertical-align:top;text-align:right;width:52%;font-size:10px;">
                        <div style="font-weight:700;">{{buyerCompanyName}}</div>
                        <div>{{buyerAddress}}</div>
                        <div style="margin-top:8px;font-weight:700;">Gutschriftausstellerin (Leistungsempf&auml;ngerin)</div>
                        <div>USt-IdNr: {{buyerVatId}}</div>
                        <div>Steuernummer: {{buyerTaxNumber}}</div>
                      </td>
                    </tr>
                  </table>

                  <div class="muted" style="font-size:8px;border-bottom:1px solid #cfd5dd;padding-bottom:4px;width:64%;margin-bottom:10px;">
                    {{buyerCompanyName}}, {{buyerAddress}}
                  </div>

                  <table style="width:100%;margin-bottom:42px;">
                    <tr>
                      <td style="vertical-align:top;width:58%;font-size:10px;">
                        <div style="font-weight:700;">{{advisorName}}</div>
                        <div>{{advisorAddress}}</div>
                        <div style="margin-top:8px;" class="muted">Gutschriftempf&auml;ngerin (Leistungserbringerin)</div>
                        <div class="muted">E-Mail: {{advisorEmail}}</div>
                        <div class="muted">Telefon: {{advisorPhone}}</div>
                        <div class="muted">Steuernummer: {{advisorTaxNumber}}</div>
                      </td>
                      <td style="vertical-align:top;width:42%;">
                        <table style="width:100%;font-size:10px;">
                          <tr><td class="muted" style="padding:0 0 5px 0;">Gutschriftnummer</td><td style="text-align:right;padding:0 0 5px 0;">{{gutschriftNr}}</td></tr>
                          <tr><td class="muted" style="padding:0 0 5px 0;">Datum</td><td style="text-align:right;padding:0 0 5px 0;">{{created}}</td></tr>
                          <tr><td class="muted" style="padding:0 0 5px 0;">Zahllauf-ID</td><td style="text-align:right;padding:0 0 5px 0;">{{paymentId}}</td></tr>
                          <tr><td class="muted" style="padding:0 0 5px 0;">Leistungszeitraum</td><td style="text-align:right;padding:0 0 5px 0;">{{periodLabel}}</td></tr>
                        </table>
                      </td>
                    </tr>
                  </table>

                  <div style="font-size:18px;margin-bottom:30px;">Gutschrift</div>

                  <table style="width:100%;font-size:10px;margin-bottom:34px;">
                    <thead>
                      <tr class="rule">
                        <th style="text-align:left;padding:8px 8px;border-bottom:1px solid #d6dbe2;width:48px;">Pos</th>
                        <th style="text-align:left;padding:8px 8px;border-bottom:1px solid #d6dbe2;">Beschreibung</th>
                        <th style="text-align:right;padding:8px 8px;border-bottom:1px solid #d6dbe2;width:140px;">Betrag</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td style="padding:9px 8px;border-bottom:1px solid #e5e7eb;">1</td>
                        <td style="padding:9px 8px;border-bottom:1px solid #e5e7eb;">Vermittlungsprovision - Provisionszeitraum {{periodLabel}}</td>
                        <td style="padding:9px 8px;border-bottom:1px solid #e5e7eb;text-align:right;">{{amount}} ({{currency}})</td>
                      </tr>
                    </tbody>
                  </table>

                  <table style="width:42%;margin-left:58%;font-size:10px;margin-bottom:28px;">
                    <tr><td style="padding:4px 0;">Zwischensumme (netto)</td><td style="padding:4px 0;text-align:right;">{{amount}}</td></tr>
                    <tr><td style="padding:4px 0;">{{vatLine}}</td><td style="padding:4px 0;text-align:right;">{{vatAmount}}</td></tr>
                    <tr><td style="padding:4px 0;border-top:1px solid #d6dbe2;">Gesamtsumme</td><td style="padding:4px 0;border-top:1px solid #d6dbe2;text-align:right;">{{grossAmount}}</td></tr>
                    <tr><td style="padding:5px 0;font-weight:700;">Auszahlungsbetrag</td><td style="padding:5px 0;text-align:right;font-weight:700;">{{grossAmount}}</td></tr>
                  </table>

                  <div style="margin-bottom:20px;">
                    <div>Gutschrift gem&auml;&szlig; &sect; 14 Abs. 2 Satz 5 UStG.</div>
                    <div>Bitte pr&uuml;fen Sie diese Gutschrift. Die Gutschrift verliert ihre Wirkung als Rechnung, soweit ihr widersprochen wird.</div>
                  </div>

                  <div style="margin-top:18px;border-top:1px solid #d6dbe2;padding-top:10px;font-size:10px;">
                    <div style="font-weight:700;">Bankverbindung der Gutschriftempf&auml;ngerin</div>
                    <div>Kontoinhaber: {{advisorAccountHolder}}</div>
                    <div>IBAN: {{advisorIban}}</div>
                    <div>BIC: {{advisorBic}}</div>
                  </div>

                  <div style="margin-top:42px;border-top:1px solid #d6dbe2;padding-top:10px;text-align:center;font-size:9px;line-height:1.45;" class="muted">
                    <div>{{buyerCompanyName}}</div>
                    <div>{{buyerAddress}}</div>
                    <div>USt-IdNr: {{buyerVatId}}</div>
                    <div>Steuernummer: {{buyerTaxNumber}}</div>
                  </div>
                </body>
                </html>
                """.replace("{{vemminaLogoDataUri}}", VEMMINA_LOGO_DATA_URI);
    }

    private static String buildValidationReminderMailBody(String advisorName, String missingFields) {
        String name = (advisorName == null || advisorName.isBlank()) ? "liebe Beraterin" : ("liebe " + advisorName.trim());
        String fields = (missingFields == null || missingFields.isBlank()) ? "einige Stammdaten" : missingFields;
        return ("Hallo " + name + "\n\n" +
                "für die vollständige Pflege Ihrer Stammdaten fehlen uns noch folgende Angaben:\n" +
                fields + "\n\n" +
                "Bitte senden Sie uns diese Informationen kurz per E-Mail zurück, damit wir Ihre Stammdaten vervollständigen können.\n\n" +
                "Vielen Dank und viele Grüße\nIhr VEMMiNA Team");
    }

    private static String buildValidationReminderMailHtml(String advisorName, String missingFields, String configuredTemplateHtml) {
        String name = (advisorName == null || advisorName.isBlank()) ? "Beraterin" : advisorName.trim();
        String fields = (missingFields == null || missingFields.isBlank()) ? "-" : missingFields;
        String template = (configuredTemplateHtml == null || configuredTemplateHtml.isBlank()) ? getDefaultValidationReminderHtmlTemplate() : configuredTemplateHtml;
        return template
                .replace("{{salutationName}}", escapeHtmlEmail(name))
                .replace("{{missingFields}}", escapeHtmlEmail(fields).replace("\n", "<br/>"));
    }

    private static String getDefaultValidationReminderHtmlTemplate() {

        return """
                <!doctype html>
                <html lang="de"><body style="font-family:Arial,sans-serif;background:#f8fafc;color:#1f2937;padding:18px;">
                <div style="max-width:680px;margin:0 auto;background:#fff;border:1px solid #dbe3ef;border-radius:12px;padding:20px;">
                  <h2 style="margin-top:0;color:#1e3a8a;">Bitte fehlende Stammdaten ergänzen</h2>
                  <p>Hallo {{salutationName}},</p>
                  <p>für die vollständige Pflege Ihrer Stammdaten fehlen uns noch folgende Angaben:</p>
                  <div style="background:#fff1f2;border:1px solid #fecdd3;border-radius:8px;padding:10px;white-space:pre-wrap;">{{missingFields}}</div>
                  <p>Bitte senden Sie uns diese Informationen kurz per E-Mail zurück, damit wir Ihre Stammdaten vervollständigen können.</p>
                  <p>Vielen Dank und viele Grüße<br/><b>Ihr VEMMiNA Team</b></p>
                </div>
                </body></html>
                """;
    }

    private static String getDefaultLeaderWeeklyReportHtmlTemplate() {
        return """
                <!doctype html>
                <html lang="de">
                <body style="margin:0;background:#f5f7fb;color:#1f2937;font-family:Arial,Helvetica,sans-serif;">
                  <table role="presentation" cellpadding="0" cellspacing="0" width="100%" style="border-collapse:collapse;background:#f5f7fb;">
                    <tr>
                      <td style="padding:24px 12px;">
                        <table role="presentation" cellpadding="0" cellspacing="0" width="100%" style="max-width:760px;margin:0 auto;background:#ffffff;border:1px solid #d9e2ef;border-collapse:collapse;">
                          <tr>
                            <td style="padding:22px 26px;border-bottom:1px solid #e2e8f0;">
                              <img src="{{vemminaLogoDataUri}}" alt="VEMMiNA" style="height:38px;display:block;margin-bottom:14px;" />
                              <div style="font-size:13px;color:#64748b;">Führungskräfte-Neukundenreport</div>
                              <h1 style="margin:4px 0 0 0;font-size:24px;line-height:1.25;color:#0f172a;">Hallo {{leaderName}},</h1>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:22px 26px;">
                              <p style="margin:0 0 16px 0;font-size:15px;line-height:1.6;color:#334155;">Hier ist Ihre persönliche Wochenübersicht für {{periodLabel}}. Gezählt werden Ihre eigenen Neukundenbestellungen und die Bestellungen Ihrer kompletten freigegebenen Downline.</p>
                              <table role="presentation" cellpadding="0" cellspacing="0" width="100%" style="border-collapse:collapse;margin:0 0 18px 0;">
                                <tr>
                                  <td style="padding:12px;border:1px solid #d9e2ef;background:#f8fafc;width:33%;">
                                    <div style="font-size:12px;color:#64748b;">Letzte 7 Tage</div>
                                    <div style="font-size:28px;font-weight:700;color:#0f766e;">{{currentWeekNewCustomers}}</div>
                                  </td>
                                  <td style="padding:12px;border:1px solid #d9e2ef;background:#ffffff;width:33%;">
                                    <div style="font-size:12px;color:#64748b;">Vorwoche</div>
                                    <div style="font-size:24px;font-weight:700;color:#334155;">{{previousWeekNewCustomers}}</div>
                                  </td>
                                  <td style="padding:12px;border:1px solid #d9e2ef;background:#ffffff;width:34%;">
                                    <div style="font-size:12px;color:#64748b;">2. Vorwoche</div>
                                    <div style="font-size:24px;font-weight:700;color:#334155;">{{secondPreviousWeekNewCustomers}}</div>
                                  </td>
                                </tr>
                              </table>
                              <table role="presentation" cellpadding="0" cellspacing="0" width="100%" style="border-collapse:collapse;margin:0 0 18px 0;">
                                <tr>
                                  <td style="padding:12px;border:1px solid #d9e2ef;">
                                    <strong>Status:</strong> {{statusLabel}}<br/>
                                    <span style="color:#475569;">{{actionText}}</span>
                                  </td>
                                  <td style="padding:12px;border:1px solid #d9e2ef;">
                                    <strong>Monatsfortschritt {{monthKey}}:</strong><br/>
                                    {{monthNewCustomers}} von {{monthlyTarget}} Neukundenbestellungen ({{monthProgressPercent}})
                                  </td>
                                </tr>
                              </table>
                              <h2 style="font-size:16px;margin:20px 0 8px 0;color:#0f172a;">Team-Beiträge</h2>
                              <table cellpadding="0" cellspacing="0" width="100%" style="border-collapse:collapse;font-size:14px;">
                                <thead>
                                  <tr>
                                    <th align="left" style="padding:8px;border:1px solid #cbd5e1;background:#edf3fb;">Beraterin</th>
                                    <th align="right" style="padding:8px;border:1px solid #cbd5e1;background:#edf3fb;">Letzte 7 Tage</th>
                                    <th align="right" style="padding:8px;border:1px solid #cbd5e1;background:#edf3fb;">Vorwoche</th>
                                    <th align="right" style="padding:8px;border:1px solid #cbd5e1;background:#edf3fb;">2. Vorwoche</th>
                                    <th align="right" style="padding:8px;border:1px solid #cbd5e1;background:#edf3fb;">Neukundenumsatz</th>
                                  </tr>
                                </thead>
                                <tbody>{{teamRows}}</tbody>
                              </table>
                              <p style="margin:18px 0 0 0;font-size:13px;line-height:1.5;color:#64748b;">Empfängermodus: {{recipientMode}}. Diese Auswertung enthält nur aggregierte Teamwerte und keine Kundendaten.</p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.replace("{{vemminaLogoDataUri}}", VEMMINA_LOGO_DATA_URI);
    }

    private static String escapeHtmlEmail(String value) {
        String safe = value == null ? "" : value;
        return safe.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String euroStatic(double value) {
        return String.format(java.util.Locale.GERMANY, "%.2f €", value);
    }

    private static String euroPdf(double value) {
        return String.format(java.util.Locale.GERMANY, "%.2f EUR", value);
    }


    private static Map<String, String> parseQueryParams(URI uri) {
        Map<String, String> query = new LinkedHashMap<>();
        if (uri == null || uri.getRawQuery() == null || uri.getRawQuery().isBlank()) return query;
        for (String part : uri.getRawQuery().split("&")) {
            if (part == null || part.isBlank()) continue;
            int i = part.indexOf('=');
            String k = i >= 0 ? part.substring(0, i) : part;
            String v = i >= 0 ? part.substring(i + 1) : "";
            try {
                k = java.net.URLDecoder.decode(k, StandardCharsets.UTF_8);
                v = java.net.URLDecoder.decode(v, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
            query.put(k, v);
        }
        return query;
    }

    private static double parseDoubleSafeStatic(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) return 0.0;
        try {
            return Double.parseDouble(raw.replace(",", "."));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static String formatDateTimeEuropeBerlinStatic(String input) {
        if (input == null || input.isBlank()) return "";
        try {
            OffsetDateTime dt = OffsetDateTime.parse(input);
            return dt.atZoneSameInstant(ZoneId.of("Europe/Berlin")).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        } catch (Exception ignored) {
            return input;
        }
    }

    private static SmtpConfig resolveSmtpConfig(Properties config) throws IOException {
        String host = firstNonBlank(
                Objects.toString(config.getProperty("smtpHost"), ""),
                System.getenv("GOAFFPRO_SMTP_HOST")
        );
        String portRaw = firstNonBlank(
                Objects.toString(config.getProperty("smtpPort"), ""),
                System.getenv("GOAFFPRO_SMTP_PORT"),
                "587"
        );
        String username = firstNonBlank(
                Objects.toString(config.getProperty("smtpUsername"), ""),
                System.getenv("GOAFFPRO_SMTP_USERNAME")
        );
        String password = firstNonBlank(
                getSecretOrConfig(config, "SMTP_PASSWORD", "smtpPassword", ""),
                System.getenv("GOAFFPRO_SMTP_PASSWORD")
        );
        String tlsRaw = firstNonBlank(
                Objects.toString(config.getProperty("smtpTls"), ""),
                System.getenv("GOAFFPRO_SMTP_TLS"),
                "false"
        );

        System.out.println("[SMTP] resolveSmtpConfig: host='" + host
                + "' port=" + portRaw
                + " username='" + username + "'"
                + " hasPassword=" + !password.isBlank()
                + " tls=" + tlsRaw);

        if (host.isBlank() || username.isBlank() || password.isBlank()) {
            System.err.println("[SMTP] Konfiguration unvollständig"
                    + " (host=" + (host.isBlank() ? "MISSING" : "ok")
                    + ", username=" + (username.isBlank() ? "MISSING" : "ok")
                    + ", password=" + (password.isBlank() ? "MISSING" : "ok") + ")");
            throw new IOException("SMTP-Konfiguration unvollständig. Bitte Host, Benutzername und Passwort in den Einstellungen setzen.");
        }

        int port;
        try {
            port = Integer.parseInt(portRaw);
        } catch (Exception e) {
            port = 587;
        }
        boolean tls = Boolean.parseBoolean(tlsRaw);
        return new SmtpConfig(host, port, username, password, tls);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private static class SmtpConfig {
        final String host;
        final int port;
        final String username;
        final String password;
        final boolean tls;

        SmtpConfig(String host, int port, String username, String password, boolean tls) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
            this.tls = tls;
        }
    }

    private static JsonNode requestJson(String apiUrl, String apiKey) throws Exception {
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("x-goaffpro-access-token", apiKey);

        int code = connection.getResponseCode();
        InputStream bodyStream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String body = new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8);
        if (code != 200) {
            throw new IOException("GoAffPro API Fehler (" + code + "): " + body);
        }
        return OBJECT_MAPPER.readTree(body);
    }

    // Die Konfiguration lebt in zwei geteilten Dateien und wird im Read-Modify-Write-Zyklus
    // fortgeschrieben. Seit der HTTP-Server einen Threadpool nutzt, muss CONFIG_LOCK diese
    // Zyklen klammern - sonst schreiben parallele Anfragen einander Schlüssel weg, im
    // schlimmsten Fall die Belegnummern-Zähler. Der Monitor ist reentrant, verschachtelte
    // Aufrufe (persistSettings -> storeConfig) sind also unbedenklich.
    private static Properties loadConfig() throws IOException {
        synchronized (CONFIG_LOCK) {
            Properties properties = new Properties();
            if (!Files.exists(CONFIG_PATH)) {
                return properties;
            }
            try (InputStream is = Files.newInputStream(CONFIG_PATH)) {
                properties.load(is);
            }
            return properties;
        }
    }

    private static Properties loadConfigWithUiSettings() throws IOException {
        synchronized (CONFIG_LOCK) {
            Properties config = loadConfig();
            Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
            mergeUiSettingsIntoConfig(config, uiSettings);
            return config;
        }
    }

    private static void storeConfig(Properties properties) throws IOException {
        synchronized (CONFIG_LOCK) {
            storeConfigUnlocked(properties);
        }
    }

    private static void storeConfigUnlocked(Properties properties) throws IOException {
        Properties forStore = new Properties();
        forStore.putAll(properties);
        for (String[] mapping : SECRET_ENV_MAPPINGS) {
            String envName = mapping[0];
            String configKey = mapping[1];
            String envValue = getEnv(envName);
            if (envValue != null && !envValue.isBlank()) {
                forStore.remove(configKey);
            }
        }
        Path parent = CONFIG_PATH.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream os = Files.newOutputStream(CONFIG_PATH)) {
            forStore.store(os, "Updated by WebUiServer");
        }
    }

    // ══════════════ LEGACY-RECHNUNG (Altfälle bis 31.12.2025) ─ BEGIN ══════════════
    // Provisionen mit einem Transaktionsdatum vor dem Stichtag werden als RECHNUNG gegen die
    // Alt-Gesellschaft ausgestellt, ab Stichtag als GUTSCHRIFT (Regelfall). Rein datumsgesteuert,
    // kein manueller Schalter.
    // ENTFERNEN: diesen Block löschen, dann alle Referenzen auf DocumentKind sowie die
    // Config-Keys mit Präfix "legacy" und "rechnung" abräumen.

    private enum DocumentKind {
        GUTSCHRIFT(
                "GS", "gutschriftCounter", "gutschriftCounterYear",
                "389", "gutschrift",
                "Gutschrift", "Gutschriftnummer",
                true,
                "eInvoiceBuyer", "S+R linear technology gmbh",
                "nachweisFirmenname", "S+R Linear Technology GmbH",
                "eInvoicePdfTemplateHtml", "emailTemplateHtml"),
        RECHNUNG(
                "RE", "rechnungCounter", "rechnungCounterYear",
                "380", "rechnung",
                "Rechnung", "Rechnungsnummer",
                false,
                "legacyBuyer", "VEMMiNA Qualitäts- Haushaltsprodukte GmbH",
                "legacyNachweisFirmenname", "VEMMiNA Qualitäts- Haushaltsprodukte GmbH",
                "eInvoicePdfTemplateHtmlRechnung", "emailTemplateHtmlRechnung");

        final String numberPrefix;
        final String counterKey;
        final String counterYearKey;
        final String zugferdTypeCode;
        final String filePrefix;
        final String label;
        final String numberLabel;
        final boolean selfBilling;
        final String buyerKeyPrefix;
        final String defaultBuyerName;
        final String providerNameKey;
        final String defaultProviderName;
        final String pdfTemplateKey;
        final String mailTemplateKey;

        DocumentKind(String numberPrefix, String counterKey, String counterYearKey,
                     String zugferdTypeCode, String filePrefix,
                     String label, String numberLabel, boolean selfBilling,
                     String buyerKeyPrefix, String defaultBuyerName,
                     String providerNameKey, String defaultProviderName,
                     String pdfTemplateKey, String mailTemplateKey) {
            this.numberPrefix = numberPrefix;
            this.counterKey = counterKey;
            this.counterYearKey = counterYearKey;
            this.zugferdTypeCode = zugferdTypeCode;
            this.filePrefix = filePrefix;
            this.label = label;
            this.numberLabel = numberLabel;
            this.selfBilling = selfBilling;
            this.buyerKeyPrefix = buyerKeyPrefix;
            this.defaultBuyerName = defaultBuyerName;
            this.providerNameKey = providerNameKey;
            this.defaultProviderName = defaultProviderName;
            this.pdfTemplateKey = pdfTemplateKey;
            this.mailTemplateKey = mailTemplateKey;
        }

        String wireValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        static DocumentKind fromWireValue(String raw, DocumentKind fallback) {
            if (raw == null) return fallback;
            String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
            if ("rechnung".equals(value)) return RECHNUNG;
            if ("gutschrift".equals(value)) return GUTSCHRIFT;
            return fallback;
        }
    }

    /** Firmen-/Gegenparteidaten je Dokumentart: Key-Präfix + Suffix ergibt den bestehenden Config-Key. */
    private static String buyerProperty(Properties config, DocumentKind kind, String suffix, String fallback) {
        return Objects.toString(config.getProperty(kind.buyerKeyPrefix + suffix), fallback).trim();
    }

    private static String documentProviderName(Properties config, DocumentKind kind) {
        String value = Objects.toString(config.getProperty(kind.providerNameKey), "").trim();
        return value.isBlank() ? kind.defaultProviderName : value;
    }

    private static String defaultPdfViewTemplate(DocumentKind kind) {
        return kind == DocumentKind.RECHNUNG
                ? getDefaultRechnungPdfViewHtmlTemplate()
                : getDefaultEInvoicePdfViewHtmlTemplate();
    }

    private static String defaultMailTemplate(DocumentKind kind) {
        return kind == DocumentKind.RECHNUNG
                ? getDefaultRechnungMailHtmlTemplate()
                : getDefaultInvoiceMailHtmlTemplate();
    }

    private static String documentPdfTemplateHtml(Properties config, DocumentKind kind) {
        String value = Objects.toString(config.getProperty(kind.pdfTemplateKey), "").trim();
        return value.isBlank() ? defaultPdfViewTemplate(kind) : value;
    }

    private static String documentMailTemplateHtml(Properties config, DocumentKind kind) {
        String value = Objects.toString(config.getProperty(kind.mailTemplateKey), "").trim();
        return value.isBlank() ? defaultMailTemplate(kind) : value;
    }

    private static String documentMailSubject(DocumentKind kind, String documentNumber, String periodLabel) {
        return kind == DocumentKind.RECHNUNG
                ? "Ihre VEMMiNA-Provisionsrechnung " + documentNumber + " – " + periodLabel
                : "Ihre VEMMiNA-Provisionsgutschrift " + documentNumber + " – " + periodLabel;
    }

    private static final String DEFAULT_RECHNUNG_CUTOFF_DATE = "2026-01-01";
    private static final String DEFAULT_LEGACY_BUYER_NAME = "VEMMiNA Qualitäts- Haushaltsprodukte GmbH";

    /** Akzeptiert nur ein gültiges ISO-Datum (yyyy-MM-dd); sonst greift der Fallback. */
    private static String normalizeIsoDate(String raw, String fallback) {
        String value = Objects.toString(raw, "").trim();
        if (value.isBlank()) return fallback;
        try {
            return LocalDate.parse(value).toString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String rechnungCutoffDateRaw(Properties config) {
        String raw = Objects.toString(config.getProperty("rechnungCutoffDate"), "").trim();
        return raw.isBlank() ? DEFAULT_RECHNUNG_CUTOFF_DATE : raw;
    }

    /** Stichtag als Tagesbeginn in Europe/Berlin. Vergleich später über Instant (offset-korrekt). */
    private static Instant resolveRechnungCutoffInstant(Properties config) {
        LocalDate date;
        try {
            date = LocalDate.parse(rechnungCutoffDateRaw(config));
        } catch (Exception ignored) {
            date = LocalDate.parse(DEFAULT_RECHNUNG_CUTOFF_DATE);
        }
        return date.atStartOfDay(BERLIN_ZONE).toInstant();
    }

    /** Ergebnis der Stichtagsprüfung. mixed == true bedeutet: kein Dokument, keine Nummer. */
    private record DocumentKindDecision(DocumentKind kind, boolean mixed, String source,
                                        int beforeCutoffCount, int fromCutoffCount,
                                        double beforeCutoffAmount, double fromCutoffAmount) {
    }

    private static DocumentKindDecision resolveDocumentKind(JsonNode payment, Properties config) {
        Instant cutoff = resolveRechnungCutoffInstant(config);
        JsonNode transactions = payment != null ? payment.get("transactions") : null;

        int before = 0;
        int from = 0;
        double beforeSum = 0.0;
        double fromSum = 0.0;
        if (transactions != null && transactions.isArray()) {
            for (JsonNode tx : transactions) {
                Instant ts;
                try {
                    ts = OffsetDateTime.parse(asText(tx, "created_at")).toInstant();
                } catch (Exception ignored) {
                    continue; // undatierte Transaktionen zählen nicht mit (spiegelt buildPaymentPeriodLabel)
                }
                double amount = parseDoubleSafeStatic(asText(tx, "amount"));
                if (ts.isBefore(cutoff)) {
                    before++;
                    beforeSum += amount;
                } else {
                    from++;
                    fromSum += amount;
                }
            }
        }

        if (before > 0 && from > 0) {
            return new DocumentKindDecision(null, true, "transactions", before, from, beforeSum, fromSum);
        }
        if (before > 0) {
            return new DocumentKindDecision(DocumentKind.RECHNUNG, false, "transactions", before, 0, beforeSum, 0.0);
        }
        if (from > 0) {
            return new DocumentKindDecision(DocumentKind.GUTSCHRIFT, false, "transactions", 0, from, 0.0, fromSum);
        }

        // Kein auswertbares Transaktionsdatum -> Rückfall auf das Zahllauf-Datum.
        try {
            Instant paid = OffsetDateTime.parse(asText(payment, "created_at")).toInstant();
            DocumentKind kind = paid.isBefore(cutoff) ? DocumentKind.RECHNUNG : DocumentKind.GUTSCHRIFT;
            return new DocumentKindDecision(kind, false, "paymentCreatedAt", 0, 0, 0.0, 0.0);
        } catch (Exception ignored) {
            return new DocumentKindDecision(DocumentKind.GUTSCHRIFT, false, "default", 0, 0, 0.0, 0.0);
        }
    }

    private static String generateNextDocumentNumber(Properties config, DocumentKind kind) throws IOException {
        synchronized (CONFIG_LOCK) {
            int year = LocalDate.now().getYear();
            int storedYear = 0;
            try { storedYear = Integer.parseInt(config.getProperty(kind.counterYearKey, "0")); } catch (NumberFormatException ignored) {}
            int counter;
            if (storedYear == year) {
                try { counter = Integer.parseInt(config.getProperty(kind.counterKey, "0")); } catch (NumberFormatException ignored) { counter = 0; }
                counter++;
            } else {
                counter = 1;
            }
            config.setProperty(kind.counterKey, String.valueOf(counter));
            config.setProperty(kind.counterYearKey, String.valueOf(year));
            storeConfig(config);
            return String.format("%s-%d-%04d", kind.numberPrefix, year, counter);
        }
    }
    // ══════════════ LEGACY-RECHNUNG ─ END ══════════════

    private static String generateNextGutschriftNumber(Properties config) throws IOException {
        return generateNextDocumentNumber(config, DocumentKind.GUTSCHRIFT);
    }

    private static double calculateVat(double netAmount, boolean isKleinunternehmer) {
        return isKleinunternehmer ? 0.0 : netAmount * 0.19;
    }

    private static final String[][] SECRET_ENV_MAPPINGS = {
            {"GOAFFPRO_API_KEY", "goaffproAPIKey"},
            {"SMTP_PASSWORD", "smtpPassword"}
    };

    private static final Map<String, String> DOT_ENV = loadDotEnvFile();

    private static Map<String, String> loadDotEnvFile() {
        Map<String, String> result = new HashMap<>();
        Path envFile = Paths.get(".env");
        if (!Files.exists(envFile)) return result;
        try {
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq <= 0) continue;
                result.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
            }
        } catch (IOException ignored) {}
        return result;
    }

    private static String getEnv(String name) {
        String v = System.getenv(name);
        if (v != null && !v.isBlank()) return v;
        return DOT_ENV.get(name);
    }

    private static String getSecretOrConfig(Properties config, String envName, String configKey, String defaultValue) {
        String envValue = getEnv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return config.getProperty(configKey, defaultValue);
    }

    private static Path resolveSettingsDirectory(Properties config) {
        String configured = Objects.toString(config.getProperty("pdfExportPath"), "").trim();
        String path = configured.isEmpty() ? DEFAULT_PDF_EXPORT_PATH : configured;
        return Paths.get(path).toAbsolutePath();
    }

    private static Path uiSettingsFile(Path directory) {
        return directory.resolve(UI_SETTINGS_FILENAME);
    }

    private static Properties loadUiSettings(Path directory) {
        Properties p = new Properties();
        try {
            Files.createDirectories(directory);
            Path file = uiSettingsFile(directory);
            if (Files.exists(file)) {
                try (InputStream is = Files.newInputStream(file)) {
                    p.load(is);
                }
            }
        } catch (Exception ignored) {
        }
        return p;
    }

    private static void saveUiSettings(Path directory, Properties source) throws IOException {
        synchronized (CONFIG_LOCK) {
            saveUiSettingsUnlocked(directory, source);
        }
    }

    private static void saveUiSettingsUnlocked(Path directory, Properties source) throws IOException {
        Files.createDirectories(directory);
        Properties ui = new Properties();
        ui.setProperty("pdfExportPath", Objects.toString(source.getProperty("pdfExportPath"), directory.toString()));
        ui.setProperty("lastImportedComission", Objects.toString(source.getProperty("lastImportedComission"), "0"));
        ui.setProperty("goaffproAPIKey", Objects.toString(source.getProperty("goaffproAPIKey"), DEFAULT_GOAFFPRO_API_KEY));
        ui.setProperty("contactEmail", Objects.toString(source.getProperty("contactEmail"), ""));
        ui.setProperty("smtpHost", Objects.toString(source.getProperty("smtpHost"), ""));
        ui.setProperty("smtpPort", Objects.toString(source.getProperty("smtpPort"), "587"));
        ui.setProperty("smtpUsername", Objects.toString(source.getProperty("smtpUsername"), ""));
        ui.setProperty("emailBcc", Objects.toString(source.getProperty("emailBcc"), ""));
        ui.setProperty("smtpPassword", Objects.toString(source.getProperty("smtpPassword"), ""));
        ui.setProperty("smtpTls", Objects.toString(source.getProperty("smtpTls"), "false"));
        ui.setProperty("sendEmailsEnabled", Objects.toString(source.getProperty("sendEmailsEnabled"), "true"));
        ui.setProperty("emailRecipientMode", Objects.toString(source.getProperty("emailRecipientMode"), "contact"));
        ui.setProperty("emailTemplateHtml", Objects.toString(source.getProperty("emailTemplateHtml"), ""));
        ui.setProperty("validationReminderTemplateHtml", Objects.toString(source.getProperty("validationReminderTemplateHtml"), ""));
        ui.setProperty("eInvoicePdfTemplateHtml", Objects.toString(source.getProperty("eInvoicePdfTemplateHtml"), ""));
        ui.setProperty("eInvoicePdfTemplateHtmlRechnung", Objects.toString(source.getProperty("eInvoicePdfTemplateHtmlRechnung"), ""));
        ui.setProperty("emailTemplateHtmlRechnung", Objects.toString(source.getProperty("emailTemplateHtmlRechnung"), ""));
        ui.setProperty("leaderWeeklyReportTemplateHtml", Objects.toString(source.getProperty("leaderWeeklyReportTemplateHtml"), ""));
        ui.setProperty("leaderWeeklyMailSchedulerEnabled", Objects.toString(source.getProperty("leaderWeeklyMailSchedulerEnabled"), "false"));
        ui.setProperty("leaderWeeklyMailProductionEnabled", Objects.toString(source.getProperty("leaderWeeklyMailProductionEnabled"), "false"));
        ui.setProperty("leaderWeeklyMailScheduleDay", normalizeLeaderWeeklyMailScheduleDay(Objects.toString(source.getProperty("leaderWeeklyMailScheduleDay"), "")));
        ui.setProperty("leaderWeeklyMailScheduleTime", normalizeLeaderWeeklyMailScheduleTime(Objects.toString(source.getProperty("leaderWeeklyMailScheduleTime"), "")));
        ui.setProperty("leaderWeeklyMailLastSentPeriodKey", Objects.toString(source.getProperty("leaderWeeklyMailLastSentPeriodKey"), ""));
        ui.setProperty("goaffproSyncEnabled", Objects.toString(source.getProperty("goaffproSyncEnabled"), "true"));
        ui.setProperty("goaffproSyncHourlyEnabled", Objects.toString(source.getProperty("goaffproSyncHourlyEnabled"), "false"));
        ui.setProperty("goaffproSyncDeepEnabled", Objects.toString(source.getProperty("goaffproSyncDeepEnabled"), "false"));
        ui.setProperty("goaffproSyncAssetDownloadEnabled", Objects.toString(source.getProperty("goaffproSyncAssetDownloadEnabled"), "true"));
        ui.setProperty("goaffproSyncMaxCallsPerHour", normalizePositiveInteger(Objects.toString(source.getProperty("goaffproSyncMaxCallsPerHour"), "60"), "60"));
        ui.setProperty("goaffproSyncSlidingWindowEnabled", Objects.toString(source.getProperty("goaffproSyncSlidingWindowEnabled"), "true"));
        ui.setProperty("goaffproSyncMinCallSpacingMs", normalizeNonNegativeInteger(Objects.toString(source.getProperty("goaffproSyncMinCallSpacingMs"), "1500"), "1500"));
        ui.setProperty("goaffproSyncDownloadSkipExistingEnabled", Objects.toString(source.getProperty("goaffproSyncDownloadSkipExistingEnabled"), "true"));
        ui.setProperty("goaffproSyncDeltaDownloadsEnabled", Objects.toString(source.getProperty("goaffproSyncDeltaDownloadsEnabled"), "false"));
        ui.setProperty("goaffproSyncDeltaLookbackDays", normalizePositiveInteger(Objects.toString(source.getProperty("goaffproSyncDeltaLookbackDays"), "14"), "14"));
        ui.setProperty("goaffproSyncMinFreeBytes", normalizePositiveLong(Objects.toString(source.getProperty("goaffproSyncMinFreeBytes"), String.valueOf(512L * 1024L * 1024L)), String.valueOf(512L * 1024L * 1024L)));
        ui.setProperty("goaffproSyncDataPath", Objects.toString(source.getProperty("goaffproSyncDataPath"), GoAffProSyncService.resolveDataDir(source).toString()));
        ui.setProperty("eInvoiceEnabled", Objects.toString(source.getProperty("eInvoiceEnabled"), "true"));
        ui.setProperty("eInvoiceAttachAndStoreEnabled", Objects.toString(source.getProperty("eInvoiceAttachAndStoreEnabled"), "true"));
        ui.setProperty("eInvoiceBuyerName", Objects.toString(source.getProperty("eInvoiceBuyerName"), "S+R linear technology gmbh"));
        ui.setProperty("eInvoiceBuyerStreet", Objects.toString(source.getProperty("eInvoiceBuyerStreet"), ""));
        ui.setProperty("eInvoiceBuyerZip", Objects.toString(source.getProperty("eInvoiceBuyerZip"), ""));
        ui.setProperty("eInvoiceBuyerCity", Objects.toString(source.getProperty("eInvoiceBuyerCity"), ""));
        ui.setProperty("eInvoiceBuyerCountry", Objects.toString(source.getProperty("eInvoiceBuyerCountry"), "DE"));
        ui.setProperty("eInvoiceBuyerVatId", Objects.toString(source.getProperty("eInvoiceBuyerVatId"), ""));
        ui.setProperty("eInvoiceBuyerTaxNumber", Objects.toString(source.getProperty("eInvoiceBuyerTaxNumber"), ""));
        ui.setProperty("eInvoiceBankIban", Objects.toString(source.getProperty("eInvoiceBankIban"), ""));
        ui.setProperty("eInvoiceBankBic", Objects.toString(source.getProperty("eInvoiceBankBic"), ""));
        ui.setProperty("eInvoiceBankAccountHolder", Objects.toString(source.getProperty("eInvoiceBankAccountHolder"), ""));
        ui.setProperty("eInvoicePaymentTerms", Objects.toString(source.getProperty("eInvoicePaymentTerms"), "Zahlbar sofort ohne Abzug"));
        ui.setProperty("nachweisFirmenname", Objects.toString(source.getProperty("nachweisFirmenname"), "S+R Linear Technology GmbH"));
        ui.setProperty("rechnungCutoffDate", normalizeIsoDate(source.getProperty("rechnungCutoffDate"), DEFAULT_RECHNUNG_CUTOFF_DATE));
        ui.setProperty("legacyBuyerName", Objects.toString(source.getProperty("legacyBuyerName"), DEFAULT_LEGACY_BUYER_NAME));
        ui.setProperty("legacyBuyerStreet", Objects.toString(source.getProperty("legacyBuyerStreet"), ""));
        ui.setProperty("legacyBuyerZip", Objects.toString(source.getProperty("legacyBuyerZip"), ""));
        ui.setProperty("legacyBuyerCity", Objects.toString(source.getProperty("legacyBuyerCity"), ""));
        ui.setProperty("legacyBuyerCountry", Objects.toString(source.getProperty("legacyBuyerCountry"), "DE"));
        ui.setProperty("legacyBuyerVatId", Objects.toString(source.getProperty("legacyBuyerVatId"), ""));
        ui.setProperty("legacyBuyerTaxNumber", Objects.toString(source.getProperty("legacyBuyerTaxNumber"), ""));
        ui.setProperty("legacyNachweisFirmenname", Objects.toString(source.getProperty("legacyNachweisFirmenname"), DEFAULT_LEGACY_BUYER_NAME));
        ui.setProperty(COMMISSION_HISTORY_KEY, String.join(",", getCommissionHistory(source)));
        ui.setProperty(COMMISSION_HISTORY_DATES_KEY, Objects.toString(source.getProperty(COMMISSION_HISTORY_DATES_KEY), ""));
        ui.setProperty(MAIL_LOG_KEY, Objects.toString(source.getProperty(MAIL_LOG_KEY), ""));
        ui.setProperty(REMINDER_LOG_KEY, Objects.toString(source.getProperty(REMINDER_LOG_KEY), ""));
        ui.setProperty(LEADER_WEEKLY_MAIL_LOG_KEY, Objects.toString(source.getProperty(LEADER_WEEKLY_MAIL_LOG_KEY), ""));

        for (String[] mapping : SECRET_ENV_MAPPINGS) {
            String envValue = getEnv(mapping[0]);
            if (envValue != null && !envValue.isBlank()) {
                ui.remove(mapping[1]);
            }
        }

        try (OutputStream os = Files.newOutputStream(uiSettingsFile(directory))) {
            ui.store(os, "GoAffPro UI settings");
        }
    }

    private static void mergeUiSettingsIntoConfig(Properties config, Properties uiSettings) {
        String uiPath = Objects.toString(uiSettings.getProperty("pdfExportPath"), "").trim();
        if (!uiPath.isEmpty()) {
            config.setProperty("pdfExportPath", uiPath);
        } else if (Objects.toString(config.getProperty("pdfExportPath"), "").isBlank()) {
            config.setProperty("pdfExportPath", DEFAULT_PDF_EXPORT_PATH);
        }

        String uiCommission = Objects.toString(uiSettings.getProperty("lastImportedComission"), "").trim();
        if (!uiCommission.isEmpty()) {
            config.setProperty("lastImportedComission", uiCommission);
        }

        String uiHistory = Objects.toString(uiSettings.getProperty(COMMISSION_HISTORY_KEY), "").trim();
        if (!uiHistory.isEmpty()) {
            config.setProperty(COMMISSION_HISTORY_KEY, uiHistory);
        }

        String uiDates = Objects.toString(uiSettings.getProperty(COMMISSION_HISTORY_DATES_KEY), "").trim();
        if (!uiDates.isEmpty()) {
            config.setProperty(COMMISSION_HISTORY_DATES_KEY, uiDates);
        }

        String uiApiKey = Objects.toString(uiSettings.getProperty("goaffproAPIKey"), "").trim();
        if (!uiApiKey.isEmpty()) {
            config.setProperty("goaffproAPIKey", uiApiKey);
        }

        String uiContactEmail = Objects.toString(uiSettings.getProperty("contactEmail"), "").trim();
        if (!uiContactEmail.isEmpty() || config.containsKey("contactEmail")) {
            config.setProperty("contactEmail", uiContactEmail);
        }

        String uiSmtpHost = Objects.toString(uiSettings.getProperty("smtpHost"), "").trim();
        if (!uiSmtpHost.isEmpty()) {
            config.setProperty("smtpHost", uiSmtpHost);
        }
        String uiSmtpPort = Objects.toString(uiSettings.getProperty("smtpPort"), "").trim();
        if (!uiSmtpPort.isEmpty()) {
            config.setProperty("smtpPort", uiSmtpPort);
        }
        String uiSmtpUsername = Objects.toString(uiSettings.getProperty("smtpUsername"), "").trim();
        if (!uiSmtpUsername.isEmpty()) {
            config.setProperty("smtpUsername", uiSmtpUsername);
        }
        String uiEmailBcc = Objects.toString(uiSettings.getProperty("emailBcc"), "").trim();
        if (!uiEmailBcc.isEmpty()) {
            config.setProperty("emailBcc", uiEmailBcc);
        }
        String uiSmtpPassword = Objects.toString(uiSettings.getProperty("smtpPassword"), "").trim();
        if (!uiSmtpPassword.isEmpty() || config.containsKey("smtpPassword")) {
            config.setProperty("smtpPassword", uiSmtpPassword);
        }
        String uiSmtpTls = Objects.toString(uiSettings.getProperty("smtpTls"), "").trim();
        if (!uiSmtpTls.isEmpty()) {
            config.setProperty("smtpTls", uiSmtpTls);
        }
        config.setProperty("sendEmailsEnabled", Objects.toString(uiSettings.getProperty("sendEmailsEnabled"), Objects.toString(config.getProperty("sendEmailsEnabled"), "true")).trim());
        String uiEmailRecipientMode = Objects.toString(uiSettings.getProperty("emailRecipientMode"), "").trim();
        if (!uiEmailRecipientMode.isEmpty()) {
            if (!"advisor".equals(uiEmailRecipientMode)) uiEmailRecipientMode = "contact";
            config.setProperty("emailRecipientMode", uiEmailRecipientMode);
        } else if (!config.containsKey("emailRecipientMode")) {
            config.setProperty("emailRecipientMode", "contact");
        }
        config.setProperty("emailTemplateHtml", Objects.toString(uiSettings.getProperty("emailTemplateHtml"), Objects.toString(config.getProperty("emailTemplateHtml"), "")));
        config.setProperty("validationReminderTemplateHtml", Objects.toString(uiSettings.getProperty("validationReminderTemplateHtml"), Objects.toString(config.getProperty("validationReminderTemplateHtml"), "")));
        config.setProperty("eInvoicePdfTemplateHtml", Objects.toString(uiSettings.getProperty("eInvoicePdfTemplateHtml"), Objects.toString(config.getProperty("eInvoicePdfTemplateHtml"), "")));
        config.setProperty("eInvoicePdfTemplateHtmlRechnung", Objects.toString(uiSettings.getProperty("eInvoicePdfTemplateHtmlRechnung"), Objects.toString(config.getProperty("eInvoicePdfTemplateHtmlRechnung"), "")));
        config.setProperty("emailTemplateHtmlRechnung", Objects.toString(uiSettings.getProperty("emailTemplateHtmlRechnung"), Objects.toString(config.getProperty("emailTemplateHtmlRechnung"), "")));
        config.setProperty("leaderWeeklyReportTemplateHtml", Objects.toString(uiSettings.getProperty("leaderWeeklyReportTemplateHtml"), Objects.toString(config.getProperty("leaderWeeklyReportTemplateHtml"), "")));
        config.setProperty("leaderWeeklyMailSchedulerEnabled", Objects.toString(uiSettings.getProperty("leaderWeeklyMailSchedulerEnabled"), Objects.toString(config.getProperty("leaderWeeklyMailSchedulerEnabled"), "false")));
        config.setProperty("leaderWeeklyMailProductionEnabled", Objects.toString(uiSettings.getProperty("leaderWeeklyMailProductionEnabled"), Objects.toString(config.getProperty("leaderWeeklyMailProductionEnabled"), "false")));
        config.setProperty("leaderWeeklyMailScheduleDay", normalizeLeaderWeeklyMailScheduleDay(Objects.toString(uiSettings.getProperty("leaderWeeklyMailScheduleDay"), Objects.toString(config.getProperty("leaderWeeklyMailScheduleDay"), ""))));
        config.setProperty("leaderWeeklyMailScheduleTime", normalizeLeaderWeeklyMailScheduleTime(Objects.toString(uiSettings.getProperty("leaderWeeklyMailScheduleTime"), Objects.toString(config.getProperty("leaderWeeklyMailScheduleTime"), ""))));
        config.setProperty("leaderWeeklyMailLastSentPeriodKey", Objects.toString(uiSettings.getProperty("leaderWeeklyMailLastSentPeriodKey"), Objects.toString(config.getProperty("leaderWeeklyMailLastSentPeriodKey"), "")));
        config.setProperty("goaffproSyncEnabled", Objects.toString(uiSettings.getProperty("goaffproSyncEnabled"), Objects.toString(config.getProperty("goaffproSyncEnabled"), "true")));
        config.setProperty("goaffproSyncHourlyEnabled", Objects.toString(uiSettings.getProperty("goaffproSyncHourlyEnabled"), Objects.toString(config.getProperty("goaffproSyncHourlyEnabled"), "false")));
        config.setProperty("goaffproSyncDeepEnabled", Objects.toString(uiSettings.getProperty("goaffproSyncDeepEnabled"), Objects.toString(config.getProperty("goaffproSyncDeepEnabled"), "false")));
        config.setProperty("goaffproSyncAssetDownloadEnabled", Objects.toString(uiSettings.getProperty("goaffproSyncAssetDownloadEnabled"), Objects.toString(config.getProperty("goaffproSyncAssetDownloadEnabled"), "true")));
        config.setProperty("goaffproSyncMaxCallsPerHour", normalizePositiveInteger(Objects.toString(uiSettings.getProperty("goaffproSyncMaxCallsPerHour"), Objects.toString(config.getProperty("goaffproSyncMaxCallsPerHour"), "60")), "60"));
        config.setProperty("goaffproSyncSlidingWindowEnabled", Objects.toString(uiSettings.getProperty("goaffproSyncSlidingWindowEnabled"), Objects.toString(config.getProperty("goaffproSyncSlidingWindowEnabled"), "true")));
        config.setProperty("goaffproSyncMinCallSpacingMs", normalizeNonNegativeInteger(Objects.toString(uiSettings.getProperty("goaffproSyncMinCallSpacingMs"), Objects.toString(config.getProperty("goaffproSyncMinCallSpacingMs"), "1500")), "1500"));
        config.setProperty("goaffproSyncDownloadSkipExistingEnabled", Objects.toString(uiSettings.getProperty("goaffproSyncDownloadSkipExistingEnabled"), Objects.toString(config.getProperty("goaffproSyncDownloadSkipExistingEnabled"), "true")));
        config.setProperty("goaffproSyncDeltaDownloadsEnabled", Objects.toString(uiSettings.getProperty("goaffproSyncDeltaDownloadsEnabled"), Objects.toString(config.getProperty("goaffproSyncDeltaDownloadsEnabled"), "false")));
        config.setProperty("goaffproSyncDeltaLookbackDays", normalizePositiveInteger(Objects.toString(uiSettings.getProperty("goaffproSyncDeltaLookbackDays"), Objects.toString(config.getProperty("goaffproSyncDeltaLookbackDays"), "14")), "14"));
        config.setProperty("goaffproSyncMinFreeBytes", normalizePositiveLong(Objects.toString(uiSettings.getProperty("goaffproSyncMinFreeBytes"), Objects.toString(config.getProperty("goaffproSyncMinFreeBytes"), String.valueOf(512L * 1024L * 1024L))), String.valueOf(512L * 1024L * 1024L)));
        String syncDataPath = Objects.toString(uiSettings.getProperty("goaffproSyncDataPath"), Objects.toString(config.getProperty("goaffproSyncDataPath"), "")).trim();
        if (!syncDataPath.isBlank()) config.setProperty("goaffproSyncDataPath", syncDataPath);
        config.setProperty("eInvoiceEnabled", Objects.toString(uiSettings.getProperty("eInvoiceEnabled"), Objects.toString(config.getProperty("eInvoiceEnabled"), "true")));
        config.setProperty("eInvoiceAttachAndStoreEnabled", Objects.toString(uiSettings.getProperty("eInvoiceAttachAndStoreEnabled"), Objects.toString(config.getProperty("eInvoiceAttachAndStoreEnabled"), "true")));
        config.setProperty("eInvoiceBuyerName", Objects.toString(uiSettings.getProperty("eInvoiceBuyerName"), Objects.toString(config.getProperty("eInvoiceBuyerName"), "S+R linear technology gmbh")));
        config.setProperty("eInvoiceBuyerStreet", Objects.toString(uiSettings.getProperty("eInvoiceBuyerStreet"), Objects.toString(config.getProperty("eInvoiceBuyerStreet"), "")));
        config.setProperty("eInvoiceBuyerZip", Objects.toString(uiSettings.getProperty("eInvoiceBuyerZip"), Objects.toString(config.getProperty("eInvoiceBuyerZip"), "")));
        config.setProperty("eInvoiceBuyerCity", Objects.toString(uiSettings.getProperty("eInvoiceBuyerCity"), Objects.toString(config.getProperty("eInvoiceBuyerCity"), "")));
        config.setProperty("eInvoiceBuyerCountry", Objects.toString(uiSettings.getProperty("eInvoiceBuyerCountry"), Objects.toString(config.getProperty("eInvoiceBuyerCountry"), "DE")));
        config.setProperty("eInvoiceBuyerVatId", Objects.toString(uiSettings.getProperty("eInvoiceBuyerVatId"), Objects.toString(config.getProperty("eInvoiceBuyerVatId"), "")));
        config.setProperty("eInvoiceBuyerTaxNumber", Objects.toString(uiSettings.getProperty("eInvoiceBuyerTaxNumber"), Objects.toString(config.getProperty("eInvoiceBuyerTaxNumber"), "")));
        config.setProperty("eInvoiceBankIban", Objects.toString(uiSettings.getProperty("eInvoiceBankIban"), Objects.toString(config.getProperty("eInvoiceBankIban"), "")));
        config.setProperty("eInvoiceBankBic", Objects.toString(uiSettings.getProperty("eInvoiceBankBic"), Objects.toString(config.getProperty("eInvoiceBankBic"), "")));
        config.setProperty("eInvoiceBankAccountHolder", Objects.toString(uiSettings.getProperty("eInvoiceBankAccountHolder"), Objects.toString(config.getProperty("eInvoiceBankAccountHolder"), "")));
        config.setProperty("eInvoicePaymentTerms", Objects.toString(uiSettings.getProperty("eInvoicePaymentTerms"), Objects.toString(config.getProperty("eInvoicePaymentTerms"), "Zahlbar sofort ohne Abzug")));
        config.setProperty("nachweisFirmenname", Objects.toString(uiSettings.getProperty("nachweisFirmenname"), Objects.toString(config.getProperty("nachweisFirmenname"), "S+R Linear Technology GmbH")));
        config.setProperty("rechnungCutoffDate", normalizeIsoDate(Objects.toString(uiSettings.getProperty("rechnungCutoffDate"), config.getProperty("rechnungCutoffDate")), DEFAULT_RECHNUNG_CUTOFF_DATE));
        config.setProperty("legacyBuyerName", Objects.toString(uiSettings.getProperty("legacyBuyerName"), Objects.toString(config.getProperty("legacyBuyerName"), DEFAULT_LEGACY_BUYER_NAME)));
        config.setProperty("legacyBuyerStreet", Objects.toString(uiSettings.getProperty("legacyBuyerStreet"), Objects.toString(config.getProperty("legacyBuyerStreet"), "")));
        config.setProperty("legacyBuyerZip", Objects.toString(uiSettings.getProperty("legacyBuyerZip"), Objects.toString(config.getProperty("legacyBuyerZip"), "")));
        config.setProperty("legacyBuyerCity", Objects.toString(uiSettings.getProperty("legacyBuyerCity"), Objects.toString(config.getProperty("legacyBuyerCity"), "")));
        config.setProperty("legacyBuyerCountry", Objects.toString(uiSettings.getProperty("legacyBuyerCountry"), Objects.toString(config.getProperty("legacyBuyerCountry"), "DE")));
        config.setProperty("legacyBuyerVatId", Objects.toString(uiSettings.getProperty("legacyBuyerVatId"), Objects.toString(config.getProperty("legacyBuyerVatId"), "")));
        config.setProperty("legacyBuyerTaxNumber", Objects.toString(uiSettings.getProperty("legacyBuyerTaxNumber"), Objects.toString(config.getProperty("legacyBuyerTaxNumber"), "")));
        config.setProperty("legacyNachweisFirmenname", Objects.toString(uiSettings.getProperty("legacyNachweisFirmenname"), Objects.toString(config.getProperty("legacyNachweisFirmenname"), DEFAULT_LEGACY_BUYER_NAME)));

        config.setProperty(MAIL_LOG_KEY, Objects.toString(uiSettings.getProperty(MAIL_LOG_KEY), Objects.toString(config.getProperty(MAIL_LOG_KEY), "")));
        config.setProperty(REMINDER_LOG_KEY, Objects.toString(uiSettings.getProperty(REMINDER_LOG_KEY), Objects.toString(config.getProperty(REMINDER_LOG_KEY), "")));
        config.setProperty(LEADER_WEEKLY_MAIL_LOG_KEY, Objects.toString(uiSettings.getProperty(LEADER_WEEKLY_MAIL_LOG_KEY), Objects.toString(config.getProperty(LEADER_WEEKLY_MAIL_LOG_KEY), "")));

        ensureCommissionInHistory(config, Objects.toString(config.getProperty("lastImportedComission"), "0"));
    }

    private static void persistSettings(Properties config) throws IOException {
        synchronized (CONFIG_LOCK) {
            persistSettingsUnlocked(config);
        }
    }

    private static void persistSettingsUnlocked(Properties config) throws IOException {
        if (Objects.toString(config.getProperty("pdfExportPath"), "").isBlank()) {
            config.setProperty("pdfExportPath", DEFAULT_PDF_EXPORT_PATH);
        }
        if (Objects.toString(config.getProperty("sendEmailsEnabled"), "").isBlank()) {
            config.setProperty("sendEmailsEnabled", "true");
        }
        String emailRecipientMode = Objects.toString(config.getProperty("emailRecipientMode"), "contact").trim();
        if (!"advisor".equals(emailRecipientMode)) {
            config.setProperty("emailRecipientMode", "contact");
        }
        if (Objects.toString(config.getProperty("goaffproAPIKey"), "").isBlank()) {
            config.setProperty("goaffproAPIKey", DEFAULT_GOAFFPRO_API_KEY);
        }
        config.setProperty("leaderWeeklyMailSchedulerEnabled",
                Objects.toString(config.getProperty("leaderWeeklyMailSchedulerEnabled"), "false").trim().isBlank()
                        ? "false" : Objects.toString(config.getProperty("leaderWeeklyMailSchedulerEnabled"), "false").trim());
        config.setProperty("leaderWeeklyMailProductionEnabled",
                Objects.toString(config.getProperty("leaderWeeklyMailProductionEnabled"), "false").trim().isBlank()
                        ? "false" : Objects.toString(config.getProperty("leaderWeeklyMailProductionEnabled"), "false").trim());
        config.setProperty("leaderWeeklyMailScheduleDay", normalizeLeaderWeeklyMailScheduleDay(Objects.toString(config.getProperty("leaderWeeklyMailScheduleDay"), "")));
        config.setProperty("leaderWeeklyMailScheduleTime", normalizeLeaderWeeklyMailScheduleTime(Objects.toString(config.getProperty("leaderWeeklyMailScheduleTime"), "")));
        if (!config.containsKey("leaderWeeklyMailLastSentPeriodKey")) {
            config.setProperty("leaderWeeklyMailLastSentPeriodKey", "");
        }
        config.setProperty("goaffproSyncEnabled", Objects.toString(config.getProperty("goaffproSyncEnabled"), "true").trim().isBlank() ? "true" : Objects.toString(config.getProperty("goaffproSyncEnabled"), "true").trim());
        config.setProperty("goaffproSyncHourlyEnabled", Objects.toString(config.getProperty("goaffproSyncHourlyEnabled"), "false").trim().isBlank() ? "false" : Objects.toString(config.getProperty("goaffproSyncHourlyEnabled"), "false").trim());
        config.setProperty("goaffproSyncDeepEnabled", Objects.toString(config.getProperty("goaffproSyncDeepEnabled"), "false").trim().isBlank() ? "false" : Objects.toString(config.getProperty("goaffproSyncDeepEnabled"), "false").trim());
        config.setProperty("goaffproSyncAssetDownloadEnabled", Objects.toString(config.getProperty("goaffproSyncAssetDownloadEnabled"), "true").trim().isBlank() ? "true" : Objects.toString(config.getProperty("goaffproSyncAssetDownloadEnabled"), "true").trim());
        config.setProperty("goaffproSyncMaxCallsPerHour", normalizePositiveInteger(Objects.toString(config.getProperty("goaffproSyncMaxCallsPerHour"), "60"), "60"));
        config.setProperty("goaffproSyncSlidingWindowEnabled", Objects.toString(config.getProperty("goaffproSyncSlidingWindowEnabled"), "true").trim().isBlank() ? "true" : Objects.toString(config.getProperty("goaffproSyncSlidingWindowEnabled"), "true").trim());
        config.setProperty("goaffproSyncMinCallSpacingMs", normalizeNonNegativeInteger(Objects.toString(config.getProperty("goaffproSyncMinCallSpacingMs"), "1500"), "1500"));
        config.setProperty("goaffproSyncDownloadSkipExistingEnabled", Objects.toString(config.getProperty("goaffproSyncDownloadSkipExistingEnabled"), "true").trim().isBlank() ? "true" : Objects.toString(config.getProperty("goaffproSyncDownloadSkipExistingEnabled"), "true").trim());
        config.setProperty("goaffproSyncDeltaDownloadsEnabled", Objects.toString(config.getProperty("goaffproSyncDeltaDownloadsEnabled"), "false").trim().isBlank() ? "false" : Objects.toString(config.getProperty("goaffproSyncDeltaDownloadsEnabled"), "false").trim());
        config.setProperty("goaffproSyncDeltaLookbackDays", normalizePositiveInteger(Objects.toString(config.getProperty("goaffproSyncDeltaLookbackDays"), "14"), "14"));
        config.setProperty("goaffproSyncMinFreeBytes", normalizePositiveLong(Objects.toString(config.getProperty("goaffproSyncMinFreeBytes"), String.valueOf(512L * 1024L * 1024L)), String.valueOf(512L * 1024L * 1024L)));
        if (Objects.toString(config.getProperty("goaffproSyncDataPath"), "").trim().isBlank()) {
            config.setProperty("goaffproSyncDataPath", GoAffProSyncService.resolveDataDir(config).toString());
        }
        if (Objects.toString(config.getProperty(COMMISSION_HISTORY_KEY), "").isBlank()) {
            for (String commission : DEFAULT_COMMISSION_HISTORY) {
                ensureCommissionInHistory(config, commission);
            }
        }
        storeConfig(config);
        saveUiSettings(resolveSettingsDirectory(config), config);
    }

    private static List<String> getCommissionHistory(Properties properties) {
        String raw = Objects.toString(properties.getProperty(COMMISSION_HISTORY_KEY), "");
        Set<String> unique = new LinkedHashSet<>();
        if (!raw.isBlank()) {
            for (String part : raw.split(",")) {
                String value = part.trim();
                if (!value.isEmpty()) {
                    unique.add(value);
                }
            }
        }
        String active = Objects.toString(properties.getProperty("lastImportedComission"), "").trim();
        if (!active.isEmpty()) {
            unique.add(active);
        }
        return sortCommissionsChronologically(new ArrayList<>(unique));
    }

    private static List<String> sortCommissionsChronologically(List<String> values) {
        List<String> sorted = values == null ? new ArrayList<>() : values.stream()
                .filter(v -> v != null && !v.isBlank())
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        sorted.sort((a, b) -> {
            try {
                return Long.compare(Long.parseLong(a), Long.parseLong(b));
            } catch (Exception e) {
                return a.compareToIgnoreCase(b);
            }
        });
        return sorted;
    }

    private static Map<String, String> getKnownCommissionDates() {
        return Map.of(
                "2103705", "26.03.2025",
                "2167905", "28.04.2025",
                "2190357", "06.05.2025",
                "2230376", "28.05.2025",
                "2336836", "30.06.2025",
                "2421355", "31.07.2025",
                "2497986", "29.08.2025",
                "2565325", "30.09.2025"
        );
    }

    private static Map<String, String> getCommissionDatesFromConfig(Properties properties) {
        Map<String, String> dates = new LinkedHashMap<>(getKnownCommissionDates());
        String raw = Objects.toString(properties.getProperty(COMMISSION_HISTORY_DATES_KEY), "");
        if (!raw.isBlank()) {
            for (String part : raw.split(";")) {
                String entry = part.trim();
                if (entry.isEmpty() || !entry.contains("=")) continue;
                int idx = entry.indexOf('=');
                String id = entry.substring(0, idx).trim();
                String date = entry.substring(idx + 1).trim();
                if (!id.isEmpty() && !date.isEmpty()) dates.put(id, date);
            }
        }
        return dates;
    }

    private static void setCommissionDate(Properties properties, String commission, String germanDate) {
        if (commission == null || commission.isBlank() || germanDate == null || germanDate.isBlank()) return;
        Map<String, String> dates = getCommissionDatesFromConfig(properties);
        dates.put(commission, germanDate);
        String raw = dates.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(";"));
        properties.setProperty(COMMISSION_HISTORY_DATES_KEY, raw);
    }

    private static void rebuildCommissionHistoryFromPayments(Properties config, String apiKey) throws Exception {
        JsonNode root = requestJson("https://api.goaffpro.com/v1/admin/payments?fields=id,created_at", apiKey);
        JsonNode payments = root.get("payments");
        if (payments == null || !payments.isArray() || payments.size() == 0) {
            throw new IOException("Keine Payments zum Neuaufbau gefunden.");
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (JsonNode payment : payments) {
            String id = asText(payment, "id").trim();
            if (id.isBlank()) continue;
            String createdAt = asText(payment, "created_at").trim();
            String date = createdAt.isBlank() ? "" : toGermanDate(createdAt);
            Map<String, String> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("date", date);
            row.put("createdAt", createdAt);
            rows.add(row);
        }
        if (rows.isEmpty()) throw new IOException("Keine gültigen Payment-IDs gefunden.");

        Map<String, Map<String, String>> highestPaymentByDay = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String dayKey = Objects.toString(row.get("date"), "").trim();
            if (dayKey.isBlank()) dayKey = "ohne Datum";

            Map<String, String> existing = highestPaymentByDay.get(dayKey);
            if (existing == null || compareAsNumericString(row.get("id"), existing.get("id")) > 0) {
                highestPaymentByDay.put(dayKey, row);
            }
        }

        List<Map<String, String>> reducedRows = new ArrayList<>(highestPaymentByDay.values());
        reducedRows.sort((a, b) -> {
            String da = Objects.toString(a.get("date"), "");
            String db = Objects.toString(b.get("date"), "");
            LocalDate lda = parseGermanDate(da);
            LocalDate ldb = parseGermanDate(db);
            if (lda != null && ldb != null && !lda.equals(ldb)) return lda.compareTo(ldb);
            return compareAsNumericString(a.get("id"), b.get("id"));
        });

        List<String> history = new ArrayList<>();
        Map<String, String> dates = new LinkedHashMap<>();
        String latestId = "";
        for (Map<String, String> row : reducedRows) {
            String id = row.get("id");
            if (!history.contains(id)) history.add(id);
            String date = Objects.toString(row.get("date"), "");
            if (!date.isBlank()) dates.put(id, date);
            if (latestId.isBlank() || compareAsNumericString(id, latestId) > 0) latestId = id;
        }

        config.setProperty(COMMISSION_HISTORY_KEY, String.join(",", history));
        String rawDates = dates.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(";"));
        config.setProperty(COMMISSION_HISTORY_DATES_KEY, rawDates);
        if (!latestId.isBlank()) config.setProperty("lastImportedComission", latestId);
    }

    private static int compareAsNumericString(String a, String b) {
        try {
            return Long.compare(Long.parseLong(Objects.toString(a, "0")), Long.parseLong(Objects.toString(b, "0")));
        } catch (Exception e) {
            return Objects.toString(a, "").compareTo(Objects.toString(b, ""));
        }
    }

    private static List<Map<String, Object>> buildCommissionDaySummary(Properties properties) {
        List<String> history = getCommissionHistory(properties);
        Map<String, String> dates = getCommissionDatesFromConfig(properties);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String commission : history) {
            String day = Objects.toString(dates.get(commission), "ohne Datum").trim();
            if (day.isBlank()) day = "ohne Datum";
            counts.put(day, counts.getOrDefault(day, 0) + 1);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", entry.getKey());
            row.put("count", entry.getValue());
            rows.add(row);
        }
        rows.sort((a, b) -> {
            LocalDate da = parseGermanDate(Objects.toString(a.get("date"), ""));
            LocalDate db = parseGermanDate(Objects.toString(b.get("date"), ""));
            if (da != null && db != null) return db.compareTo(da);
            if (da != null) return -1;
            if (db != null) return 1;
            return Objects.toString(a.get("date"), "").compareToIgnoreCase(Objects.toString(b.get("date"), ""));
        });
        return rows;
    }

    private static Map<String, String> buildCommissionHistoryLabels(Properties properties) {
        List<String> history = getCommissionHistory(properties);
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, String> dates = getCommissionDatesFromConfig(properties);
        for (String commission : history) {
            String date = dates.get(commission);
            labels.put(commission, date == null ? commission : (commission + " (" + date + ")"));
        }
        return labels;
    }

    private static void ensureCommissionInHistory(Properties properties, String commission) {
        if (commission == null || commission.isBlank()) {
            return;
        }
        List<String> history = getCommissionHistory(properties);
        if (!history.contains(commission)) {
            history.add(commission);
        }
        properties.setProperty(COMMISSION_HISTORY_KEY, String.join(",", history));
    }

    private static boolean removeCommissionFromHistory(Properties properties, String commission) {
        if (commission == null || commission.isBlank()) return false;
        List<String> history = getCommissionHistory(properties);
        boolean removed = history.removeIf(v -> commission.equals(v));
        properties.setProperty(COMMISSION_HISTORY_KEY, String.join(",", history));
        return removed;
    }

    private static void removeCommissionDate(Properties properties, String commission) {
        if (commission == null || commission.isBlank()) return;
        Map<String, String> dates = getCommissionDatesFromConfig(properties);
        dates.remove(commission);
        String raw = dates.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(";"));
        properties.setProperty(COMMISSION_HISTORY_DATES_KEY, raw);
    }

    private static LocalDate parseIsoDate(String input) {
        if (input == null || input.isBlank()) return null;
        try {
            return LocalDate.parse(input.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static LocalDate parseIsoDateTimeToLocalDate(String input) {
        if (input == null || input.isBlank()) return null;
        try {
            return OffsetDateTime.parse(input).atZoneSameInstant(ZoneId.of("Europe/Berlin")).toLocalDate();
        } catch (Exception ignored) {
            return null;
        }
    }

private static String toGermanDate(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        try {
            OffsetDateTime dateTime = OffsetDateTime.parse(input);
            return dateTime.atZoneSameInstant(ZoneId.systemDefault()).format(OUTPUT_FORMATTER);
        } catch (Exception ignored) {
            try {
                return OffsetDateTime.parse(input, INPUT_FORMATTER).format(OUTPUT_FORMATTER);
            } catch (Exception e) {
                return input;
            }
        }
    }

    private static String asText(JsonNode node, String field) {
        JsonNode value = node != null ? node.get(field) : null;
        return value != null && !value.isNull() ? value.asText() : "";
    }

    private static boolean isGreaterNumeric(String value, String compareTo) {
        try {
            return Long.parseLong(value) > Long.parseLong(compareTo);
        } catch (Exception e) {
            return value.compareTo(compareTo) > 0;
        }
    }

    private static void sendResponse(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }


    private static LocalDate parseGermanDate(String input) {
        if (input == null || input.isBlank()) return null;
        try {
            return LocalDate.parse(input.trim(), DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isValidIban(String rawIban) {
        if (rawIban == null) return false;
        String iban = rawIban.replaceAll("\\s+", "").toUpperCase();
        if (iban.length() < 15 || iban.length() > 34) return false;
        if (!iban.matches("[A-Z]{2}[0-9A-Z]+")) return false;
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder numeric = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            if (Character.isDigit(c)) numeric.append(c);
            else if (c >= 'A' && c <= 'Z') numeric.append((int) (c - 'A' + 10));
            else return false;
        }
        int mod = 0;
        for (int i = 0; i < numeric.length(); i++) {
            mod = (mod * 10 + (numeric.charAt(i) - '0')) % 97;
        }
        return mod == 1;
    }

    /**
     * Prüft, ob ein Pfad wirklich unterhalb einer erlaubten Wurzel liegt.
     * toRealPath() löst dabei auch Symlinks auf; Path#startsWith vergleicht Pfadelemente,
     * sodass ein Geschwisterverzeichnis mit gleichem Namenspräfix nicht durchrutscht.
     */
    static boolean isUnderRoot(Path candidate, Path root) {
        if (candidate == null || root == null) return false;
        try {
            return candidate.toRealPath().startsWith(root.toRealPath());
        } catch (IOException e) {
            return candidate.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize());
        }
    }

    private static String sanitizeFilename(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String toCanonicalJson(Object value) {
        try {
            if (value == null) return "null";
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Objects.toString(input, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "hash-unavailable";
        }
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Liefert den Versionsverlauf samt Auskunft, ob er ueberhaupt ermittelbar war. Der laufende
     * Stand steht immer an erster Stelle - auch im Container, wo kein git verfuegbar ist. Frueher
     * gab diese Methode dort eine leere Liste zurueck und die Oberflaeche zeigte wortlos nichts an.
     */
    private static Map<String, Object> readRecentVersions() {
        List<Map<String, String>> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        if (!BUILD_INFO.commit().isBlank()) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("version", BUILD_INFO.commit());
            row.put("timestamp", readableBuildTimestamp(BUILD_INFO.version()));
            row.put("summary", toGermanSummary(BUILD_INFO.summary()));
            items.add(row);
            seen.add(BUILD_INFO.commit());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        String log = gitOutput("log", "-n", "12", "--pretty=format:%h|%ct|%s");
        if (log == null) {
            payload.put("versions", items);
            payload.put("available", false);
            payload.put("reason", items.isEmpty()
                    ? "Der Versionsverlauf braucht ein Git-Repository und ist hier nicht verfügbar; im Container ist das der Normalfall."
                    : "Nur der laufende Stand ist bekannt. Der vollständige Verlauf braucht ein Git-Repository und ist im Container nicht verfügbar.");
            return payload;
        }

        for (String line : log.split("\\R")) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\|", 3);
            if (parts.length < 3) continue;
            String hash = parts[0].trim();
            if (!seen.add(hash)) continue;
            String ts;
            try {
                ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochSecond(Long.parseLong(parts[1].trim())));
            } catch (Exception ignored) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            row.put("version", hash);
            row.put("timestamp", ts);
            row.put("summary", toGermanSummary(parts[2].trim()));
            items.add(row);
        }

        payload.put("versions", items);
        payload.put("available", true);
        return payload;
    }

    private static String toGermanSummary(String commitSubject) {
        if (commitSubject == null || commitSubject.isBlank()) {
            return "Keine Beschreibung verfügbar.";
        }

        String text = commitSubject.trim();
        String lower = text.toLowerCase();

        String normalized = text
                .replace("advisor", "Beraterin")
                .replace("Advisor", "Beraterin")
                .replace("analytics", "Auswertungen")
                .replace("Analytics", "Auswertungen")
                .replace("workflow", "Ablauf")
                .replace("Workflow", "Ablauf")
                .replace("mail-log", "Versandhistorie")
                .replace("Mail-Log", "Versandhistorie")
                .replace("invoice", "Gutschrift")
                .replace("Invoice", "Gutschrift")
                .replace("settings", "Einstellungen")
                .replace("Settings", "Einstellungen");

        if (lower.startsWith("fix ") || lower.startsWith("fix:")) {
            return "Fehlerbehebung: " + normalized.substring(normalized.indexOf(' ') + 1).trim();
        }
        if (lower.startsWith("add ") || lower.startsWith("add:")) {
            return "Erweiterung: " + normalized.substring(normalized.indexOf(' ') + 1).trim();
        }
        if (lower.startsWith("update ") || lower.startsWith("update:")) {
            return "Aktualisierung: " + normalized.substring(normalized.indexOf(' ') + 1).trim();
        }
        if (lower.startsWith("refactor ") || lower.startsWith("refactor:")) {
            return "Umstrukturierung: " + normalized.substring(normalized.indexOf(' ') + 1).trim();
        }
        if (lower.startsWith("remove ") || lower.startsWith("remove:")) {
            return "Entfernung: " + normalized.substring(normalized.indexOf(' ') + 1).trim();
        }

        return "Änderung: " + normalized;
    }

    /**
     * Woher die Versionskennung stammt: "build" = beim Bauen eingebacken (im Container der
     * Normalfall), "git" = zur Laufzeit aus dem Repository gelesen (lokale Entwicklung),
     * "unknown" = nicht ermittelbar. {@code sequenceKnown} ist falsch, wenn keine belastbare
     * Build-Nummer vorliegt; die Oberflaeche weicht dann auf den Commit-Hash aus.
     */
    private record BuildInfo(String version, String commit, String branch, String summary,
                             String source, boolean sequenceKnown) {
    }

    /**
     * Eingebackene Kennung zuerst, dann git zur Laufzeit, sonst ehrliches Unwissen.
     * Frueher wurde stattdessen das aktuelle Datum mit der Sequenz 000000 erfunden - das ergab
     * im Container dauerhaft die irrefuehrende Anzeige "Build 0 - heutiges Datum".
     */
    private static BuildInfo detectBuildInfo() {
        BuildInfo baked = buildInfoFromResource();
        if (baked != null) return baked;
        BuildInfo fromGit = buildInfoFromGit();
        if (fromGit != null) return fromGit;
        return new BuildInfo("", "", "", "", "unknown", false);
    }

    /** Liest die vom Maven-Build erzeugte version.properties vom Klassenpfad. */
    private static BuildInfo buildInfoFromResource() {
        try (InputStream in = WebUiServer.class.getResourceAsStream("/version.properties")) {
            if (in == null) return null;
            Properties p = new Properties();
            p.load(in);
            // Das Plugin liefert git.commit.time bereits im Zielformat yyyyMMddHHmmss.
            String time = p.getProperty("git.commit.time", "").trim();
            if (time.length() != 14 || !time.chars().allMatch(Character::isDigit)) return null;
            int sequence = plausibleSequence(p.getProperty("git.total.commit.count", "").trim());
            return new BuildInfo(time + "-" + String.format("%06d", sequence),
                    p.getProperty("git.commit.id.abbrev", "").trim(),
                    p.getProperty("git.branch", "").trim(),
                    p.getProperty("git.commit.message.short", "").trim(),
                    "build", sequence > 0);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Fallback fuer die lokale Entwicklung, wo die Anwendung aus dem Repository heraus laeuft. */
    private static BuildInfo buildInfoFromGit() {
        String tsOutput = gitOutput("show", "-s", "--format=%ct", "HEAD");
        String countOutput = gitOutput("rev-list", "--count", "HEAD");
        if (tsOutput == null || countOutput == null) return null;
        try {
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochSecond(Long.parseLong(tsOutput)));
            int sequence = plausibleSequence(countOutput);
            return new BuildInfo(timestamp + "-" + String.format("%06d", sequence),
                    orEmpty(gitOutput("rev-parse", "--short", "HEAD")),
                    orEmpty(gitOutput("rev-parse", "--abbrev-ref", "HEAD")),
                    orEmpty(gitOutput("show", "-s", "--format=%s", "HEAD")),
                    "git", sequence > 0);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Ein flacher Klon (Portainer holt Git-Stacks mit depth=1) meldet als Commit-Anzahl immer 1.
     * Eine daraus gebildete Build-Nummer waere gelogen, deshalb gilt sie hier als unbekannt.
     */
    private static int plausibleSequence(String rawCount) {
        if (rawCount == null || !rawCount.matches("\\d+")) return 0;
        try {
            int count = Integer.parseInt(rawCount);
            return count > 1 ? count : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /** Fuehrt ein git-Kommando aus; null, wenn git fehlt, scheitert oder nichts ausgibt. */
    private static String gitOutput(String... args) {
        try {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(List.of(args));
            Process process = new ProcessBuilder(command)
                    .directory(Paths.get(".").toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor() == 0 && !output.isBlank() ? output : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Macht aus yyyyMMddHHmmss-NNNNNN die im Verlauf uebliche Schreibweise. */
    private static String readableBuildTimestamp(String version) {
        if (version == null || version.length() < 14) return "";
        String d = version.substring(0, 14);
        if (!d.chars().allMatch(Character::isDigit)) return "";
        return d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8) + " "
                + d.substring(8, 10) + ":" + d.substring(10, 12) + ":" + d.substring(12, 14);
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "Unbekannter Fehler";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
