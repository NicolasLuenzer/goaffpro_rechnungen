import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gutschriften führen keine stornierten Provisionen aus der Zeit vor dem Start der
 * S+R-Provisionen (01.10.2026). Ausgeblendet wird je Bestellung bzw. Belohnung und nur, was
 * sich zu 0 aufhebt - sonst wird die Belegerstellung abgebrochen.
 */
class GutschriftStartDateTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── Reflection-Helfer ──

    private static Object split(JsonNode payment, Properties config) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("splitBeforeGutschriftStart", JsonNode.class, Properties.class);
        m.setAccessible(true);
        return m.invoke(null, payment, config);
    }

    private static Object call(Object target, String method) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        return m.invoke(target);
    }

    private static String blockReason(Object split, Properties config) throws Exception {
        Class<?> splitClass = Class.forName("WebUiServer$StartDateSplit");
        Method m = WebUiServer.class.getDeclaredMethod("gutschriftStartBlockReason",
                splitClass, String.class, Properties.class);
        m.setAccessible(true);
        return (String) m.invoke(null, split, "p1", config);
    }

    private static String periodLabel(JsonNode payment) throws Exception {
        Method m = WebUiServer.class.getDeclaredMethod("buildPaymentPeriodLabel", JsonNode.class);
        m.setAccessible(true);
        return (String) m.invoke(null, payment);
    }

    private static int hiddenCount(Object split) throws Exception {
        return (Integer) call(split, "hiddenCount");
    }

    private static boolean balanced(Object split) throws Exception {
        return (Boolean) call(split, "balanced");
    }

    @SuppressWarnings("unchecked")
    private static List<String> unbalanced(Object split) throws Exception {
        return (List<String>) call(split, "unbalanced");
    }

    private static JsonNode visible(Object split) throws Exception {
        return (JsonNode) call(split, "visiblePayment");
    }

    private static List<String> visibleTxIds(Object split) throws Exception {
        List<String> ids = new ArrayList<>();
        for (JsonNode tx : visible(split).path("transactions")) ids.add(tx.path("tx_id").asText());
        return ids;
    }

    // ── Testdaten ──

    private static String tx(String txId, String entityType, long entityId, String event,
                             String amount, String createdAt, String orderNumber) {
        return "{\"tx_id\":\"" + txId + "\",\"entity_type\":\"" + entityType + "\",\"entity_id\":" + entityId
                + ",\"event_type\":\"" + event + "\",\"amount\":\"" + amount + "\",\"created_at\":\"" + createdAt
                + "\",\"metadata\":{\"order_number\":\"" + orderNumber + "\"}}";
    }

    private static JsonNode payment(String amount, String... transactions) throws Exception {
        return MAPPER.readTree("{\"id\":\"p1\",\"amount\":\"" + amount + "\",\"currency\":\"EUR\","
                + "\"created_at\":\"2026-10-15T10:00:00Z\",\"transactions\":[" + String.join(",", transactions) + "]}");
    }

    // ── Tests ──

    @Test
    void nurBuchungenAbStartBleibenUnveraendert() throws Exception {
        JsonNode payment = payment("25.00", tx("t1", "orders", 2, "insert", "25.00", "2026-10-05T09:00:00Z", "#2002"));
        Object s = split(payment, new Properties());

        assertEquals(0, hiddenCount(s));
        assertTrue(balanced(s));
        assertEquals(List.of("t1"), visibleTxIds(s));
        assertNull(blockReason(s, new Properties()));
    }

    @Test
    void stornierteAltprovisionWirdAusgeblendet() throws Exception {
        JsonNode payment = payment("25.00",
                tx("t1", "orders", 1, "insert", "10.00", "2026-09-10T09:00:00Z", "#1001"),
                tx("t2", "orders", 1, "delete", "-10.00", "2026-09-20T09:00:00Z", "#1001"),
                tx("t3", "orders", 2, "insert", "25.00", "2026-10-05T09:00:00Z", "#2002"));
        Object s = split(payment, new Properties());

        assertTrue(balanced(s));
        assertEquals(2, hiddenCount(s), "Buchung und Storno der Altprovision verschwinden beide");
        assertEquals(List.of("t3"), visibleTxIds(s));
        assertNull(blockReason(s, new Properties()));
    }

    /**
     * Der Fall, der einen reinen Datumsschnitt scheitern ließe: storniert wurde erst im Oktober,
     * die Stornobuchung trägt deshalb ein Datum nach dem Start.
     */
    @Test
    void stornoNachStartWirdMitSeinerBuchungAusgeblendet() throws Exception {
        JsonNode payment = payment("25.00",
                tx("t1", "orders", 1, "insert", "10.00", "2026-09-25T09:00:00Z", "#1001"),
                tx("t2", "orders", 1, "delete", "-10.00", "2026-10-03T09:00:00Z", "#1001"),
                tx("t3", "orders", 2, "insert", "25.00", "2026-10-05T09:00:00Z", "#2002"));
        Object s = split(payment, new Properties());

        assertTrue(balanced(s));
        assertEquals(2, hiddenCount(s));
        assertEquals(List.of("t3"), visibleTxIds(s),
                "Die Stornobuchung vom Oktober gehört zu ihrer September-Buchung und verschwindet mit ihr");
    }

    @Test
    void nichtStornierteAltprovisionBlockiert() throws Exception {
        JsonNode payment = payment("35.00",
                tx("t1", "orders", 1, "insert", "10.00", "2026-09-10T09:00:00Z", "#1291"),
                tx("t2", "orders", 2, "insert", "25.00", "2026-10-05T09:00:00Z", "#2002"));
        Object s = split(payment, new Properties());

        assertFalse(balanced(s));
        assertEquals(0, hiddenCount(s), "Bei Restsaldo wird nichts ausgeblendet");
        assertEquals(List.of("t1", "t2"), visibleTxIds(s), "Der Zahllauf bleibt unverändert");
        assertEquals(List.of("Bestellung #1291: 10,00 €"), unbalanced(s));

        String reason = blockReason(s, new Properties());
        assertNotNull(reason);
        assertTrue(reason.contains("#1291"), "Die betroffene Bestellung muss genannt werden");
        assertTrue(reason.contains("01.10.2026"));
        assertTrue(reason.contains("keine Belegnummer"), "Es darf klar sein, dass keine Nummer verbraucht wurde");
    }

    @Test
    void teilstornoBlockiert() throws Exception {
        JsonNode payment = payment("32.00",
                tx("t1", "orders", 1, "insert", "10.00", "2026-09-25T09:00:00Z", "#1001"),
                tx("t2", "orders", 1, "update", "-3.00", "2026-10-03T09:00:00Z", "#1001"),
                tx("t3", "orders", 2, "insert", "25.00", "2026-10-05T09:00:00Z", "#2002"));
        Object s = split(payment, new Properties());

        assertFalse(balanced(s));
        assertEquals(List.of("Bestellung #1001: 7,00 €"), unbalanced(s));
    }

    /** Liegt die ursprüngliche Buchung in einem früheren Zahllauf, zählt der Zeitpunkt der Korrektur. */
    @Test
    void reineKorrekturAbStartBleibtSichtbar() throws Exception {
        JsonNode payment = payment("20.00",
                tx("t1", "orders", 9, "update", "-5.00", "2026-10-03T09:00:00Z", "#0909"),
                tx("t2", "orders", 2, "insert", "25.00", "2026-10-05T09:00:00Z", "#2002"));
        Object s = split(payment, new Properties());

        assertTrue(balanced(s));
        assertEquals(0, hiddenCount(s));
        assertEquals(List.of("t1", "t2"), visibleTxIds(s));
    }

    @Test
    void undatierteBuchungBleibtSichtbar() throws Exception {
        JsonNode payment = payment("10.00", tx("t1", "orders", 1, "insert", "10.00", "", "#1001"));
        Object s = split(payment, new Properties());

        assertEquals(0, hiddenCount(s));
        assertEquals(List.of("t1"), visibleTxIds(s));
    }

    /** 2026-09-30T22:00:00Z ist 01.10.2026 00:00 in Berlin (Sommerzeit). */
    @Test
    void grenzeIstMitternachtBerlin() throws Exception {
        JsonNode payment = payment("25.00",
                tx("t1", "orders", 1, "insert", "10.00", "2026-09-30T21:59:58Z", "#1001"),
                tx("t2", "orders", 1, "delete", "-10.00", "2026-09-30T21:59:59Z", "#1001"),
                tx("t3", "orders", 2, "insert", "25.00", "2026-09-30T22:00:00Z", "#2002"));
        Object s = split(payment, new Properties());

        assertEquals(2, hiddenCount(s), "30.09. 23:59:59 Berlin liegt noch vor dem Start");
        assertEquals(List.of("t3"), visibleTxIds(s), "01.10. 00:00 Berlin zählt bereits dazu");
    }

    @Test
    void reihenfolgeUndAuszahlungsbetragBleibenErhalten() throws Exception {
        JsonNode payment = payment("40.00",
                tx("t1", "orders", 3, "insert", "15.00", "2026-10-07T09:00:00Z", "#3003"),
                tx("t2", "orders", 1, "insert", "10.00", "2026-09-10T09:00:00Z", "#1001"),
                tx("t3", "orders", 2, "insert", "25.00", "2026-10-05T09:00:00Z", "#2002"),
                tx("t4", "orders", 1, "delete", "-10.00", "2026-09-20T09:00:00Z", "#1001"));
        Object s = split(payment, new Properties());

        assertEquals(List.of("t1", "t3"), visibleTxIds(s), "Die sichtbaren Buchungen behalten ihre Reihenfolge");
        assertEquals("40.00", visible(s).path("amount").asText(), "Der Auszahlungsbetrag bleibt unverändert");
        assertEquals(4, payment.path("transactions").size(), "Der ursprüngliche Zahllauf wird nicht verändert");
    }

    /** Dieselbe entity_id bei Bestellung und Belohnung sind zwei verschiedene Provisionen. */
    @Test
    void bestellungUndBelohnungMitGleicherIdWerdenNichtVerrechnet() throws Exception {
        JsonNode payment = payment("25.00",
                tx("t1", "orders", 100, "insert", "10.00", "2026-09-10T09:00:00Z", "#1001"),
                tx("t2", "rewards", 100, "delete", "-10.00", "2026-09-12T09:00:00Z", "#1001"),
                tx("t3", "orders", 2, "insert", "25.00", "2026-10-05T09:00:00Z", "#2002"));
        Object s = split(payment, new Properties());

        assertFalse(balanced(s), "Bestellung und Belohnung dürfen sich nicht gegenseitig aufheben");
        assertEquals(List.of("Bestellung #1001: 10,00 €", "Belohnung 100 zu #1001: -10,00 €"), unbalanced(s));
    }

    @Test
    void ausschliesslichStornierteAltbuchungenBlockieren() throws Exception {
        JsonNode payment = payment("0.00",
                tx("t1", "orders", 1, "insert", "10.00", "2026-09-10T09:00:00Z", "#1001"),
                tx("t2", "orders", 1, "delete", "-10.00", "2026-09-20T09:00:00Z", "#1001"));
        Object s = split(payment, new Properties());

        assertTrue(balanced(s));
        String reason = blockReason(s, new Properties());
        assertNotNull(reason, "Ohne verbleibende Buchung darf keine leere Gutschrift entstehen");
        assertTrue(reason.contains("ausschließlich"));
    }

    @Test
    void ohneTransaktionenUnveraendert() throws Exception {
        JsonNode payment = MAPPER.readTree("{\"id\":\"p1\",\"amount\":\"0.00\",\"created_at\":\"2026-10-15T10:00:00Z\"}");
        Object s = split(payment, new Properties());

        assertEquals(0, hiddenCount(s));
        assertTrue(balanced(s));
        assertNull(blockReason(s, new Properties()));
    }

    @Test
    void startdatumIstKonfigurierbar() throws Exception {
        JsonNode payment = payment("25.00",
                tx("t1", "orders", 1, "insert", "10.00", "2026-10-10T09:00:00Z", "#1001"),
                tx("t2", "orders", 1, "delete", "-10.00", "2026-10-12T09:00:00Z", "#1001"),
                tx("t3", "orders", 2, "insert", "25.00", "2026-11-05T09:00:00Z", "#2002"));

        assertEquals(0, hiddenCount(split(payment, new Properties())), "Standard 01.10.: Oktober bleibt sichtbar");

        Properties november = new Properties();
        november.setProperty("gutschriftTransactionStartDate", "2026-11-01");
        assertEquals(2, hiddenCount(split(payment, november)), "Mit Start 01.11. wird der Oktober ausgeblendet");
    }

    @Test
    void ungueltigesStartdatumFaelltAufStandardZurueck() throws Exception {
        Properties kaputt = new Properties();
        kaputt.setProperty("gutschriftTransactionStartDate", "nicht-ein-datum");
        JsonNode payment = payment("25.00",
                tx("t1", "orders", 1, "insert", "10.00", "2026-09-10T09:00:00Z", "#1001"),
                tx("t2", "orders", 1, "delete", "-10.00", "2026-09-20T09:00:00Z", "#1001"),
                tx("t3", "orders", 2, "insert", "25.00", "2026-10-05T09:00:00Z", "#2002"));

        assertEquals(2, hiddenCount(split(payment, kaputt)));
    }

    @Test
    void leistungszeitraumBeginntAbStart() throws Exception {
        JsonNode payment = payment("25.00",
                tx("t1", "orders", 1, "insert", "10.00", "2026-09-10T09:00:00Z", "#1001"),
                tx("t2", "orders", 1, "delete", "-10.00", "2026-09-20T09:00:00Z", "#1001"),
                tx("t3", "orders", 2, "insert", "25.00", "2026-10-05T09:00:00Z", "#2002"));

        assertEquals("10.09.2026 bis 05.10.2026", periodLabel(payment), "Ohne Bereinigung reicht der Zeitraum in den September");
        assertEquals("05.10.2026 bis 05.10.2026", periodLabel(visible(split(payment, new Properties()))));
    }
}
