package TopSentinelMailGallery;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class TopSentinelMailGallery {

    private static final String DEFAULT_DB_DRIVER = "oracle.jdbc.driver.OracleDriver";
    private static final String DEFAULT_DB_URL = "jdbc:oracle:thin:@topgate1_sfe:1534/TOPGATE1";
    private static final String DEFAULT_DB_USER = "TOPDBF";
    private static final String DEFAULT_DB_PASSWORD = "";
    private static final String DEFAULT_MAIL_ENDPOINT = "http://topmail:9096/TopMail.asmx";
    private static final String DEFAULT_MAIL_LOG_DIR = "\\\\pvtopesb\\e$\\TopSentinelMailGallery\\logs";
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final int REMINDER_START_MINUTE = 1;
    private static final int REMINDER_END_MINUTE = 9;
    private static final int RESPONSE_LOG_MAX_LENGTH = 500;

    private static final String MAIL_MITTENTE = "topsentinel@asl5.liguria.it";
//    private static final String MAIL_DESTINATARIO =
//            "manuel.manzotti@asl5.liguria.it,"
//            + "alessio.chiappini@asl5.liguria.it,"
//            + "mattia.pierini@asl5.liguria.it,"
//            + "ict.sviluppo@asl5.liguria.it,"
//            + "fabio.demichelis@asl5.liguria.it,"
//            + "marco.vitali@elco.it,"
//            + "raffaella.paladino@elco.it";
    private static final String MAIL_DESTINATARIO = "manuel.manzotti@asl5.liguria.it";
    private static final String MAIL_CCN = "";
    private static final String MAIL_OGGETTO = "TopSentinel - Gallery non raggiungibile";
    private static final String MAIL_CORPO =
            "Si comunica che l'endpoint GALLERY risulta in stato: ENDPOINT NON RAGGIUNGIBILE";

    private static final String SELECT_SQL =
            "SELECT * FROM TOPSENTINEL_ENDPOINT";
    private static final String UPDATE_SQL =
            "UPDATE TOPSENTINEL_ENDPOINT "
            + "SET ATTIVO = ?, "
            + "LAST_UPDATE = SYSDATE, "
            + "ULTIMA_VOLTA_RAGGIUNGIBILE = CASE WHEN ? = 1 THEN SYSDATE ELSE ULTIMA_VOLTA_RAGGIUNGIBILE END "
            + "WHERE ID = ?";
    private static final String DB_NOW_SQL = "SELECT SYSDATE FROM DUAL";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MAIL_LOG_FILE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MAIL_LOG_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private TopSentinelMailGallery() {
    }

    public static void main(String[] args) {
        if (hasFlag(args, "--help") || hasFlag(args, "-h")) {
            printUsage();
            return;
        }

        Config config;
        try {
            config = Config.from(args);
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            System.err.println();
            printUsage();
            System.exit(2);
            return;
        }

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        System.out.println("[" + timestamp + "] Avvio controllo TOPSENTINEL_ENDPOINT per Gallery");
        System.out.println("DB URL: " + config.dbUrl);
        System.out.println("Utente DB: " + config.dbUser);
        System.out.println("Timeout connessione endpoint: " + config.timeoutMs + " ms");
        System.out.println("Endpoint servizio mail: " + config.mailEndpoint);
        System.out.println("Cartella log invio mail: " + config.mailLogDir);

        try {
            DataService dataService = new DataService(
                    null,
                    config.dbDriver,
                    config.dbUrl,
                    config.dbUser,
                    config.dbPassword);
            TopMailService mailService = new TopMailService(config.mailEndpoint);

            int reachableCount = 0;
            int unreachableCount = 0;
            int updatedCount = 0;
            int mailSentCount = 0;
            int mailFailedCount = 0;

            try (Connection conn = dataService.getConnection()) {
                Timestamp dbNow = loadDatabaseNow(conn);
                List<EndpointRow> endpoints = loadEndpoints(conn);
                System.out.println("SYSDATE DB: " + dbNow);
                System.out.println("Righe lette: " + endpoints.size());

                try (PreparedStatement updateStatement = conn.prepareStatement(UPDATE_SQL)) {
                    for (EndpointRow row : endpoints) {
                        CheckResult result = checkReachability(row, config.timeoutMs);
                        MailAlert mailAlert = determineMailAlert(row, result.reachable, dbNow);
                        updateStatus(updateStatement, row.id, result.reachable ? 1 : 0);
                        updatedCount++;

                        if (result.reachable) {
                            reachableCount++;
                        } else {
                            unreachableCount++;
                            if (mailAlert.send) {
                                if (sendUnreachableMail(mailService, config.mailLogDir, row, mailAlert)) {
                                    mailSentCount++;
                                } else {
                                    mailFailedCount++;
                                }
                            }
                        }

                        System.out.println(
                                "ID=" + row.id
                                + " ENDPOINT=" + row.endpoint
                                + " PORTA=" + row.port
                                + " HOST=" + result.host
                                + " ATTIVO_PRECEDENTE=" + printable(row.active)
                                + " ESITO=" + (result.reachable ? "RAGGIUNGIBILE" : "NON RAGGIUNGIBILE")
                                + " ATTIVO=" + (result.reachable ? 1 : 0)
                                + " MAIL=" + mailAlert.summary
                                + " DETTAGLIO=" + result.detail
                                + " TEMPO_MS=" + result.elapsedMs);
                    }
                }
            }

            System.out.println("Controllo completato.");
            System.out.println("Raggiungibili: " + reachableCount);
            System.out.println("Non raggiungibili: " + unreachableCount);
            System.out.println("Record aggiornati: " + updatedCount);
            System.out.println("Mail inviate: " + mailSentCount);
            System.out.println("Mail non inviate: " + mailFailedCount);
        } catch (SQLException ex) {
            System.err.println("Errore durante il controllo degli endpoint: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static Timestamp loadDatabaseNow(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(DB_NOW_SQL);
                ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("Impossibile leggere SYSDATE dal database");
            }
            return rs.getTimestamp(1);
        }
    }

    private static List<EndpointRow> loadEndpoints(Connection conn) throws SQLException {
        List<EndpointRow> endpoints = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SELECT_SQL);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                endpoints.add(new EndpointRow(
                        rs.getLong("ID"),
                        rs.getString("ENDPOINT"),
                        rs.getInt("PORTA"),
                        getNullableInt(rs, "ATTIVO")));
            }
        }
        return endpoints;
    }

    private static Integer getNullableInt(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : Integer.valueOf(value);
    }

    private static void updateStatus(PreparedStatement ps, long id, int activeFlag) throws SQLException {
        ps.setInt(1, activeFlag);
        ps.setInt(2, activeFlag);
        ps.setLong(3, id);
        ps.executeUpdate();
    }

    private static MailAlert determineMailAlert(EndpointRow row, boolean reachable, Timestamp dbNow) {
        if (reachable) {
            return MailAlert.none();
        }

        if (row.wasActive()) {
            return MailAlert.send("CAMBIO_STATO_ATTIVO_1_A_0");
        }

        int currentMinute = dbNow.toLocalDateTime().getMinute();
        if (currentMinute < REMINDER_START_MINUTE || currentMinute > REMINDER_END_MINUTE) {
            return MailAlert.none();
        }

        return MailAlert.send("PROMEMORIA_MINUTO_" + currentMinute);
    }

    private static boolean sendUnreachableMail(
            TopMailService mailService,
            String mailLogDir,
            EndpointRow row,
            MailAlert mailAlert) {
        try {
            TopMailService.MailResult mailResult = mailService.sendMail(
                    MAIL_MITTENTE,
                    MAIL_DESTINATARIO,
                    MAIL_CCN,
                    MAIL_OGGETTO,
                    MAIL_CORPO);

            if (mailResult.isSuccess()) {
                try {
                    writeMailLog(mailLogDir, MAIL_CORPO);
                } catch (IOException ex) {
                    System.err.println("Mail inviata ma scrittura log non riuscita per ID=" + row.id
                            + " PERCORSO=" + mailLogDir
                            + ": " + safe(ex.getMessage()));
                }
                System.out.println("Mail inviata per ID=" + row.id + " MOTIVO=" + mailAlert.summary
                        + " ESITO=" + mailResult.getSummary());
                return true;
            }

            System.err.println("Invio mail non riuscito per ID=" + row.id + " MOTIVO=" + mailAlert.summary
                    + " ESITO=" + mailResult.getSummary()
                    + " RISPOSTA=" + abbreviate(mailResult.getResponseBody(), RESPONSE_LOG_MAX_LENGTH));
            return false;
        } catch (IOException ex) {
            System.err.println("Errore invio mail per ID=" + row.id + " MOTIVO=" + mailAlert.summary
                    + ": " + safe(ex.getMessage()));
            return false;
        }
    }

    private static void writeMailLog(String mailLogDir, String mailBody) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        Path logDir = Paths.get(mailLogDir);
        Files.createDirectories(logDir);

        Path logFile = logDir.resolve(now.format(MAIL_LOG_FILE_FORMAT) + ".txt");
        String line = now.format(MAIL_LOG_TIME_FORMAT)
                + " | "
                + normalizeLogLine(mailBody)
                + System.lineSeparator();

        Files.write(
                logFile,
                line.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    private static CheckResult checkReachability(EndpointRow row, int timeoutMs) {
        String host = normalizeHost(row.endpoint);
        if (host.isEmpty()) {
            return new CheckResult(host, false, 0L, "endpoint vuoto o non valido");
        }
        if (row.port < 1 || row.port > 65535) {
            return new CheckResult(host, false, 0L, "porta non valida");
        }

        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, row.port), timeoutMs);
            return new CheckResult(host, true, elapsedMillis(start), "connessione riuscita");
        } catch (IOException ex) {
            return new CheckResult(host, false, elapsedMillis(start), safe(ex.getMessage()));
        }
    }

    private static String normalizeHost(String endpoint) {
        String trimmed = safe(endpoint).trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        if (trimmed.contains("://")) {
            try {
                URI uri = new URI(trimmed);
                if (uri.getHost() != null && !uri.getHost().trim().isEmpty()) {
                    trimmed = uri.getHost().trim();
                }
            } catch (URISyntaxException ignore) {
            }
        }

        int slashIndex = trimmed.indexOf('/');
        if (slashIndex >= 0) {
            trimmed = trimmed.substring(0, slashIndex);
        }

        if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length() > 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }

        int colonCount = countOccurrences(trimmed, ':');
        if (colonCount == 1) {
            int separatorIndex = trimmed.lastIndexOf(':');
            String suffix = trimmed.substring(separatorIndex + 1);
            if (isDigits(suffix)) {
                trimmed = trimmed.substring(0, separatorIndex);
            }
        }

        return trimmed.trim();
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDigits(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static int countOccurrences(String value, char target) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }

    private static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String printable(Object value) {
        return value == null ? "NULL" : String.valueOf(value);
    }

    private static String abbreviate(String value, int maxLength) {
        String text = safe(value);
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private static String normalizeLogLine(String value) {
        return safe(value)
                .replace("\r\n", " ")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
    }

    private static void printUsage() {
        System.out.println("Uso:");
        System.out.println("  java -jar TopSentinelMailGallery.jar");
        System.out.println("  java -jar TopSentinelMailGallery.jar --timeout-ms=3000");
        System.out.println("  java -jar TopSentinelMailGallery.jar --db-url=... --db-user=... --db-password=...");
        System.out.println();
        System.out.println("Opzioni supportate:");
        System.out.println("  --timeout-ms=<millisecondi>");
        System.out.println("  --db-driver=<classe-driver>");
        System.out.println("  --db-url=<jdbc-url>");
        System.out.println("  --db-user=<utente>");
        System.out.println("  --db-password=<password>");
        System.out.println("  --mail-endpoint=<url-servizio-topmail>");
        System.out.println("  --mail-log-dir=<cartella-log-invio-mail>");
        System.out.println();
        System.out.println("Valori di default:");
        System.out.println("  db-driver=" + DEFAULT_DB_DRIVER);
        System.out.println("  db-url=" + DEFAULT_DB_URL);
        System.out.println("  db-user=" + DEFAULT_DB_USER);
        System.out.println("  timeout-ms=" + DEFAULT_TIMEOUT_MS);
        System.out.println("  mail-endpoint=" + DEFAULT_MAIL_ENDPOINT);
        System.out.println("  mail-log-dir=" + DEFAULT_MAIL_LOG_DIR);
    }

    private static final class Config {

        private final String dbDriver;
        private final String dbUrl;
        private final String dbUser;
        private final String dbPassword;
        private final String mailEndpoint;
        private final String mailLogDir;
        private final int timeoutMs;

        private Config(
                String dbDriver,
                String dbUrl,
                String dbUser,
                String dbPassword,
                String mailEndpoint,
                String mailLogDir,
                int timeoutMs) {
            this.dbDriver = dbDriver;
            this.dbUrl = dbUrl;
            this.dbUser = dbUser;
            this.dbPassword = dbPassword;
            this.mailEndpoint = mailEndpoint;
            this.mailLogDir = mailLogDir;
            this.timeoutMs = timeoutMs;
        }

        private static Config from(String[] args) {
            String dbDriver = DEFAULT_DB_DRIVER;
            String dbUrl = DEFAULT_DB_URL;
            String dbUser = DEFAULT_DB_USER;
            String dbPassword = DEFAULT_DB_PASSWORD;
            String mailEndpoint = DEFAULT_MAIL_ENDPOINT;
            String mailLogDir = DEFAULT_MAIL_LOG_DIR;
            int timeoutMs = DEFAULT_TIMEOUT_MS;

            for (String arg : args) {
                if (!arg.startsWith("--")) {
                    throw new IllegalArgumentException("Argomento non valido: " + arg);
                }
                int separatorIndex = arg.indexOf('=');
                if (separatorIndex < 0) {
                    throw new IllegalArgumentException("Argomento non valido: " + arg);
                }

                String key = arg.substring(2, separatorIndex);
                String value = arg.substring(separatorIndex + 1);

                if ("timeout-ms".equals(key)) {
                    timeoutMs = parsePositiveInt(value, "timeout-ms");
                } else if ("db-driver".equals(key)) {
                    dbDriver = requireValue(value, key);
                } else if ("db-url".equals(key)) {
                    dbUrl = requireValue(value, key);
                } else if ("db-user".equals(key)) {
                    dbUser = requireValue(value, key);
                } else if ("db-password".equals(key)) {
                    dbPassword = value;
                } else if ("mail-endpoint".equals(key)) {
                    mailEndpoint = requireValue(value, key);
                } else if ("mail-log-dir".equals(key)) {
                    mailLogDir = requireValue(value, key);
                } else {
                    throw new IllegalArgumentException("Opzione non supportata: --" + key);
                }
            }

            return new Config(dbDriver, dbUrl, dbUser, dbPassword, mailEndpoint, mailLogDir, timeoutMs);
        }

        private static int parsePositiveInt(String value, String optionName) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed < 1) {
                    throw new IllegalArgumentException("Valore non valido per " + optionName + ": " + value);
                }
                return parsed;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Valore non valido per " + optionName + ": " + value, ex);
            }
        }

        private static String requireValue(String value, String optionName) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException("Valore non valido per " + optionName);
            }
            return value.trim();
        }
    }

    private static final class EndpointRow {

        private final long id;
        private final String endpoint;
        private final int port;
        private final Integer active;

        private EndpointRow(
                long id,
                String endpoint,
                int port,
                Integer active) {
            this.id = id;
            this.endpoint = safe(endpoint).trim();
            this.port = port;
            this.active = active;
        }

        private boolean wasActive() {
            return active != null && active.intValue() == 1;
        }
    }

    private static final class CheckResult {

        private final String host;
        private final boolean reachable;
        private final long elapsedMs;
        private final String detail;

        private CheckResult(String host, boolean reachable, long elapsedMs, String detail) {
            this.host = host;
            this.reachable = reachable;
            this.elapsedMs = elapsedMs;
            this.detail = detail;
        }
    }

    private static final class MailAlert {

        private final boolean send;
        private final String summary;

        private MailAlert(boolean send, String summary) {
            this.send = send;
            this.summary = summary;
        }

        private static MailAlert none() {
            return new MailAlert(false, "NO");
        }

        private static MailAlert send(String summary) {
            return new MailAlert(true, summary);
        }
    }
}
