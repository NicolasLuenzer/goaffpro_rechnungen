import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MainProvisionenGoaffpro {

    private static final String API_URL = "https://api.goaffpro.com/v1/admin/payments?since_id=1454894&fields=id,affiliate_id,amount,currency,payment_method,payment_details,affiliate_message,admin_note,created_at";
    private static String API_KEY;

    public static void main(String[] args) {
        loadConfig();
        try {
            String jsonResponse = makeApiRequest(API_URL);
            if (jsonResponse != null) {
                processJsonResponse(jsonResponse);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadConfig() {
        try (InputStream input = new FileInputStream("C:\\Users\\nluenzer\\IdeaProjects\\untitled\\src\\main\\java\\config.properties")) {
            Properties prop = new Properties();
            prop.load(input);
            API_KEY = prop.getProperty("goaffproAPIKey");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        }

        private static String makeApiRequest(String apiUrl) throws Exception {
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("x-goaffpro-access-token", API_KEY);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                return response.toString();
            } else {
                System.out.println("GET request failed. Response Code: " + responseCode);
                return null;
            }
        }

        private static void processJsonResponse(String jsonResponse) throws Exception {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            for (JsonNode paymentNode : rootNode) {
                String id = paymentNode.get("id").asText();
                String affiliateId = paymentNode.get("affiliate_id").asText();
                double amount = paymentNode.get("amount").asDouble();
                String currency = paymentNode.get("currency").asText();
                String paymentMethod = paymentNode.get("payment_method").asText();
                String paymentDetails = paymentNode.get("payment_details").asText();
                String affiliateMessage = paymentNode.get("affiliate_message").asText();
                String adminNote = paymentNode.get("admin_note").asText();
                String createdAt = paymentNode.get("created_at").asText();

                System.out.println("Payment ID: " + id);
                System.out.println("Affiliate ID: " + affiliateId);
                System.out.println("Amount: " + amount + " " + currency);
                System.out.println("Payment Method: " + paymentMethod);
                System.out.println("Payment Details: " + paymentDetails);
                System.out.println("Affiliate Message: " + affiliateMessage);
                System.out.println("Admin Note: " + adminNote);
                System.out.println("Created At: " + createdAt);
                System.out.println("---------------------------------------");
            }
        }
    }
