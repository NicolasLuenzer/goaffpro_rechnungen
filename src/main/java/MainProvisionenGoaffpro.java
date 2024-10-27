import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import generated.FibuBelege;

import generated.FibuBelege.FibuBeleg;
import generated.FibuBelege.FibuBeleg.Belegkopf;

import generated.FibuBelege.FibuBeleg.FibuBelegpositionen.FibuBelegposition;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;


public class MainProvisionenGoaffpro {

    public static final String CONFIG_PROPERTIES_PATH = "C:\\Users\\nluenzer\\IdeaProjects\\untitled\\src\\main\\java\\config.properties";
    private static String API_URL;
    private static String API_KEY;
    private static String exportPath;
    private static String lastImportedComission;

    public static void main(String[] args) {
        loadConfig();
        API_URL = "https://api.goaffpro.com/v1/admin/payments?since_id=" + lastImportedComission + "&fields=id,affiliate_id,amount,currency,payment_method,payment_details,affiliate_message,admin_note,created_at";
        try {
            String jsonResponse = makeApiRequest(API_URL);
            if (jsonResponse != null) {
                generateXMLFromResponse(jsonResponse);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadConfig() {
        try (InputStream input = new FileInputStream(CONFIG_PROPERTIES_PATH)) {
            Properties prop = new Properties();
            prop.load(input);
            API_KEY = prop.getProperty("goaffproAPIKey");
            exportPath = prop.getProperty("exportPath");
            lastImportedComission = prop.getProperty("lastImportedComission");
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

    private static void generateXMLFromResponse(String jsonResponse) throws JAXBException, IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        JsonNode paymentsNode = rootNode.get("payments");

        if (paymentsNode != null && paymentsNode.size() > 0) {
            FibuBelege fibuBelege = new FibuBelege();
            fibuBelege.setFirmaNr("20");

            String highestId = lastImportedComission;

            for (JsonNode payment : paymentsNode) {
                FibuBeleg fibuBeleg = new FibuBeleg();

                // Belegkopf
                Belegkopf belegkopf = new Belegkopf();
                belegkopf.setBelegart("re");
                belegkopf.setBelegnummer("AUTO");
                String belegdatum = getValueAsString(payment, "created_at");
                if (belegdatum != null) {
                    try {
                        Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(belegdatum);
                        belegdatum = new SimpleDateFormat("dd.MM.yyyy").format(date);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                belegkopf.setBelegdatum(belegdatum);
                belegkopf.setBruttoErfassung("j");

                String affiliateId = getValueAsString(payment, "affiliate_id");
                String affiliateName = "";
                if (affiliateId != null) {
                    try {
                        String affiliateResponse = makeAffiliateNameRequest(affiliateId);
                        if (affiliateResponse != null) {
                            JsonNode affiliateNode = objectMapper.readTree(affiliateResponse).get("affiliates").get(0);
                            affiliateName = getValueAsString(affiliateNode, "name");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                belegkopf.setBuchungstext(affiliateName);

                belegkopf.setBucherKz("ACCOUNTONE");
                belegkopf.setBelegwaehrungskurs("1");
                belegkopf.setBelegwaehrung("EUR");
                belegkopf.setReferenznr(getValueAsString(payment, "id"));
                fibuBeleg.setBelegkopf(belegkopf);

                // Update highestId if current payment id is greater
                String currentId = getValueAsString(payment, "id");
                if (currentId != null && currentId.compareTo(highestId) > 0) {
                    highestId = currentId;
                }

                // FibuBelegposition
                FibuBelegposition positionFirst = new FibuBelegposition();
                positionFirst.setBuchungsschluessel("350");
                positionFirst.setKontonummer(getValueAsString(payment, "affiliate_id"));
                positionFirst.setBetrag(getValueAsString(payment, "amount"));


                // Set Opinfos for the first position
                FibuBelegposition.Opinfos opinfos = new FibuBelegposition.Opinfos();
                FibuBelegposition.Opinfos.OpAngaben opAngaben = new FibuBelegposition.Opinfos.OpAngaben();

                opAngaben.setOpNr(getValueAsString(payment, "id"));
                opAngaben.setOpText(affiliateName);
                opAngaben.setVerwendungszweck(getValueAsString(payment, "id"));
                opAngaben.setOpBetrag(getValueAsString(payment, "amount"));
                opinfos.setOpAngaben(opAngaben);
                positionFirst.setOpinfos(opinfos);


                // FibuBelegposition Second
                FibuBelegposition positionSecond = new FibuBelegposition();
                positionSecond.setBuchungsschluessel("110");
                positionSecond.setKontonummer(getValueAsString(payment, "affiliate_id")); // hier gucken
                positionSecond.setBetrag(getValueAsString(payment, "amount"));
                positionSecond.setPosLeistungsdatum(belegdatum);

                FibuBeleg.FibuBelegpositionen fibuBelegpositionen = new FibuBeleg.FibuBelegpositionen();
                List<FibuBelegposition> positionList = fibuBelegpositionen.getFibuBelegposition();
                positionList.add(positionFirst);
                positionList.add(positionSecond);

                fibuBeleg.setFibuBelegpositionen(fibuBelegpositionen);
                fibuBelege.getFibuBeleg().add(fibuBeleg);
            }

            // Write the content into an XML file using JAXB
            JAXBContext jaxbContext = JAXBContext.newInstance(FibuBelege.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = "fibu_belege_" + timestamp + ".xml";
            Path exportFilePath = Paths.get(exportPath, fileName);

            try (FileOutputStream fos = new FileOutputStream(exportFilePath.toFile())) {
                marshaller.marshal(fibuBelege, fos);
            }

            System.out.println("XML file generated and saved successfully: " + exportFilePath);

            // Update the lastImportedComission in the config file
            updateConfigPropertyWithoutReloading("lastImportedComission", highestId);
        }
    }

    private static void updateConfigPropertyWithoutReloading(String key, String value) {
        try (RandomAccessFile raf = new RandomAccessFile(CONFIG_PROPERTIES_PATH, "rw")) {
            String line;
            long writePosition = 0;

            while ((line = raf.readLine()) != null) {
                if (line.startsWith(key + "=")) {
                    break;
                }
                writePosition = raf.getFilePointer();
            }

            raf.seek(writePosition);
            raf.writeBytes(key + "=" + value + System.lineSeparator());

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    private static String makeAffiliateNameRequest(String affiliateId) throws Exception {
        String apiUrl = String.format("https://api.goaffpro.com/v1/admin/affiliates?id=%s&fields=name", affiliateId);
        return makeApiRequest(apiUrl);
    }
    private static String getValueAsString(JsonNode node, String fieldName) {
        JsonNode valueNode = node.get(fieldName);
        return valueNode != null && !valueNode.isNull() ? valueNode.asText() : null;
    }
}