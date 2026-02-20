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
    private static final String DEFAULT_PDF_EXPORT_PATH = "C:\\Users\\nluenzer\\Downloads\\goaffpro";
    private static final String UI_SETTINGS_FILENAME = "goaffpro_ui_settings.properties";
    private static final String DEFAULT_GOAFFPRO_API_KEY = "91bdb6e219f5b9ffeff929077b4badd5d7a26c235c672e20285885835683b845";
    private static final List<String> DEFAULT_COMMISSION_HISTORY = List.of("2103705", "2167905", "2190357", "2230376", "2336836", "2421355", "2497986", "2565325");
    private static final String APP_VERSION = resolveVersionWithTimestampAndSequence();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", new UiHandler());
        server.createContext("/api/executables", new ExecutablesHandler());
        server.createContext("/api/provisionen-goaffpro/poll", new PollGoaffproHandler());
        server.createContext("/api/settings", new SettingsHandler());
        server.createContext("/api/provisionen-goaffpro/export-pdf", new ExportPdfHandler());
        server.createContext("/api/provisionen-goaffpro/invoice-details-pdf", new InvoiceDetailsPdfHandler());
        server.createContext("/api/version", new VersionHandler());
        server.createContext("/api/version/history", new VersionHistoryHandler());
        server.createContext("/api/analytics/fetch", new AnalyticsFetchHandler());
        server.createContext("/api/commissions/add-latest", new AddLatestCommissionHandler());
        server.createContext("/api/commissions/remove", new RemoveCommissionHandler());
        server.createContext("/api/help", new HelpHandler());
        server.createContext("/api/validation/advisors", new ValidationAdvisorsHandler());
        server.createContext("/api/validation/advisors/tree", new ValidationAdvisorTreeHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("Web UI Server gestartet auf http://localhost:8080");
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
                String contactEmail = Objects.toString(config.getProperty("contactEmail"), "").trim();
                String smtpHost = Objects.toString(config.getProperty("smtpHost"), "").trim();
                String smtpPort = Objects.toString(config.getProperty("smtpPort"), "587").trim();
                String smtpUsername = Objects.toString(config.getProperty("smtpUsername"), "").trim();
                boolean smtpTls = Boolean.parseBoolean(Objects.toString(config.getProperty("smtpTls"), "false"));
                boolean hasSmtpPassword = !Objects.toString(config.getProperty("smtpPassword"), "").trim().isBlank();
                boolean sendEmailsEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("sendEmailsEnabled"), "true"));
                String emailRecipientMode = Objects.toString(config.getProperty("emailRecipientMode"), "contact").trim();
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
                payload.put("smtpTls", smtpTls);
                payload.put("hasSmtpPassword", hasSmtpPassword);
                payload.put("sendEmailsEnabled", sendEmailsEnabled);
                payload.put("emailRecipientMode", emailRecipientMode);
                payload.put("lastImportedComissionHistory", getCommissionHistory(config));
                payload.put("commissionHistoryLabels", buildCommissionHistoryLabels(config));
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
                    String smtpPassword = asText(body, "smtpPassword").trim();
                    boolean smtpTls = body.has("smtpTls") && body.get("smtpTls").asBoolean(false);
                    boolean sendEmailsEnabled = !body.has("sendEmailsEnabled") || body.get("sendEmailsEnabled").asBoolean(true);
                    String emailRecipientMode = asText(body, "emailRecipientMode").trim();
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
                    config.setProperty("smtpTls", String.valueOf(smtpTls));
                    if (!smtpPassword.isBlank()) {
                        config.setProperty("smtpPassword", smtpPassword);
                    }
                    config.setProperty("sendEmailsEnabled", String.valueOf(sendEmailsEnabled));
                    config.setProperty("emailRecipientMode", emailRecipientMode);

                    persistSettings(config);

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("message", "Einstellungen gespeichert.");
                    payload.put("pdfExportPath", Objects.toString(config.getProperty("pdfExportPath"), DEFAULT_PDF_EXPORT_PATH));
                    payload.put("settingsDirectory", resolveSettingsDirectory(config).toString());
                    payload.put("lastImportedComission", Objects.toString(config.getProperty("lastImportedComission"), "0"));
                    payload.put("goaffproAPIKey", Objects.toString(config.getProperty("goaffproAPIKey"), DEFAULT_GOAFFPRO_API_KEY));
                    payload.put("contactEmail", Objects.toString(config.getProperty("contactEmail"), ""));
                    payload.put("smtpHost", Objects.toString(config.getProperty("smtpHost"), ""));
                    payload.put("smtpPort", Objects.toString(config.getProperty("smtpPort"), "587"));
                    payload.put("smtpUsername", Objects.toString(config.getProperty("smtpUsername"), ""));
                    payload.put("smtpTls", Boolean.parseBoolean(Objects.toString(config.getProperty("smtpTls"), "false")));
                    payload.put("hasSmtpPassword", !Objects.toString(config.getProperty("smtpPassword"), "").trim().isBlank());
                    payload.put("sendEmailsEnabled", Boolean.parseBoolean(Objects.toString(config.getProperty("sendEmailsEnabled"), "true")));
                    payload.put("emailRecipientMode", Objects.toString(config.getProperty("emailRecipientMode"), "contact"));
                    payload.put("lastImportedComissionHistory", getCommissionHistory(config));
                payload.put("commissionHistoryLabels", buildCommissionHistoryLabels(config));
                    sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
                } catch (Exception e) {
                    sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
                return;
            }

            sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
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
                createInvoiceDetailsPdf(pdfPath, response, affiliate);
                writeOriginalJson(jsonPath, response);

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
                    String periodLabel = buildPaymentPeriodLabel(payment);
                    String affiliateNameForMail = affiliate != null ? asText(affiliate, "name") : "";
                    sendInvoiceMailWithAttachment(targetEmail, pdfPath, jsonPath, affiliateNameForMail, periodLabel, payment, affiliate, resolveSmtpConfig(config));
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
                payload.put("opened", opened);
                payload.put("openMessage", openMessage);
                payload.put("pdfExportPath", exportDir.toString());
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
                    return;
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

    private static Map<String, JsonNode> fetchAffiliatesById(String apiKey, List<String> affiliateIds) throws Exception {
        if (affiliateIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String ids = String.join(",", affiliateIds);
        String url = "https://api.goaffpro.com/v1/admin/affiliates?id=" + ids + "&fields=id,name,email,phone,company_name,ref_code,status,address_1,address_2,city,state,zip,country,tax_identification_number,parent_id,upline_affiliate_id,upline_id,parent_affiliate_id";
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
        String[] keys = new String[]{"name", "email", "phone", "address", "country", "dateOfBirth", "taxNumber", "iban", "paymentMethod"};
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

    private static void sendInvoiceMailWithAttachment(String toEmail, Path pdfPath, Path jsonPath, String affiliateName, String periodLabel, JsonNode payment, JsonNode affiliate, SmtpConfig smtpConfig) throws Exception {
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

        String displayName = (affiliateName == null || affiliateName.isBlank()) ? "Beraterin" : affiliateName.trim();
        String subject = "Provisionszahlung für den Zeitraum " + periodLabel + " - " + displayName;
        message.setSubject(subject, StandardCharsets.UTF_8.name());

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(buildInvoiceMailBody(payment, affiliate, periodLabel), StandardCharsets.UTF_8.name());

        MimeBodyPart attachmentPart = new MimeBodyPart();
        FileDataSource fds = new FileDataSource(pdfPath.toFile());
        attachmentPart.setDataHandler(new DataHandler(fds));
        attachmentPart.setFileName(pdfPath.getFileName().toString());

        MimeBodyPart jsonAttachmentPart = new MimeBodyPart();
        FileDataSource jsonDs = new FileDataSource(jsonPath.toFile());
        jsonAttachmentPart.setDataHandler(new DataHandler(jsonDs));
        jsonAttachmentPart.setFileName(jsonPath.getFileName().toString());

        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(textPart);
        multipart.addBodyPart(attachmentPart);
        multipart.addBodyPart(jsonAttachmentPart);
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

        return "Hallo " + salutationName + ",\n\n"
                + "gerade hat ein neuer Zahllauf stattgefunden. Ihre Provision ist damit zur Auszahlung vorgesehen. "
                + "Die Überweisung sollte in der Regel innerhalb der nächsten 2 Bankarbeitstage auf Ihrem Konto eingehen.\n\n"
                + "Kurze Übersicht zu Ihrem aktuellen Zahllauf:\n"
                + "- Zeitraum: " + periodLabel + "\n"
                + "- Zahllauf-ID: " + paymentId + "\n"
                + "- Auszahlungsbetrag: " + payout + "\n"
                + "- Zahlungsmethode: " + method + "\n"
                + "- Auszahlungsdatum (System): " + created + "\n"
                + "- Anzahl Transaktionen: " + txCount + "\n\n"
                + "Im Anhang finden Sie Ihren Provisionsnachweis als PDF sowie die zugehörige JSON-Datei.\n\n"
                + "Viele Grüße\n"
                + "S+R Linear Technology GmbH";
    }

    private static String euroStatic(double value) {
        return String.format(java.util.Locale.GERMANY, "%.2f €", value);
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
        ui.setProperty("contactEmail", Objects.toString(source.getProperty("contactEmail"), ""));
        ui.setProperty("smtpHost", Objects.toString(source.getProperty("smtpHost"), ""));
        ui.setProperty("smtpPort", Objects.toString(source.getProperty("smtpPort"), "587"));
        ui.setProperty("smtpUsername", Objects.toString(source.getProperty("smtpUsername"), ""));
        ui.setProperty("smtpPassword", Objects.toString(source.getProperty("smtpPassword"), ""));
        ui.setProperty("smtpTls", Objects.toString(source.getProperty("smtpTls"), "false"));
        ui.setProperty("sendEmailsEnabled", Objects.toString(source.getProperty("sendEmailsEnabled"), "true"));
        ui.setProperty("emailRecipientMode", Objects.toString(source.getProperty("emailRecipientMode"), "contact"));
        ui.setProperty(COMMISSION_HISTORY_KEY, String.join(",", getCommissionHistory(source)));
        ui.setProperty(COMMISSION_HISTORY_DATES_KEY, Objects.toString(source.getProperty(COMMISSION_HISTORY_DATES_KEY), ""));

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

        config.setProperty("smtpHost", Objects.toString(uiSettings.getProperty("smtpHost"), Objects.toString(config.getProperty("smtpHost"), "")).trim());
        config.setProperty("smtpPort", Objects.toString(uiSettings.getProperty("smtpPort"), Objects.toString(config.getProperty("smtpPort"), "587")).trim());
        config.setProperty("smtpUsername", Objects.toString(uiSettings.getProperty("smtpUsername"), Objects.toString(config.getProperty("smtpUsername"), "")).trim());
        String uiSmtpPassword = Objects.toString(uiSettings.getProperty("smtpPassword"), "").trim();
        if (!uiSmtpPassword.isEmpty() || config.containsKey("smtpPassword")) {
            config.setProperty("smtpPassword", uiSmtpPassword);
        }
        config.setProperty("smtpTls", Objects.toString(uiSettings.getProperty("smtpTls"), Objects.toString(config.getProperty("smtpTls"), "false")).trim());
        config.setProperty("sendEmailsEnabled", Objects.toString(uiSettings.getProperty("sendEmailsEnabled"), Objects.toString(config.getProperty("sendEmailsEnabled"), "true")).trim());
        String uiEmailRecipientMode = Objects.toString(uiSettings.getProperty("emailRecipientMode"), Objects.toString(config.getProperty("emailRecipientMode"), "contact")).trim();
        if (!"advisor".equals(uiEmailRecipientMode)) uiEmailRecipientMode = "contact";
        config.setProperty("emailRecipientMode", uiEmailRecipientMode);

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

        if (lower.startsWith("fix ") || lower.startsWith("fix:")) {
            return "Fehlerbehebung: " + text.substring(text.indexOf(' ') + 1).trim();
        }
        if (lower.startsWith("add ") || lower.startsWith("add:")) {
            return "Erweiterung: " + text.substring(text.indexOf(' ') + 1).trim();
        }
        if (lower.startsWith("update ") || lower.startsWith("update:")) {
            return "Aktualisierung: " + text.substring(text.indexOf(' ') + 1).trim();
        }
        if (lower.startsWith("refactor ") || lower.startsWith("refactor:")) {
            return "Umstrukturierung: " + text.substring(text.indexOf(' ') + 1).trim();
        }
        if (lower.startsWith("remove ") || lower.startsWith("remove:")) {
            return "Entfernung: " + text.substring(text.indexOf(' ') + 1).trim();
        }

        return "Änderung: " + text;
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
