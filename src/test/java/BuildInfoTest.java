import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class BuildInfoTest {
    private static Object buildInfo(Properties gitProperties, Properties buildProperties) throws Exception {
        Method method = WebUiServer.class.getDeclaredMethod(
                "buildInfoFromResources", Properties.class, Properties.class);
        method.setAccessible(true);
        return method.invoke(null, gitProperties, buildProperties);
    }

    private static Object value(Object buildInfo, String accessor) throws Exception {
        Method method = buildInfo.getClass().getDeclaredMethod(accessor);
        method.setAccessible(true);
        return method.invoke(buildInfo);
    }

    @Test
    void gitMetadatenHabenVorrangVorBuildZeit() throws Exception {
        Properties git = new Properties();
        git.setProperty("git.commit.time", "20260908063014");
        git.setProperty("git.total.commit.count", "42");
        git.setProperty("git.commit.id.abbrev", "93b398c");
        git.setProperty("git.branch", "main");
        git.setProperty("git.commit.message.short", "Beispiel-Commit");
        Properties fallback = new Properties();
        fallback.setProperty("build.time", "2026-09-08T04:30:00Z");

        Object info = buildInfo(git, fallback);

        assertEquals("20260908063014-000042", value(info, "version"));
        assertEquals("93b398c", value(info, "commit"));
        assertEquals("build", value(info, "source"));
        assertEquals(true, value(info, "sequenceKnown"));
    }

    @Test
    void buildZeitWirdOhneGitInBerlinerZeitVerwendet() throws Exception {
        Properties fallback = new Properties();
        fallback.setProperty("build.time", "2026-09-08T04:30:00Z");

        Object info = buildInfo(new Properties(), fallback);

        assertEquals("20260908063000-000000", value(info, "version"));
        assertEquals("", value(info, "commit"));
        assertEquals("build-time", value(info, "source"));
        assertFalse((Boolean) value(info, "sequenceKnown"));
    }

    @Test
    void ungueltigeMetadatenErgebenKeinenEingebettetenStand() throws Exception {
        Properties git = new Properties();
        git.setProperty("git.commit.time", "ungueltig");
        Properties fallback = new Properties();
        fallback.setProperty("build.time", "kein-zeitpunkt");

        assertNull(buildInfo(git, fallback));
    }
}
