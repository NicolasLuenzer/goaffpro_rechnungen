import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Belegnummern-Register: ordnet jedem Zahllauf (GoAffPro-Payment) je Dokumentart genau eine
 * Stammnummer zu. Wird für denselben Zahllauf erneut ein Beleg erzeugt, zieht er keine neue
 * fortlaufende Nummer, sondern eine Version der Stammnummer: GS-2026-0042, GS-2026-0042-1, -2 ...
 *
 * Das Register liegt in der Sync-Datenbank und nicht in config.properties: Die Properties werden
 * im Read-Modify-Write-Zyklus als Ganzes zurückgeschrieben, ein Registereintrag ginge dabei
 * verloren. Die Datenbank sichert BackupService ohnehin vollständig mit.
 *
 * Zustandslos wie BackupService: der DB-Pfad kommt als Parameter herein, dadurch lässt sich die
 * Klasse gegen temporäre Verzeichnisse testen.
 */
public final class DocumentNumberRegistry {

    static final String TABLE = "document_numbers";

    /** Vergebene Nummer. {@code replaces} ist der unmittelbar vorherige Beleg, leer bei der Stammnummer. */
    public record Issued(String number, String replaces, int version) {
    }

    /** Belege eines Zahllaufs aus der Zeit vor dem Register: früheste und zuletzt vergebene Nummer. */
    public record Existing(String baseNumber, String lastNumber) {
    }

    private record Row(String baseNumber, int lastVersion, String lastNumber) {
    }

    private static final Object LOCK = new Object();

    /** So lange wartet die Vergabe auf die Schreibsperre, wenn gerade der Sync schreibt. */
    static final int LOCK_TIMEOUT_MS = 20_000;

    /**
     * Die Datenbank blieb belegt, solange die Vergabe auf die Schreibsperre wartete. Bis dahin
     * wurde weder gelesen noch eine Nummer gezogen - ein neuer Versuch ist gefahrlos.
     */
    public static final class DatabaseBusyException extends Exception {
        DatabaseBusyException(Throwable cause) {
            super("Die Datenbank ist gerade belegt – vermutlich schreibt der GoAffPro-Sync. Bitte in einer Minute"
                    + " erneut versuchen oder unter GoAffPro Sync „Sync pausieren“. Es wurde keine Belegnummer vergeben.",
                    cause);
        }
    }

    private DocumentNumberRegistry() {
    }

    /**
     * Vergibt die Belegnummer für einen Zahllauf.
     *
     * @param altbestand      wird nur gefragt, wenn das Register den Zahllauf noch nicht kennt;
     *                        liefert die vor Einführung des Registers vergebenen Nummern
     * @param neueStammnummer zieht die nächste fortlaufende Nummer; wird nur aufgerufen, wenn
     *                        weder Register noch Altbestand eine Stammnummer kennen
     * @throws DatabaseBusyException wenn die Datenbank länger als {@link #LOCK_TIMEOUT_MS} belegt war
     */
    public static Issued issue(Path db, String paymentId, String kind,
                               Supplier<Optional<Existing>> altbestand,
                               Callable<String> neueStammnummer) throws Exception {
        return issue(db, paymentId, kind, altbestand, neueStammnummer, LOCK_TIMEOUT_MS);
    }

    /**
     * Die Schreibsperre kommt zuerst (BEGIN IMMEDIATE), erst danach wird gelesen und eine Nummer
     * gezogen. Bisher begann die Transaktion lesend und brauchte die Sperre erst beim Eintragen.
     * Schrieb der Sync dazwischen, ließ SQLite die Transaktion nicht mehr schreiben (SQLITE_BUSY
     * bzw. BUSY_SNAPSHOT, ohne auf busy_timeout zu warten) - und die schon gezogene Nummer war
     * verloren (NAS, 23.11.2026).
     *
     * Bewusst ein ausdrückliches BEGIN/COMMIT bei eingeschaltetem JDBC-Autocommit: Mit dem
     * Transaktionsmodus IMMEDIATE von sqlite-jdbc begänne nach commit() sofort die nächste
     * Schreibtransaktion - die könnte nach einem erfolgreichen Commit noch an BUSY scheitern.
     */
    static Issued issue(Path db, String paymentId, String kind,
                        Supplier<Optional<Existing>> altbestand,
                        Callable<String> neueStammnummer, int lockTimeoutMs) throws Exception {
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("paymentId fehlt");
        }
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("Dokumentart fehlt");
        }
        synchronized (LOCK) {
            try (Connection c = connect(db, lockTimeoutMs)) {
                beginImmediate(c);
                try {
                    ensureTable(c);
                    Issued issued = issueInTransaction(c, paymentId.trim(), kind.trim(), altbestand, neueStammnummer);
                    execute(c, "COMMIT");
                    return issued;
                } catch (Exception e) {
                    rollbackQuietly(c);
                    throw e;
                }
            }
        }
    }

    private static void beginImmediate(Connection c) throws Exception {
        try {
            execute(c, "BEGIN IMMEDIATE");
        } catch (SQLException e) {
            if (isBusy(e)) throw new DatabaseBusyException(e);
            throw e;
        }
    }

    /** SQLITE_BUSY samt erweiterter Codes wie BUSY_SNAPSHOT oder BUSY_RECOVERY. */
    static boolean isBusy(SQLException e) {
        return (e.getErrorCode() & 0xFF) == 5;
    }

    private static void execute(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private static void rollbackQuietly(Connection c) {
        try {
            execute(c, "ROLLBACK");
        } catch (Exception ignored) {
            // Beim Schließen der Verbindung verwirft SQLite die offene Transaktion ohnehin.
        }
    }

    private static Issued issueInTransaction(Connection c, String paymentId, String kind,
                                             Supplier<Optional<Existing>> altbestand,
                                             Callable<String> neueStammnummer) throws Exception {
        String now = Instant.now().toString();
        Row row = find(c, paymentId, kind);
        if (row == null) {
            Optional<Existing> alt = altbestand != null ? altbestand.get() : Optional.empty();
            if (alt.isPresent() && !isBlank(alt.get().baseNumber())) {
                String base = alt.get().baseNumber().trim();
                String last = isBlank(alt.get().lastNumber()) ? base : alt.get().lastNumber().trim();
                insert(c, paymentId, kind, base, last, "altbestand", now);
                row = new Row(base, 0, last);
            } else {
                String base = neueStammnummer.call();
                if (isBlank(base)) {
                    throw new IllegalStateException("Es wurde keine Belegnummer erzeugt.");
                }
                insert(c, paymentId, kind, base, base, "neu", now);
                return new Issued(base, "", 0);
            }
        }

        int version = row.lastVersion() + 1;
        String number = row.baseNumber() + "-" + version;
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE " + TABLE + " SET last_version=?, last_number=?, last_issued_at=? "
                        + "WHERE payment_id=? AND document_kind=?")) {
            ps.setInt(1, version);
            ps.setString(2, number);
            ps.setString(3, now);
            ps.setString(4, paymentId);
            ps.setString(5, kind);
            ps.executeUpdate();
        }
        return new Issued(number, row.lastNumber(), version);
    }

    private static Row find(Connection c, String paymentId, String kind) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT base_number, last_version, last_number FROM " + TABLE
                        + " WHERE payment_id=? AND document_kind=?")) {
            ps.setString(1, paymentId);
            ps.setString(2, kind);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Row(rs.getString(1), rs.getInt(2), rs.getString(3));
            }
        }
    }

    private static void insert(Connection c, String paymentId, String kind, String base, String last,
                               String source, String now) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + TABLE + " (payment_id, document_kind, base_number, last_version, last_number, "
                        + "source, first_issued_at, last_issued_at) VALUES (?,?,?,0,?,?,?,?)")) {
            ps.setString(1, paymentId);
            ps.setString(2, kind);
            ps.setString(3, base);
            ps.setString(4, last);
            ps.setString(5, source);
            ps.setString(6, now);
            ps.setString(7, now);
            ps.executeUpdate();
        }
    }

    private static void ensureTable(Connection c) throws Exception {
        try (Statement st = c.createStatement()) {
            // base_number ist UNIQUE: Eine Stammnummer gehört genau einem Zahllauf. Eine doppelt
            // vergebene Nummer scheitert damit laut, statt still zwei Belege gleich zu nummerieren.
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS document_numbers (
                      payment_id TEXT NOT NULL,
                      document_kind TEXT NOT NULL,
                      base_number TEXT NOT NULL UNIQUE,
                      last_version INTEGER NOT NULL DEFAULT 0,
                      last_number TEXT NOT NULL,
                      source TEXT NOT NULL,
                      first_issued_at TEXT NOT NULL,
                      last_issued_at TEXT NOT NULL,
                      PRIMARY KEY (payment_id, document_kind)
                    )
                    """);
        }
    }

    private static Connection connect(Path db, int lockTimeoutMs) throws Exception {
        Path parent = db.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
        try (Statement st = connection.createStatement()) {
            // Der Sync-Thread schreibt in dieselbe Datei: So lange wartet BEGIN IMMEDIATE auf ihn.
            st.execute("PRAGMA busy_timeout=" + Math.max(0, lockTimeoutMs));
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
