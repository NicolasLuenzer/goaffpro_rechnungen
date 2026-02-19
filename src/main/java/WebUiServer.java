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
                ensureCommissionInHistory(config, activeCommission);
                persistSettings(config);

                Map<String, Object> payload = new HashMap<>();
                payload.put("pdfExportPath", exportDir);
                payload.put("settingsDirectory", resolveSettingsDirectory(config).toString());
                payload.put("lastImportedComission", activeCommission);
                payload.put("lastImportedComissionHistory", getCommissionHistory(config));
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(payload));
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    String newPath = asText(body, "pdfExportPath").trim();
                    String selectedCommission = asText(body, "lastImportedComission").trim();

                    Properties config = loadConfig();
                    Path chosenDir = newPath.isEmpty() ? resolveSettingsDirectory(config) : Paths.get(newPath).toAbsolutePath();
                    Files.createDirectories(chosenDir);

                    config.setProperty("pdfExportPath", chosenDir.toString());
                    if (!selectedCommission.isEmpty()) {
                        config.setProperty("lastImportedComission", selectedCommission);
                        ensureCommissionInHistory(config, selectedCommission);
                    }

                    persistSettings(config);

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("message", "Einstellungen gespeichert.");
                    payload.put("pdfExportPath", Objects.toString(config.getProperty("pdfExportPath"), DEFAULT_PDF_EXPORT_PATH));
                    payload.put("settingsDirectory", resolveSettingsDirectory(config).toString());
                    payload.put("lastImportedComission", Objects.toString(config.getProperty("lastImportedComission"), "0"));
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
                            "Betrag: " + safe(row.get("amount"), ""),
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
                // Verwende die gesamte API-Antwort für die PDF, wie vom Request zurückgegeben.

                String exportDirValue = requestedDir.isEmpty()
                        ? Objects.toString(config.getProperty("pdfExportPath"), DEFAULT_PDF_EXPORT_PATH)
                        : requestedDir;
                Path exportDir = Paths.get(exportDirValue).toAbsolutePath();
                Files.createDirectories(exportDir);

                String filename = "rechnungsdetails_" + sanitizeFilename(paymentId) + "_" + FILE_TIMESTAMP.format(LocalDateTime.now()) + ".pdf";
                Path pdfPath = exportDir.resolve(filename);
                createInvoiceDetailsPdf(pdfPath, response);

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
                payload.put("message", "Rechnungsdetails-PDF erstellt.");
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

        private void createInvoiceDetailsPdf(Path pdfPath, JsonNode apiResponse) throws IOException {
            try (PDDocument document = new PDDocument()) {
                JsonNode payments = apiResponse.get("payments");
                JsonNode payment = (payments != null && payments.isArray() && payments.size() > 0) ? payments.get(0) : null;

                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                    float x = 40f;
                    float y = 770f;

                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
                    cs.newLineAtOffset(x, y);
                    cs.showText("GoAffPro Rechnungsdetails");
                    cs.endText();
                    y -= 24;

                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, 9);
                    cs.newLineAtOffset(x, y);
                    cs.showText(shortenForPdf("Request: " + asText(apiResponse, "request_url"), 130));
                    cs.endText();
                    y -= 20;

                    if (payment != null) {
                        float totalWidth = 530f;
                        float keyWidth = totalWidth * 0.20f;
                        float valueWidth = totalWidth * 0.80f;

                        List<String[]> headerRows = new ArrayList<>();
                        headerRows.add(new String[]{label("Zahlungs-ID", "id", 0), asText(payment, "id")});
                        headerRows.add(new String[]{label("Affiliate-ID", "affiliate_id", 0), asText(payment, "affiliate_id")});
                        headerRows.add(new String[]{label("Betrag", "amount", 0), formatAmountEuro(asText(payment, "amount"))});
                        headerRows.add(new String[]{label("Zahlungsart", "payment_method", 0), asText(payment, "payment_method")});
                        headerRows.add(new String[]{label("Affiliate-Nachricht", "affiliate_message", 0), asText(payment, "affiliate_message")});
                        headerRows.add(new String[]{label("Admin-Notiz", "admin_note", 0), asText(payment, "admin_note")});
                        headerRows.add(new String[]{label("Bestelldatum", "created_at", 0), asText(payment, "created_at")});

                        for (String[] row : headerRows) {
                            float used = drawTableRow(cs, x, y, 18f, keyWidth, valueWidth, row[0], row[1]);
                            y -= used;
                        }

                        y -= 10;
                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
                        cs.newLineAtOffset(x, y);
                        cs.showText("Payment Details");
                        cs.endText();
                        y -= 16;

                        JsonNode paymentDetails = payment.get("payment_details");
                        if (paymentDetails != null && paymentDetails.isObject()) {
                            var it = paymentDetails.fieldNames();
                            while (it.hasNext()) {
                                String key = it.next();
                                String value = "amount".equals(key) ? formatAmountEuro(asText(paymentDetails, key)) : asText(paymentDetails, key);
                                float used = drawTableRow(cs, x, y, 18f, keyWidth, valueWidth, label(resolveGermanLabel(key), key, 1), translateFieldValue(key, value));
                                y -= used;
                                if (y < 90f) break;
                            }
                        }
                    }
                }

                JsonNode transactions = payment != null ? payment.get("transactions") : null;
                if (transactions != null && transactions.isArray() && transactions.size() > 0) {
                    int idx = 0;
                    while (idx < transactions.size()) {
                        PDPage txPage = new PDPage();
                        document.addPage(txPage);
                        try (PDPageContentStream cs = new PDPageContentStream(document, txPage)) {
                            float x = 30f;
                            float y = 770f;

                            cs.beginText();
                            cs.setFont(PDType1Font.HELVETICA_BOLD, 13);
                            cs.newLineAtOffset(x, y);
                            cs.showText("Transactions");
                            cs.endText();
                            y -= 20;

                            float totalWidth = 560f;
                            float keyWidth = totalWidth * 0.20f;
                            float valueWidth = totalWidth * 0.80f;
                            float usedHeader = drawTableRow(cs, x, y, 18f, keyWidth, valueWidth, "Feld", "Wert");
                            y -= usedHeader;

                            while (idx < transactions.size() && y > 80f) {
                                JsonNode tx = transactions.get(idx);
                                float u1 = drawTableRow(cs, x, y, 18f, keyWidth, valueWidth, label(resolveGermanLabel("tx_id"), "tx_id", 1), translateFieldValue("tx_id", asText(tx, "tx_id"))); y -= u1;
                                float u2 = drawTableRow(cs, x, y, 18f, keyWidth, valueWidth, label(resolveGermanLabel("amount"), "amount", 1), translateFieldValue("amount", asText(tx, "amount"))); y -= u2;
                                float u3 = drawTableRow(cs, x, y, 18f, keyWidth, valueWidth, label(resolveGermanLabel("entity_type"), "entity_type", 1), translateFieldValue("entity_type", asText(tx, "entity_type"))); y -= u3;
                                float u4 = drawTableRow(cs, x, y, 18f, keyWidth, valueWidth, label(resolveGermanLabel("created_at"), "created_at", 1), translateFieldValue("created_at", asText(tx, "created_at"))); y -= u4;

                                JsonNode metadata = tx.get("metadata");
                                if (metadata != null && metadata.isObject()) {
                                    var it = metadata.fieldNames();
                                    while (it.hasNext() && y > 80f) {
                                        String key = it.next();
                                        String rawValue = asText(metadata, key);
                                        float um = drawTableRow(cs, x, y, 18f, keyWidth, valueWidth,
                                                label(resolveGermanLabel(key), key, 2),
                                                translateFieldValue(key, rawValue));
                                        y -= um;
                                    }
                                }

                                float sep = drawTableRow(cs, x, y, 12f, totalWidth, 0f, "", "");
                                y -= sep;
                                idx++;
                            }
                        }
                    }
                }

                document.save(pdfPath.toFile());
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
                case "amount" -> "Betrag";
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
        String url = "https://api.goaffpro.com/v1/admin/affiliates?id=" + ids + "&fields=id,name,country,tax_identification_number";
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

        ensureCommissionInHistory(config, Objects.toString(config.getProperty("lastImportedComission"), "0"));
    }

    private static void persistSettings(Properties config) throws IOException {
        if (Objects.toString(config.getProperty("pdfExportPath"), "").isBlank()) {
            config.setProperty("pdfExportPath", DEFAULT_PDF_EXPORT_PATH);
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
