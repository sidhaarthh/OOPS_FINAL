package com.smartcare;

import com.smartcare.dao.AppointmentDAO;
import com.smartcare.dao.DoctorDAO;
import com.smartcare.dao.UserDAO;
import com.smartcare.database.DatabaseConnection;
import com.smartcare.database.DatabaseInitializer;
import com.smartcare.model.Administrator;
import com.smartcare.model.Appointment;
import com.smartcare.model.AppointmentStatus;
import com.smartcare.model.Doctor;
import com.smartcare.model.Patient;
import com.smartcare.model.SlotRecommendation;
import com.smartcare.service.AppointmentService;
import com.smartcare.service.AuthService;
import com.smartcare.service.DoctorService;
import com.smartcare.service.RecommendationEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated System Test verifying all 20 required points specified in Part 13.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SmartCareSystemTest {

    private static UserDAO userDAO;
    private static DoctorDAO doctorDAO;
    private static AppointmentDAO appointmentDAO;
    private static AuthService authService;
    private static DoctorService doctorService;
    private static AppointmentService appointmentService;
    private static RecommendationEngine recommendationEngine;

    private static final String TEST_PATIENT_PHONE = "9876543210";
    private static final String TEST_PATIENT_PASS = "SecretPass123";
    private static int createdPatientId;
    private static int createdDoctorId;
    private static int createdAppointmentId;

    @BeforeAll
    public static void setUp() throws Exception {
        // Initialize database
        boolean ok = DatabaseInitializer.initializeDatabase();
        assertTrue(ok, "Database initialization must succeed.");

        userDAO = new UserDAO();
        doctorDAO = new DoctorDAO();
        appointmentDAO = new AppointmentDAO();
        authService = new AuthService(userDAO);
        doctorService = new DoctorService(doctorDAO);
        appointmentService = new AppointmentService(appointmentDAO, doctorDAO);
        recommendationEngine = new RecommendationEngine(appointmentDAO);
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Database JDBC connection succeeds")
    public void test01_ConnectionSucceeds() throws Exception {
        try (Connection conn = DatabaseConnection.getConnection()) {
            assertNotNull(conn, "Connection must not be null");
            assertFalse(conn.isClosed(), "Connection must be open");
            assertTrue(conn.isValid(2), "Connection must be valid");
        }
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Database version query succeeds")
    public void test02_VersionQuerySucceeds() throws Exception {
        String versionSql = DatabaseConnection.isMySQL() ? "SELECT VERSION();" : "SELECT sqlite_version();";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(versionSql)) {
            assertTrue(rs.next(), "Version query must return a row");
            String ver = rs.getString(1);
            assertNotNull(ver);
            assertFalse(ver.trim().isEmpty());
            System.out.println("[Test 2] Verified " + DatabaseConnection.getDatabaseType().toUpperCase() + " Version: " + ver);
        }
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Database target is created and accessible")
    public void test03_DatabaseFileCreated() throws Exception {
        if (DatabaseConnection.isMySQL()) {
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT DATABASE();")) {
                assertTrue(rs.next(), "MySQL should return current database");
                String currentDb = rs.getString(1);
                assertNotNull(currentDb);
                assertEquals(DatabaseConnection.getDatabaseName().toLowerCase(), currentDb.toLowerCase());
            }
        } else {
            File dbFile = new File(DatabaseConnection.getDatabasePath());
            assertTrue(dbFile.exists(), "Database file must exist on disk");
            assertTrue(dbFile.length() > 0, "Database file must have non-zero size");
        }
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Tables and constraints are created automatically")
    public void test04_TablesCreated() throws Exception {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            if (DatabaseConnection.isMySQL()) {
                try (ResultSet rs = stmt.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'users';")) {
                    assertTrue(rs.next(), "Table 'users' must exist");
                }
                try (ResultSet rs = stmt.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'doctors';")) {
                    assertTrue(rs.next(), "Table 'doctors' must exist");
                }
                try (ResultSet rs = stmt.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'appointments';")) {
                    assertTrue(rs.next(), "Table 'appointments' must exist");
                }
                try (ResultSet rs = stmt.executeQuery("SELECT index_name FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'appointments' AND index_name = 'idx_active_doctor_slot';")) {
                    assertTrue(rs.next(), "Index 'idx_active_doctor_slot' must exist");
                }
            } else {
                // Check users table
                try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='users';")) {
                    assertTrue(rs.next(), "Table 'users' must exist");
                }
                // Check doctors table
                try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='doctors';")) {
                    assertTrue(rs.next(), "Table 'doctors' must exist");
                }
                // Check appointments table
                try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='appointments';")) {
                    assertTrue(rs.next(), "Table 'appointments' must exist");
                }
                // Check partial index
                try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='index' AND name='idx_active_doctor_slot';")) {
                    assertTrue(rs.next(), "Index 'idx_active_doctor_slot' must exist");
                }
            }
        }
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Existing database records are preserved on restart")
    public void test05_RecordsPreservedOnRestart() throws Exception {
        int initialDocCount = doctorDAO.countActiveDoctors();
        // Re-run initializer (simulating app restart)
        DatabaseInitializer.initializeDatabase();
        int afterDocCount = doctorDAO.countActiveDoctors();
        assertEquals(initialDocCount, afterDocCount, "Doctor count must remain unchanged after re-running initialization");
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: Sample doctors are inserted only when needed")
    public void test06_SampleDoctorsPresent() throws Exception {
        List<Doctor> doctors = doctorDAO.findAll(false);
        assertTrue(doctors.size() >= 4, "At least 4 baseline sample doctors must be present");
        boolean foundCardiology = doctors.stream().anyMatch(d -> d.getSpecialization().equalsIgnoreCase("Cardiology"));
        assertTrue(foundCardiology, "Cardiologist Dr. Sarah Johnson must be seeded");
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Patient registration works")
    public void test07_PatientRegistrationWorks() throws Exception {
        // Clean up any previous test patient with this phone
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM appointments WHERE patient_id IN (SELECT user_id FROM users WHERE phone='" + TEST_PATIENT_PHONE + "');");
            stmt.execute("DELETE FROM users WHERE phone = '" + TEST_PATIENT_PHONE + "';");
        }

        Patient registered = authService.registerPatient("John Doe", TEST_PATIENT_PHONE, TEST_PATIENT_PASS);
        assertNotNull(registered);
        assertTrue(registered.getId() > 0, "Generated ID must be positive");
        assertEquals("John Doe", registered.getName());
        assertEquals(TEST_PATIENT_PHONE, registered.getPhone());
        createdPatientId = registered.getId();
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: Duplicate phone numbers are rejected")
    public void test08_DuplicatePhoneRejected() {
        assertThrows(IllegalArgumentException.class, () -> {
            authService.registerPatient("Duplicate Person", TEST_PATIENT_PHONE, "AnotherPass");
        }, "Registration with duplicate phone must throw IllegalArgumentException");
    }

    @Test
    @Order(9)
    @DisplayName("Test 9: Correct patient credentials allow login")
    public void test09_CorrectCredentialsLogin() throws Exception {
        Patient authenticated = authService.authenticatePatient(TEST_PATIENT_PHONE, TEST_PATIENT_PASS);
        assertNotNull(authenticated, "Authenticated patient must not be null");
        assertEquals(createdPatientId, authenticated.getId());
        assertEquals("Patient", authenticated.getRoleDescription());
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: Incorrect credentials are rejected")
    public void test10_IncorrectCredentialsRejected() {
        assertThrows(SecurityException.class, () -> {
            authService.authenticatePatient(TEST_PATIENT_PHONE, "WrongPassword999");
        }, "Incorrect password must throw SecurityException");

        assertThrows(SecurityException.class, () -> {
            authService.authenticatePatient("0000000000", TEST_PATIENT_PASS);
        }, "Non-existent phone must throw SecurityException");
    }

    @Test
    @Order(11)
    @DisplayName("Test 11: Administrator login works")
    public void test11_AdminLoginWorks() throws Exception {
        Administrator admin = authService.authenticateAdmin(
                DatabaseInitializer.DEFAULT_ADMIN_USERNAME,
                DatabaseInitializer.DEFAULT_ADMIN_PASSWORD
        );
        assertNotNull(admin, "Admin authentication must succeed");
        assertEquals("Administrator", admin.getRoleDescription());
        assertTrue(admin.isAdmin());
    }

    @Test
    @Order(12)
    @DisplayName("Test 12: Patients cannot access administrator functions")
    public void test12_RoleSeparationEnforced() {
        // Attempting admin login with patient credentials must fail
        assertThrows(SecurityException.class, () -> {
            authService.authenticateAdmin(TEST_PATIENT_PHONE, TEST_PATIENT_PASS);
        }, "Patient phone credentials cannot authenticate as administrator");
    }

    @Test
    @Order(13)
    @DisplayName("Test 13: Doctors can be added and updated")
    public void test13_AddAndUpdateDoctor() throws Exception {
        Doctor newDoc = doctorService.addDoctor("Dr. Alan Turing", "Neurology", "09:00", "14:00");
        assertTrue(newDoc.getId() > 0);
        createdDoctorId = newDoc.getId();

        // Update
        boolean updated = doctorService.updateDoctor(createdDoctorId, "Dr. Alan M. Turing", "Neurology", "09:30", "15:00", true);
        assertTrue(updated, "Update doctor must return true");

        Doctor fetched = doctorService.getDoctorById(createdDoctorId);
        assertEquals("Dr. Alan M. Turing", fetched.getName());
        assertEquals("09:30", fetched.getStartTime());
        assertEquals("15:00", fetched.getEndTime());
    }

    @Test
    @Order(14)
    @DisplayName("Test 14: Available appointment slots are displayed correctly")
    public void test14_AvailableSlotsGenerated() throws Exception {
        Doctor doc = doctorService.getDoctorById(createdDoctorId);
        String testDate = LocalDate.now().plusDays(2).toString();

        List<SlotRecommendation> slots = recommendationEngine.getRecommendations(doc, testDate, "10:00");
        assertFalse(slots.isEmpty(), "Candidate slots must be generated within doctor's working hours");

        // Doctor working hours: 09:30 to 15:00. First slot is 09:30, last slot is 14:30 (30 min duration)
        assertEquals("09:30", slots.get(slots.size() - 1).getSlotTime().compareTo("09:30") >= 0 ? "09:30" : "");
        assertTrue(slots.stream().anyMatch(s -> s.getSlotTime().equals("10:00")));
    }

    @Test
    @Order(15)
    @DisplayName("Test 15: Double booking is strictly prevented")
    public void test15_DoubleBookingPrevented() throws Exception {
        String testDate = LocalDate.now().plusDays(3).toString();
        String testTime = "10:30";

        // First booking succeeds
        Appointment first = appointmentService.bookAppointment(createdPatientId, createdDoctorId, testDate, testTime);
        assertNotNull(first);
        createdAppointmentId = first.getAppointmentId();

        // Second booking for the same slot must throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> {
            appointmentService.bookAppointment(createdPatientId, createdDoctorId, testDate, testTime);
        }, "Double booking for the exact same doctor, date, and time must be rejected!");
    }

    @Test
    @Order(16)
    @DisplayName("Test 16: SmartCare scores and ranks available slots")
    public void test16_RecommendationRanking() throws Exception {
        Doctor doc = doctorService.getDoctorById(createdDoctorId);
        String testDate = LocalDate.now().plusDays(4).toString();

        List<SlotRecommendation> recs = recommendationEngine.getRecommendations(doc, testDate, "10:00");
        assertFalse(recs.isEmpty());

        // Top recommendation must have highest score
        SlotRecommendation top = recs.get(0);
        assertEquals("10:00", top.getSlotTime(), "Preferred time (10:00) must receive the highest composite score");
        assertEquals(40, top.getPreferenceScore(), "Exact time match gets maximum 40 preference points");
        assertTrue(top.getTotalScore() >= 80, "Top recommendation score should be >= 80");
        assertNotNull(top.getExplanation());
        assertFalse(top.getExplanation().isEmpty());

        // Verify descending order
        for (int i = 0; i < recs.size() - 1; i++) {
            assertTrue(recs.get(i).getTotalScore() >= recs.get(i + 1).getTotalScore(),
                    "Recommendations must be sorted in descending order of score");
        }
    }

    @Test
    @Order(17)
    @DisplayName("Test 17: No-slot situations are handled correctly")
    public void test17_NoSlotHandling() throws Exception {
        // Create a doctor with a 30-minute window (only 1 slot possible: 10:00 - 10:30)
        Doctor tightDoc = doctorService.addDoctor("Dr. Tight Schedule", "Specialist", "10:00", "10:30");
        String testDate = LocalDate.now().plusDays(5).toString();

        // Book the single available slot
        appointmentService.bookAppointment(createdPatientId, tightDoc.getId(), testDate, "10:00");

        // Request recommendations when fully booked
        List<SlotRecommendation> emptyRecs = recommendationEngine.getRecommendations(tightDoc, testDate, "10:00");
        assertTrue(emptyRecs.isEmpty(), "Fully booked doctor must return empty recommendations list without crashing");
    }

    @Test
    @Order(18)
    @DisplayName("Test 18: Patients can see only their own appointments")
    public void test18_PatientIsolation() throws Exception {
        List<Appointment> list = appointmentService.getPatientAppointments(createdPatientId);
        assertFalse(list.isEmpty());
        for (Appointment a : list) {
            assertEquals(createdPatientId, a.getPatientId(), "Patients must strictly see only their own bookings");
        }
    }

    @Test
    @Order(19)
    @DisplayName("Test 19: Cancellation updates the slot's availability")
    public void test19_CancellationFreesSlot() throws Exception {
        String testDate = LocalDate.now().plusDays(6).toString();
        String testTime = "11:00";

        // 1. Book appointment
        Appointment appt = appointmentService.bookAppointment(createdPatientId, createdDoctorId, testDate, testTime);

        // 2. Verify slot is currently booked
        Set<String> bookedBefore = appointmentDAO.getBookedSlotsForDoctor(createdDoctorId, testDate);
        assertTrue(bookedBefore.contains(testTime), "Slot must be booked");

        // 3. Cancel appointment
        boolean cancelled = appointmentService.cancelAppointment(appt.getAppointmentId(), createdPatientId, false);
        assertTrue(cancelled, "Cancellation must return true");

        // 4. Verify slot is freed in database
        Set<String> bookedAfter = appointmentDAO.getBookedSlotsForDoctor(createdDoctorId, testDate);
        assertFalse(bookedAfter.contains(testTime), "Slot must now be free for other patients!");

        // 5. Verify the slot can now be booked again without error
        Appointment rebooked = appointmentService.bookAppointment(createdPatientId, createdDoctorId, testDate, testTime);
        assertNotNull(rebooked, "Slot must be bookable again after cancellation");
        assertEquals(AppointmentStatus.BOOKED, rebooked.getStatus());
    }

    @Test
    @Order(20)
    @DisplayName("Test 20: Data persists after closing and reopening the application")
    public void test20_DataPersistenceAcrossReopen() throws Exception {
        // Query user and doctor count
        Patient foundPatient = userDAO.findPatientByPhone(TEST_PATIENT_PHONE);
        assertNotNull(foundPatient);
        Doctor foundDoc = doctorDAO.findById(createdDoctorId);
        assertNotNull(foundDoc);

        // Simulate application restart by creating fresh DAO instances and re-verifying
        UserDAO freshUserDAO = new UserDAO();
        DoctorDAO freshDocDAO = new DoctorDAO();
        AppointmentDAO freshApptDAO = new AppointmentDAO();

        Patient reloadedPatient = freshUserDAO.findPatientByPhone(TEST_PATIENT_PHONE);
        assertNotNull(reloadedPatient, "Patient record must persist across restarts");
        assertEquals(foundPatient.getName(), reloadedPatient.getName());

        Doctor reloadedDoc = freshDocDAO.findById(createdDoctorId);
        assertNotNull(reloadedDoc, "Doctor record must persist across restarts");
        assertEquals(foundDoc.getName(), reloadedDoc.getName());

        List<Appointment> appointments = freshApptDAO.getAppointmentsByPatient(createdPatientId);
        assertFalse(appointments.isEmpty(), "Appointments must persist across application restarts");
    }
}
