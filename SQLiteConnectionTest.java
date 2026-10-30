package com.smartcare;

import com.smartcare.database.DatabaseConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQLite JDBC Connection and Functionality Test.
 * Satisfies Part 3.D requirements:
 * 1. Load SQLite JDBC driver.
 * 2. Open JDBC connection.
 * 3. Execute SELECT sqlite_version().
 * 4. Verify connection is valid.
 * 5. Create a temporary test table.
 * 6. Insert and verify record read-back.
 * 7. Close resources properly with try-with-resources.
 * 8. Print clear success/failure diagnostics.
 */
public class SQLiteConnectionTest {

    @Test
    @DisplayName("Verify SQLite JDBC Driver, Version, and CRUD Operations")
    public void testSQLiteDriverAndOperations() {
        boolean success = runVerification();
        assertTrue(success, "SQLite Connection Test must succeed!");
    }

    public static boolean runVerification() {
        System.out.println("=================================================");
        System.out.println("   SMARTCARE — SQLITE JDBC CONNECTION TEST       ");
        System.out.println("=================================================");

        String dbPath = DatabaseConnection.getDatabasePath();
        String jdbcUrl = DatabaseConnection.getJdbcUrl();
        System.out.println("[Step 1] Database file target: " + dbPath);
        System.out.println("[Step 1] JDBC URL:             " + jdbcUrl);

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Step 2 & 4: Verify connection
            if (conn == null || conn.isClosed() || !conn.isValid(3)) {
                System.err.println("[FAIL] Connection could not be validated.");
                return false;
            }
            System.out.println("[Step 2] JDBC Connection established successfully.");

            // Step 3: Execute SELECT sqlite_version()
            String sqliteVersion = "";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT sqlite_version();")) {
                if (rs.next()) {
                    sqliteVersion = rs.getString(1);
                    System.out.println("[Step 3] SQLite Version reported by JDBC: " + sqliteVersion);
                } else {
                    System.err.println("[FAIL] Could not query sqlite_version()");
                    return false;
                }
            }

            // Step 4: Verify foreign keys pragma
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys;")) {
                if (rs.next()) {
                    int fkEnabled = rs.getInt(1);
                    System.out.println("[Step 4] Foreign keys enabled: " + (fkEnabled == 1 ? "YES (1)" : "NO (0)"));
                    if (fkEnabled != 1) {
                        System.err.println("[FAIL] Foreign keys must be enabled on connection.");
                        return false;
                    }
                }
            }

            // Step 5: Create a temporary test table
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS _test_connectivity ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "test_name TEXT NOT NULL, "
                        + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP);");
                System.out.println("[Step 5] Temporary test table '_test_connectivity' ready.");
            }

            // Step 6: Insert and read back record
            String sampleToken = "SmartCare_Init_" + System.currentTimeMillis();
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO _test_connectivity (test_name) VALUES (?);", Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, sampleToken);
                int rows = pstmt.executeUpdate();
                if (rows != 1) {
                    System.err.println("[FAIL] Failed to insert verification record.");
                    return false;
                }
            }

            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT test_name FROM _test_connectivity WHERE test_name = ?;")) {
                pstmt.setString(1, sampleToken);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next() && sampleToken.equals(rs.getString("test_name"))) {
                        System.out.println("[Step 6] Verification record inserted and read back successfully.");
                    } else {
                        System.err.println("[FAIL] Verification record mismatch or not found.");
                        return false;
                    }
                }
            }

            // Clean up temporary test table
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS _test_connectivity;");
                System.out.println("[Step 7] Temporary test table cleaned up successfully.");
            }

            System.out.println("=================================================");
            System.out.println("   RESULT: SQLITE CONNECTION TEST PASSED (OK)    ");
            System.out.println("=================================================");
            return true;

        } catch (Exception e) {
            System.err.println("[FAIL] Exception during SQLite verification: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static void main(String[] args) {
        boolean ok = runVerification();
        System.exit(ok ? 0 : 1);
    }
}
