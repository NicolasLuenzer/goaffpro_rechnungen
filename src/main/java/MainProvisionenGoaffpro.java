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

    private static String API_URL = "https://api.goaffpro.com/v1/admin/affiliates?id=%s&fields=id,avatar,honorific,date_of_birth,gender,name,first_name,last_name,email,ref_code,company_name,ref_codes,coupon,coupons,phone,website,facebook,twitter,instagram,address_1,address_2,city,state,zip,country,phone,admin_note,extra_1,extra_2,extra_3,group_id,registration_ip,personal_message,payment_method,payment_details,commission,status,last_login,total_referral_earnings,total_network_earnings,total_amount_paid,total_amount_pending,total_other_earnings,number_of_orders,tax_identification_number,login_token,signup_page,comments,tags,approved_at,blocked_at,created_at,updated_at";
    private static String API_KEY;

    public static void main(String[] args) {
        loadConfig();
        try {
            String affiliateId = "15325677";
            String jsonResponse = makeApiRequest(String.format(API_URL, affiliateId));
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
        JsonNode affiliatesNode = rootNode.get("affiliates");

        if (affiliatesNode != null && affiliatesNode.isArray()) {
            for (JsonNode affiliate : affiliatesNode) {
                String id = affiliate.get("id").asText();
                String name = affiliate.get("name").asText();
                String firstName = affiliate.get("first_name").asText();
                String lastName = affiliate.get("last_name").asText();
                String email = affiliate.get("email").asText();
                String refCode = affiliate.get("ref_code").asText();
                JsonNode refCodesNode = affiliate.get("ref_codes");
                String refCodes = refCodesNode.isArray() ? refCodesNode.toString() : "";
                JsonNode couponNode = affiliate.get("coupon");
                String coupon = couponNode.isObject() ? couponNode.toString() : "";
                JsonNode couponsNode = affiliate.get("coupons");
                String coupons = couponsNode.isArray() ? couponsNode.toString() : "";
                String phone = affiliate.get("phone").asText();
                String address1 = affiliate.get("address_1").asText();
                String city = affiliate.get("city").asText();
                String state = affiliate.get("state").asText();
                String country = affiliate.get("country").asText();
                String zipCode = affiliate.get("zip").asText();
                String taxIdentificationNumber = affiliate.get("tax_identification_number").asText();
                String personalMessage = affiliate.get("personal_message").asText();
                String paymentMethod = affiliate.get("payment_method").asText();
                JsonNode paymentDetailsNode = affiliate.get("payment_details");
                String paymentDetails = paymentDetailsNode.isObject() ? paymentDetailsNode.toString() : "";
                String paypalEmail = paymentDetailsNode.has("paypal_email") ? paymentDetailsNode.get("paypal_email").asText() : "";
                JsonNode commissionNode = affiliate.get("commission");
                String commissionType = commissionNode.has("type") ? commissionNode.get("type").asText() : "";
                String commissionAmount = commissionNode.has("amount") ? commissionNode.get("amount").asText() : "";

                System.out.println("Affiliate ID: " + id);
                System.out.println("Name: " + name);
                System.out.println("First Name: " + firstName);
                System.out.println("Last Name: " + lastName);
                System.out.println("Email: " + email);
                System.out.println("Ref Code: " + refCode);
                System.out.println("Ref Codes: " + refCodes);
                System.out.println("Coupon: " + coupon);
                System.out.println("Coupons: " + coupons);
                System.out.println("Phone: " + phone);
                System.out.println("Address 1: " + address1);
                System.out.println("City: " + city);
                System.out.println("State: " + state);
                System.out.println("Country: " + country);
                System.out.println("ZIP Code: " + zipCode);
                System.out.println("Tax Identification Number: " + taxIdentificationNumber);
                System.out.println("Personal Message: " + personalMessage);
                System.out.println("Payment Method: " + paymentMethod);
                System.out.println("Payment Details: " + paymentDetails);
                System.out.println("PayPal Email: " + paypalEmail);
                System.out.println("Commission Type: " + commissionType);
                System.out.println("Commission Amount: " + commissionAmount);
                System.out.println("---------------------------------------");
            }
        }
    }
}
