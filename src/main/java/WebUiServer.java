import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

public class WebUiServer {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[XXX]");
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Path CONFIG_PATH = Paths.get("src/main/java/config.properties");
    private static final Path UI_PATH = Paths.get("src/main/resources/ui/dashboard.html");

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", new UiHandler());
        server.createContext("/api/executables", new ExecutablesHandler());
        server.createContext("/api/provisionen-goaffpro/poll", new PollGoaffproHandler());
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
                String apiKey = Objects.toString(config.getProperty("goaffproAPIKey"), "").trim();
                String lastImportedComission = Objects.toString(config.getProperty("lastImportedComission"), "0").trim();

                String paymentsUrl = "https://api.goaffpro.com/v1/admin/payments?since_id=" + lastImportedComission
                        + "&fields=id,affiliate_id,amount,currency,payment_method,payment_details,affiliate_message,admin_note,created_at";

                JsonNode paymentRoot = requestJson(paymentsUrl, apiKey);
                JsonNode payments = paymentRoot.get("payments");

                if (payments == null || !payments.isArray() || payments.size() == 0) {
                    Map<String, Object> emptyResult = new HashMap<>();
                    emptyResult.put("payments", Collections.emptyList());
                    emptyResult.put("message", "Keine neuen Zahlungen gefunden.");
                    emptyResult.put("lastImportedComission", lastImportedComission);
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

                String highestId = lastImportedComission;
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

                if (!highestId.equals(lastImportedComission)) {
                    config.setProperty("lastImportedComission", highestId);
                    storeConfig(config);
                }

                Map<String, Object> result = new HashMap<>();
                result.put("payments", responsePayments);
                result.put("message", responsePayments.size() + " neue Zahlung(en) gefunden.");
                result.put("lastImportedComission", highestId);
                sendResponse(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsString(result));
            } catch (Exception e) {
                Map<String, String> err = new HashMap<>();
                err.put("error", e.getMessage());
                sendResponse(exchange, 500, "application/json", OBJECT_MAPPER.writeValueAsString(err));
            }
        }

        private Map<String, JsonNode> fetchAffiliatesById(String apiKey, List<String> affiliateIds) throws Exception {
            if (affiliateIds.isEmpty()) {
                return Collections.emptyMap();
            }

            String ids = String.join(",", affiliateIds);
            String url = "https://api.goaffpro.com/v1/admin/affiliates?id=" + ids
                    + "&fields=id,name,country,tax_identification_number";
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

        private JsonNode requestJson(String apiUrl, String apiKey) throws Exception {
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

        private Properties loadConfig() throws IOException {
            Properties properties = new Properties();
            try (InputStream is = Files.newInputStream(CONFIG_PATH)) {
                properties.load(is);
            }
            return properties;
        }

        private void storeConfig(Properties properties) throws IOException {
            try (OutputStream os = Files.newOutputStream(CONFIG_PATH)) {
                properties.store(os, "Updated by WebUiServer");
            }
        }

        private String toGermanDate(String input) {
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

        private String asText(JsonNode node, String field) {
            JsonNode value = node != null ? node.get(field) : null;
            return value != null && !value.isNull() ? value.asText() : "";
        }

        private boolean isGreaterNumeric(String value, String compareTo) {
            try {
                return Long.parseLong(value) > Long.parseLong(compareTo);
            } catch (Exception e) {
                return value.compareTo(compareTo) > 0;
            }
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
}
