import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MainProvisionenGoaffpro {

    private static String API_URL = "https://api.goaffpro.com/v1/admin/affiliates?id=%s&fields=id,avatar,honorific,date_of_birth,gender,name,first_name,last_name,email,ref_code,company_name,ref_codes,coupon,coupons,phone,website,facebook,twitter,instagram,address_1,address_2,city,state,zip,country,phone,admin_note,extra_1,extra_2,extra_3,group_id,registration_ip,personal_message,payment_method,payment_details,commission,status,last_login,total_referral_earnings,total_network_earnings,total_amount_paid,total_amount_pending,total_other_earnings,number_of_orders,tax_identification_number,login_token,signup_page,comments,tags,approved_at,blocked_at,created_at,updated_at";

    private static String API_KEY;
    private static String exportPath;

    public static void main(String[] args) {
        loadConfig();
        try {
            // String affiliateId = "15325677";   // Kempfle
            String affiliateId = "13637495";
            String jsonResponse = makeApiRequest(String.format(API_URL, affiliateId));
            if (jsonResponse != null) {
                generateXMLFromResponse(jsonResponse);
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
            exportPath = prop.getProperty("exportPath");
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

    private static void generateXMLFromResponse(String jsonResponse) throws ParserConfigurationException, TransformerException, IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        JsonNode affiliateNode = rootNode.get("affiliates");

        if (affiliateNode != null && affiliateNode.size() > 0) {
            DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();
            Document document = documentBuilder.newDocument();

            // Root element
            Element root = document.createElement("EGeckoPersonenkonten");
            root.setAttribute("objectgroupNr", "20");
            root.setAttribute("datumFormat", "dd.MM.yyyy");
            document.appendChild(root);

            for (JsonNode affiliate : affiliateNode) {
                // Personenkonto element
                Element personenkonto = document.createElement("Personenkonto");
                root.appendChild(personenkonto);

                // Add child elements to Personenkonto
                Element kontenart = document.createElement("kontenart");
                kontenart.appendChild(document.createTextNode("K"));
                personenkonto.appendChild(kontenart);

                Element kontonummer = document.createElement("kontonummer");
                kontonummer.appendChild(document.createTextNode(affiliate.get("id").asText()));
                personenkonto.appendChild(kontonummer);

                Element bezeichnung = document.createElement("bezeichnung");
                bezeichnung.appendChild(document.createTextNode(affiliate.get("name").asText()));
                personenkonto.appendChild(bezeichnung);

                // Geschaeftspartner element
                Element geschaeftspartner = document.createElement("Geschaeftspartner");
                personenkonto.appendChild(geschaeftspartner);

                Element nummer = document.createElement("nummer");
                nummer.appendChild(document.createTextNode("AUTO"));
                geschaeftspartner.appendChild(nummer);

                Element kzJuristischePerson = document.createElement("kzJuristischePerson");
                kzJuristischePerson.appendChild(document.createTextNode("n"));
                geschaeftspartner.appendChild(kzJuristischePerson);

                Element anzeigename = document.createElement("anzeigename");
                anzeigename.appendChild(document.createTextNode(affiliate.get("name").asText() + " - " + affiliate.get("email").asText()));
                geschaeftspartner.appendChild(anzeigename);

                // Personendaten element
                Element personendaten = document.createElement("Personendaten");
                geschaeftspartner.appendChild(personendaten);

                Element gueltigVon = document.createElement("gueltigVon");
                gueltigVon.appendChild(document.createTextNode("01.01.1900"));
                personendaten.appendChild(gueltigVon);

                Element name1 = document.createElement("name1");
                name1.appendChild(document.createTextNode(affiliate.get("first_name").asText()));
                personendaten.appendChild(name1);

                Element name2 = document.createElement("name2");
                name2.appendChild(document.createTextNode(affiliate.get("last_name").asText()));
                personendaten.appendChild(name2);

                // Anschrift element
                Element anschrift = document.createElement("Anschrift");
                geschaeftspartner.appendChild(anschrift);

                Element anschriftGueltigVon = document.createElement("gueltigVon");
                anschriftGueltigVon.appendChild(document.createTextNode("01.01.1900"));
                anschrift.appendChild(anschriftGueltigVon);

                Element anschriftName1 = document.createElement("name1");
                anschriftName1.appendChild(document.createTextNode(affiliate.get("first_name").asText()));
                anschrift.appendChild(anschriftName1);

                Element anschriftName2 = document.createElement("name2");
                anschriftName2.appendChild(document.createTextNode(affiliate.get("last_name").asText()));
                anschrift.appendChild(anschriftName2);

                // Extract house number from address using regex
                String address = affiliate.get("address_1").asText();
                String hausnummerValue = "";
                Pattern pattern = Pattern.compile("(\\d+\\s?[a-zA-Z]*)$");
                Matcher matcher = pattern.matcher(address);
                if (matcher.find()) {
                    hausnummerValue = matcher.group(1);
                    address = address.replace(hausnummerValue, "").trim();
                }

                Element strasse = document.createElement("strasse");
                strasse.appendChild(document.createTextNode(address));
                anschrift.appendChild(strasse);

                Element hausnummer = document.createElement("hausnummer");
                hausnummer.appendChild(document.createTextNode(hausnummerValue));
                anschrift.appendChild(hausnummer);

                Element plz = document.createElement("plz");
                plz.appendChild(document.createTextNode(affiliate.get("zip").asText()));
                anschrift.appendChild(plz);

                Element ort = document.createElement("ort");
                ort.appendChild(document.createTextNode(affiliate.get("city").asText()));
                anschrift.appendChild(ort);

                Element landkennzeichen = document.createElement("landkennzeichen");
                landkennzeichen.appendChild(document.createTextNode(affiliate.get("country").asText()));
                anschrift.appendChild(landkennzeichen);
            }

            // Write the content into an XML file
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = "egecko_personenkonten_" + timestamp + ".xml";
            Path exportFilePath = Paths.get(exportPath, fileName);

            try (FileOutputStream fos = new FileOutputStream(exportFilePath.toFile())) {
                DOMSource domSource = new DOMSource(document);
                StreamResult streamResult = new StreamResult(fos);
                transformer.transform(domSource, streamResult);
            }

            System.out.println("XML file generated and saved successfully: " + exportFilePath);
        }
    }
}
