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
 * MySQL JDBC Connection and Verification Test.
 * Validates:
 * 1. MySQL JDBC driver is loaded.
 * 2. JDBC connection to MySQL server opens.
 * 3. SELECT VERSION() executes successfully.
 * 4. CRUD operations on MySQL.
 * 5. Resource cleanup with try-with-resources.
 */
public class MySQLConnectionTest {

    @Test
    @DisplayName("Verify MySQL JDBC Driver, Server Connection, and Basic Operations")
    public void testMySQLConnectivity() {
        boolean success = runVerification();
        assertTrue(success, "MySQL Connection Test must succeed!");
    }

    public static boolean runVerification() {
        System.out.println("=================================================");
        System.out.println("     SMARTCARE — MYSQL JDBC CONNECTION TEST      ");
        System.out.println("=================================================");

        String jdbcUrl = DatabaseConnection.getJdbcUrl();
        System.out.println("[Step 1] Database Engine: " + DatabaseConnection.getDatabaseType().toUpperCase());
        System.out.println("[Step 1] JDBC URL:        " + jdbcUrl);

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null || conn.isClosed() || !conn.isValid(3)) {
                System.err.println("[FAIL] MySQL Connection could not be validated.");
                return false;
            }
            System.out.println("[Step 2] MySQL Connection established successfully.");

            // Query MySQL version
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT VERSION();")) {
                if (rs.next()) {
                    System.out.println("[Step 3] MySQL Server Version: " + rs.getString(1));
                } else {
                    System.err.println("[FAIL] Could not query MySQL VERSION().");
                    return false;
                }
            }

            // Create temporary test table
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS _test_mysql_conn ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "test_name VARCHAR(100) NOT NULL, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");
                System.out.println("[Step 4] Temporary table '_test_mysql_conn' ready.");
            }

            // Insert verification record
            String sampleToken = "SmartCare_MySQL_" + System.currentTimeMillis();
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO _test_mysql_conn (test_name) VALUES (?);", Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, sampleToken);
                int affected = pstmt.executeUpdate();
                if (affected != 1) {
                    System.err.println("[FAIL] Insert into _test_mysql_conn failed.");
                    return false;
                }
            }

            // Read back verification record
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT test_name FROM _test_mysql_conn WHERE test_name = ?;")) {
                pstmt.setString(1, sampleToken);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next() && sampleToken.equals(rs.getString("test_name"))) {
                        System.out.println("[Step 5] Verification record written and read back successfully.");
                    } else {
                        System.err.println("[FAIL] Verification record read mismatch.");
                        return false;
                    }
                }
            }

            // Clean up temporary table
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS _test_mysql_conn;");
                System.out.println("[Step 6] Temporary test table cleaned up.");
            }

            System.out.println("=================================================");
            System.out.println("     RESULT: MYSQL CONNECTION TEST PASSED (OK)   ");
            System.out.println("=================================================");
            return true;

        } catch (Exception e) {
            System.err.println("[FAIL] Exception during MySQL connection verification: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static void main(String[] args) {
        boolean ok = runVerification();
        System.exit(ok ? 0 : 1);
    }
}
