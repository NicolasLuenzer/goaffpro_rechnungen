import generated.FibuBelege;
import generated.FibuBelege.FibuBeleg;
import generated.FibuBelege.FibuBeleg.Belegkopf;
import generated.FibuBelege.FibuBeleg.FibuBelegpositionen;
import generated.FibuBelege.FibuBeleg.FibuBelegpositionen.FibuBelegposition;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

public class Main {
    private static String importBelegePath;
    private static String importZahlungenPath;
    private static String processedPath;
    private static String exportPath;
    private static String processPath;

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

            importBelegePath = prop.getProperty("importBelegePath");
            importZahlungenPath = prop.getProperty("importZahlungenPath");
            processedPath = prop.getProperty("processedPath");
            exportPath = prop.getProperty("exportPath");
            processPath = prop.getProperty("processPath");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    static class FileProcessingTask extends TimerTask {

        private void removeFirstLine(Path csvFilePath) throws IOException {
            List<String> lines = Files.readAllLines(csvFilePath, StandardCharsets.UTF_8);
            if (!lines.isEmpty()) {
                lines.remove(0); // Entferne die erste Zeile
                Files.write(csvFilePath, lines, StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        }

        @Override
        public void run() {
            try {
                DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Paths.get(importBelegePath));
                for (Path filePath : directoryStream) {
                    if (Files.isRegularFile(filePath)) {
                        try {
                            // Remove the first line from the CSV file
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
            try (BufferedReader bufferedReader = Files.newBufferedReader(csvFilePath, StandardCharsets.UTF_8)) {
                Iterable<CSVRecord> records = CSVFormat.DEFAULT.withDelimiter(';').withSkipHeaderRecord(true).withFirstRecordAsHeader().parse(bufferedReader);
                for (CSVRecord record : records) {
                    // Extract data from CSV and map to FibuBelege object
                    FibuBelege fibuBelege = transformToFibuBelege(record);
                    saveXmlToFile(fibuBelege);
                }
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

        private FibuBelege transformToFibuBelege(CSVRecord record) {
            // Create FibuBelege and populate it with data
            FibuBelege fibuBelege = new FibuBelege();
            fibuBelege.setFirmaNr("20");

            FibuBeleg fibuBeleg = new FibuBeleg();
            Belegkopf belegkopf = new Belegkopf();
            belegkopf.setBelegart(record.get("Umsatz (ohne Soll/Haben-Kz)"));
            belegkopf.setBelegnummer(record.get("Soll/Haben-Kennzeichen"));
            belegkopf.setBelegdatum(record.get("WKZ Umsatz"));
            belegkopf.setBruttoErfassung(record.get("Kurs"));
            belegkopf.setBuchungstext(record.get("Basis-Umsatz"));
            belegkopf.setBucherKz(record.get("WKZ Basis-Umsatz"));
            belegkopf.setBelegwaehrungskurs(record.get("Konto"));
            belegkopf.setBelegwaehrung(record.get("Gegenkonto (ohne BU-Schlüssel)"));
            belegkopf.setReferenznr(record.get("BU-Schlüssel"));
            fibuBeleg.setBelegkopf(belegkopf);

            FibuBelegpositionen fibuBelegpositionen = new FibuBelegpositionen();
            List<FibuBelegposition> positionList = fibuBelegpositionen.getFibuBelegposition();

            // Assuming multiple positions in the CSV line
            for (int i = 9; i < record.size(); i += 5) {
                FibuBelegposition position = new FibuBelegposition();
                position.setBuchungsschluessel(record.get(i));
                position.setKontonummer(record.get(i + 1));
                position.setBetrag(record.get(i + 2));
                if (!record.get(i + 3).isEmpty()) {
                    FibuBelegposition.Opinfos opinfos = new FibuBelegposition.Opinfos();
                    FibuBelegposition.Opinfos.OpAngaben opAngaben = new FibuBelegposition.Opinfos.OpAngaben();
                    opAngaben.setOpNr(record.get(i + 3));
                    opAngaben.setOpText(record.get(i + 4));
                    opAngaben.setVerwendungszweck(record.get(i + 3));
                    opAngaben.setOpBetrag(record.get(i + 2));
                    opinfos.setOpAngaben(opAngaben);
                    position.setOpinfos(opinfos);
                }
                positionList.add(position);
            }

            fibuBeleg.setFibuBelegpositionen(fibuBelegpositionen);
            fibuBelege.getFibuBeleg().add(fibuBeleg);

            return fibuBelege;
        }

        private void saveXmlToFile(FibuBelege fibuBelege) throws JAXBException, IOException {
            JAXBContext jaxbContext = JAXBContext.newInstance(FibuBelege.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = "export_" + timestamp + ".xml";
            Path exportFilePath = Paths.get(exportPath, fileName);

            try (FileOutputStream fos = new FileOutputStream(exportFilePath.toFile())) {
                marshaller.marshal(fibuBelege, fos);
            }
        }
    }
}
