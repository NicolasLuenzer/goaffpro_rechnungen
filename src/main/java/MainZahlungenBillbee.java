import generated.FibuBelege;
import generated.FibuBelege.FibuBeleg;
import generated.FibuBelege.FibuBeleg.Belegkopf;
import generated.FibuBelege.FibuBeleg.FibuBelegpositionen;
import generated.FibuBelege.FibuBeleg.FibuBelegpositionen.FibuBelegposition;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainZahlungenBillbee {

    private static String importZahlungenBillbeePath;
    private static String processedPath;
    private static String exportPath;
    private static String processPath;
    private static String belegJahr;

    public static void main(String[] args) {
        // Load configuration properties
        loadConfig();

        // Schedule the task to run periodically every 10 seconds
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new FileProcessingTask(), 0, 10000);

        // Keep the main thread alive
        while (true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void loadConfig() {
        try (InputStream input = new FileInputStream("C:\\Users\\nluenzer\\IdeaProjects\\untitled\\src\\main\\java\\config.properties")) {
            Properties prop = new Properties();
            prop.load(input);

            importZahlungenBillbeePath = prop.getProperty("importZahlungenBillbeePath");
            processedPath = prop.getProperty("processedPath");
            exportPath = prop.getProperty("exportPath");
            processPath = prop.getProperty("processPath");
            belegJahr = prop.getProperty("belegJahr");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    static class FileProcessingTask extends TimerTask {

        private void removeFirstLine(Path csvFilePath) throws IOException {
            List<String> lines = Files.readAllLines(csvFilePath, Charset.forName("windows-1252"));
            if (!lines.isEmpty() && lines.get(0).startsWith("\"EXTF")) {
                lines.remove(0); // Entferne die erste Zeile, wenn sie mit "EXTF" anfängt
                Files.write(csvFilePath, lines, Charset.forName("windows-1252"), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING); // Entferne die erste Zeile, wenn sie mit "EXTF" anfängt
                Files.write(csvFilePath, lines, Charset.forName("windows-1252"), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        }

        @Override
        public void run() {
            try {
                DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Paths.get(importZahlungenBillbeePath));
                for (Path filePath : directoryStream) {
                    if (Files.isRegularFile(filePath)) {
                        try {
                            // Remove the first line from the CSV file if necessary
                            removeFirstLineSafely(filePath);

                            // Extract, Transform, Load (ETL)
                            extractTransformLoad(filePath);

                            // Move the processed file to the processed directory
                            Path processedFilePath = Paths.get(processedPath, filePath.getFileName().toString());
                            Files.move(filePath, processedFilePath, StandardCopyOption.REPLACE_EXISTING);

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void extractTransformLoad(Path csvFilePath) {
            try (BufferedReader bufferedReader = Files.newBufferedReader(csvFilePath, Charset.forName("windows-1252"))) {
                Iterable<CSVRecord> records = CSVFormat.DEFAULT.withDelimiter(';').withQuote('"').withSkipHeaderRecord(true).withFirstRecordAsHeader().parse(bufferedReader);
                FibuBelege fibuBelege = new FibuBelege();
                fibuBelege.setFirmaNr("20");

                // Transform each record to FibuBelege and save as XML
                for (CSVRecord record : records) {
                    FibuBeleg fibuBeleg = transformToFibuBeleg(record);
                    if (fibuBeleg != null) {
                        fibuBelege.getFibuBeleg().add(fibuBeleg);
                    }
                }

                // Save all FibuBelege to a single XML file
                saveXmlToFile(fibuBelege);
            } catch (IOException | JAXBException e) {
                e.printStackTrace();
            }
        }

        private void removeFirstLineSafely(Path csvFilePath) {
            try {
                removeFirstLine(csvFilePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private FibuBeleg transformToFibuBeleg(CSVRecord record) {
            FibuBeleg fibuBeleg = new FibuBeleg();
            Belegkopf belegkopf = new Belegkopf();
            belegkopf.setBelegart("ze");
            belegkopf.setBelegnummer("AUTO");
            belegkopf.setBelegdatum(formatLeistungsdatum(record.get("Belegdatum")));
            belegkopf.setBruttoErfassung("j");
            belegkopf.setBuchungstext(record.get("Belegfeld 1"));
            belegkopf.setBucherKz("ACCOUNTONE");
            belegkopf.setBelegwaehrungskurs("1");
            belegkopf.setBelegwaehrung("EUR");

            belegkopf.setReferenznr(record.get("Auftragsnummer"));
            fibuBeleg.setBelegkopf(belegkopf);

            FibuBelegpositionen fibuBelegpositionen = new FibuBelegpositionen();
            List<FibuBelegposition> positionList = fibuBelegpositionen.getFibuBelegposition();

            // Add first position based on the first record
            FibuBelegposition firstPosition = new FibuBelegposition();

            boolean isDE = "Deutschland".equals(record.get("Belegfeld 2"));

            firstPosition.setBuchungsschluessel("110");
            firstPosition.setKontonummer("1223");
            double betrag = Double.parseDouble(record.get("Umsatz").replace(',', '.'));
            firstPosition.setBetrag(String.valueOf(betrag));
            positionList.add(firstPosition);

            FibuBelegposition secondPosition = new FibuBelegposition();

            secondPosition.setBuchungsschluessel("260");
            secondPosition.setKontonummer(isDE ? "20004" : "20015");
            secondPosition.setBetrag(record.get("Umsatz").replace(',', '.'));
            secondPosition.setPosLeistungsdatum(formatLeistungsdatum(record.get("Leistungsdatum")));
            positionList.add(secondPosition);

                // Set Opinfos for the second position
                FibuBelegposition.Opinfos opinfos = new FibuBelegposition.Opinfos();
                FibuBelegposition.Opinfos.OpAngaben opAngaben = new FibuBelegposition.Opinfos.OpAngaben();
                opAngaben.setOpNr(belegkopf.getReferenznr());
                opAngaben.setOpText(belegkopf.getBuchungstext());
                opAngaben.setVerwendungszweck(belegkopf.getReferenznr());
                opAngaben.setOpBetrag(secondPosition.getBetrag());
                opinfos.setOpAngaben(opAngaben);
                secondPosition.setOpinfos(opinfos);

            fibuBeleg.setFibuBelegpositionen(fibuBelegpositionen);
            return fibuBeleg;
        }

        private String formatLeistungsdatum(String leistungsdatum) {
            if (leistungsdatum.length() == 8) {
                return leistungsdatum.substring(0, 2) + "." + leistungsdatum.substring(2, 4) + "." + leistungsdatum.substring(4);
            } else if (leistungsdatum.length() == 4) {
                return leistungsdatum.substring(0, 2) + "." + leistungsdatum.substring(2, 4) + "." + belegJahr;
            }
            return leistungsdatum;
        }

        private void saveXmlToFile(FibuBelege fibuBelege) throws JAXBException, IOException {
            JAXBContext jaxbContext = JAXBContext.newInstance(FibuBelege.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = "export_zahlungen_" + timestamp + ".xml";
            Path exportFilePath = Paths.get(exportPath, fileName);

            try (FileOutputStream fos = new FileOutputStream(exportFilePath.toFile())) {
                marshaller.marshal(fibuBelege, fos);
            }
        }
    }
}
