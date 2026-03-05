import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

public class WebUiServer {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[XXX]");
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Path CONFIG_PATH = Paths.get("src/main/java/config.properties");
    private static final Path UI_PATH = Paths.get("src/main/resources/ui/dashboard.html");
    private static final Path HELP_DOC_PATH = Paths.get("docs/HILFE.md");
    private static final String COMMISSION_HISTORY_KEY = "lastImportedComissionHistory";
    private static final String COMMISSION_HISTORY_DATES_KEY = "lastImportedComissionHistoryDates";
    private static final String MAIL_LOG_KEY = "sentMailLogJson";
    private static final String REMINDER_LOG_KEY = "sentReminderLogJson";
    private static final String DEFAULT_PDF_EXPORT_PATH = "C:\\Users\\nluenzer\\Downloads\\goaffpro";
    private static final String UI_SETTINGS_FILENAME = "goaffpro_ui_settings.properties";
    private static final String DEFAULT_GOAFFPRO_API_KEY = "91bdb6e219f5b9ffeff929077b4badd5d7a26c235c672e20285885835683b845";
    private static final String USER_STORE_FILENAME = "goaffpro_users.enc";
    private static final String AUTH_SECRET_DEFAULT = "goaffpro-auth-secret";
    private static final Map<String, SessionUser> ACTIVE_SESSIONS = new ConcurrentHashMap<>();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final List<String> DEFAULT_COMMISSION_HISTORY = List.of("2103705", "2167905", "2190357", "2230376", "2336836", "2421355", "2497986", "2565325");
    private static final String APP_VERSION = resolveVersionWithTimestampAndSequence();

    public static void main(String[] args) throws IOException {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(8080), 0);
        } catch (java.net.BindException e) {
            System.err.println("Port 8080 ist bereits belegt. Versuche, den bestehenden Prozess zu beenden...");
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
        }
        server.createContext("/", new UiHandler());
        server.createContext("/api/executables", new ExecutablesHandler());
        server.createContext("/api/provisionen-goaffpro/poll", new PollGoaffproHandler());
        server.createContext("/api/settings", new SettingsHandler());
        server.createContext("/api/provisionen-goaffpro/export-pdf", new ExportPdfHandler());
        server.createContext("/api/provisionen-goaffpro/invoice-details-pdf", new InvoiceDetailsPdfHandler());
        server.createContext("/api/mail-log", new MailLogHandler());
        server.createContext("/api/mail-log/download", new MailLogDownloadHandler());
        server.createContext("/api/version", new VersionHandler());
        server.createContext("/api/version/history", new VersionHistoryHandler());
        server.createContext("/api/analytics/fetch", new AnalyticsFetchHandler());
        server.createContext("/api/analytics/advisor-detail", new AnalyticsAdvisorDetailHandler());
        server.createContext("/api/commissions/add-latest", new AddLatestCommissionHandler());
        server.createContext("/api/commissions/remove", new RemoveCommissionHandler());
        server.createContext("/api/commissions/rebuild-from-payments", new RebuildCommissionHistoryHandler());
        server.createContext("/api/help", new HelpHandler());
        server.createContext("/api/validation/advisors", new ValidationAdvisorsHandler());
        server.createContext("/api/validation/advisors/tree", new ValidationAdvisorTreeHandler());
        server.createContext("/api/validation/send-reminder", new ValidationReminderMailHandler());
        server.createContext("/api/validation/reminder-log", new ValidationReminderLogHandler());
        server.createContext("/api/erpnext/sales-invoices", new ErpnextSalesInvoicesHandler());
        server.createContext("/api/erpnext/purchase-orders", new ErpnextPurchaseOrdersHandler());
        server.createContext("/api/as/bank-accounts", new AsBankAccountsHandler());
        server.createContext("/api/as/mt940/import", new AsMt940ImportHandler());
        server.createContext("/api/as/bank-transactions", new AsBankTransactionsHandler());
        server.createContext("/api/auth/login", new AuthLoginHandler());
        server.createContext("/api/auth/me", new AuthMeHandler());
        server.createContext("/api/auth/logout", new AuthLogoutHandler());
        server.createContext("/api/users", new UsersHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("Web UI Server gestartet auf http://localhost:8080");
    }


    private static class AuthLoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { sendResponse(exchange, 200, "application/json", "{}"); return; }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}"); return; }
            try {
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String username = asText(body, "username").trim();
                String password = asText(body, "password");
                Properties config = loadConfig();
                List<UserAccount> users = loadUserAccounts(config);
                UserAccount u = users.stream().filter(x -> x.username.equalsIgnoreCase(username)).findFirst().orElse(null);
                if (u == null || !verifyPassword(password, u.passwordSalt, u.passwordHash)) { sendResponse(exchange, 401, "application/json", "{\"error\":\"Ungültige Login-Daten\"}"); return; }
                String token = generateToken();
                ACTIVE_SESSIONS.put(token, new SessionUser(u.username, u.isAdmin, u.department));
                Map<String,Object> payload = new LinkedHashMap<>();
                payload.put("token", token);
                payload.put("user", userToMap(u));
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) { sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}"); }
        }
    }

    private static class AuthMeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { sendResponse(exchange, 200, "application/json", "{}"); return; }
            SessionUser s = requireSession(exchange);
            if (s == null) return;
            try {
                Properties config = loadConfig();
                List<UserAccount> users = loadUserAccounts(config);
                UserAccount u = users.stream().filter(x -> x.username.equalsIgnoreCase(s.username)).findFirst().orElse(null);
                if (u == null) { sendResponse(exchange, 401, "application/json", "{\"error\":\"Session ungültig\"}"); return; }
                Map<String,Object> payload = new LinkedHashMap<>();
                payload.put("user", userToMap(u));
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) { sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}"); }
        }
    }

    private static class AuthLogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { sendResponse(exchange, 200, "application/json", "{}"); return; }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}"); return; }
            String token = Objects.toString(exchange.getRequestHeaders().getFirst("X-Auth-Token"), "").trim();
            if (!token.isBlank()) ACTIVE_SESSIONS.remove(token);
            sendResponse(exchange, 200, "application/json", "{}");
        }
    }

    private static class UsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { sendResponse(exchange, 200, "application/json", "{}"); return; }
            SessionUser su = requireSession(exchange);
            if (su == null) return;
            if (!su.isAdmin) { sendResponse(exchange, 403, "application/json", "{\"error\":\"Nur Admin\"}"); return; }
            try {
                Properties config = loadConfig();
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    List<Map<String,Object>> users = loadUserAccounts(config).stream().map(WebUiServer::userToMap).collect(Collectors.toList());
                    Map<String,Object> p = new HashMap<>(); p.put("users", users);
                    sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(p));
                    return;
                }
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}"); return; }
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String action = asText(body, "action").trim();
                List<UserAccount> users = loadUserAccounts(config);
                if ("create".equals(action)) {
                    String username = asText(body, "username").trim();
                    if (username.isBlank()) { sendResponse(exchange, 400, "application/json", "{\"error\":\"username fehlt\"}"); return; }
                    if (users.stream().anyMatch(u -> u.username.equalsIgnoreCase(username))) { sendResponse(exchange, 400, "application/json", "{\"error\":\"Benutzer existiert bereits\"}"); return; }
                    UserAccount u = new UserAccount();
                    u.username = username;
                    u.firstName = asText(body, "firstName").trim();
                    u.lastName = asText(body, "lastName").trim();
                    u.email = asText(body, "email").trim();
                    u.phone = asText(body, "phone").trim();
                    u.department = normalizeDepartment(asText(body, "department").trim());
                    u.isAdmin = body.has("isAdmin") && body.get("isAdmin").asBoolean(false);
                    String temp = randomPassword();
                    String[] pw = hashPassword(temp);
                    u.passwordSalt = pw[0]; u.passwordHash = pw[1];
                    u.forcePasswordChange = true;
                    users.add(u);
                    saveUserAccounts(config, users);
                    trySendPasswordChangeMail(u.email, username, temp, config);
                    sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(Map.of("message","Benutzer angelegt")));
                    return;
                }
                if ("resetPassword".equals(action)) {
                    String username = asText(body, "username").trim();
                    String newPassword = asText(body, "newPassword");
                    UserAccount u = users.stream().filter(x -> x.username.equalsIgnoreCase(username)).findFirst().orElse(null);
                    if (u == null) { sendResponse(exchange, 404, "application/json", "{\"error\":\"Benutzer nicht gefunden\"}"); return; }
                    if (newPassword.isBlank()) newPassword = randomPassword();
                    String[] pw = hashPassword(newPassword);
                    u.passwordSalt = pw[0]; u.passwordHash = pw[1];
                    u.forcePasswordChange = true;
                    saveUserAccounts(config, users);
                    trySendPasswordChangeMail(u.email, u.username, newPassword, config);
                    sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(Map.of("message","Passwort überschrieben")));
                    return;
                }
                if ("update".equals(action)) {
                    String username = asText(body, "username").trim();
                    UserAccount u = users.stream().filter(x -> x.username.equalsIgnoreCase(username)).findFirst().orElse(null);
                    if (u == null) { sendResponse(exchange, 404, "application/json", "{\"error\":\"Benutzer nicht gefunden\"}"); return; }
                    u.firstName = asText(body, "firstName").trim();
                    u.lastName = asText(body, "lastName").trim();
                    u.email = asText(body, "email").trim();
                    u.phone = asText(body, "phone").trim();
                    u.department = normalizeDepartment(asText(body, "department").trim());
                    u.isAdmin = body.has("isAdmin") && body.get("isAdmin").asBoolean(false);
                    saveUserAccounts(config, users);
                    sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(Map.of("message", "Benutzer aktualisiert")));
                    return;
                }
                if ("delete".equals(action)) {
                    String username = asText(body, "username").trim();
                    boolean removed = users.removeIf(x -> x.username.equalsIgnoreCase(username));
                    if (!removed) { sendResponse(exchange, 404, "application/json", "{\"error\":\"Benutzer nicht gefunden\"}"); return; }
                    saveUserAccounts(config, users);
                    sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(Map.of("message", "Benutzer gelöscht")));
                    return;
                }
                sendResponse(exchange, 400, "application/json", "{\"error\":\"Unbekannte Aktion\"}");
            } catch (Exception e) { sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}"); }
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

                String apiKey = Objects.toString(config.getProperty("goaffproAPIKey"), DEFAULT_GOAFFPRO_API_KEY).trim();
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
                String goaffproAPIKey = Objects.toString(config.getProperty("goaffproAPIKey"), DEFAULT_GOAFFPRO_API_KEY).trim();
                String erpnextBaseUrl = Objects.toString(config.getProperty("erpnextBaseUrl"), "").trim();
                String erpnextApiKey = Objects.toString(config.getProperty("erpnextApiKey"), "").trim();
                String contactEmail = Objects.toString(config.getProperty("contactEmail"), "").trim();
                String smtpHost = Objects.toString(config.getProperty("smtpHost"), "").trim();
                String smtpPort = Objects.toString(config.getProperty("smtpPort"), "587").trim();
                String smtpUsername = Objects.toString(config.getProperty("smtpUsername"), "").trim();
                String emailBcc = Objects.toString(config.getProperty("emailBcc"), "").trim();
                boolean smtpTls = Boolean.parseBoolean(Objects.toString(config.getProperty("smtpTls"), "false"));
                boolean hasSmtpPassword = !Objects.toString(config.getProperty("smtpPassword"), "").trim().isBlank();
                boolean sendEmailsEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("sendEmailsEnabled"), "true"));
                String emailRecipientMode = Objects.toString(config.getProperty("emailRecipientMode"), "contact").trim();
                String emailTemplateHtml = Objects.toString(config.getProperty("emailTemplateHtml"), "");
                String validationReminderTemplateHtml = Objects.toString(config.getProperty("validationReminderTemplateHtml"), "");
                String eInvoicePdfTemplateHtml = Objects.toString(config.getProperty("eInvoicePdfTemplateHtml"), "");
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
                ensureCommissionInHistory(config, activeCommission);
                persistSettings(config);

                Map<String, Object> payload = new HashMap<>();
                payload.put("pdfExportPath", exportDir);
                payload.put("settingsDirectory", resolveSettingsDirectory(config).toString());
                payload.put("lastImportedComission", activeCommission);
                payload.put("goaffproAPIKey", goaffproAPIKey);
                payload.put("erpnextBaseUrl", erpnextBaseUrl);
                payload.put("erpnextApiKey", erpnextApiKey);
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
                    String erpnextBaseUrl = asText(body, "erpnextBaseUrl").trim();
                    String erpnextApiKey = asText(body, "erpnextApiKey").trim();
                    String erpnextApiSecret = asText(body, "erpnextApiSecret").trim();
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
                    if (!"advisor".equals(emailRecipientMode)) emailRecipientMode = "contact";

                    Properties config = loadConfig();
                    Path chosenDir = newPath.isEmpty() ? resolveSettingsDirectory(config) : Paths.get(newPath).toAbsolutePath();
                    Files.createDirectories(chosenDir);

                    config.setProperty("pdfExportPath", chosenDir.toString());
                    if (!goaffproAPIKey.isEmpty()) {
                        config.setProperty("goaffproAPIKey", goaffproAPIKey);
                    }
                    config.setProperty("erpnextBaseUrl", erpnextBaseUrl);
                    config.setProperty("erpnextApiKey", erpnextApiKey);
                    if (!erpnextApiSecret.isBlank()) {
                        config.setProperty("erpnextApiSecret", erpnextApiSecret);
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

                    persistSettings(config);

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("message", "Einstellungen gespeichert.");
                    payload.put("pdfExportPath", Objects.toString(config.getProperty("pdfExportPath"), DEFAULT_PDF_EXPORT_PATH));
                    payload.put("settingsDirectory", resolveSettingsDirectory(config).toString());
                    payload.put("lastImportedComission", Objects.toString(config.getProperty("lastImportedComission"), "0"));
                    payload.put("goaffproAPIKey", Objects.toString(config.getProperty("goaffproAPIKey"), DEFAULT_GOAFFPRO_API_KEY));
                    payload.put("erpnextBaseUrl", Objects.toString(config.getProperty("erpnextBaseUrl"), ""));
                    payload.put("erpnextApiKey", Objects.toString(config.getProperty("erpnextApiKey"), ""));
                    payload.put("contactEmail", Objects.toString(config.getProperty("contactEmail"), ""));
                    payload.put("smtpHost", Objects.toString(config.getProperty("smtpHost"), ""));
                    payload.put("smtpPort", Objects.toString(config.getProperty("smtpPort"), "587"));
                    payload.put("smtpUsername", Objects.toString(config.getProperty("smtpUsername"), ""));
                    payload.put("emailBcc", Objects.toString(config.getProperty("emailBcc"), ""));
                    payload.put("smtpTls", Boolean.parseBoolean(Objects.toString(config.getProperty("smtpTls"), "false")));
                    payload.put("hasSmtpPassword", !Objects.toString(config.getProperty("smtpPassword"), "").trim().isBlank());
                    payload.put("sendEmailsEnabled", Boolean.parseBoolean(Objects.toString(config.getProperty("sendEmailsEnabled"), "true")));
                    payload.put("emailRecipientMode", Objects.toString(config.getProperty("emailRecipientMode"), "contact"));
                    payload.put("emailTemplateHtml", Objects.toString(config.getProperty("emailTemplateHtml"), "").isBlank() ? getDefaultInvoiceMailHtmlTemplate() : Objects.toString(config.getProperty("emailTemplateHtml"), ""));
                    payload.put("emailTemplateHtmlDefault", getDefaultInvoiceMailHtmlTemplate());
                    payload.put("validationReminderTemplateHtml", Objects.toString(config.getProperty("validationReminderTemplateHtml"), "").isBlank() ? getDefaultValidationReminderHtmlTemplate() : Objects.toString(config.getProperty("validationReminderTemplateHtml"), ""));
                    payload.put("validationReminderTemplateHtmlDefault", getDefaultValidationReminderHtmlTemplate());
                    payload.put("eInvoicePdfTemplateHtml", Objects.toString(config.getProperty("eInvoicePdfTemplateHtml"), "").isBlank() ? getDefaultEInvoicePdfViewHtmlTemplate() : Objects.toString(config.getProperty("eInvoicePdfTemplateHtml"), ""));
                    payload.put("eInvoicePdfTemplateHtmlDefault", getDefaultEInvoicePdfViewHtmlTemplate());
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

    private static class ErpnextSalesInvoicesHandler implements HttpHandler {
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
            SessionUser su = requireSession(exchange);
            if (su == null) return;
            try {
                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);

                String baseUrl = Objects.toString(config.getProperty("erpnextBaseUrl"), "").trim();
                String apiKey = Objects.toString(config.getProperty("erpnextApiKey"), "").trim();
                String apiSecret = Objects.toString(config.getProperty("erpnextApiSecret"), "").trim();
                if (baseUrl.isBlank()) {
                    sendResponse(exchange, 400, "application/json", "{\"error\":\"ERPNext Basis-URL fehlt. Bitte in den Einstellungen hinterlegen.\"}");
                    return;
                }
                if (apiKey.isBlank() || apiSecret.isBlank()) {
                    sendResponse(exchange, 400, "application/json", "{\"error\":\"ERPNext API-Zugangsdaten fehlen.\"}");
                    return;
                }

                Map<String, Object> payload = fetchErpnextSalesInvoices(baseUrl, apiKey, apiSecret);
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class ErpnextPurchaseOrdersHandler implements HttpHandler {
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
            SessionUser su = requireSession(exchange);
            if (su == null) return;
            try {
                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);

                String baseUrl = Objects.toString(config.getProperty("erpnextBaseUrl"), "").trim();
                String apiKey = Objects.toString(config.getProperty("erpnextApiKey"), "").trim();
                String apiSecret = Objects.toString(config.getProperty("erpnextApiSecret"), "").trim();
                if (baseUrl.isBlank()) {
                    sendResponse(exchange, 400, "application/json", "{\"error\":\"ERPNext Basis-URL fehlt. Bitte in den Einstellungen hinterlegen.\"}");
                    return;
                }
                if (apiKey.isBlank() || apiSecret.isBlank()) {
                    sendResponse(exchange, 400, "application/json", "{\"error\":\"ERPNext API-Zugangsdaten fehlen.\"}");
                    return;
                }

                Map<String, Object> payload = fetchErpnextPurchaseOrders(baseUrl, apiKey, apiSecret);
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class AsBankAccountsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { sendResponse(exchange, 200, "application/json", "{}"); return; }
            SessionUser su = requireSession(exchange);
            if (su == null) return;
            if (!canAccessDepartment(su, "AS")) { sendResponse(exchange, 403, "application/json", "{\"error\":\"Kein Zugriff auf Bereich AS\"}"); return; }
            try {
                Properties config = loadConfig();
                List<BankAccountRecord> accounts = loadBankAccounts(config);
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("rows", accounts);
                    payload.put("count", accounts.size());
                    sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
                    return;
                }
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}"); return; }
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String name = asText(body, "name").trim();
                String ibanOrAccountNo = asText(body, "ibanOrAccountNo").trim();
                if (name.isBlank() || ibanOrAccountNo.isBlank()) { sendResponse(exchange, 400, "application/json", "{\"error\":\"name und ibanOrAccountNo sind Pflichtfelder\"}"); return; }
                String normalizedIban = normalizeToken(ibanOrAccountNo);
                boolean exists = accounts.stream().anyMatch(a -> normalizeToken(a.ibanOrAccountNo).equals(normalizedIban));
                if (exists) { sendResponse(exchange, 400, "application/json", "{\"error\":\"Bankkonto existiert bereits\"}"); return; }
                BankAccountRecord account = new BankAccountRecord();
                account.id = java.util.UUID.randomUUID().toString();
                account.name = name;
                account.ibanOrAccountNo = ibanOrAccountNo;
                account.bic = asText(body, "bic").trim();
                account.bankName = asText(body, "bankName").trim();
                account.currency = asText(body, "currency").trim();
                account.createdAt = Instant.now().toString();
                accounts.add(account);
                saveBankAccounts(config, accounts);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("message", "Bankkonto angelegt.");
                payload.put("account", account);
                payload.put("rows", accounts);
                payload.put("count", accounts.size());
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class AsMt940ImportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { sendResponse(exchange, 200, "application/json", "{}"); return; }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}"); return; }
            SessionUser su = requireSession(exchange);
            if (su == null) return;
            if (!canAccessDepartment(su, "AS")) { sendResponse(exchange, 403, "application/json", "{\"error\":\"Kein Zugriff auf Bereich AS\"}"); return; }
            try {
                JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String bankAccountId = asText(body, "bankAccountId").trim();
                String fileName = asText(body, "fileName").trim();
                String content = asText(body, "content");
                if (bankAccountId.isBlank() || content.isBlank()) { sendResponse(exchange, 400, "application/json", "{\"error\":\"bankAccountId und content sind Pflichtfelder\"}"); return; }

                Properties config = loadConfig();
                List<BankAccountRecord> accounts = loadBankAccounts(config);
                BankAccountRecord account = accounts.stream().filter(a -> bankAccountId.equals(a.id)).findFirst().orElse(null);
                if (account == null) { sendResponse(exchange, 404, "application/json", "{\"error\":\"Bankkonto nicht gefunden\"}"); return; }

                List<BankTransactionRecord> existing = loadBankTransactions(config);
                Set<String> existingFingerprints = existing.stream()
                        .filter(t -> bankAccountId.equals(t.bankAccountId))
                        .map(t -> Objects.toString(t.fingerprint, ""))
                        .filter(v -> !v.isBlank())
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                List<BankTransactionRecord> parsed = parseImportedTransactions(content, bankAccountId, fileName);
                int duplicates = 0;
                int inserted = 0;
                int ignored = 0;
                for (BankTransactionRecord tx : parsed) {
                    if (tx.fingerprint == null || tx.fingerprint.isBlank()) { ignored++; continue; }
                    if (existingFingerprints.contains(tx.fingerprint)) { duplicates++; continue; }
                    existingFingerprints.add(tx.fingerprint);
                    existing.add(tx);
                    inserted++;
                }
                if (inserted > 0) saveBankTransactions(config, existing);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("message", "Import abgeschlossen.");
                payload.put("totalParsed", parsed.size());
                payload.put("inserted", inserted);
                payload.put("duplicates", duplicates);
                payload.put("ignored", ignored);
                payload.put("bankAccountId", bankAccountId);
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static class AsBankTransactionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { sendResponse(exchange, 200, "application/json", "{}"); return; }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) { sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}"); return; }
            SessionUser su = requireSession(exchange);
            if (su == null) return;
            if (!canAccessDepartment(su, "AS")) { sendResponse(exchange, 403, "application/json", "{\"error\":\"Kein Zugriff auf Bereich AS\"}"); return; }
            try {
                Properties config = loadConfig();
                String query = Objects.toString(exchange.getRequestURI().getQuery(), "");
                Map<String, String> params = parseQueryParams(query);
                String bankAccountId = Objects.toString(params.get("bankAccountId"), "").trim();
                if (bankAccountId.isBlank()) { sendResponse(exchange, 400, "application/json", "{\"error\":\"bankAccountId fehlt\"}"); return; }
                String q = Objects.toString(params.get("q"), "").trim().toLowerCase();
                List<BankTransactionRecord> rows = loadBankTransactions(config).stream()
                        .filter(t -> bankAccountId.equals(Objects.toString(t.bankAccountId, "")))
                        .filter(t -> q.isBlank() || (Objects.toString(t.purpose, "") + " " + Objects.toString(t.counterparty, "") + " " + Objects.toString(t.reference, "")).toLowerCase().contains(q))
                        .sorted((a,b) -> Objects.toString(b.bookingDate, "").compareTo(Objects.toString(a.bookingDate, "")))
                        .collect(Collectors.toList());
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("rows", rows);
                payload.put("count", rows.size());
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
            } catch (Exception e) {
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
                String apiKey = Objects.toString(config.getProperty("goaffproAPIKey"), DEFAULT_GOAFFPRO_API_KEY).trim();

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
                String apiKey = Objects.toString(config.getProperty("goaffproAPIKey"), DEFAULT_GOAFFPRO_API_KEY).trim();

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
                String apiKey = Objects.toString(config.getProperty("goaffproAPIKey"), DEFAULT_GOAFFPRO_API_KEY).trim();

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
                String apiKey = Objects.toString(config.getProperty("goaffproAPIKey"), DEFAULT_GOAFFPRO_API_KEY).trim();

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
                String apiKey = Objects.toString(config.getProperty("goaffproAPIKey"), DEFAULT_GOAFFPRO_API_KEY).trim();

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
                String apiKey = Objects.toString(config.getProperty("goaffproAPIKey"), DEFAULT_GOAFFPRO_API_KEY).trim();

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
                byte[] bytes = Files.readAllBytes(p);
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + p.getFileName().toString().replace("\"", "") + "\"");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
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

                String apiKey = Objects.toString(config.getProperty("goaffproAPIKey"), DEFAULT_GOAFFPRO_API_KEY).trim();
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
                Files.createDirectories(runExportDir);

                String timestamp = FILE_TIMESTAMP.format(LocalDateTime.now());
                String baseFilename = "rechnungsdetails_" + sanitizeFilename(paymentId) + "_" + timestamp;
                Path pdfPath = runExportDir.resolve(baseFilename + ".pdf");
                Path jsonPath = runExportDir.resolve(baseFilename + ".json");
                boolean eInvoiceAttachAndStoreEnabled = includeEInvoiceArtifactsRequest != null
                        ? includeEInvoiceArtifactsRequest
                        : Boolean.parseBoolean(Objects.toString(config.getProperty("eInvoiceAttachAndStoreEnabled"), "true"));
                Path zugferdPath = eInvoiceAttachAndStoreEnabled ? runExportDir.resolve("rechnung_" + sanitizeFilename(paymentId) + "_" + timestamp + ".xml") : null;
                Path eInvoicePdfPath = eInvoiceAttachAndStoreEnabled ? runExportDir.resolve("rechnung_" + sanitizeFilename(paymentId) + "_" + timestamp + ".pdf") : null;
                createInvoiceDetailsPdf(pdfPath, response, affiliate);
                writeOriginalJson(jsonPath, response);
                if (eInvoiceAttachAndStoreEnabled) {
                    createZugferdInvoiceXml(zugferdPath, payment, affiliate, config);
                    createEInvoicePdfWithEmbeddedXml(eInvoicePdfPath, zugferdPath, payment, affiliate, config);
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
                String periodLabel = buildPaymentPeriodLabel(payment);
                if (sendEmailsEnabled) {
                    String affiliateNameForMail = affiliate != null ? asText(affiliate, "name") : "";
                    sendInvoiceMailWithAttachment(targetEmail, Objects.toString(config.getProperty("emailBcc"), "").trim(), pdfPath, jsonPath, zugferdPath, eInvoicePdfPath, eInvoiceAttachAndStoreEnabled, affiliateNameForMail, periodLabel, payment, affiliate, Objects.toString(config.getProperty("emailTemplateHtml"), ""), resolveSmtpConfig(config));
                    String subject = "Provisionszahlung für den Zeitraum " + periodLabel + " - " + ((affiliateNameForMail == null || affiliateNameForMail.isBlank()) ? "Beraterin" : affiliateNameForMail.trim());
                    appendMailLogEntry(config, paymentId, emailRecipientMode, targetEmail, subject, periodLabel, pdfPath, jsonPath, zugferdPath, eInvoicePdfPath);
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
                payload.put("message", sendEmailsEnabled ? ("advisor".equals(emailRecipientMode) ? "Rechnungsdetails-PDF erstellt und an Beraterinnen-E-Mail versendet." : "Rechnungsdetails-PDF erstellt und an Kontakt-E-Mail versendet.") : "Rechnungsdetails-PDF erstellt (E-Mail-Versand deaktiviert).");
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
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }

        private void createInvoiceDetailsPdf(Path pdfPath, JsonNode apiResponse, JsonNode affiliate) throws IOException {
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

                    String titleText = "Provisionsnachweis (Zahllauf) – Direkt- und Team-Provisionen";
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
                            "• Bei Kleinunternehmerregelung (§ 19 UStG): sicherstellen, dass eure Belege/Rechnungen passen.",
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
                    String providerNote = "Dieser Nachweis wurde von der S+R Linear Technology GmbH bereitgestellt. " +
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
            return String.format(java.util.Locale.GERMANY, "%.2f €", value);
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
            String safe = text == null ? "" : text.replaceAll("[\r\n]+", " ");
            return safe.length() > maxLen ? safe.substring(0, maxLen - 1) + "…" : safe;
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
            Map<String, String> payload = new HashMap<>();
            payload.put("version", APP_VERSION);
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

            List<Map<String, String>> versions = readRecentVersions();
            Map<String, Object> payload = new HashMap<>();
            payload.put("versions", versions);
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

    private static void createZugferdInvoiceXml(Path xmlPath, JsonNode payment, JsonNode affiliate, Properties config) throws IOException {
        boolean enabled = Boolean.parseBoolean(Objects.toString(config.getProperty("eInvoiceEnabled"), "true"));
        if (!enabled) {
            Files.writeString(xmlPath, "<!-- ZUGFeRD deaktiviert -->", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return;
        }

        String buyerName = Objects.toString(config.getProperty("eInvoiceBuyerName"), "S+R linear technology gmbh").trim();
        String buyerStreet = Objects.toString(config.getProperty("eInvoiceBuyerStreet"), "").trim();
        String buyerZip = Objects.toString(config.getProperty("eInvoiceBuyerZip"), "").trim();
        String buyerCity = Objects.toString(config.getProperty("eInvoiceBuyerCity"), "").trim();
        String buyerCountry = Objects.toString(config.getProperty("eInvoiceBuyerCountry"), "DE").trim();
        String buyerVatId = Objects.toString(config.getProperty("eInvoiceBuyerVatId"), "").trim();
        String buyerTaxNumber = Objects.toString(config.getProperty("eInvoiceBuyerTaxNumber"), "").trim();
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

        String invoiceId = asText(payment, "id");
        String issueDate = formatDateYmd(asText(payment, "created_at"));
        String currency = asText(payment, "currency");
        if (currency.isBlank()) currency = "EUR";
        double amount = parseDoubleSafeStatic(asText(payment, "amount"));

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
                    <ram:ID>{{invoiceId}}</ram:ID>
                    <ram:TypeCode>380</ram:TypeCode>
                    <ram:IssueDateTime><udt:DateTimeString format="102">{{issueDate}}</udt:DateTimeString></ram:IssueDateTime>
                  </rsm:ExchangedDocument>
                  <rsm:SupplyChainTradeTransaction>
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
                    <ram:ApplicableHeaderTradeSettlement>
                      <ram:InvoiceCurrencyCode>{{currency}}</ram:InvoiceCurrencyCode>
                      <ram:SpecifiedTradeSettlementPaymentMeans>
                        <ram:TypeCode>58</ram:TypeCode>
                        <ram:PayeePartyCreditorFinancialAccount><ram:IBANID>{{bankIban}}</ram:IBANID><ram:AccountName>{{bankAccountHolder}}</ram:AccountName></ram:PayeePartyCreditorFinancialAccount>
                        <ram:PayeeSpecifiedCreditorFinancialInstitution><ram:BICID>{{bankBic}}</ram:BICID></ram:PayeeSpecifiedCreditorFinancialInstitution>
                      </ram:SpecifiedTradeSettlementPaymentMeans>
                      <ram:SpecifiedTradePaymentTerms><ram:Description>{{paymentTerms}}</ram:Description></ram:SpecifiedTradePaymentTerms>
                      <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
                        <ram:LineTotalAmount>{{amount}}</ram:LineTotalAmount>
                        <ram:GrandTotalAmount>{{amount}}</ram:GrandTotalAmount>
                        <ram:DuePayableAmount>{{amount}}</ram:DuePayableAmount>
                      </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
                    </ram:ApplicableHeaderTradeSettlement>
                  </rsm:SupplyChainTradeTransaction>
                </rsm:CrossIndustryInvoice>
                """;
        xml = xml.replace("{{invoiceId}}", escapeXml(invoiceId))
                .replace("{{issueDate}}", escapeXml(issueDate))
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
                .replace("{{amount}}", String.format(java.util.Locale.US, "%.2f", amount));

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

    private static String buildPaymentPeriodLabel(JsonNode payment) {
        JsonNode transactions = payment != null ? payment.get("transactions") : null;
        if (transactions == null || !transactions.isArray() || transactions.size() == 0) {
            return "ohne Zeitraum";
        }

        OffsetDateTime minDate = null;
        OffsetDateTime maxDate = null;
        for (JsonNode tx : transactions) {
            try {
                OffsetDateTime dt = OffsetDateTime.parse(asText(tx, "created_at"));
                if (minDate == null || dt.isBefore(minDate)) minDate = dt;
                if (maxDate == null || dt.isAfter(maxDate)) maxDate = dt;
            } catch (Exception ignored) {
            }
        }
        if (minDate == null || maxDate == null) return "ohne Zeitraum";
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        return minDate.atZoneSameInstant(ZoneId.of("Europe/Berlin")).format(f)
                + " bis "
                + maxDate.atZoneSameInstant(ZoneId.of("Europe/Berlin")).format(f);
    }

    private static void writeOriginalJson(Path jsonPath, JsonNode response) throws IOException {
        String pretty = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response);
        Files.writeString(jsonPath, pretty, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void sendInvoiceMailWithAttachment(String toEmail, String bccEmail, Path pdfPath, Path jsonPath, Path zugferdPath, Path eInvoicePdfPath, boolean includeEInvoiceAttachments, String affiliateName, String periodLabel, JsonNode payment, JsonNode affiliate, String configuredEmailTemplateHtml, SmtpConfig smtpConfig) throws Exception {
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
        String subject = "Provisionszahlung für den Zeitraum " + periodLabel + " - " + displayName;
        message.setSubject(subject, StandardCharsets.UTF_8.name());

        String plainTextBody = buildInvoiceMailBody(payment, affiliate, periodLabel);
        String htmlBody = buildInvoiceMailHtml(payment, affiliate, periodLabel, configuredEmailTemplateHtml);

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
            transport.connect(smtpConfig.host, smtpConfig.port, smtpConfig.username, smtpConfig.password);
            transport.sendMessage(message, message.getAllRecipients());
            System.out.println("E-Mail versendet an " + toEmail + " | Betreff: " + subject);
        } finally {
            transport.close();
        }
    }


    private static String buildInvoiceMailBody(JsonNode payment, JsonNode affiliate, String periodLabel) {
        String affiliateName = affiliate != null ? asText(affiliate, "name") : "";
        String salutationName = (affiliateName == null || affiliateName.isBlank()) ? "liebe Beraterin" : ("liebe " + affiliateName.trim());
        String paymentId = payment != null ? asText(payment, "id") : "";
        String payout = euroStatic(parseDoubleSafeStatic(payment != null ? asText(payment, "amount") : "0"));
        String method = payment != null ? asText(payment, "payment_method") : "";
        String created = formatDateTimeEuropeBerlinStatic(payment != null ? asText(payment, "created_at") : "");

        int txCount = 0;
        JsonNode transactions = payment != null ? payment.get("transactions") : null;
        if (transactions != null && transactions.isArray()) txCount = transactions.size();

        return """
                Hallo %s,

                gerade hat ein neuer Zahllauf stattgefunden. Ihre Provision ist damit zur Auszahlung vorgesehen. Die Überweisung sollte in der Regel innerhalb der nächsten 2 Bankarbeitstage auf Ihrem Konto eingehen.

                Kurze Übersicht zu Ihrem aktuellen Zahllauf:
                - Zeitraum: %s
                - Zahllauf-ID: %s
                - Auszahlungsbetrag: %s
                - Zahlungsmethode: %s
                - Auszahlungsdatum (System): %s
                - Anzahl Transaktionen: %s

                Im Anhang finden Sie Ihren Provisionsnachweis als PDF sowie die zugehörige JSON-Datei.

                Viele Grüße
                Ihr VEMMiNA Team
                """.formatted(salutationName, periodLabel, paymentId, payout, method, created, txCount);
    }

    private static String buildInvoiceMailHtml(JsonNode payment, JsonNode affiliate, String periodLabel, String configuredTemplateHtml) {
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
                ? getDefaultInvoiceMailHtmlTemplate()
                : configuredTemplateHtml;

        return template
                .replace("{{salutationName}}", escapeHtmlEmail(salutationName))
                .replace("{{periodLabel}}", escapeHtmlEmail(periodLabel))
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
                              <h1 style="margin:8px 0 6px 0;font-size:34px;line-height:1.2;">Provisionsinformation</h1>
                              <p style="margin:0;font-size:16px;line-height:1.5;opacity:0.95;">Ihr aktueller Zahllauf wurde erfolgreich verarbeitet.</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:28px;">
                              <p style="margin:0 0 14px 0;font-size:24px;line-height:1.35;color:#1e293b;">Hallo {{salutationName}},</p>
                              <p style="margin:0 0 18px 0;font-size:16px;line-height:1.7;color:#334155;">wir haben einen neuen Zahllauf für Sie verarbeitet. Die Auszahlung sollte in der Regel innerhalb der nächsten 2 Bankarbeitstage auf Ihrem Konto eingehen.</p>

                              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="border-collapse:separate;border-spacing:0;background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;overflow:hidden;">
                                <tr><td style="padding:12px 14px;font-size:15px;color:#1f2937;border-bottom:1px solid #e2e8f0;"><strong>Zeitraum</strong><br/>{{periodLabel}}</td></tr>
                                <tr><td style="padding:12px 14px;font-size:15px;color:#1f2937;border-bottom:1px solid #e2e8f0;"><strong>Zahllauf-ID</strong><br/>{{paymentId}}</td></tr>
                                <tr><td style="padding:12px 14px;font-size:15px;color:#1f2937;border-bottom:1px solid #e2e8f0;"><strong>Auszahlungsbetrag</strong><br/><span style="font-size:20px;font-weight:700;color:#108474;">{{payout}}</span></td></tr>
                                <tr><td style="padding:12px 14px;font-size:15px;color:#1f2937;border-bottom:1px solid #e2e8f0;"><strong>Zahlungsmethode</strong><br/>{{method}}</td></tr>
                                <tr><td style="padding:12px 14px;font-size:15px;color:#1f2937;border-bottom:1px solid #e2e8f0;"><strong>Auszahlungsdatum (System)</strong><br/>{{created}}</td></tr>
                                <tr><td style="padding:12px 14px;font-size:15px;color:#1f2937;"><strong>Anzahl Transaktionen</strong><br/>{{txCount}}</td></tr>
                              </table>

                              <p style="margin:18px 0 0 0;font-size:15px;line-height:1.7;color:#334155;">Im Anhang finden Sie Ihren Provisionsnachweis als PDF sowie die zugehörige JSON-Datei.</p>
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

    private static void appendMailLogEntry(Properties config, String paymentId, String recipientMode, String toEmail, String subject, String periodLabel, Path pdfPath, Path jsonPath, Path zugferdPath, Path eInvoiceViewPdfPath) {
        List<Map<String, String>> entries = readMailLogEntries(config);
        Map<String, String> row = new LinkedHashMap<>();
        row.put("paymentId", Objects.toString(paymentId, ""));
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
            transport.connect(smtpConfig.host, smtpConfig.port, smtpConfig.username, smtpConfig.password);
            transport.sendMessage(message, message.getAllRecipients());
        } finally {
            transport.close();
        }
    }

    private static void createEInvoicePdfWithEmbeddedXml(Path pdfPath, Path xmlPath, JsonNode payment, JsonNode affiliate, Properties config) throws IOException {
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
        String amount = euroStatic(parseDoubleSafeStatic(payment != null ? asText(payment, "amount") : "0"));
        String buyerCompanyName = Objects.toString(config.getProperty("eInvoiceBuyerName"), "").trim();
        String buyerStreet = Objects.toString(config.getProperty("eInvoiceBuyerStreet"), "").trim();
        String buyerZip = Objects.toString(config.getProperty("eInvoiceBuyerZip"), "").trim();
        String buyerCity = Objects.toString(config.getProperty("eInvoiceBuyerCity"), "").trim();
        String buyerCountry = Objects.toString(config.getProperty("eInvoiceBuyerCountry"), "DE").trim();
        String buyerVatId = Objects.toString(config.getProperty("eInvoiceBuyerVatId"), "").trim();
        String buyerTaxNumber = Objects.toString(config.getProperty("eInvoiceBuyerTaxNumber"), "").trim();
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

                // ── HEADER: advisor name + address as small letterhead line ──
                String hdrLine = advisorName + (advisorAddressOneLiner.isBlank() ? "" : " \u00b7 " + advisorAddressOneLiner);
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

                // LEFT: buyer address block
                cs.setNonStrokingColor(new Color(130, 130, 130));
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 8f);
                cs.newLineAtOffset(colL, y); cs.showText("Rechnungsempf\u00e4nger"); cs.endText();
                y -= 14f;
                cs.setNonStrokingColor(new Color(15, 15, 15));
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA_BOLD, 12f);
                cs.newLineAtOffset(colL, y);
                cs.showText(sanitizePdfText(shortenForPdf(buyerCompanyName, 32))); cs.endText();
                y -= 14f;
                List<String> buyerLines = new ArrayList<>();
                if (!buyerStreet.isBlank()) buyerLines.add(buyerStreet);
                String cLine = (buyerZip + " " + buyerCity).trim();
                if (!cLine.isBlank()) buyerLines.add(cLine);
                if (!buyerCountry.isBlank()) buyerLines.add(buyerCountry);
                for (String bl : buyerLines) {
                    cs.setNonStrokingColor(new Color(40, 40, 40));
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 10f);
                    cs.newLineAtOffset(colL, y); cs.showText(sanitizePdfText(bl)); cs.endText();
                    y -= 13f;
                }
                if (!buyerVatId.isBlank()) {
                    cs.setNonStrokingColor(new Color(100, 100, 100));
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 8.5f);
                    cs.newLineAtOffset(colL, y); cs.showText("USt-IdNr: " + sanitizePdfText(buyerVatId)); cs.endText();
                    y -= 11f;
                }
                if (!buyerTaxNumber.isBlank()) {
                    cs.setNonStrokingColor(new Color(100, 100, 100));
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 8.5f);
                    cs.newLineAtOffset(colL, y); cs.showText("Steuernummer: " + sanitizePdfText(buyerTaxNumber)); cs.endText();
                    y -= 11f;
                }
                float leftEndY = y;

                // RIGHT: contact info + invoice metadata
                float ry = startY2col;
                List<String[]> metaRows = new ArrayList<>();
                if (!advisorEmail.isBlank()) metaRows.add(new String[]{"E-Mail:", advisorEmail});
                if (!advisorPhone.isBlank()) metaRows.add(new String[]{"Telefon:", advisorPhone});
                if (!advisorTaxNumber.isBlank()) metaRows.add(new String[]{"Steuernummer:", advisorTaxNumber});
                metaRows.add(new String[]{"Rechnungsnummer:", paymentId});
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

                // ── INVOICE TITLE BOX ──
                float boxH = 28f;
                cs.setNonStrokingColor(new Color(235, 235, 235));
                cs.addRect(left, y - boxH, usableW, boxH); cs.fill();
                cs.setStrokingColor(new Color(200, 200, 200)); cs.setLineWidth(0.4f);
                cs.addRect(left, y - boxH, usableW, boxH); cs.stroke();
                cs.setNonStrokingColor(new Color(15, 15, 15));
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA_BOLD, 13f);
                cs.newLineAtOffset(left + 8f, y - 19f);
                cs.showText("RECHNUNG  Nr. " + sanitizePdfText(paymentId)); cs.endText();
                cs.setNonStrokingColor(new Color(80, 80, 80));
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 9.5f);
                cs.newLineAtOffset(right - 135f, y - 19f);
                cs.showText("Datum: " + sanitizePdfText(created)); cs.endText();
                y -= boxH + 14f;

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
                String desc = "Provisionsabrechnung Zahllauf " + sanitizePdfText(paymentId);
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

                // VAT note
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

                // Grand Total
                cs.setNonStrokingColor(new Color(225, 225, 225));
                cs.addRect(tX, y - 24f, tW, 24f); cs.fill();
                cs.setStrokingColor(new Color(180, 180, 180));
                cs.addRect(tX, y - 24f, tW, 24f); cs.stroke();
                cs.setNonStrokingColor(new Color(15, 15, 15));
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA_BOLD, 11f);
                cs.newLineAtOffset(tX + 4f, y - 16f); cs.showText("Gesamtbetrag"); cs.endText();
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA_BOLD, 11f);
                cs.newLineAtOffset(tValX, y - 16f); cs.showText(sanitizePdfText(amount)); cs.endText();
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
                cs.newLineAtOffset(left, y); cs.showText("Bankverbindung"); cs.endText();
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

                // ── BOTTOM FOOTER ──
                float footerY = 35f;
                cs.setStrokingColor(new Color(180, 180, 180)); cs.setLineWidth(0.4f);
                cs.moveTo(left, footerY + 12f); cs.lineTo(right, footerY + 12f); cs.stroke();
                cs.setNonStrokingColor(new Color(120, 120, 120));
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 7f);
                cs.newLineAtOffset(left, footerY);
                cs.showText(sanitizePdfText(shortenForPdf(hdrLine, 95))); cs.endText();
                if (!advisorTaxNumber.isBlank()) {
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 7f);
                    cs.newLineAtOffset(left, footerY - 9f);
                    cs.showText("Steuernummer: " + sanitizePdfText(advisorTaxNumber)); cs.endText();
                }
            }
            if (xmlPath != null && Files.exists(xmlPath)) {
                attachZugferdXmlToPdf(document, xmlPath);
            }
            document.save(pdfPath.toFile());
        }
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
        StringBuilder out = new StringBuilder(value.length());
        value.codePoints().forEach(cp -> {
            if (cp == '\n' || cp == '\r' || cp == '\t') {
                out.append(' ');
                return;
            }
            if (cp < 32 || (cp >= 127 && cp <= 159)) {
                out.append(' ');
                return;
            }
            if (cp > 255) {
                out.append('?');
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

    private static String renderEInvoicePdfViewHtml(String template, JsonNode payment, JsonNode affiliate, Properties config) {
        String advisorName = affiliate != null ? asText(affiliate, "name") : "Beraterin";
        String advisorAddress = formatAffiliateAddress(affiliate);
        String advisorEmail = affiliate != null ? asText(affiliate, "email") : "";
        String advisorPhone = affiliate != null ? asText(affiliate, "phone") : "";
        String tax = affiliate != null ? asText(affiliate, "tax_identification_number") : "";
        String paymentId = payment != null ? asText(payment, "id") : "-";
        String created = formatDateTimeEuropeBerlinStatic(payment != null ? asText(payment, "created_at") : "");
        String amount = euroStatic(parseDoubleSafeStatic(payment != null ? asText(payment, "amount") : "0"));
        String currency = payment != null ? asText(payment, "currency") : "EUR";
        String buyerCompanyName = Objects.toString(config.getProperty("eInvoiceBuyerName"), "S+R linear technology gmbh").trim();
        String buyerStreet = Objects.toString(config.getProperty("eInvoiceBuyerStreet"), "").trim();
        String buyerZip = Objects.toString(config.getProperty("eInvoiceBuyerZip"), "").trim();
        String buyerCity = Objects.toString(config.getProperty("eInvoiceBuyerCity"), "").trim();
        String buyerCountry = Objects.toString(config.getProperty("eInvoiceBuyerCountry"), "DE").trim();
        String buyerVatId = Objects.toString(config.getProperty("eInvoiceBuyerVatId"), "").trim();
        String buyerTaxNumber = Objects.toString(config.getProperty("eInvoiceBuyerTaxNumber"), "").trim();
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
                .replace("{{invoiceNumber}}", escapeHtmlEmail(paymentId))
                .replace("{{paymentId}}", escapeHtmlEmail(paymentId))
                .replace("{{created}}", escapeHtmlEmail(created))
                .replace("{{amount}}", escapeHtmlEmail(amount))
                .replace("{{currency}}", escapeHtmlEmail(currency));
    }

    private static String getDefaultEInvoicePdfViewHtmlTemplate() {
        return """
                <!doctype html>
                <html lang="de"><body style="font-family:Arial,sans-serif;background:#f3f4f6;color:#111827;padding:20px;">
                <div style="max-width:900px;margin:0 auto;background:#fff;border:1px solid #d1d5db;padding:22px;">
                  <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:16px;">
                    <div>
                      <div style="font-size:22px;font-weight:700;letter-spacing:.3px;">RECHNUNG</div>
                      <div style="margin-top:6px;font-size:13px;color:#374151;">Rechnungsnummer: <b>{{invoiceNumber}}</b></div>
                      <div style="font-size:13px;color:#374151;">Datum: {{created}}</div>
                    </div>
                    <div style="text-align:right;font-size:12px;color:#4b5563;">
                      <div><b>Rechnungsstellerin</b></div>
                      <div>{{advisorName}}</div>
                      <div>{{advisorAddress}}</div>
                      <div>E-Mail: {{advisorEmail}}</div>
                      <div>Telefon: {{advisorPhone}}</div>
                      <div>Steuernummer: {{advisorTaxNumber}}</div>
                    </div>
                  </div>

                  <div style="margin-top:20px;padding:12px;border:1px solid #d1d5db;background:#fafafa;">
                    <div style="font-size:12px;color:#6b7280;">Rechnungsempfänger</div>
                    <div style="font-size:16px;font-weight:700;">{{buyerCompanyName}}</div>
                    <div style="font-size:13px;">{{buyerAddress}}</div>
                    <div style="font-size:12px;color:#6b7280;">USt-IdNr: {{buyerVatId}} | Steuernummer: {{buyerTaxNumber}}</div>
                  </div>

                  <table style="width:100%;margin-top:22px;border-collapse:collapse;font-size:13px;">
                    <thead>
                      <tr style="background:#f3f4f6;">
                        <th style="text-align:left;padding:8px;border:1px solid #d1d5db;">Pos.</th>
                        <th style="text-align:left;padding:8px;border:1px solid #d1d5db;">Beschreibung</th>
                        <th style="text-align:right;padding:8px;border:1px solid #d1d5db;">Betrag</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td style="padding:8px;border:1px solid #d1d5db;">1</td>
                        <td style="padding:8px;border:1px solid #d1d5db;">Provisionsabrechnung Zahllauf {{paymentId}}</td>
                        <td style="padding:8px;border:1px solid #d1d5db;text-align:right;">{{amount}} ({{currency}})</td>
                      </tr>
                    </tbody>
                  </table>

                  <div style="margin-top:18px;display:flex;justify-content:flex-end;">
                    <table style="min-width:280px;border-collapse:collapse;font-size:13px;">
                      <tr><td style="padding:6px 10px;border:1px solid #d1d5db;">Zwischensumme</td><td style="padding:6px 10px;border:1px solid #d1d5db;text-align:right;">{{amount}}</td></tr>
                      <tr><td style="padding:6px 10px;border:1px solid #d1d5db;">USt.</td><td style="padding:6px 10px;border:1px solid #d1d5db;text-align:right;">n. V. / laut Stammdaten</td></tr>
                      <tr style="font-weight:700;"><td style="padding:6px 10px;border:1px solid #d1d5db;">Gesamtbetrag</td><td style="padding:6px 10px;border:1px solid #d1d5db;text-align:right;">{{amount}}</td></tr>
                    </table>
                  </div>

                  <div style="margin-top:18px;font-size:12px;color:#374151;line-height:1.5;">
                    <div><b>Bankverbindung der Rechnungsstellerin:</b> {{advisorAccountHolder}}, IBAN {{advisorIban}}, BIC {{advisorBic}}</div>
                    <div style="margin-top:8px;">Hinweis: Diese Rechnung wird von der Beraterin an {{buyerCompanyName}} gestellt. Die App erzeugt die Unterlagen als Service.</div>
                  </div>
                </div>
                </body></html>
                """;
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


    private static class SessionUser {
        final String username;
        final boolean isAdmin;
        final String department;
        SessionUser(String username, boolean isAdmin, String department) { this.username = username; this.isAdmin = isAdmin; this.department = department; }
    }

    private static class UserAccount {
        public String username;
        public String firstName;
        public String lastName;
        public String email;
        public String phone;
        public String department;
        public boolean isAdmin;
        public boolean forcePasswordChange;
        public String passwordSalt;
        public String passwordHash;
    }

    private static SessionUser requireSession(HttpExchange exchange) throws IOException {
        String token = Objects.toString(exchange.getRequestHeaders().getFirst("X-Auth-Token"), "").trim();
        SessionUser su = ACTIVE_SESSIONS.get(token);
        if (su == null) {
            sendResponse(exchange, 401, "application/json", "{\"error\":\"Nicht angemeldet\"}");
            return null;
        }
        return su;
    }

    private static Path resolveUserStorePath(Properties config) {
        String exportDir = Objects.toString(config.getProperty("pdfExportPath"), DEFAULT_PDF_EXPORT_PATH).trim();
        if (exportDir.isBlank()) exportDir = DEFAULT_PDF_EXPORT_PATH;
        return Paths.get(exportDir).toAbsolutePath().resolve(USER_STORE_FILENAME);
    }

    private static List<UserAccount> loadUserAccounts(Properties config) throws Exception {
        ensureUserStoreInitialized(config);
        Path p = resolveUserStorePath(config);
        try {
            byte[] enc = Files.readAllBytes(p);
            byte[] plain = decrypt(enc, Objects.toString(config.getProperty("authSecret"), AUTH_SECRET_DEFAULT));
            return OBJECT_MAPPER.readValue(plain, new TypeReference<List<UserAccount>>(){});
        } catch (Exception ex) {
            List<UserAccount> defaults = new ArrayList<>(List.of(buildDefaultAdminUser(config)));
            saveUserAccounts(config, defaults);
            return defaults;
        }
    }

    private static void saveUserAccounts(Properties config, List<UserAccount> users) throws Exception {
        Path p = resolveUserStorePath(config);
        Files.createDirectories(p.getParent());
        byte[] raw = OBJECT_MAPPER.writeValueAsBytes(users);
        byte[] enc = encrypt(raw, Objects.toString(config.getProperty("authSecret"), AUTH_SECRET_DEFAULT));
        Files.write(p, enc, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void ensureUserStoreInitialized(Properties config) throws Exception {
        if (Objects.toString(config.getProperty("adminUsername"), "").isBlank()) config.setProperty("adminUsername", "admin");
        if (Objects.toString(config.getProperty("adminPassword"), "").isBlank()) config.setProperty("adminPassword", "admin");
        if (Objects.toString(config.getProperty("authSecret"), "").isBlank()) config.setProperty("authSecret", AUTH_SECRET_DEFAULT);
        Path p = resolveUserStorePath(config);
        if (Files.exists(p)) return;
        UserAccount admin = buildDefaultAdminUser(config);
        saveUserAccounts(config, new ArrayList<>(List.of(admin)));
        persistSettings(config);
    }

    private static UserAccount buildDefaultAdminUser(Properties config) {
        UserAccount admin = new UserAccount();
        admin.username = Objects.toString(config.getProperty("adminUsername"), "admin");
        admin.firstName = "Admin";
        admin.lastName = "User";
        admin.department = "ALL";
        admin.isAdmin = true;
        admin.email = "";
        admin.phone = "";
        admin.forcePasswordChange = false;
        String[] pw = hashPassword(Objects.toString(config.getProperty("adminPassword"), "admin"));
        admin.passwordSalt = pw[0];
        admin.passwordHash = pw[1];
        return admin;
    }

    private static byte[] encrypt(byte[] plain, String secret) throws Exception {
        byte[] key = sha256Bytes(secret.getBytes(StandardCharsets.UTF_8));
        byte[] iv = new byte[12];
        SECURE_RANDOM.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] enc = c.doFinal(plain);
        byte[] out = new byte[iv.length + enc.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(enc, 0, out, iv.length, enc.length);
        return out;
    }

    private static byte[] decrypt(byte[] data, String secret) throws Exception {
        byte[] key = sha256Bytes(secret.getBytes(StandardCharsets.UTF_8));
        byte[] iv = java.util.Arrays.copyOfRange(data, 0, 12);
        byte[] enc = java.util.Arrays.copyOfRange(data, 12, data.length);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return c.doFinal(enc);
    }

    private static String[] hashPassword(String password) {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        String saltB64 = Base64.getEncoder().encodeToString(salt);
        String hash = sha256Hex(saltB64 + ":" + (password == null ? "" : password));
        return new String[]{saltB64, hash};
    }

    private static boolean verifyPassword(String password, String saltB64, String hash) {
        return sha256Hex(Objects.toString(saltB64, "") + ":" + (password == null ? "" : password)).equals(Objects.toString(hash, ""));
    }

    private static byte[] sha256Bytes(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(data);
    }

    private static String generateToken() {
        byte[] b = new byte[24];
        SECURE_RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String randomPassword() {
        byte[] b = new byte[9];
        SECURE_RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static final java.util.Set<String> VALID_DEPARTMENTS = java.util.Set.of("AS", "VEMMINA", "LT", "ALL");

    private static String normalizeDepartment(String dep) {
        if (dep == null || dep.isBlank()) return "VEMMINA";
        String[] parts = dep.split("[,;\\s]+");
        List<String> result = new ArrayList<>();
        for (String p : parts) {
            String d = p.trim().toUpperCase();
            if (VALID_DEPARTMENTS.contains(d)) result.add(d);
        }
        if (result.isEmpty()) return "VEMMINA";
        if (result.contains("ALL")) return "ALL";
        return String.join(",", result);
    }

    private static Map<String, Object> userToMap(UserAccount u) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("username", u.username);
        m.put("firstName", u.firstName);
        m.put("lastName", u.lastName);
        m.put("email", u.email);
        m.put("phone", u.phone);
        m.put("department", u.department);
        m.put("isAdmin", u.isAdmin);
        m.put("forcePasswordChange", u.forcePasswordChange);
        return m;
    }

    private static void trySendPasswordChangeMail(String to, String username, String tempPassword, Properties config) {
        try {
            if (to == null || to.isBlank()) return;
            SmtpConfig smtpConfig = resolveSmtpConfig(config);
            if (smtpConfig.username == null || smtpConfig.username.isBlank()) return;
            String subject = "Zugang eingerichtet - Passwort bitte ändern";
            String text = "Hallo " + username + "\n\nIhr Zugang wurde eingerichtet.\nTemporäres Passwort: " + tempPassword + "\nBitte melden Sie sich an und ändern Sie das Passwort umgehend.";
            String html = "<p>Hallo " + escapeHtmlEmail(username) + ",</p><p>Ihr Zugang wurde eingerichtet.</p><p><b>Temporäres Passwort:</b> " + escapeHtmlEmail(tempPassword) + "</p><p>Bitte melden Sie sich an und ändern Sie das Passwort umgehend.</p>";
            sendSimpleHtmlMail(to, Objects.toString(config.getProperty("emailBcc"), ""), subject, text, html, smtpConfig);
        } catch (Exception ignored) {}
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
                Objects.toString(config.getProperty("smtpPassword"), ""),
                System.getenv("GOAFFPRO_SMTP_PASSWORD")
        );
        String tlsRaw = firstNonBlank(
                Objects.toString(config.getProperty("smtpTls"), ""),
                System.getenv("GOAFFPRO_SMTP_TLS"),
                "false"
        );

        if (host.isBlank() || username.isBlank() || password.isBlank()) {
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

    private static Map<String, Object> fetchErpnextSalesInvoices(String baseUrl, String apiKey, String apiSecret) throws Exception {
        String normalizedBase = baseUrl.replaceAll("/+$", "");
        String fields = "[\"name\",\"posting_date\",\"customer\",\"customer_name\",\"grand_total\",\"outstanding_amount\",\"status\",\"currency\",\"due_date\",\"company\"]";
        String query = "fields=" + URLEncoder.encode(fields, StandardCharsets.UTF_8)
                + "&limit_page_length=200"
                + "&order_by=" + URLEncoder.encode("posting_date desc", StandardCharsets.UTF_8);
        String endpoint = normalizedBase + "/api/resource/Sales%20Invoice?" + query;

        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "token " + apiKey + ":" + apiSecret);
        connection.setRequestProperty("Accept", "application/json");

        int code = connection.getResponseCode();
        InputStream bodyStream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String body = bodyStream == null ? "" : new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8);
        if (code < 200 || code >= 300) {
            throw new IOException("ERPNext API Fehler (" + code + "): " + body);
        }

        JsonNode root = OBJECT_MAPPER.readTree(body);
        JsonNode dataNode = root.path("data");
        List<Map<String, Object>> rows = new ArrayList<>();
        if (dataNode.isArray()) {
            for (JsonNode row : dataNode) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("name", asText(row, "name"));
                out.put("postingDate", asText(row, "posting_date"));
                out.put("dueDate", asText(row, "due_date"));
                out.put("customer", asText(row, "customer"));
                out.put("customerName", asText(row, "customer_name"));
                out.put("grandTotal", asText(row, "grand_total"));
                out.put("outstandingAmount", asText(row, "outstanding_amount"));
                out.put("currency", asText(row, "currency"));
                out.put("status", asText(row, "status"));
                out.put("company", asText(row, "company"));
                rows.add(out);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rows", rows);
        payload.put("count", rows.size());
        return payload;
    }

    private static Map<String, Object> fetchErpnextPurchaseOrders(String baseUrl, String apiKey, String apiSecret) throws Exception {
        String normalizedBase = baseUrl.replaceAll("/+$", "");
        String fields = "[\"name\",\"transaction_date\",\"schedule_date\",\"supplier\",\"supplier_name\",\"grand_total\",\"per_received\",\"per_billed\",\"status\",\"company\",\"currency\"]";
        String query = "fields=" + URLEncoder.encode(fields, StandardCharsets.UTF_8)
                + "&limit_page_length=200"
                + "&order_by=" + URLEncoder.encode("transaction_date desc", StandardCharsets.UTF_8);
        String endpoint = normalizedBase + "/api/resource/Purchase%20Order?" + query;

        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "token " + apiKey + ":" + apiSecret);
        connection.setRequestProperty("Accept", "application/json");

        int code = connection.getResponseCode();
        InputStream bodyStream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String body = bodyStream == null ? "" : new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8);
        if (code < 200 || code >= 300) {
            throw new IOException("ERPNext API Fehler (" + code + "): " + body);
        }

        JsonNode root = OBJECT_MAPPER.readTree(body);
        JsonNode dataNode = root.path("data");
        List<Map<String, Object>> rows = new ArrayList<>();
        if (dataNode.isArray()) {
            for (JsonNode row : dataNode) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("name", asText(row, "name"));
                out.put("transactionDate", asText(row, "transaction_date"));
                out.put("scheduleDate", asText(row, "schedule_date"));
                out.put("supplier", asText(row, "supplier"));
                out.put("supplierName", asText(row, "supplier_name"));
                out.put("grandTotal", asText(row, "grand_total"));
                out.put("perReceived", asText(row, "per_received"));
                out.put("perBilled", asText(row, "per_billed"));
                out.put("currency", asText(row, "currency"));
                out.put("status", asText(row, "status"));
                out.put("company", asText(row, "company"));
                rows.add(out);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rows", rows);
        payload.put("count", rows.size());
        return payload;
    }

    private static Properties loadConfig() throws IOException {
        Properties properties = new Properties();
        try (InputStream is = Files.newInputStream(CONFIG_PATH)) {
            properties.load(is);
        }
        return properties;
    }

    private static void storeConfig(Properties properties) throws IOException {
        try (OutputStream os = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(os, "Updated by WebUiServer");
        }
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
        Files.createDirectories(directory);
        Properties ui = new Properties();
        ui.setProperty("pdfExportPath", Objects.toString(source.getProperty("pdfExportPath"), directory.toString()));
        ui.setProperty("lastImportedComission", Objects.toString(source.getProperty("lastImportedComission"), "0"));
        ui.setProperty("goaffproAPIKey", Objects.toString(source.getProperty("goaffproAPIKey"), DEFAULT_GOAFFPRO_API_KEY));
        ui.setProperty("erpnextBaseUrl", Objects.toString(source.getProperty("erpnextBaseUrl"), ""));
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
        ui.setProperty(COMMISSION_HISTORY_KEY, String.join(",", getCommissionHistory(source)));
        ui.setProperty(COMMISSION_HISTORY_DATES_KEY, Objects.toString(source.getProperty(COMMISSION_HISTORY_DATES_KEY), ""));
        ui.setProperty(MAIL_LOG_KEY, Objects.toString(source.getProperty(MAIL_LOG_KEY), ""));
        ui.setProperty(REMINDER_LOG_KEY, Objects.toString(source.getProperty(REMINDER_LOG_KEY), ""));

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

        config.setProperty("erpnextBaseUrl", Objects.toString(uiSettings.getProperty("erpnextBaseUrl"), Objects.toString(config.getProperty("erpnextBaseUrl"), "")).trim());

        String uiContactEmail = Objects.toString(uiSettings.getProperty("contactEmail"), "").trim();
        if (!uiContactEmail.isEmpty() || config.containsKey("contactEmail")) {
            config.setProperty("contactEmail", uiContactEmail);
        }

        config.setProperty("smtpHost", Objects.toString(uiSettings.getProperty("smtpHost"), Objects.toString(config.getProperty("smtpHost"), "")).trim());
        config.setProperty("smtpPort", Objects.toString(uiSettings.getProperty("smtpPort"), Objects.toString(config.getProperty("smtpPort"), "587")).trim());
        config.setProperty("smtpUsername", Objects.toString(uiSettings.getProperty("smtpUsername"), Objects.toString(config.getProperty("smtpUsername"), "")).trim());
        config.setProperty("emailBcc", Objects.toString(uiSettings.getProperty("emailBcc"), Objects.toString(config.getProperty("emailBcc"), "")).trim());
        String uiSmtpPassword = Objects.toString(uiSettings.getProperty("smtpPassword"), "").trim();
        if (!uiSmtpPassword.isEmpty() || config.containsKey("smtpPassword")) {
            config.setProperty("smtpPassword", uiSmtpPassword);
        }
        config.setProperty("smtpTls", Objects.toString(uiSettings.getProperty("smtpTls"), Objects.toString(config.getProperty("smtpTls"), "false")).trim());
        config.setProperty("sendEmailsEnabled", Objects.toString(uiSettings.getProperty("sendEmailsEnabled"), Objects.toString(config.getProperty("sendEmailsEnabled"), "true")).trim());
        String uiEmailRecipientMode = Objects.toString(uiSettings.getProperty("emailRecipientMode"), Objects.toString(config.getProperty("emailRecipientMode"), "contact")).trim();
        if (!"advisor".equals(uiEmailRecipientMode)) uiEmailRecipientMode = "contact";
        config.setProperty("emailRecipientMode", uiEmailRecipientMode);
        config.setProperty("emailTemplateHtml", Objects.toString(uiSettings.getProperty("emailTemplateHtml"), Objects.toString(config.getProperty("emailTemplateHtml"), "")));
        config.setProperty("validationReminderTemplateHtml", Objects.toString(uiSettings.getProperty("validationReminderTemplateHtml"), Objects.toString(config.getProperty("validationReminderTemplateHtml"), "")));
        config.setProperty("eInvoicePdfTemplateHtml", Objects.toString(uiSettings.getProperty("eInvoicePdfTemplateHtml"), Objects.toString(config.getProperty("eInvoicePdfTemplateHtml"), "")));
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

        config.setProperty(MAIL_LOG_KEY, Objects.toString(uiSettings.getProperty(MAIL_LOG_KEY), Objects.toString(config.getProperty(MAIL_LOG_KEY), "")));
        config.setProperty(REMINDER_LOG_KEY, Objects.toString(uiSettings.getProperty(REMINDER_LOG_KEY), Objects.toString(config.getProperty(REMINDER_LOG_KEY), "")));

        ensureCommissionInHistory(config, Objects.toString(config.getProperty("lastImportedComission"), "0"));
    }

    private static void persistSettings(Properties config) throws IOException {
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

    private static boolean canAccessDepartment(SessionUser su, String required) {
        if (su == null) return false;
        if (su.isAdmin) return true;
        String dep = Objects.toString(su.department, "").toUpperCase();
        if (dep.contains("ALL")) return true;
        String req = Objects.toString(required, "").toUpperCase();
        if (req.isBlank()) return true;
        for (String part : dep.split("[,;\\s]+")) {
            if (part.trim().equals(req)) return true;
        }
        return false;
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> out = new LinkedHashMap<>();
        if (query == null || query.isBlank()) return out;
        for (String part : query.split("&")) {
            if (part.isBlank()) continue;
            String[] kv = part.split("=", 2);
            String key = java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String val = kv.length > 1 ? java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            out.put(key, val);
        }
        return out;
    }

    private static Path bankAccountsFile(Path settingsDir) { return settingsDir.resolve("bank_accounts.json"); }
    private static Path bankTransactionsFile(Path settingsDir) { return settingsDir.resolve("bank_transactions.json"); }

    private static List<BankAccountRecord> loadBankAccounts(Properties config) {
        try {
            Path settingsDir = resolveSettingsDirectory(config);
            Files.createDirectories(settingsDir);
            Path file = bankAccountsFile(settingsDir);
            if (!Files.exists(file)) return new ArrayList<>();
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            if (raw.isBlank()) return new ArrayList<>();
            return OBJECT_MAPPER.readValue(raw, new TypeReference<List<BankAccountRecord>>(){});
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private static void saveBankAccounts(Properties config, List<BankAccountRecord> rows) throws IOException {
        Path settingsDir = resolveSettingsDirectory(config);
        Files.createDirectories(settingsDir);
        String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(rows == null ? new ArrayList<>() : rows);
        Files.writeString(bankAccountsFile(settingsDir), json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static List<BankTransactionRecord> loadBankTransactions(Properties config) {
        try {
            Path settingsDir = resolveSettingsDirectory(config);
            Files.createDirectories(settingsDir);
            Path file = bankTransactionsFile(settingsDir);
            if (!Files.exists(file)) return new ArrayList<>();
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            if (raw.isBlank()) return new ArrayList<>();
            return OBJECT_MAPPER.readValue(raw, new TypeReference<List<BankTransactionRecord>>(){});
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private static void saveBankTransactions(Properties config, List<BankTransactionRecord> rows) throws IOException {
        Path settingsDir = resolveSettingsDirectory(config);
        Files.createDirectories(settingsDir);
        String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(rows == null ? new ArrayList<>() : rows);
        Files.writeString(bankTransactionsFile(settingsDir), json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static List<BankTransactionRecord> parseMt940Transactions(String content, String bankAccountId, String sourceFileName) {
        List<BankTransactionRecord> result = new ArrayList<>();
        if (content == null || content.isBlank()) return result;

        String[] lines = content.replace("\r", "").split("\n");
        List<String> logical = new ArrayList<>();
        for (String line : lines) {
            String ln = Objects.toString(line, "");
            if (ln.startsWith(":")) logical.add(ln);
            else if (!logical.isEmpty()) logical.set(logical.size() - 1, logical.get(logical.size() - 1) + " " + ln.trim());
        }

        BankTransactionRecord current = null;
        for (String line : logical) {
            if (line.startsWith(":61:")) {
                current = parseMt940Line61(line.substring(4));
                if (current == null) continue;
                current.bankAccountId = bankAccountId;
                current.importedAt = Instant.now().toString();
                current.sourceFileName = Objects.toString(sourceFileName, "");
                result.add(current);
                continue;
            }
            if (line.startsWith(":86:") && current != null) {
                Tag86Data tag86 = parseMt940Tag86(line.substring(4).trim());
                if (!tag86.purpose.isBlank()) {
                    current.purpose = current.purpose == null || current.purpose.isBlank()
                            ? tag86.purpose
                            : (current.purpose + " " + tag86.purpose).trim();
                }
                if (!tag86.counterparty.isBlank()) {
                    current.counterparty = tag86.counterparty;
                }
                continue;
            }
        }

        for (BankTransactionRecord tx : result) tx.fingerprint = buildTransactionFingerprint(tx);
        return result;
    }

    private static List<BankTransactionRecord> parseImportedTransactions(String content, String bankAccountId, String sourceFileName) {
        String normalized = Objects.toString(content, "");
        String firstChunk = normalized.stripLeading();
        if (sourceFileName != null && sourceFileName.toLowerCase().endsWith(".csv")) {
            return parseBankCsvTransactions(normalized, bankAccountId, sourceFileName);
        }
        if (firstChunk.startsWith("Bezeichnung Auftragskonto;") || firstChunk.startsWith("\uFEFFBezeichnung Auftragskonto;")) {
            return parseBankCsvTransactions(normalized, bankAccountId, sourceFileName);
        }
        return parseMt940Transactions(normalized, bankAccountId, sourceFileName);
    }

    private static List<BankTransactionRecord> parseBankCsvTransactions(String content, String bankAccountId, String sourceFileName) {
        List<BankTransactionRecord> rows = new ArrayList<>();
        if (content == null || content.isBlank()) return rows;
        String normalized = content.replace("\r", "");
        String[] lines = normalized.split("\n");
        if (lines.length < 2) return rows;

        List<String> headers = splitCsvSemicolonLine(lines[0]);
        if (!headers.isEmpty() && headers.get(0).startsWith("\uFEFF")) {
            headers.set(0, headers.get(0).substring(1));
        }
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) idx.put(headers.get(i).trim(), i);

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.trim().isEmpty()) continue;
            List<String> cells = splitCsvSemicolonLine(line);
            BankTransactionRecord tx = new BankTransactionRecord();
            tx.bankAccountId = bankAccountId;
            tx.sourceFileName = Objects.toString(sourceFileName, "");
            tx.importedAt = Instant.now().toString();

            tx.bookingDate = parseGermanDateIso(getCsvCell(cells, idx, "Buchungstag"));
            tx.valueDate = parseGermanDateIso(getCsvCell(cells, idx, "Valutadatum"));
            tx.counterparty = getCsvCell(cells, idx, "Name Zahlungsbeteiligter").trim();
            tx.purpose = getCsvCell(cells, idx, "Verwendungszweck").trim();
            tx.transactionCode = getCsvCell(cells, idx, "Buchungstext").trim();
            tx.currency = getCsvCell(cells, idx, "Waehrung").trim();
            tx.amount = parseGermanAmount(getCsvCell(cells, idx, "Betrag"));
            tx.balanceAfter = parseGermanAmount(getCsvCell(cells, idx, "Saldo nach Buchung"));
            String mandatsreferenz = getCsvCell(cells, idx, "Mandatsreferenz").trim();
            String glaeubigerId = getCsvCell(cells, idx, "Glaeubiger ID").trim();
            String remark = getCsvCell(cells, idx, "Bemerkung").trim();
            tx.reference = !mandatsreferenz.isBlank() ? mandatsreferenz : (!glaeubigerId.isBlank() ? glaeubigerId : tx.transactionCode);
            tx.bankReference = remark;

            if (tx.currency.isBlank()) tx.currency = "EUR";
            if (tx.valueDate.isBlank()) tx.valueDate = tx.bookingDate;
            if (tx.bookingDate.isBlank() && tx.valueDate.isBlank()) continue;
            tx.fingerprint = buildTransactionFingerprint(tx);
            rows.add(tx);
        }
        return rows;
    }

    private static List<String> splitCsvSemicolonLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (c == ';' && !inQuotes) {
                out.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        out.add(current.toString());
        return out;
    }

    private static String getCsvCell(List<String> cells, Map<String, Integer> idx, String header) {
        Integer i = idx.get(header);
        if (i == null || i < 0 || i >= cells.size()) return "";
        return Objects.toString(cells.get(i), "");
    }

    private static String parseGermanDateIso(String input) {
        String raw = Objects.toString(input, "").trim();
        if (raw.isBlank()) return "";
        try {
            LocalDate d = LocalDate.parse(raw, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            return d.toString();
        } catch (Exception ignored) {
            return raw;
        }
    }

    private static String parseGermanAmount(String input) {
        String raw = Objects.toString(input, "").trim();
        if (raw.isBlank()) return "";
        String normalized = raw.replace(".", "").replace(',', '.').replaceAll("[^0-9+.-]", "");
        if (normalized.isBlank()) return "";
        try {
            double value = Double.parseDouble(normalized);
            return String.format(java.util.Locale.US, "%.2f", value);
        } catch (Exception ignored) {
            return normalized;
        }
    }

    private static BankTransactionRecord parseMt940Line61(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.length() < 7) return null;
        BankTransactionRecord tx = new BankTransactionRecord();
        tx.currency = "EUR";
        tx.bookingDate = parseMt940Date(value.substring(0, 6));

        int idx = 6;
        if (value.length() >= 10 && Character.isDigit(value.charAt(6)) && Character.isDigit(value.charAt(7)) && Character.isDigit(value.charAt(8)) && Character.isDigit(value.charAt(9))) {
            tx.valueDate = parseMt940Date(value.substring(0, 2) + value.substring(6, 10));
            idx = 10;
        } else tx.valueDate = tx.bookingDate;

        if (idx >= value.length()) return tx;
        char dc = value.charAt(idx);
        idx++;

        // optional funds code (e.g. R)
        if (idx < value.length()) {
            char fundsCode = value.charAt(idx);
            if (Character.isLetter(fundsCode) && fundsCode != 'N' && fundsCode != 'F' && fundsCode != 'S') {
                idx++;
            }
        }

        StringBuilder amount = new StringBuilder();
        while (idx < value.length()) {
            char c = value.charAt(idx);
            if ((c >= '0' && c <= '9') || c == ',' || c == '.') { amount.append(c); idx++; }
            else break;
        }
        String amountStr = amount.toString().replace(',', '.');
        if (!amountStr.isBlank()) {
            try {
                double val = Double.parseDouble(amountStr);
                if (dc == 'D') val = -val;
                tx.amount = String.format(java.util.Locale.US, "%.2f", val);
            } catch (Exception ignored) {}
        }
        if (idx < value.length()) {
            // transaction code starts often with N/F/S + 3-char code
            char marker = value.charAt(idx);
            if ((marker == 'N' || marker == 'F' || marker == 'S') && idx + 3 < value.length()) {
                tx.transactionCode = value.substring(idx + 1, Math.min(idx + 4, value.length()));
                idx += 4;
            }
        }

        String trailing = idx < value.length() ? value.substring(idx).trim() : "";
        if (!trailing.isBlank()) {
            int bankRefIdx = trailing.indexOf("//");
            if (bankRefIdx >= 0) {
                tx.reference = trailing.substring(0, bankRefIdx).trim();
                tx.bankReference = trailing.substring(bankRefIdx + 2).trim();
            } else {
                tx.reference = trailing;
            }
        }
        return tx;
    }

    private static String parseMt940Date(String yyMMddOrMMdd) {
        try {
            if (yyMMddOrMMdd == null) return "";
            if (yyMMddOrMMdd.length() == 6) {
                int yy = Integer.parseInt(yyMMddOrMMdd.substring(0,2));
                int year = yy >= 70 ? 1900 + yy : 2000 + yy;
                int month = Integer.parseInt(yyMMddOrMMdd.substring(2,4));
                int day = Integer.parseInt(yyMMddOrMMdd.substring(4,6));
                return String.format("%04d-%02d-%02d", year, month, day);
            }
            if (yyMMddOrMMdd.length() == 4) {
                LocalDate now = LocalDate.now();
                int month = Integer.parseInt(yyMMddOrMMdd.substring(0,2));
                int day = Integer.parseInt(yyMMddOrMMdd.substring(2,4));
                return String.format("%04d-%02d-%02d", now.getYear(), month, day);
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static Tag86Data parseMt940Tag86(String raw) {
        Tag86Data out = new Tag86Data();
        String value = Objects.toString(raw, "").trim();
        if (value.isBlank()) return out;

        // leading numeric token (e.g. 808/166) is not business content
        if (value.length() >= 3 && Character.isDigit(value.charAt(0)) && Character.isDigit(value.charAt(1)) && Character.isDigit(value.charAt(2))) {
            value = value.substring(3).trim();
        }

        Map<String, StringBuilder> segments = new LinkedHashMap<>();
        String currentKey = "00";
        segments.put(currentKey, new StringBuilder());
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c == '?' && i + 2 < value.length() && Character.isDigit(value.charAt(i + 1)) && Character.isDigit(value.charAt(i + 2))) {
                currentKey = "" + value.charAt(i + 1) + value.charAt(i + 2);
                segments.putIfAbsent(currentKey, new StringBuilder());
                i += 3;
                continue;
            }
            segments.get(currentKey).append(c);
            i++;
        }

        String typeText = Objects.toString(segments.getOrDefault("00", new StringBuilder()), "").trim();

        StringBuilder purpose = new StringBuilder();
        for (int k = 20; k <= 29; k++) {
            String key = String.format("%02d", k);
            if (segments.containsKey(key)) {
                if (purpose.length() > 0) purpose.append(' ');
                purpose.append(segments.get(key).toString().trim());
            }
        }

        // Some banks (e.g. Vivid) put human readable detail in ?60, while ?00 is only a category.
        StringBuilder detailText = new StringBuilder();
        for (int k = 60; k <= 63; k++) {
            String key = String.format("%02d", k);
            if (segments.containsKey(key)) {
                if (detailText.length() > 0) detailText.append(' ');
                detailText.append(segments.get(key).toString().trim());
            }
        }

        if (purpose.length() == 0 && detailText.length() > 0) {
            purpose.append(detailText);
        }
        if (purpose.length() == 0 && !typeText.isBlank()) {
            purpose.append(typeText);
        }

        String p = purpose.toString().replace("SVWZ+", "").replaceAll("\\s+", " ").trim();
        String d = detailText.toString().replace("SVWZ+", "").replaceAll("\\s+", " ").trim();
        if (!d.isBlank() && !p.toLowerCase().contains(d.toLowerCase())) {
            p = p.isBlank() ? d : (p + " | " + d);
        }
        if (!typeText.isBlank() && !p.toLowerCase().contains(typeText.toLowerCase())) {
            p = p.isBlank() ? typeText : (typeText + " | " + p);
        }
        out.purpose = p;

        StringBuilder cp = new StringBuilder();
        for (int k = 32; k <= 33; k++) {
            String key = String.format("%02d", k);
            if (segments.containsKey(key)) {
                if (cp.length() > 0) cp.append(' ');
                cp.append(segments.get(key).toString().trim());
            }
        }
        out.counterparty = cp.toString().replaceAll("\\s+", " ").trim();

        // Fallback for counterpart if 32/33 is missing
        if (out.counterparty.isBlank()) {
            String fallback = Objects.toString(segments.getOrDefault("31", new StringBuilder()), "").trim();
            if (fallback.isBlank()) fallback = Objects.toString(segments.getOrDefault("30", new StringBuilder()), "").trim();
            out.counterparty = fallback;
        }
        return out;
    }

    private static class Tag86Data {
        String purpose = "";
        String counterparty = "";
    }

    private static String buildTransactionFingerprint(BankTransactionRecord tx) {
        String raw = String.join("|",
                normalizeToken(tx.bankAccountId),
                normalizeToken(tx.bookingDate),
                normalizeToken(tx.valueDate),
                normalizeToken(tx.amount),
                normalizeToken(tx.currency),
                normalizeToken(tx.counterparty),
                normalizeToken(tx.purpose),
                normalizeToken(tx.reference),
                normalizeToken(tx.bankReference));
        return sha256Hex(raw);
    }

    private static String normalizeToken(String input) {
        return Objects.toString(input, "").replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private static class BankAccountRecord {
        public String id;
        public String name;
        public String ibanOrAccountNo;
        public String bic;
        public String bankName;
        public String currency;
        public String createdAt;
    }

    private static class BankTransactionRecord {
        public String id = java.util.UUID.randomUUID().toString();
        public String bankAccountId;
        public String bookingDate;
        public String valueDate;
        public String amount;
        public String currency;
        public String counterparty;
        public String purpose;
        public String reference;
        public String bankReference;
        public String transactionCode;
        public String balanceAfter;
        public String fingerprint;
        public String importedAt;
        public String sourceFileName;
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

    private static List<Map<String, String>> readRecentVersions() {
        List<Map<String, String>> items = new ArrayList<>();
        try {
            Process process = new ProcessBuilder("git", "log", "-n", "12", "--pretty=format:%h|%ct|%s")
                    .directory(Paths.get(".").toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = process.waitFor();
            if (code == 0) {
                for (String line : output.split("\\R")) {
                    if (line.isBlank()) continue;
                    String[] parts = line.split("\\|", 3);
                    if (parts.length < 3) continue;
                    long epoch = Long.parseLong(parts[1].trim());
                    String ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            .withZone(ZoneId.systemDefault())
                            .format(Instant.ofEpochSecond(epoch));
                    Map<String, String> row = new HashMap<>();
                    row.put("version", parts[0].trim());
                    row.put("timestamp", ts);
                    row.put("summary", toGermanSummary(parts[2].trim()));
                    items.add(row);
                }
            }
        } catch (Exception ignored) {
        }
        return items;
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
                .replace("invoice", "Rechnung")
                .replace("Invoice", "Rechnung")
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

    private static String resolveVersionWithTimestampAndSequence() {
        try {
            Process tsProcess = new ProcessBuilder("git", "show", "-s", "--format=%ct", "HEAD")
                    .directory(Paths.get(".").toFile())
                    .redirectErrorStream(true)
                    .start();
            String tsOutput = new String(tsProcess.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int tsCode = tsProcess.waitFor();

            Process countProcess = new ProcessBuilder("git", "rev-list", "--count", "HEAD")
                    .directory(Paths.get(".").toFile())
                    .redirectErrorStream(true)
                    .start();
            String countOutput = new String(countProcess.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int countCode = countProcess.waitFor();

            if (tsCode == 0 && countCode == 0 && !tsOutput.isBlank() && !countOutput.isBlank()) {
                long epoch = Long.parseLong(tsOutput);
                String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochSecond(epoch));
                String seq = String.format("%06d", Integer.parseInt(countOutput));
                return timestamp + "-" + seq;
            }
        } catch (Exception ignored) {
        }
        String fallbackTs = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        return fallbackTs + "-000000";
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "Unbekannter Fehler";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
