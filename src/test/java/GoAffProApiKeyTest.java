import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Der GoAffPro-Admin-Schlüssel steht nicht im Code. Er kommt aus GOAFFPRO_API_KEY oder den
 * Einstellungen; fehlt er, wird GoAffPro nicht angefragt und der Endpunkt sagt, was fehlt.
 */
class GoAffProApiKeyTest {
    private static final String MELDUNG =
            "GoAffPro-API-Schlüssel fehlt - bitte in den Einstellungen oder als GOAFFPRO_API_KEY hinterlegen";

    // ── Reflection-Helfer ──

    private static Object invoke(String name, Class<?>[] types, Object... args) throws Throwable {
        Method m = WebUiServer.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        try {
            return m.invoke(null, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static String resolve(Properties config) throws Throwable {
        return (String) invoke("resolveGoaffproApiKey", new Class<?>[]{Properties.class}, config);
    }

    private static String require(Properties config) throws Throwable {
        return (String) invoke("requireGoaffproApiKey", new Class<?>[]{Properties.class}, config);
    }

    /** Ist GOAFFPRO_API_KEY auf dem Rechner gesetzt (Umgebung oder .env), hat er Vorrang vor der Einstellung. */
    private static void assumeKeinSchluesselAusDerUmgebung() throws Throwable {
        assumeTrue(resolve(new Properties()).isEmpty(), "GOAFFPRO_API_KEY ist in Umgebung oder .env gesetzt");
    }

    // ── Tests ──

    @Test
    void ohneSchluesselIstDerRueckfallwertLeer() throws Throwable {
        assumeKeinSchluesselAusDerUmgebung();
        assertEquals("", resolve(new Properties()));
    }

    @Test
    void fehlenderSchluesselBrichtMitDeutscherMeldungAb() throws Throwable {
        assumeKeinSchluesselAusDerUmgebung();
        Properties config = new Properties();
        config.setProperty("goaffproAPIKey", "   ");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> require(config));
        assertEquals(MELDUNG, e.getMessage());
    }

    @Test
    void schluesselAusDenEinstellungenWirdGenutzt() throws Throwable {
        assumeKeinSchluesselAusDerUmgebung();
        Properties config = new Properties();
        config.setProperty("goaffproAPIKey", "  test-schluessel  ");
        assertEquals("test-schluessel", require(config));
    }

    @Test
    void ohneSchluesselGehtKeineAnfrageAnGoAffProRaus() {
        // Die Prüfung liegt vor dem Verbindungsaufbau - der Test braucht kein Netz.
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> invoke("requestJson",
                new Class<?>[]{String.class, String.class},
                "https://api.goaffpro.com/v1/admin/payments?fields=id", ""));
        assertEquals(MELDUNG, e.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void einstellungsAntwortMeldetNurDenStatusNieDenSchluessel() throws Throwable {
        assumeKeinSchluesselAusDerUmgebung();
        Properties config = new Properties();
        config.setProperty("goaffproAPIKey", "geheimer-test-schluessel");
        Map<String, Object> payload = new HashMap<>();
        invoke("putGoaffproApiKeyStatus", new Class<?>[]{Map.class, Properties.class}, payload, config);

        assertEquals(Boolean.TRUE, payload.get("hasGoaffproApiKey"));
        assertEquals(Boolean.FALSE, payload.get("goaffproApiKeyFromEnv"));
        assertFalse(payload.containsKey("goaffproAPIKey"));
        assertFalse(payload.values().stream().anyMatch(v -> String.valueOf(v).contains("geheimer-test-schluessel")),
                "Der Schlüssel darf in keinem Feld der Antwort stehen");
    }

    @Test
    void ohneSchluesselMeldetDieEinstellungsAntwortKeinen() throws Throwable {
        assumeKeinSchluesselAusDerUmgebung();
        Map<String, Object> payload = new HashMap<>();
        invoke("putGoaffproApiKeyStatus", new Class<?>[]{Map.class, Properties.class}, payload, new Properties());
        assertEquals(Boolean.FALSE, payload.get("hasGoaffproApiKey"));
    }

    @Test
    void keineSchluesselKonstanteImCode() throws Exception {
        // GoAffPro-Admin-Schlüssel sind 64 Hex-Zeichen lang. Prüfsummen-Konstanten (z. B. *_SHA256)
        // haben dasselbe Format und bleiben erlaubt, deshalb zählen nur Namen wie *API_KEY*/*TOKEN*/*SECRET*.
        for (Field field : WebUiServer.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) continue;
            if (!field.getName().toUpperCase().matches(".*(API_?KEY|TOKEN|SECRET).*")) continue;
            field.setAccessible(true);
            Object value = field.get(null);
            assertFalse(value != null && value.toString().trim().matches("(?i)[0-9a-f]{64}"),
                    "Konstante " + field.getName() + " sieht nach einem API-Schlüssel aus");
        }
    }
}
