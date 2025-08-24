import java.sql.*;
import java.util.*;

public class AttendanceCalculator {
    // --- CHANGE THESE to match your MySQL credentials ---
    private static final String DB_URL = "jdbc:mysql://localhost:3306/attendance_calculator?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Sharma@098";

    private static final Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("Starting Attendance Calculator...");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            conn.setAutoCommit(true);
            ensureTables(conn);

            while (true) {
                System.out.println("\n=== Welcome ===");
                System.out.println("1) Login");
                System.out.println("2) Register");
                System.out.println("3) Exit");
                System.out.print("Enter choice: ");
                String choice = sc.nextLine().trim();

                if (choice.equals("1")) {
                    Integer userId = login(conn);
                    if (userId != null) postLoginFlow(conn, userId);
                } else if (choice.equals("2")) {
                    register(conn);
                } else if (choice.equals("3")) {
                    System.out.println("Goodbye!");
                    return;
                } else {
                    System.out.println("Invalid option. Try again.");
                }
            }

        } catch (SQLException ex) {
            System.err.println("Database error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // ---------- Table creation ----------
    private static void ensureTables(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS users (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "username VARCHAR(100) NOT NULL UNIQUE," +
                "password VARCHAR(255) NOT NULL" +
                ")"
            );

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS subjects (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "user_id INT NOT NULL," +
                "subject_name VARCHAR(200) NOT NULL," +
                "total_classes INT NOT NULL DEFAULT 0," +
                "attended_classes INT NOT NULL DEFAULT 0," +
                "UNIQUE (user_id, subject_name)," +
                "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                ")"
            );

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS timetable (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "user_id INT NOT NULL," +
                "day_of_week VARCHAR(10) NOT NULL," +
                "subject_name VARCHAR(200) NOT NULL," +
                "classes_per_entry INT NOT NULL DEFAULT 1," +
                "start_time TIME NULL," +
                "end_time TIME NULL," +
                "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                ")"
            );
        }
    }

    // ---------- Auth ----------
    private static void register(Connection conn) {
        try {
            System.out.print("Choose a username: ");
            String username = sc.nextLine().trim();
            System.out.print("Choose a password: ");
            String password = sc.nextLine();

            String sql = "INSERT INTO users (username, password) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, password); // for production, hash the password
                ps.executeUpdate();
                System.out.println("Registration successful. You can now log in.");
            }
        } catch (SQLIntegrityConstraintViolationException ex) {
            System.out.println("Username already exists. Choose a different username.");
        } catch (SQLException ex) {
            System.err.println("DB error during registration: " + ex.getMessage());
        }
    }

    private static Integer login(Connection conn) {
        try {
            System.out.print("Username: ");
            String username = sc.nextLine().trim();
            System.out.print("Password: ");
            String password = sc.nextLine();

            String sql = "SELECT id FROM users WHERE username = ? AND password = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, password);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int userId = rs.getInt("id");
                        System.out.println("Login successful. Welcome, " + username + "!");
                        return userId;
                    } else {
                        System.out.println("Invalid username or password.");
                    }
                }
            }
        } catch (SQLException ex) {
            System.err.println("DB error during login: " + ex.getMessage());
        }
        return null;
    }

    // ---------- Post-login flow ----------
    private static void postLoginFlow(Connection conn, int userId) throws SQLException {
        // If user has no subjects, ask to add them (required)
        if (!hasSubjects(conn, userId)) {
            System.out.println("\nYou don't have any subjects recorded. Let's add them now.");
            addInitialSubjects(conn, userId);
        }

        // If user has subjects but no timetable entries, offer optional add
        if (!hasTimetable(conn, userId)) {
            System.out.print("\nYou don't have a timetable. Would you like to add timetable entries now? (Y/N): ");
            String ans = sc.nextLine().trim();
            if (ans.equalsIgnoreCase("Y")) {
                addTimetableInteractive(conn, userId);
            } else {
                System.out.println("Skipping timetable setup.");
            }
        }

        // Enter main menu
        mainMenu(conn, userId);
    }

    // ---------- Helpers: check existence ----------
    private static boolean hasSubjects(Connection conn, int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM subjects WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private static boolean hasTimetable(Connection conn, int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM timetable WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    // ---------- Initial subjects input ----------
    private static void addInitialSubjects(Connection conn, int userId) throws SQLException {
        int subjectCount = 0;
        while (true) {
            System.out.print("How many subjects do you have? ");
            String s = sc.nextLine().trim();
            try {
                subjectCount = Integer.parseInt(s);
                if (subjectCount <= 0) {
                    System.out.println("Enter a positive integer.");
                    continue;
                }
                break;
            } catch (NumberFormatException ex) {
                System.out.println("Please enter a valid integer.");
            }
        }

        String insert = "INSERT INTO subjects (user_id, subject_name, total_classes, attended_classes) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insert)) {
            for (int i = 1; i <= subjectCount; i++) {
                System.out.print("Subject " + i + " name: ");
                String name = sc.nextLine().trim();

                int total = readInt("Total classes for " + name + ": ");
                int attended = readInt("Attended classes for " + name + ": ");
                if (attended > total) {
                    System.out.println("Attended cannot be greater than total. Setting attended = total.");
                    attended = total;
                }

                ps.setInt(1, userId);
                ps.setString(2, name);
                ps.setInt(3, total);
                ps.setInt(4, attended);
                try {
                    ps.executeUpdate();
                } catch (SQLIntegrityConstraintViolationException ex) {
                    // unique constraint: subject already exists for this user — update instead
                    upsertSubject(conn, userId, name, total, attended);
                }
            }
        }
        System.out.println("Subjects saved.");
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = sc.nextLine().trim();
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ex) {
                System.out.println("Enter a valid integer.");
            }
        }
    }

    // upsert helper: update if exists, else insert (used during initial subject entry)
    private static void upsertSubject(Connection conn, int userId, String subject, int totalAdd, int attendedAdd) throws SQLException {
        String select = "SELECT id, total_classes, attended_classes FROM subjects WHERE user_id = ? AND subject_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setInt(1, userId);
            ps.setString(2, subject);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    int currentTotal = rs.getInt("total_classes");
                    int currentAttended = rs.getInt("attended_classes");
                    String upd = "UPDATE subjects SET total_classes = ?, attended_classes = ? WHERE id = ?";
                    try (PreparedStatement ups = conn.prepareStatement(upd)) {
                        ups.setInt(1, currentTotal + totalAdd);
                        ups.setInt(2, currentAttended + attendedAdd);
                        ups.setInt(3, id);
                        ups.executeUpdate();
                    }
                    return;
                }
            }
        }
        // not found -> insert
        String insert = "INSERT INTO subjects (user_id, subject_name, total_classes, attended_classes) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps2 = conn.prepareStatement(insert)) {
            ps2.setInt(1, userId);
            ps2.setString(2, subject);
            ps2.setInt(3, totalAdd);
            ps2.setInt(4, attendedAdd);
            ps2.executeUpdate();
        }
    }

    // ---------- Timetable interactive ----------
    private static void addTimetableInteractive(Connection conn, int userId) throws SQLException {
        System.out.println("Add weekly timetable entries. Enter 'done' as subject name to stop.");
        while (true) {
            System.out.print("Subject name (or 'done'): ");
            String subj = sc.nextLine().trim();
            if (subj.equalsIgnoreCase("done")) break;

            System.out.print("Day of week (MONDAY,TUESDAY,...): ");
            String day = sc.nextLine().trim().toUpperCase();
            System.out.print("Classes for this entry (default 1): ");
            String s = sc.nextLine().trim();
            int per = 1;
            if (!s.isEmpty()) {
                try { per = Integer.parseInt(s); } catch (NumberFormatException ex) { per = 1; }
            }
            System.out.print("Start time (HH:MM) or blank: ");
            String st = sc.nextLine().trim();
            System.out.print("End time (HH:MM) or blank: ");
            String et = sc.nextLine().trim();

            String sql = "INSERT INTO timetable (user_id, day_of_week, subject_name, classes_per_entry, start_time, end_time) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.setString(2, day);
                ps.setString(3, subj);
                ps.setInt(4, per);
                if (st.isEmpty()) ps.setNull(5, Types.TIME); else ps.setTime(5, Time.valueOf(st + ":00"));
                if (et.isEmpty()) ps.setNull(6, Types.TIME); else ps.setTime(6, Time.valueOf(et + ":00"));
                ps.executeUpdate();
                System.out.println("Timetable entry added.");
            }
        }
    }

    // ---------- Main menu ----------
    private static void mainMenu(Connection conn, int userId) throws SQLException {
        while (true) {
            System.out.println("\n--- Main Menu ---");
            System.out.println("1) Add Subject");
            System.out.println("2) Manual Attendance Entry (per subject)");
            System.out.println("3) View Attendance Report");
            System.out.println("4) Edit Total Classes for a Subject");
            System.out.println("5) View Timetable");
            System.out.println("6) Add Timetable Entry");
            System.out.println("7) Logout");
            System.out.print("Choose: ");
            String opt = sc.nextLine().trim();

            switch (opt) {
                case "1" -> addSubjectInteractive(conn, userId);
                case "2" -> manualAttendanceInteractive(conn, userId);
                case "3" -> viewAttendanceReport(conn, userId);
                case "4" -> editTotalInteractive(conn, userId);
                case "5" -> viewTimetable(conn, userId);
                case "6" -> addTimetableInteractive(conn, userId);
                case "7" -> {
                    System.out.println("Logging out...");
                    return;
                }
                default -> System.out.println("Invalid option.");
            }
        }
    }

    // ---------- Subject / Attendance operations ----------
    private static void addSubjectInteractive(Connection conn, int userId) throws SQLException {
        System.out.print("Subject name: ");
        String subj = sc.nextLine().trim();
        int total = readInt("Total classes: ");
        int attended = readInt("Attended classes: ");
        if (attended > total) {
            System.out.println("Attended cannot be greater than total. Setting attended = total.");
            attended = total;
        }
        upsertSubject(conn, userId, subj, total, attended);
        System.out.println("Subject added/updated.");
    }

    private static void manualAttendanceInteractive(Connection conn, int userId) throws SQLException {
        // list subjects
        List<String> subjects = getSubjectNames(conn, userId);
        if (subjects.isEmpty()) {
            System.out.println("No subjects found. Please add subjects first.");
            return;
        }
        System.out.println("Select subject by number:");
        for (int i = 0; i < subjects.size(); i++) {
            System.out.printf("%d) %s%n", i + 1, subjects.get(i));
        }
        int idx = -1;
        while (idx < 1 || idx > subjects.size()) {
            idx = readInt("Enter choice: ");
        }
        String subj = subjects.get(idx - 1);
        int addTotal = readInt("How many classes to add to total for " + subj + " (e.g., 1): ");
        int addAttended = readInt("How many of those were attended? (0.." + addTotal + "): ");
        if (addAttended > addTotal) {
            System.out.println("Attended cannot exceed total added. Setting attended = total added.");
            addAttended = addTotal;
        }
        upsertAttendance(conn, userId, subj, addTotal, addAttended);
        System.out.println("Attendance updated for " + subj + ".");
    }

    private static List<String> getSubjectNames(Connection conn, int userId) throws SQLException {
        String sql = "SELECT subject_name FROM subjects WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> list = new ArrayList<>();
                while (rs.next()) list.add(rs.getString("subject_name"));
                return list;
            }
        }
    }

    private static void upsertAttendance(Connection conn, int userId, String subject, int addTotal, int addAttended) throws SQLException {
        String select = "SELECT id, total_classes, attended_classes FROM subjects WHERE user_id = ? AND subject_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setInt(1, userId);
            ps.setString(2, subject);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    int curTotal = rs.getInt("total_classes");
                    int curAtt = rs.getInt("attended_classes");
                    String upd = "UPDATE subjects SET total_classes = ?, attended_classes = ? WHERE id = ?";
                    try (PreparedStatement ups = conn.prepareStatement(upd)) {
                        ups.setInt(1, curTotal + addTotal);
                        ups.setInt(2, curAtt + addAttended);
                        ups.setInt(3, id);
                        ups.executeUpdate();
                    }
                    return;
                }
            }
        }
        // not found -> insert
        String ins = "INSERT INTO subjects (user_id, subject_name, total_classes, attended_classes) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps2 = conn.prepareStatement(ins)) {
            ps2.setInt(1, userId);
            ps2.setString(2, subject);
            ps2.setInt(3, addTotal);
            ps2.setInt(4, addAttended);
            ps2.executeUpdate();
        }
    }

    private static void viewAttendanceReport(Connection conn, int userId) throws SQLException {
        String sql = "SELECT subject_name, total_classes, attended_classes FROM subjects WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("\n--- Attendance Report ---");
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    String subj = rs.getString("subject_name");
                    int total = rs.getInt("total_classes");
                    int attended = rs.getInt("attended_classes");
                    double pct = total == 0 ? 0.0 : (attended * 100.0 / total);
                    System.out.printf("%s : %d / %d  (%.2f%%)%n", subj, attended, total, pct);
                    if (Math.abs(pct - 75.0) < 0.0001) {
                        System.out.println("  -> Exactly 75% attendance.");
                    } else if (pct > 75.0) {
                        int skips = calculateMaxSkips(attended, total);
                        System.out.println("  -> You can skip up to " + skips + " classes and still remain >=75%.");
                    } else {
                        int need = calculateNeeded(attended, total);
                        System.out.println("  -> You need to attend at least " + need + " more classes to reach 75%.");
                    }
                }
                if (!any) System.out.println("No subjects found.");
            }
        }
    }

    private static int calculateMaxSkips(int attended, int total) {
        int skips = 0;
        while ((attended - skips) >= 0) {
            double newPct = ((attended - skips) * 100.0) / (total + skips);
            if (newPct < 75.0) break;
            skips++;
        }
        return Math.max(0, skips - 1);
    }

    private static int calculateNeeded(int attended, int total) {
        int needed = 0;
        while (true) {
            double newPct = ((attended + needed) * 100.0) / (total + needed);
            if (newPct >= 75.0) break;
            needed++;
        }
        return needed;
    }

    private static void editTotalInteractive(Connection conn, int userId) throws SQLException {
        List<String> subjects = getSubjectNames(conn, userId);
        if (subjects.isEmpty()) {
            System.out.println("No subjects to edit.");
            return;
        }
        System.out.println("Select subject to edit total:");
        for (int i = 0; i < subjects.size(); i++) {
            System.out.printf("%d) %s%n", i + 1, subjects.get(i));
        }
        int choice = readInt("Enter choice: ");
        if (choice < 1 || choice > subjects.size()) {
            System.out.println("Invalid choice.");
            return;
        }
        String subj = subjects.get(choice - 1);
        int currentTotal = getSubjectTotal(conn, userId, subj);
        System.out.println("Current total for " + subj + " = " + currentTotal);
        int newTotal = readInt("Enter new total classes: ");
        int currentAtt = getSubjectAttended(conn, userId, subj);
        if (newTotal < currentAtt) {
            System.out.println("New total cannot be less than already attended classes. Setting new total = attended.");
            newTotal = currentAtt;
        }
        setAttendanceTotal(conn, userId, subj, newTotal, currentAtt);
        System.out.println("Total updated.");
    }

    private static int getSubjectTotal(Connection conn, int userId, String subject) throws SQLException {
        String sql = "SELECT total_classes FROM subjects WHERE user_id = ? AND subject_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, subject);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("total_classes");
            }
        }
        return 0;
    }

    private static int getSubjectAttended(Connection conn, int userId, String subject) throws SQLException {
        String sql = "SELECT attended_classes FROM subjects WHERE user_id = ? AND subject_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, subject);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("attended_classes");
            }
        }
        return 0;
    }

    private static void setAttendanceTotal(Connection conn, int userId, String subject, int total, int attended) throws SQLException {
        String sql = "UPDATE subjects SET total_classes = ?, attended_classes = ? WHERE user_id = ? AND subject_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, total);
            ps.setInt(2, attended);
            ps.setInt(3, userId);
            ps.setString(4, subject);
            ps.executeUpdate();
        }
    }

    // ---------- Timetable view ----------
    private static void viewTimetable(Connection conn, int userId) throws SQLException {
        String sql = "SELECT day_of_week, subject_name, classes_per_entry, start_time, end_time FROM timetable WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("\n--- Timetable ---");
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    String day = rs.getString("day_of_week");
                    String subj = rs.getString("subject_name");
                    int per = rs.getInt("classes_per_entry");
                    Time st = rs.getTime("start_time");
                    Time et = rs.getTime("end_time");
                    System.out.printf("%s - %s (x%d) [%s - %s]%n",
                        day, subj, per,
                        (st == null ? "--" : st.toString()),
                        (et == null ? "--" : et.toString()));
                }
                if (!any) System.out.println("No timetable entries.");
            }
        }
    }
}
