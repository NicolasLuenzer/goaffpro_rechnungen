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
import java.time.Instant;
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
import java.util.stream.Collectors;

public class WebUiServer {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[XXX]");
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Path CONFIG_PATH = Paths.get("src/main/java/config.properties");
    private static final Path UI_PATH = Paths.get("src/main/resources/ui/dashboard.html");
    private static final String COMMISSION_HISTORY_KEY = "lastImportedComissionHistory";
    private static final String DEFAULT_PDF_EXPORT_PATH = "C:\\Users\\nluenzer\\Downloads\\goaffpro";
    private static final String UI_SETTINGS_FILENAME = "goaffpro_ui_settings.properties";
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
                Properties config = loadConfig();
                Properties uiSettings = loadUiSettings(resolveSettingsDirectory(config));
                mergeUiSettingsIntoConfig(config, uiSettings);

                String apiKey = Objects.toString(config.getProperty("goaffproAPIKey"), "").trim();
                String activeLastImportedComission = Objects.toString(config.getProperty("lastImportedComission"), "0").trim();

                String paymentsUrl = "https://api.goaffpro.com/v1/admin/payments?since_id=" + activeLastImportedComission
                        + "&fields=id,affiliate_id,amount,currency,payment_method,payment_details,affiliate_message,admin_note,created_at";

                JsonNode paymentRoot = requestJson(paymentsUrl, apiKey);
                JsonNode payments = paymentRoot.get("payments");

                if (payments == null || !payments.isArray() || payments.size() == 0) {
                    ensureCommissionInHistory(config, activeLastImportedComission);
                    persistSettings(config);

                    Map<String, Object> emptyResult = new HashMap<>();
                    emptyResult.put("payments", Collections.emptyList());
                    emptyResult.put("message", "Keine neuen Zahlungen gefunden.");
                    emptyResult.put("lastImportedComission", activeLastImportedComission);
                    emptyResult.put("lastImportedComissionHistory", getCommissionHistory(config));
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
                List<Map<String, String>> responsePayments = new ArrayList<>();
                for (JsonNode payment : payments) {
                    String paymentId = asText(payment, "id");
                    String affiliateId = asText(payment, "affiliate_id");
                    JsonNode affiliate = affiliatesById.get(affiliateId);

                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("paymentId", paymentId);
                    item.put("belegdatum", toGermanDate(asText(payment, "created_at")));
                    item.put("affiliateName", affiliate != null ? asText(affiliate, "name") : "");
                    item.put("affiliateEmail", affiliate != null ? asText(affiliate, "email") : "");
                    item.put("affiliateAddress", formatAffiliateAddress(affiliate));
                    item.put("affiliateCountry", affiliate != null ? asText(affiliate, "country") : "");
                    item.put("affiliateSteuernummer", affiliate != null ? asText(affiliate, "tax_identification_number") : "");
                    item.put("amount", asText(payment, "amount"));
                    item.put("currency", asText(payment, "currency"));
                    responsePayments.add(item);

                    if (isGreaterNumeric(paymentId, highestId)) {
                        highestId = paymentId;
                    }
                }

                ensureCommissionInHistory(config, activeLastImportedComission);
                ensureCommissionInHistory(config, highestId);
                persistSettings(config);

                Map<String, Object> result = new HashMap<>();
                result.put("payments", responsePayments);
                result.put("message", responsePayments.size() + " neue Zahlung(en) gefunden.");
                result.put("lastImportedComission", activeLastImportedComission);
                result.put("highestDiscoveredComission", highestId);
                result.put("lastImportedComissionHistory", getCommissionHistory(config));
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
                String contactEmail = Objects.toString(config.getProperty("contactEmail"), "").trim();
                String smtpHost = Objects.toString(config.getProperty("smtpHost"), "").trim();
                String smtpPort = Objects.toString(config.getProperty("smtpPort"), "587").trim();
                String smtpUsername = Objects.toString(config.getProperty("smtpUsername"), "").trim();
                boolean smtpTls = Boolean.parseBoolean(Objects.toString(config.getProperty("smtpTls"), "false"));
                boolean hasSmtpPassword = !Objects.toString(config.getProperty("smtpPassword"), "").trim().isBlank();
                boolean sendEmailsEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("sendEmailsEnabled"), "true"));
                ensureCommissionInHistory(config, activeCommission);
                persistSettings(config);

                Map<String, Object> payload = new HashMap<>();
                payload.put("pdfExportPath", exportDir);
                payload.put("settingsDirectory", resolveSettingsDirectory(config).toString());
                payload.put("lastImportedComission", activeCommission);
                payload.put("contactEmail", contactEmail);
                payload.put("smtpHost", smtpHost);
                payload.put("smtpPort", smtpPort);
                payload.put("smtpUsername", smtpUsername);
                payload.put("smtpTls", smtpTls);
                payload.put("hasSmtpPassword", hasSmtpPassword);
                payload.put("sendEmailsEnabled", sendEmailsEnabled);
                payload.put("lastImportedComissionHistory", getCommissionHistory(config));
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    String newPath = asText(body, "pdfExportPath").trim();
                    String selectedCommission = asText(body, "lastImportedComission").trim();
                    String contactEmail = asText(body, "contactEmail").trim();
                    String smtpHost = asText(body, "smtpHost").trim();
                    String smtpPort = asText(body, "smtpPort").trim();
                    String smtpUsername = asText(body, "smtpUsername").trim();
                    String smtpPassword = asText(body, "smtpPassword").trim();
                    boolean smtpTls = body.has("smtpTls") && body.get("smtpTls").asBoolean(false);
                    boolean sendEmailsEnabled = !body.has("sendEmailsEnabled") || body.get("sendEmailsEnabled").asBoolean(true);

                    Properties config = loadConfig();
                    Path chosenDir = newPath.isEmpty() ? resolveSettingsDirectory(config) : Paths.get(newPath).toAbsolutePath();
                    Files.createDirectories(chosenDir);

                    config.setProperty("pdfExportPath", chosenDir.toString());
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

                    persistSettings(config);

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("message", "Einstellungen gespeichert.");
                    payload.put("pdfExportPath", Objects.toString(config.getProperty("pdfExportPath"), DEFAULT_PDF_EXPORT_PATH));
                    payload.put("settingsDirectory", resolveSettingsDirectory(config).toString());
                    payload.put("lastImportedComission", Objects.toString(config.getProperty("lastImportedComission"), "0"));
                    payload.put("contactEmail", Objects.toString(config.getProperty("contactEmail"), ""));
                    payload.put("smtpHost", Objects.toString(config.getProperty("smtpHost"), ""));
                    payload.put("smtpPort", Objects.toString(config.getProperty("smtpPort"), "587"));
                    payload.put("smtpUsername", Objects.toString(config.getProperty("smtpUsername"), ""));
                    payload.put("smtpTls", Boolean.parseBoolean(Objects.toString(config.getProperty("smtpTls"), "false")));
                    payload.put("hasSmtpPassword", !Objects.toString(config.getProperty("smtpPassword"), "").trim().isBlank());
                    payload.put("sendEmailsEnabled", Boolean.parseBoolean(Objects.toString(config.getProperty("sendEmailsEnabled"), "true")));
                    payload.put("lastImportedComissionHistory", getCommissionHistory(config));
                    sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
                } catch (Exception e) {
                    sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
                return;
            }

            sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
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

                List<String> exportedFiles = new ArrayList<>();
                for (Map<String, String> row : selectedRows) {
                    String paymentId = safe(row.get("paymentId"), "unbekannt");
                    String filename = "payment_" + sanitizeFilename(paymentId) + "_" + FILE_TIMESTAMP.format(LocalDateTime.now()) + ".pdf";
                    Path pdfPath = exportDir.resolve(filename);
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

                try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
                    cs.newLineAtOffset(50, 750);
                    cs.showText("GoAffPro Zahlungsexport");
                    cs.endText();

                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, 11);
                    cs.newLineAtOffset(50, 720);

                    List<String> lines = List.of(
                            "Payment-ID: " + safe(row.get("paymentId"), ""),
                            "Belegdatum: " + safe(row.get("belegdatum"), ""),
                            "Affiliate-Name: " + safe(row.get("affiliateName"), ""),
                            "Affiliate-Land: " + safe(row.get("affiliateCountry"), ""),
                            "Affiliate-Steuernummer: " + safe(row.get("affiliateSteuernummer"), ""),
                            "Provision: " + safe(row.get("amount"), ""),
                            "Waehrung: " + safe(row.get("currency"), "")
                    );

                    boolean first = true;
                    for (String line : lines) {
                        if (!first) {
                            cs.newLineAtOffset(0, -18);
                        }
                        cs.showText(line);
                        first = false;
                    }
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

                String apiKey = Objects.toString(config.getProperty("goaffproAPIKey"), "").trim();
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

                String filename = "rechnungsdetails_" + sanitizeFilename(paymentId) + "_" + FILE_TIMESTAMP.format(LocalDateTime.now()) + ".pdf";
                Path pdfPath = exportDir.resolve(filename);
                createInvoiceDetailsPdf(pdfPath, response, affiliate);

                String contactEmail = Objects.toString(config.getProperty("contactEmail"), "").trim();
                boolean sendEmailsEnabled = Boolean.parseBoolean(Objects.toString(config.getProperty("sendEmailsEnabled"), "true"));
                if (sendEmailsEnabled && contactEmail.isBlank()) {
                    sendResponse(exchange, 400, "application/json", "{\"error\":\"Kontakt-E-Mail ist nicht gesetzt. Bitte in den Einstellungen hinterlegen.\"}");
                    return;
                }
                if (sendEmailsEnabled) {
                    String periodLabel = buildPaymentPeriodLabel(payment);
                    String affiliateNameForMail = affiliate != null ? asText(affiliate, "name") : "";
                    sendInvoiceMailWithAttachment(contactEmail, pdfPath, affiliateNameForMail, periodLabel, payment, affiliate, resolveSmtpConfig(config));
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
                payload.put("message", sendEmailsEnabled ? "Rechnungsdetails-PDF erstellt und per E-Mail versendet." : "Rechnungsdetails-PDF erstellt (E-Mail-Versand deaktiviert).");
                payload.put("requestUrl", detailsUrl);
                payload.put("file", pdfPath.toString());
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
        String url = "https://api.goaffpro.com/v1/admin/affiliates?id=" + ids + "&fields=id,name,email,phone,company_name,ref_code,status,address_1,address_2,city,state,zip,country,tax_identification_number";
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

    private static void sendInvoiceMailWithAttachment(String toEmail, Path pdfPath, String affiliateName, String periodLabel, JsonNode payment, JsonNode affiliate, SmtpConfig smtpConfig) throws Exception {
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

        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(textPart);
        multipart.addBodyPart(attachmentPart);
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
        String affiliateEmail = affiliate != null ? asText(affiliate, "email") : "";
        String paymentId = payment != null ? asText(payment, "id") : "";
        String affiliateId = payment != null ? asText(payment, "affiliate_id") : "";
        String payout = euroStatic(parseDoubleSafeStatic(payment != null ? asText(payment, "amount") : "0"));
        String method = payment != null ? asText(payment, "payment_method") : "";
        String created = formatDateTimeEuropeBerlinStatic(payment != null ? asText(payment, "created_at") : "");

        int txCount = 0;
        JsonNode transactions = payment != null ? payment.get("transactions") : null;
        if (transactions != null && transactions.isArray()) txCount = transactions.size();

        return "Guten Tag,\n\n"
                + "anbei erhalten Sie den Provisionsnachweis als PDF-Anhang.\n\n"
                + "Zahllauf-Informationen:\n"
                + "- Zeitraum: " + periodLabel + "\n"
                + "- Zahllauf-ID: " + paymentId + "\n"
                + "- Affiliate-ID: " + affiliateId + "\n"
                + "- Name der Beraterin: " + affiliateName + "\n"
                + "- E-Mail der Beraterin: " + affiliateEmail + "\n"
                + "- Auszahlungsbetrag: " + payout + "\n"
                + "- Zahlungsmethode: " + method + "\n"
                + "- Auszahlungsdatum (System): " + created + "\n"
                + "- Anzahl Transaktionen im Zahllauf: " + txCount + "\n\n"
                + "Freundliche Grüße\n"
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
        ui.setProperty("contactEmail", Objects.toString(source.getProperty("contactEmail"), ""));
        ui.setProperty("smtpHost", Objects.toString(source.getProperty("smtpHost"), ""));
        ui.setProperty("smtpPort", Objects.toString(source.getProperty("smtpPort"), "587"));
        ui.setProperty("smtpUsername", Objects.toString(source.getProperty("smtpUsername"), ""));
        ui.setProperty("smtpPassword", Objects.toString(source.getProperty("smtpPassword"), ""));
        ui.setProperty("smtpTls", Objects.toString(source.getProperty("smtpTls"), "false"));
        ui.setProperty("sendEmailsEnabled", Objects.toString(source.getProperty("sendEmailsEnabled"), "true"));
        ui.setProperty(COMMISSION_HISTORY_KEY, String.join(",", getCommissionHistory(source)));

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

        ensureCommissionInHistory(config, Objects.toString(config.getProperty("lastImportedComission"), "0"));
    }

    private static void persistSettings(Properties config) throws IOException {
        if (Objects.toString(config.getProperty("pdfExportPath"), "").isBlank()) {
            config.setProperty("pdfExportPath", DEFAULT_PDF_EXPORT_PATH);
        }
        if (Objects.toString(config.getProperty("sendEmailsEnabled"), "").isBlank()) {
            config.setProperty("sendEmailsEnabled", "true");
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
        return new ArrayList<>(unique);
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

    private static String sanitizeFilename(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
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
