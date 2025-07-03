import java.util.*;

class User {
    String username;
    String password;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }
}

class SubjectAttendance {
    String subject;
    int totalClasses;
    int attendedClasses;
    double percentage;

    public SubjectAttendance(String subject, int totalClasses, int attendedClasses) {
        this.subject = subject;
        this.totalClasses = totalClasses;
        this.attendedClasses = attendedClasses;
        this.percentage = calculatePercentage();
    }

    private double calculatePercentage() {
        if (totalClasses == 0) return 0.0;
        return (attendedClasses * 100.0) / totalClasses;
    }

    public void display() {
        System.out.println("\nSubject: " + subject);
        System.out.println("Total Classes: " + totalClasses);
        System.out.println("Attended Classes: " + attendedClasses);
        System.out.printf("Attendance: %.2f%%\n", percentage);

        if (Math.abs(percentage - 75.0) < 0.001) {
            System.out.println("Congratulations, you have exactly 75% attendance!");
        } else if (percentage > 75.0) {
            int maxSkips = calculateMaxSkips();
            System.out.println("You are eligible. You can still skip up to " + maxSkips + " more classes.");
        } else {
            int needed = calculateClassesNeeded();
            System.out.println("Not eligible. You need to attend at least " + needed + " more classes to reach 75%.");
        }
    }

    private int calculateMaxSkips() {
        int skips = 0;
        int attended = attendedClasses;
        int total = totalClasses;
        while ((attended - skips) >= 0) {
            double newPercent = ((attended - skips) * 100.0) / (total + skips);
            if (newPercent < 75.0) break;
            skips++;
        }
        return skips - 1;
    }

    private int calculateClassesNeeded() {
        int needed = 0;
        int attended = attendedClasses;
        int total = totalClasses;
        while (true) {
            double newPercent = ((attended + needed) * 100.0) / (total + needed);
            if (newPercent >= 75.0) break;
            needed++;
        }
        return needed;
    }
}

public class AttendanceCalculator {

    private static final Map<String, User> users = new HashMap<>();
    private static final Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("Welcome to the Attendance Calculator!");

        User currentUser = authenticateUser();
        if (currentUser == null) {
            System.out.println("Exiting...");
            return;
        }

        System.out.println("Choose mode:");
        System.out.println("1. Manual one-time check");
        System.out.println("2. Set weekly timetable and auto-track attendance");

        System.out.print("Enter choice (1 or 2): ");
        int modeChoice = sc.nextInt();
        sc.nextLine(); // consume newline

        if (modeChoice == 1) {
            runManualMode(currentUser);
        } else if (modeChoice == 2) {
            System.out.println("Timetable mode coming next...");
        } else {
            System.out.println("Invalid choice.");
        }
    }

    private static void runManualMode(User user) {
        System.out.print("How many subjects do you have? ");
        int subjectCount = sc.nextInt();
        sc.nextLine();

        List<SubjectAttendance> records = new ArrayList<>();

        for (int i = 1; i <= subjectCount; i++) {
            System.out.println("\nSubject " + i);
            System.out.print("Enter subject name: ");
            String subject = sc.nextLine();

            System.out.print("Enter total classes for " + subject + ": ");
            int total = sc.nextInt();
            System.out.print("Enter attended classes for " + subject + ": ");
            int attended = sc.nextInt();
            sc.nextLine();

            if (attended > total || total <= 0 || attended < 0) {
                System.out.println("Invalid data. Skipping...");
                continue;
            }

            records.add(new SubjectAttendance(subject, total, attended));
        }

        System.out.println("\nAttendance Report for " + user.username);
        for (SubjectAttendance sa : records) {
            sa.display();
        }
    }

    private static User authenticateUser() {
        System.out.print("Are you an existing user? (yes/no): ");
        String response = sc.nextLine().trim().toLowerCase();

        if (response.equals("yes")) {
            System.out.print("Enter username: ");
            String username = sc.nextLine();
            System.out.print("Enter password: ");
            String password = sc.nextLine();

            User user = users.get(username);
            if (user != null && user.password.equals(password)) {
                System.out.println("Login successful!");
                return user;
            } else {
                System.out.println("Invalid username or password.");
                return null;
            }
        } else if (response.equals("no")) {
            System.out.print("Create a username: ");
            String newUsername = sc.nextLine();
            System.out.print("Create a password: ");
            String newPassword = sc.nextLine();

            if (users.containsKey(newUsername)) {
                System.out.println("Username already exists. Please try again.");
                return null;
            }

            User newUser = new User(newUsername, newPassword);
            users.put(newUsername, newUser);
            System.out.println("Account created successfully!");
            return newUser;
        } else {
            System.out.println("Invalid input.");
            return null;
        }
    }
}
