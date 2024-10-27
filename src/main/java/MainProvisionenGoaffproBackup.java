// Example usage from another class
public class MainProvisionenGoaffproBackup {
    public static void main(String[] args) {
        try {
            String affiliateId = "13637495,15654476";
            String jsonResponse = ExportAffiliatesFromGoaffproHandler.makeApiRequest(affiliateId);
            if (jsonResponse != null) {
                ExportAffiliatesFromGoaffproHandler.generateXMLFromResponse(jsonResponse);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}