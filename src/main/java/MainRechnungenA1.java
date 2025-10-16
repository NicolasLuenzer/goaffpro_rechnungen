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

public class MainRechnungenA1 {
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
        try (InputStream input = new FileInputStream("/Users/nicolas/IdeaProjects/java_pds_converter/src/main/java/config.properties")) {
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
            if (!lines.isEmpty() && lines.get(0).contains("EXTF")) {
                lines.remove(0); // Entferne die erste Zeile, wenn sie mit "EXTF" anfängt
                Files.write(csvFilePath, lines, Charset.forName("windows-1252"), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING); // Entferne die erste Zeile, wenn sie mit "EXTF" anfängt
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
                Iterable<CSVRecord> records = CSVFormat.DEFAULT.withDelimiter(';').withQuote('"').withSkipHeaderRecord(true).withFirstRecordAsHeader().parse(bufferedReader);
                Map<String, List<CSVRecord>> groupedRecords = new HashMap<>();
                // Transform each group to FibuBelege and add to a list
                FibuBelege fibuBelege = new FibuBelege();
                fibuBelege.setFirmaNr("20");

                // Group records by "Belegfeld 1" and filter out records with "CH" in "EU-Land u. UStID (Bestimmung)"
                for (CSVRecord record : records) {
                    if (!"CH".equals(record.get("EU-Land u. UStID (Bestimmung)"))
                            && !"LI".equals(record.get("EU-Land u. UStID (Bestimmung)"))
                    ) {
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
            String leistungsdatum = formatLeistungsdatum(firstRecord.get("Datum Zuord. Steuerperiode"));
            belegkopf.setBelegdatum(leistungsdatum);

            belegkopf.setBruttoErfassung("j");
            belegkopf.setBuchungstext(firstRecord.get("Buchungstext"));

            belegkopf.setBucherKz("ACCOUNTONE");
            belegkopf.setBelegwaehrungskurs("1");
            belegkopf.setBelegwaehrung("EUR");
            String belegFeld1 = firstRecord.get("Belegfeld 1").replaceAll("AA-(\\d+)", "#$1");
            belegkopf.setReferenznr(belegFeld1);
            fibuBeleg.setBelegkopf(belegkopf);

            FibuBelegpositionen fibuBelegpositionen = new FibuBelegpositionen();
            List<FibuBelegposition> positionList = fibuBelegpositionen.getFibuBelegposition();

            // Add first position based on the first record
            FibuBelegposition firstPosition = new FibuBelegposition();
            firstPosition.setBuchungsschluessel("210");
            firstPosition.setKontonummer(firstRecord.get("Konto"));

            if ("DE".equals(firstRecord.get("EU-Land u. UStID (Bestimmung)"))) {
                firstPosition.setKontonummer(firstRecord.get("Konto"));
            } else
                firstPosition.setKontonummer(mapKononummer(firstRecord.get("Konto"), firstRecord.get("EU-Land u. UStID (Bestimmung)")));

            firstPosition.setBetrag(firstRecord.get("Umsatz (ohne Soll/Haben-Kz)".replace(',', '.')));

            // Set Opinfos for the first position
            FibuBelegposition.Opinfos opinfos = new FibuBelegposition.Opinfos();
            FibuBelegposition.Opinfos.OpAngaben opAngaben = new FibuBelegposition.Opinfos.OpAngaben();
            opAngaben.setOpNr(belegFeld1);

            opAngaben.setOpText(firstRecord.get("Buchungstext"));
            opAngaben.setVerwendungszweck(belegFeld1);

            double aggregatedAmount = 0.0;

            // Summiere Werte aus den folgenden Belegpositionen und integriere in die Schleife
            for (int i = 0; i < records.size(); i++) {
                CSVRecord record = records.get(i);
                if("8120".equals(record.get("Gegenkonto (ohne BU-Schlüssel)")) &&
                        ("CH".equals(firstRecord.get("EU-Land u. UStID (Bestimmung)")) ||
                                "LI".equals(firstRecord.get("EU-Land u. UStID (Bestimmung)"))
                        )
                                 ){
                    continue;
                }


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

                if (!"DE".equals(record.get("EU-Land u. UStID (Bestimmung)"))) {
                    position.setSteuerschluessel(mapSteuersatz(record.get("EU-Steuersatz (Bestimmung)"), record.get("EU-Land u. UStID (Bestimmung)")));
                } else if ("DE".equals(record.get("EU-Land u. UStID (Bestimmung)"))) {
                    position.setSteuerschluessel(mapSteuersatz(record.get("BU-Schlüssel"), "DE"));
                }

                if(("8215".equals(record.get("Gegenkonto (ohne BU-Schlüssel)")) ||
                        "8316".equals(record.get("Gegenkonto (ohne BU-Schlüssel)")) ||
                        "8311".equals(record.get("Gegenkonto (ohne BU-Schlüssel)"))) &&
                    firstRecord.get("Buchungstext").toLowerCase().contains("loni") ||
                        "ATU80064617".equals(firstRecord.get("EU-Land u. UStID (Bestimmung)"))){
                    position.setKontonummer("8215");
                    position.setSteuerschluessel("meg");
                    opAngaben.setUstIdentNr("ATU80064617");
                }
                positionList.add(position);

            }
            aggregatedAmount = Math.round(aggregatedAmount * 100.0) / 100.0;
            //aggregatedAmount = Double.parseDouble(String.format("%.2f", aggregatedAmount).replace(',', '.')); hier kam es aber vor, dass -0.0 raus kam

            if(aggregatedAmount < 0){
                belegkopf.setBelegart("ga");
                firstPosition.setBuchungsschluessel("250");
                aggregatedAmount = aggregatedAmount * -1;
            }
            firstPosition.setBetrag(String.valueOf(aggregatedAmount));
            opAngaben.setOpBetrag(String.valueOf(aggregatedAmount));

            opinfos.setOpAngaben(opAngaben);
            firstPosition.setOpinfos(opinfos);
            positionList.add(0, firstPosition);

            fibuBeleg.setFibuBelegpositionen(fibuBelegpositionen);

            return fibuBeleg;
        }

        private String mapSteuersatz(String steuersatz, String land) {
            switch (steuersatz) {
                case "20,00", "20":
                    return "meg20";
                case "10,00", "10":
                    return "meg10";
                case "2,00", "2":
                    return "22";
                case "6", "6,00":
                    return "meg6BE";
                case "17,00", "17":
                    return "meg17LU";
                case "3,00", "3":
                    if("LU".equals(land))
                        return "meg3LU";
                    else if("DE".equals(land))
                        return "50";
                    else
                        return steuersatz;
                case "21", "21,00":
                    return switch (land) {
                        case "BE" -> "meg21BE";
                        case "ES" -> "meg21ES";
                        case null, default -> "kein_meg21_fuer_land";
                    };
                case "173", "173,00":
                    return "mnull";
                case "0", "0,00":
                    return switch (land) {
                        case "DE" -> "mnst";
                        case "AT" -> "meg0";
                        case "BE" -> "meg0BE";
                        case "ES" -> "meg0ES";
                        case "LU" -> "meg0LU";
                        case null, default -> "0";
                    };
                default:
                    return steuersatz;
            }
        }

        private String mapKononummer(String kontonummer, String land) {
            if ("AT".equals(land) || "ATU80064617".equals(land) )
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
                case "20009":
                    return "20011";
                default:
                    return kontonummer;
            }
            else if ("BE".equals(land))
                switch (kontonummer) {
                case "20001":
                    return "20018";
                case "20002":
                    return "20019";
                case "20004":
                    return "20020";
                case "20005":
                    return "20022";
                case "20008":
                    return "20024";
                case "20009":
                        return "20010";
                default:
                    return kontonummer;
            }
            else if  ("ES".equals(land))
                switch (kontonummer) {
                    case "20001":
                        return "20023";
                    case "20002":
                        return "20024";
                    case "20004":
                        return "20025";
                    case "20005":
                        return "20027";
                    case "20008":
                        return "20026";
                    case "20009":
                        return "20033";
                    default:
                        return kontonummer;
                }
            else if  ("LU".equals(land))
                switch (kontonummer) {
                    case "20001":
                        return "20028";
                    case "20002":
                        return "20029";
                    case "20004":
                        return "20030";
                    case "20005":
                        return "20031";
                    case "20008":
                        return "20032";
                    case "20009":
                        return "20034";
                    default:
                        return kontonummer;
                }
            else
                return kontonummer;
        }


        private String formatLeistungsdatum(String leistungsdatum) {
            if (leistungsdatum.length() == 8) {
                return leistungsdatum.substring(0, 2) + "." + leistungsdatum.substring(2,4) + "." + leistungsdatum.substring(4);
            }
            if (leistungsdatum.length() == 7) {
                leistungsdatum = "0" + leistungsdatum;
                return leistungsdatum.substring(0, 2) + "." + leistungsdatum.substring(2,4) + "." + leistungsdatum.substring(4);
            }
            return leistungsdatum;
        }

        private void saveXmlToFile(FibuBelege fibuBelege) throws JAXBException, IOException {
            JAXBContext jaxbContext = JAXBContext.newInstance(FibuBelege.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = "export_fibu_beleg_übernahme_erlöse_accountOne" + timestamp + ".xml";
            Path exportFilePath = Paths.get(exportPath, fileName);

            try (FileOutputStream fos = new FileOutputStream(exportFilePath.toFile())) {
                marshaller.setProperty("com.sun.xml.bind.xmlHeaders", "<!-- Erstellt am: " + timestamp + " -->\n");
                marshaller.marshal(fibuBelege, fos);
            }
            System.out.println("Datei geschrieben");
        }
    }
}
