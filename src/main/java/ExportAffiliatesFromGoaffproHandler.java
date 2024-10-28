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

public class ExportAffiliatesFromGoaffproHandler {

    private static String API_URL = "https://api.goaffpro.com/v1/admin/affiliates?id=%s&fields=id,avatar,honorific,date_of_birth,gender,name,first_name,last_name,email,ref_code,company_name,ref_codes,coupon,coupons,phone,website,facebook,twitter,instagram,address_1,address_2,city,state,zip,country,phone,admin_note,extra_1,extra_2,extra_3,group_id,registration_ip,personal_message,payment_method,payment_details,commission,status,last_login,total_referral_earnings,total_network_earnings,total_amount_paid,total_amount_pending,total_other_earnings,number_of_orders,tax_identification_number,login_token,signup_page,comments,tags,approved_at,blocked_at,created_at,updated_at";

    private static String API_KEY;
    private static String exportPath;

    static {
        loadConfig();
    }

    public static void loadConfig() {
        try (InputStream input = new FileInputStream("/Users/nicolas/IdeaProjects/java_pds_converter/src/main/java/config.properties")) {
            Properties prop = new Properties();
            prop.load(input);
            API_KEY = prop.getProperty("goaffproAPIKey");
            exportPath = prop.getProperty("exportPath");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static String makeApiRequest(String affiliateId) throws Exception {
        String apiUrl = String.format(API_URL, affiliateId);
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

    public static void generateXMLFromResponse(String jsonResponse) throws ParserConfigurationException, TransformerException, IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        JsonNode affiliateNode = rootNode.get("affiliates");

        if (affiliateNode != null && !affiliateNode.isEmpty()) {
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
                String kontonummerValue = getValueAsString(affiliate, "id");
                kontonummer.appendChild(document.createTextNode(kontonummerValue != null ? kontonummerValue : ""));
                personenkonto.appendChild(kontonummer);

                Element bezeichnung = document.createElement("bezeichnung");
                String bezeichnungValue = getValueAsString(affiliate, "name");
                bezeichnung.appendChild(document.createTextNode(bezeichnungValue != null ? bezeichnungValue : ""));
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
                String anzeigenameValue = getValueAsString(affiliate, "name") + " - " + getValueAsString(affiliate, "email");
                anzeigename.appendChild(document.createTextNode(anzeigenameValue != null ? anzeigenameValue : ""));
                geschaeftspartner.appendChild(anzeigename);

                // Personendaten element
                Element personendaten = document.createElement("Personendaten");
                geschaeftspartner.appendChild(personendaten);

                Element gueltigVon = document.createElement("gueltigVon");
                gueltigVon.appendChild(document.createTextNode("01.01.1900"));
                personendaten.appendChild(gueltigVon);

                Element name1 = document.createElement("name1");
                String name1Value = getValueAsString(affiliate, "first_name");
                name1.appendChild(document.createTextNode(name1Value != null ? name1Value : ""));
                personendaten.appendChild(name1);

                Element name2 = document.createElement("name2");
                String name2Value = getValueAsString(affiliate, "last_name");
                name2.appendChild(document.createTextNode(name2Value != null ? name2Value : ""));
                personendaten.appendChild(name2);

                // Anschrift element
                Element anschrift = document.createElement("Anschrift");
                geschaeftspartner.appendChild(anschrift);

                Element anschriftGueltigVon = document.createElement("gueltigVon");
                anschriftGueltigVon.appendChild(document.createTextNode("01.01.1900"));
                anschrift.appendChild(anschriftGueltigVon);

                Element anschriftName1 = document.createElement("name1");
                anschriftName1.appendChild(document.createTextNode(name1Value != null ? name1Value : ""));
                anschrift.appendChild(anschriftName1);

                Element anschriftName2 = document.createElement("name2");
                anschriftName2.appendChild(document.createTextNode(name2Value != null ? name2Value : ""));
                anschrift.appendChild(anschriftName2);

                // Extract house number from address using regex
                String address = getValueAsString(affiliate, "address_1");
                String hausnummerValue = "";
                String hausnummerZusatzValue = "";
                if (address != null) {
                    Pattern pattern = Pattern.compile("(\\d+)(\\s?[a-zA-Z]*)$");
                    Matcher matcher = pattern.matcher(address);
                    if (matcher.find()) {
                        hausnummerValue = matcher.group(1);
                        if (matcher.groupCount() > 1) {
                            hausnummerZusatzValue = matcher.group(2).trim();
                        }
                        address = address.replace(matcher.group(0), "").trim();
                    }
                }

                Element strasse = document.createElement("strasse");
                strasse.appendChild(document.createTextNode(address != null ? address : ""));
                anschrift.appendChild(strasse);

                Element hausnummer = document.createElement("hausnummer");
                hausnummer.appendChild(document.createTextNode(hausnummerValue != null ? hausnummerValue : ""));
                anschrift.appendChild(hausnummer);

                Element hausnummerZusatz = document.createElement("hausnummerZusatz");
                hausnummerZusatz.appendChild(document.createTextNode(hausnummerZusatzValue != null ? hausnummerZusatzValue : ""));
                anschrift.appendChild(hausnummerZusatz);

                Element plz = document.createElement("plz");
                String plzValue = getValueAsString(affiliate, "zip");
                plz.appendChild(document.createTextNode(plzValue != null ? plzValue : ""));
                anschrift.appendChild(plz);

                Element ort = document.createElement("ort");
                String ortValue = getValueAsString(affiliate, "city");
                ort.appendChild(document.createTextNode(ortValue != null ? ortValue : ""));
                anschrift.appendChild(ort);

                Element landkennzeichen = document.createElement("landkennzeichen");
                String landkennzeichenValue = getValueAsString(affiliate, "country");
                landkennzeichen.appendChild(document.createTextNode(landkennzeichenValue != null ? landkennzeichenValue : ""));
                anschrift.appendChild(landkennzeichen);

                // TeleKommunikationen element
                Element teleKommunikationen = document.createElement("TeleKommunikationen");
                geschaeftspartner.appendChild(teleKommunikationen);

                Element teleKommunikation = document.createElement("TeleKommunikation");
                teleKommunikationen.appendChild(teleKommunikation);

                Element qualifier = document.createElement("qualifier");
                qualifier.appendChild(document.createTextNode("update"));
                teleKommunikation.appendChild(qualifier);

                Element art = document.createElement("art");
                art.appendChild(document.createTextNode("geschäftlich"));
                teleKommunikation.appendChild(art);

                Element vorwahl = document.createElement("vorwahl");
                teleKommunikation.appendChild(vorwahl);

                Element rufnummer = document.createElement("rufnummer");
                String phoneNumber = getValueAsString(affiliate, "phone");
                rufnummer.appendChild(document.createTextNode(phoneNumber != null ? phoneNumber : ""));
                teleKommunikation.appendChild(rufnummer);

                // OnlineKommunikationen element
                Element onlineKommunikationen = document.createElement("OnlineKommunikationen");
                geschaeftspartner.appendChild(onlineKommunikationen);

                Element onlineKommunikation = document.createElement("OnlineKommunikation");
                onlineKommunikationen.appendChild(onlineKommunikation);

                Element onlineQualifier = document.createElement("qualifier");
                onlineQualifier.appendChild(document.createTextNode("update"));
                onlineKommunikation.appendChild(onlineQualifier);

                Element onlineArt = document.createElement("art");
                onlineArt.appendChild(document.createTextNode("geschäftlich"));
                onlineKommunikation.appendChild(onlineArt);

                Element email = document.createElement("email");
                String emailValue = getValueAsString(affiliate, "email");
                email.appendChild(document.createTextNode(emailValue != null ? emailValue : ""));
                onlineKommunikation.appendChild(email);

                // Bankverbindungen element
                Element bankverbindungen = document.createElement("Bankverbindungen");
                geschaeftspartner.appendChild(bankverbindungen);

                Element bankverbindung = document.createElement("Bankverbindung");
                bankverbindungen.appendChild(bankverbindung);

                Element bankQualifier = document.createElement("qualifier");
                bankQualifier.appendChild(document.createTextNode("update"));
                bankverbindung.appendChild(bankQualifier);

                Element hauptbank = document.createElement("hauptbank");
                hauptbank.appendChild(document.createTextNode("j"));
                bankverbindung.appendChild(hauptbank);

                Element iban = document.createElement("iban");
                String accountNumber = affiliate.get("payment_details") != null ? affiliate.get("payment_details").get("account_number").asText() : null;
                iban.appendChild(document.createTextNode(accountNumber != null ? accountNumber : ""));
                bankverbindung.appendChild(iban);

                Element kontobezeichnung = document.createElement("kontobezeichnung");
                String accountName = affiliate.get("payment_details") != null ? affiliate.get("payment_details").get("account_name").asText() : null;
                kontobezeichnung.appendChild(document.createTextNode(accountName != null ? accountName : ""));
                bankverbindung.appendChild(kontobezeichnung);

                // festkontoForderung element
                Element festkontoForderung = document.createElement("festkontoForderung");
                festkontoForderung.appendChild(document.createTextNode("1400"));
                personenkonto.appendChild(festkontoForderung);

                // rechnungskonditionNr element
                Element rechnungskonditionNr = document.createElement("rechnungskonditionNr");
                rechnungskonditionNr.appendChild(document.createTextNode("1012"));
                personenkonto.appendChild(rechnungskonditionNr);

                // zahlartNr element
                Element zahlartNr = document.createElement("zahlartNr");
                zahlartNr.appendChild(document.createTextNode("31"));
                personenkonto.appendChild(zahlartNr);
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

    private static String getValueAsString(JsonNode node, String fieldName) {
        JsonNode valueNode = node.get(fieldName);
        return valueNode != null && !valueNode.isNull() ? valueNode.asText() : null;
    }
}