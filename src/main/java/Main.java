import generated.FibuBelege;
import generated.FibuBelege.FibuBeleg;
import generated.FibuBelege.FibuBeleg.Belegkopf;
import generated.FibuBelege.FibuBeleg.FibuBelegpositionen;
import generated.FibuBelege.FibuBeleg.FibuBelegpositionen.FibuBelegposition;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

public class Main {
    private static String importBelegePath;
    private static String importZahlungenPath;
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

            importBelegePath = prop.getProperty("importBelegePath");
            importZahlungenPath = prop.getProperty("importZahlungenPath");
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
            if (!lines.isEmpty() && lines.get(0).startsWith("EXTF")) {
                lines.remove(0); // Entferne die erste Zeile, wenn sie mit "EXTF" anfängt
                Files.write(csvFilePath, lines, Charset.forName("windows-1252"), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        }

        @Override
        public void run() {
            try {
                DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Paths.get(importBelegePath));
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
                Iterable<CSVRecord> records = CSVFormat.DEFAULT.withDelimiter(';').withSkipHeaderRecord(true).withFirstRecordAsHeader().parse(bufferedReader);
                Map<String, List<CSVRecord>> groupedRecords = new HashMap<>();
                // Transform each group to FibuBelege and add to a list
                FibuBelege fibuBelege = new FibuBelege();
                fibuBelege.setFirmaNr("20");

                // Group records by "Belegfeld 1" and filter out records with "CH" in "EU-Land u. UStID (Bestimmung)"
                for (CSVRecord record : records) {
                    if (!"CH".equals(record.get("EU-Land u. UStID (Bestimmung)"))) {
                        String belegfeld1 = record.get("Belegfeld 1");
                        groupedRecords.computeIfAbsent(belegfeld1, k -> new ArrayList<>()).add(record);
                    }
                }

                // Transform each group to FibuBelege and save as XML
                for (Map.Entry<String, List<CSVRecord>> entry : groupedRecords.entrySet()) {
                    FibuBeleg fibuBeleg = transformToFibuBeleg(entry.getValue());
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

        private FibuBeleg transformToFibuBeleg(List<CSVRecord> records) {
            if (records.isEmpty()) {
                return null;
            }

            CSVRecord firstRecord = records.get(0);
            FibuBeleg fibuBeleg = new FibuBeleg();
            Belegkopf belegkopf = new Belegkopf();
            belegkopf.setBelegart("ra");
            belegkopf.setBelegnummer("AUTO");

            // Set Leistungsdatum using a separate method to format it
            String leistungsdatum = formatLeistungsdatum(firstRecord.get("Leistungsdatum"));
            belegkopf.setBelegdatum(leistungsdatum);

            belegkopf.setBruttoErfassung("j");
            belegkopf.setBuchungstext(firstRecord.get("Buchungstext").replaceAll("AA-(\\d+)", "#$1"));
            belegkopf.setBucherKz("ACCOUNTONE");
            belegkopf.setBelegwaehrungskurs("1");
            belegkopf.setBelegwaehrung("EUR");
            belegkopf.setReferenznr(firstRecord.get("Belegfeld 1"));
            fibuBeleg.setBelegkopf(belegkopf);

            FibuBelegpositionen fibuBelegpositionen = new FibuBelegpositionen();
            List<FibuBelegposition> positionList = fibuBelegpositionen.getFibuBelegposition();

            // Add first position based on the first record
            FibuBelegposition firstPosition = new FibuBelegposition();
            firstPosition.setBuchungsschluessel("210");
            firstPosition.setKontonummer(firstRecord.get("Konto"));
            firstPosition.setBetrag(firstRecord.get("Umsatz (ohne Soll/Haben-Kz)".replace(',', '.')));

            // Set Opinfos for the first position
            FibuBelegposition.Opinfos opinfos = new FibuBelegposition.Opinfos();
            FibuBelegposition.Opinfos.OpAngaben opAngaben = new FibuBelegposition.Opinfos.OpAngaben();
            opAngaben.setOpNr(firstRecord.get("Belegfeld 1"));
            opAngaben.setOpText(firstRecord.get("Buchungstext").replaceAll("AA-(\\d+)", "#$1"));
            opAngaben.setVerwendungszweck(firstRecord.get("Belegfeld 1"));

            double aggregatedAmount = 0.0;

            // Summiere Werte aus den folgenden Belegpositionen und integriere in die Schleife
            for (int i = 0; i < records.size(); i++) {
                CSVRecord record = records.get(i);
                String SoderH = record.get("Soll/Haben-Kennzeichen");
                double betrag = Double.parseDouble(record.get("Basis-Umsatz").replace(',', '.'));
                if ("S".equals(SoderH)) {
                    aggregatedAmount += betrag;
                } else if ("H".equals(SoderH)) {
                    aggregatedAmount -= betrag;
                }
                FibuBelegposition position = new FibuBelegposition();
                position.setBuchungsschluessel("S".equals(SoderH) ? "150" : "110");
                position.setKontonummer(record.get("Gegenkonto (ohne BU-Schlüssel)"));
                position.setBetrag(record.get("Basis-Umsatz").replace(',', '.'));
                position.setPosLeistungsdatum(leistungsdatum);
                position.setSteuerschluessel(mapSteuersatz(record.get("EU-Steuersatz")));
                positionList.add(position);

            }
            firstPosition.setBetrag(String.valueOf(aggregatedAmount));
            opAngaben.setOpBetrag(String.valueOf(aggregatedAmount));
            opinfos.setOpAngaben(opAngaben);
            firstPosition.setOpinfos(opinfos);
            positionList.add(0, firstPosition);

            fibuBeleg.setFibuBelegpositionen(fibuBelegpositionen);

            return fibuBeleg;
        }

        private String mapSteuersatz(String steuersatz) {
            Map<String, String> steuersatzMapping = new HashMap<>();
            steuersatzMapping.put("20", "meg20");
            steuersatzMapping.put("10", "meg10");
            return steuersatzMapping.getOrDefault(steuersatz, steuersatz);
        }

        private String formatLeistungsdatum(String leistungsdatum) {
            if (leistungsdatum.length() == 4) {
                return leistungsdatum.substring(0, 2) + "." + leistungsdatum.substring(2) + "." + belegJahr;
            }
            return leistungsdatum;
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
