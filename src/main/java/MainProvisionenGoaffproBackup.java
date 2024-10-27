// Example usage from another class
public class MainProvisionenGoaffproBackup {
    public static void main(String[] args) {
        try {
            String affiliateId = "15233060";
            String jsonResponse = ExportAffiliatesFromGoaffproHandler.makeApiRequest(affiliateId);
            if (jsonResponse != null) {
                ExportAffiliatesFromGoaffproHandler.generateXMLFromResponse(jsonResponse);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}