import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;


public class WorkingWithJDBC {

    private static final String URL = "jdbc:postgresql://localhost:5432/test_db_jdbc?useUnicode=true&characterEncoding=UTF-8";
    private static final String USER = "postgres";
    private static final String PASSWORD = "";

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            
        createTables(conn);
        insertTestData(conn);
        Map<Integer, Map<String, Object>> statsMap = getStats(conn);
        generateXmlReport(statsMap);

        } catch (SQLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createTables(Connection conn) throws SQLException {
        String dropDepartments = "DROP TABLE IF EXISTS sessions CASCADE";
        String dropUsers = "DROP TABLE IF EXISTS users CASCADE";
        String dropDepartmentsTable = "DROP TABLE IF EXISTS departments CASCADE";

        String createDepartments = """
            CREATE TABLE departments (
                key   INTEGER PRIMARY KEY,
                name  VARCHAR(100),
                label VARCHAR(10)
            )""";

        String createUsers = """
            CREATE TABLE users (
                key             INTEGER PRIMARY KEY,
                name            VARCHAR(100),
                login           VARCHAR(50),
                password        VARCHAR(50),
                key_department  INTEGER REFERENCES departments(key)
            )""";

        String createSessions = """
            CREATE TABLE sessions (
                key         INTEGER PRIMARY KEY,
                key_user    INTEGER REFERENCES users(key),
                date_logon  TIMESTAMP,
                date_logoff TIMESTAMP
            )""";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(dropDepartments);
            stmt.execute(dropUsers);
            stmt.execute(dropDepartmentsTable);
            stmt.execute(createDepartments);
            stmt.execute(createUsers);
            stmt.execute(createSessions);
        }
    }

    private static void insertTestData(Connection conn) throws SQLException {

        String insertDept = "INSERT INTO departments (key, name, label) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertDept)) {
            Object[][] departments  = {
                {1, "Разработка", "DEV"},
                {2, "Аналитика", "ANL"}
            };
            for (Object[] d : departments) {
                ps.setInt(1, (int) d[0]);
                ps.setString(2, (String) d[1]);
                ps.setString(3, (String) d[2]);
                ps.executeUpdate();
            }
        }

        String insertUser = "INSERT INTO users (key, name, login, password, key_department) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertUser)) {
            Object[][] users = {
                {1, "Алексеев Д.А.", "alexeev_da", "dev_pass_1", 1},
                {2, "Борисов С.В.", "borisov_sv", "dev_pass_2", 1},
                {3, "Виноградова Е.Н.", "vinogradova_en", "anl_pass_1", 2},
                {4, "Дмитриев К.М.", "dmitriev_km", "anl_pass_2", 2}
            };
            for (Object[] u : users) {
                ps.setInt(1, (int) u[0]);
                ps.setString(2, (String) u[1]);
                ps.setString(3, (String) u[2]);
                ps.setString(4, (String) u[3]);
                ps.setInt(5, (int) u[4]);
                ps.executeUpdate();
            }
        }

        String insertSession = "INSERT INTO sessions (key, key_user, date_logon, date_logoff) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSession)) {
            Object[][] sessions = {
                {1, 1, "2026-04-20 09:00:00", "2026-04-20 18:05:00"},
                {2, 2, "2026-04-20 10:15:00", "2026-04-20 19:00:00"},
                {3, 3, "2026-04-21 08:50:00", "2026-04-21 17:30:00"},
                {4, 4, "2026-04-21 11:00:00", "2026-04-21 14:20:00"}
            };
            for (Object[] s : sessions) {
                ps.setInt(1, (int) s[0]);
                ps.setInt(2, (int) s[1]);
                ps.setTimestamp(3, Timestamp.valueOf((String) s[2]));
                ps.setTimestamp(4, Timestamp.valueOf((String) s[3]));
                ps.executeUpdate();
            }
        }
    }

    private static Map<Integer, Map<String, Object>> getStats(Connection conn) throws SQLException {
        String sql = """
            SELECT
                d.key AS key_department,
                d.name AS name_department,
                COUNT(s.key) AS count_session,
                COALESCE(EXTRACT(epoch FROM SUM(s.date_logoff - s.date_logon)) /60, 0) AS time_session
            FROM departments d
            LEFT JOIN users AS u ON u.key_department = d.key
            LEFT JOIN sessions AS s ON s.key_user = u.key
            GROUP BY d.key, d.name
            ORDER BY d.key
            """;

        Map<Integer, Map<String, Object>> map = new HashMap<>();
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int key = rs.getInt("key_department");
                Map<String, Object> row = new HashMap<>();
                row.put("name_department", rs.getString("name_department"));
                row.put("count_session", rs.getInt("count_session"));
                row.put("time_session", rs.getDouble("time_session"));
                map.put(key, row);
            }
        }

        return map;
    }

    private static void generateXmlReport(Map<Integer, Map<String, Object>> statsMap) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<report>\n");
        for (Map.Entry<Integer, Map<String, Object>> entry : statsMap.entrySet()) {
            int keyDep = entry.getKey();
            Map<String, Object> row = entry.getValue();
            String name = (String) row.get("name_department");
            int count = (int) row.get("count_session");
            int minutes = ((Double) row.get("time_session")).intValue();  // целое число минут
            xml.append("  <department");
            xml.append(" key_department=\"").append(keyDep).append("\"");
            xml.append(" name_department=\"").append(name).append("\"");
            xml.append(" count_session=\"").append(count).append("\"");
            xml.append(" time_session=\"").append(minutes).append("\"");
            xml.append("/>\n");
        }
        xml.append("</report>");
        String workingDirectory = System.getProperty("user.dir");
        Path outputPath = new File(workingDirectory, "report.xml").toPath();
        Files.writeString(outputPath, xml.toString(), StandardCharsets.UTF_8);
    }
}
