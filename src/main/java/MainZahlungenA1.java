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

public class MainZahlungenA1 {

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
        try (InputStream input = new FileInputStream("/Users/nicolas/IdeaProjects/java_pds_converter/src/main/java/config.properties")) {
            Properties prop = new Properties();
            prop.load(input);

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
            if (!lines.isEmpty() && lines.get(0).contains("EXTF")) {
                lines.remove(0); // Entferne die erste Zeile, wenn sie mit "EXTF" anfängt
                Files.write(csvFilePath, lines, Charset.forName("windows-1252"), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING); // Entferne die erste Zeile, wenn sie mit "EXTF" anfängt
                Files.write(csvFilePath, lines, Charset.forName("windows-1252"), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        }

        @Override
        public void run() {
            try {
                DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Paths.get(importZahlungenPath));
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
            String SoderH = record.get("Soll/Haben-Kennzeichen");
            belegkopf.setBelegart("H".equals(SoderH) ? "za" : "S".equals(SoderH) ? "ze" : null);
            belegkopf.setBelegnummer("AUTO");
            String leistungsdatum = formatLeistungsdatum(record.get("Belegdatum"));
            belegkopf.setBelegdatum(leistungsdatum);
            belegkopf.setBruttoErfassung("j");
            belegkopf.setBuchungstext(record.get("Buchungstext"));
            belegkopf.setBucherKz("ACCOUNTONE");
            belegkopf.setBelegwaehrungskurs("1");
            belegkopf.setBelegwaehrung("EUR");
            String belegFeld1 = record.get("Belegfeld 1").replaceAll("AA-(\\d+)", "#$1");

            boolean isGebuehren = belegFeld1.isEmpty();
            boolean isTransit = "Transit".equals(belegFeld1);
            boolean isZahlung = !isGebuehren && !isTransit;
            boolean isZA = "za".equals(belegkopf.getBelegart());
            boolean isZE = "ze".equals(belegkopf.getBelegart());
            boolean isCH = "173".equals(record.get("BU-Schlüssel"))/* && isZahlung*/;
            boolean isAT = "AT".equals(record.get("EU-Land u. UStID (Bestimmung)"));

            belegkopf.setReferenznr(isGebuehren ? "Gebühr" : isTransit ? "Transit" : belegFeld1);
            fibuBeleg.setBelegkopf(belegkopf);

            FibuBelegpositionen fibuBelegpositionen = new FibuBelegpositionen();
            List<FibuBelegposition> positionList = fibuBelegpositionen.getFibuBelegposition();

            // Add first position based on the first record
            FibuBelegposition firstPosition = new FibuBelegposition();



            firstPosition.setBuchungsschluessel(isZE ? "110" : isZA ? "150" : null);
            if(isTransit || isGebuehren) firstPosition.setBuchungsschluessel(isZE ? "110" : isZA ? "150" : null);
            if(isZahlung) firstPosition.setBuchungsschluessel(isZE ? "110" : isZA ? "150" : null);


            firstPosition.setKontonummer(record.get("Konto"));
            double betrag = Double.parseDouble(record.get("Basis-Umsatz").replace(',', '.'));
            firstPosition.setBetrag(String.valueOf(betrag));
            positionList.add(firstPosition);

            FibuBelegposition secondPosition = new FibuBelegposition();

            // wenn transit oder gebühr dann 110
            // sonst (bei Zahlungen) wenn za dann 230 oder wenn ze dann 260
            String buchungsschluessel = isTransit || isGebuehren ? "110" : isZA ? "230" : isZE ? "260" : null;
            if(isZahlung && isZE) {
                buchungsschluessel= "260";
            }
            else if (isZahlung && isZA) buchungsschluessel = "230";

            if((isTransit || isGebuehren) && isZE) {
                buchungsschluessel= "150";
            }
            else if ((isTransit || isGebuehren) && isZE)
                buchungsschluessel = "110";

            if("2151".equals(record.get("Gegenkonto (ohne BU-Schlüssel)")) && isZE)
            {
                buchungsschluessel= "150";
            }
            else if ("2151".equals(record.get("Gegenkonto (ohne BU-Schlüssel)")) && isZA)
            {
                buchungsschluessel= "110";
            }

            secondPosition.setBuchungsschluessel(buchungsschluessel);

            // für die Schweizer
            // wenn zahlung und bu-schlüssel = 173  und gegenkonto 20002 oder 20001 dann setzte 50001 sonst gegenkonto (ohne BU-Schlüssel)
            String gegenkonto = record.get("Gegenkonto (ohne BU-Schlüssel)");
            if("1452".equals(gegenkonto)) return null;

            if (isCH) {
                secondPosition.setKontonummer("50001");
            } else {
                secondPosition.setKontonummer(mapKontonummer(gegenkonto, isAT));
            }
            secondPosition.setBetrag(record.get("Basis-Umsatz").replace(',', '.'));
            secondPosition.setPosLeistungsdatum(leistungsdatum);
            if(isGebuehren) secondPosition.setSteuerschluessel("v13b_Inl");
            positionList.add(secondPosition);

            if(isZahlung) {
                // Set Opinfos for the second position
                FibuBelegposition.Opinfos opinfos = new FibuBelegposition.Opinfos();
                FibuBelegposition.Opinfos.OpAngaben opAngaben = new FibuBelegposition.Opinfos.OpAngaben();
                opAngaben.setOpNr(belegFeld1);
                if(isCH)
                {
                    opAngaben.setOpNr(leistungsdatum.replaceAll("(\\d{2})\\.(\\d{2})\\.(\\d{2})(\\d{2})", "$2/$4"));
                }
                opAngaben.setOpText(record.get("Buchungstext"));
                opAngaben.setVerwendungszweck(belegFeld1);
                opAngaben.setOpBetrag(secondPosition.getBetrag());
                opinfos.setOpAngaben(opAngaben);
                secondPosition.setOpinfos(opinfos);
            }

            fibuBeleg.setFibuBelegpositionen(fibuBelegpositionen);

            return fibuBeleg;
        }

        private Boolean isPersonenkonto(String kontonummer) {
            Map<String, Boolean> mappingTabelle = new HashMap<>();
            mappingTabelle.put("20001", true);
            mappingTabelle.put("20002", true);
            mappingTabelle.put("20004", true);
            mappingTabelle.put("2151", false);
            mappingTabelle.put("1360", false);
            mappingTabelle.put("4970", false);

            return mappingTabelle.getOrDefault(kontonummer, false);
        }

        private String mapSteuersatz(String steuersatz) {
            switch (steuersatz) {
                case "20,00":
                    return "meg20";
                case "10,00":
                    return "meg10";
                case "3":
                    return "50";
                case "2":
                    return "22";
                case "173":
                    return "mnull";
                default:
                    return steuersatz;
            }
        }

        private String mapKontonummer(String kontonummer, boolean isAT) {
            if(!isAT)
                return kontonummer;

            // wenn Österreich dann mapping
            switch (kontonummer) {
                case "20001":
                    return "20013";
                case "20002":
                    return "20014";
                case "20004":
                    return "20015";
                case "20005":
                    return "20016";
                case "20008":
                    return "20017";
                default:
                    return kontonummer;
            }
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
            String fileName = "export_fibu_beleg_übernahme_zahlungen_accountOne" + timestamp + ".xml";
            Path exportFilePath = Paths.get(exportPath, fileName);

            try (FileOutputStream fos = new FileOutputStream(exportFilePath.toFile())) {
                marshaller.setProperty("com.sun.xml.bind.xmlHeaders", "<!-- Erstellt am: " + timestamp + " -->\n");
                marshaller.marshal(fibuBelege, fos);
            }
            System.out.println("Datei geschrieben");
        }
    }
}
