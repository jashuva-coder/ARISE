import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class College {

    static class Student {
        String name, username, password;
        Student(String n, String u, String p) {
            name=n; username=u; password=p;
        }
    }

    static class Course {
        String id, name;
        Course(String i, String n) {
            id=i; name=n;
        }
    }

    static class Enrollment {
        String username, courseId;
        Enrollment(String u, String c) {
            username=u; courseId=c;
        }
    }

    static List<Student> students = new ArrayList<>();
    static List<Course> courses = new ArrayList<>();
    static List<Enrollment> enrollments = new ArrayList<>();
    static Map<String,String> sessions = new HashMap<>();

    static final String ADMIN = "admin";
    static final String ADMIN_PASSWORD = "admin123";

    public static void main(String[] args) throws Exception {

        students.add(new Student("Arun", "s101", "1234"));
        students.add(new Student("Priya", "s102", "1234"));

        courses.add(new Course("C101", "Java Programming"));
        courses.add(new Course("C102", "HTML and CSS"));
        courses.add(new Course("C103", "Database Management"));

        HttpServer server =
            HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", College::handle);
        server.start();

        System.out.println("College Management System Started");
        System.out.println("Open http://localhost:8080");
    }

    static void handle(HttpExchange ex) throws IOException {

        Map<String,String> data =
            ex.getRequestMethod().equals("POST")
            ? readForm(ex) : new HashMap<>();

        String user = getLoggedInUser(ex);
        String message = "";
        String action = data.getOrDefault("action", "");

        if (action.equals("login")) {

            String username = data.getOrDefault("username", "");
            String password = data.getOrDefault("password", "");

            Student s = findStudent(username);

            if (username.equals(ADMIN)
                    && password.equals(ADMIN_PASSWORD)) {
                createSession(ex, username);
                return;
            }

            if (s != null && s.password.equals(password)) {
                createSession(ex, username);
                return;
            }

            message = "Invalid username or password!";
        }

        else if (action.equals("logout")) {

            String token = getToken(ex);
            if (token != null) sessions.remove(token);

            ex.getResponseHeaders().add(
                "Set-Cookie",
                "COLLEGE_SESSION=; Path=/; Max-Age=0; HttpOnly"
            );

            redirect(ex);
            return;
        }

        else if (user == null && !action.isEmpty()) {
            message = "Please login first!";
        }

        else if (user != null && user.equals(ADMIN)) {

            switch (action) {

                case "addStudent":

                    String name = data.getOrDefault("name", "").trim();
                    String newUser = data.getOrDefault("newUser", "").trim();
                    String newPass = data.getOrDefault("newPass", "").trim();

                    if (name.isEmpty() || newUser.isEmpty() ||
                        newPass.isEmpty() || findStudent(newUser) != null ||
                        newUser.equals(ADMIN)) {
                        message = "Enter valid details and a unique username.";
                    } else {
                        students.add(new Student(name, newUser, newPass));
                        message = "Student added successfully!";
                    }
                    break;

                case "deleteStudent":

                    String deleteUser = data.getOrDefault("username", "");

                    students.removeIf(s -> s.username.equals(deleteUser));
                    enrollments.removeIf(e -> e.username.equals(deleteUser));

                    message = "Student deleted!";
                    break;

                case "addCourse":

                    String courseId = data.getOrDefault("courseId", "").trim();
                    String courseName = data.getOrDefault("courseName", "").trim();

                    if (courseId.isEmpty() || courseName.isEmpty() ||
                        findCourse(courseId) != null) {
                        message = "Enter a unique course ID and course name.";
                    } else {
                        courses.add(new Course(courseId, courseName));
                        message = "Course added successfully!";
                    }
                    break;

                case "editCourse":

                    Course edit = findCourse(data.getOrDefault("courseId", ""));
                    String newName = data.getOrDefault("newName", "").trim();

                    if (edit != null && !newName.isEmpty()) {
                        edit.name = newName;
                        message = "Course updated!";
                    } else {
                        message = "Course not found or name is empty.";
                    }
                    break;

                case "deleteCourse":

                    String deleteCourse = data.getOrDefault("courseId", "");

                    courses.removeIf(c -> c.id.equals(deleteCourse));
                    enrollments.removeIf(e -> e.courseId.equals(deleteCourse));

                    message = "Course deleted!";
                    break;
            }
        }

        else if (user != null && action.equals("enroll")) {

            String courseId = data.getOrDefault("courseId", "");

            long count = enrollments.stream()
                .filter(e -> e.username.equals(user)).count();

            boolean already = enrollments.stream()
                .anyMatch(e -> e.username.equals(user)
                    && e.courseId.equals(courseId));

            if (findCourse(courseId) == null) {
                message = "Course not found!";
            } else if (already) {
                message = "Already enrolled in this course!";
            } else if (count >= 5) {
                message = "Maximum 5 courses allowed!";
            } else {
                enrollments.add(new Enrollment(user, courseId));
                message = "Enrollment successful!";
            }
        }

        else if (user != null && !action.isEmpty()) {
            message = "You do not have permission!";
        }

        String content;

        if (user == null) {
            content = loginPage();
        } else if (user.equals(ADMIN)) {
            content = adminPage();
        } else {
           content = studentPage(user, ex.getRequestURI().toString());
        }

        String template = Files.readString(Path.of("index.html"));

        String html = template
            .replace("{{CONTENT}}", content)
            .replace("{{MESSAGE}}", esc(message));

        send(ex, html);
    }

    static String loginPage() {

        return """
        <div class="login-card">
            <div class="login-icon">🎓</div>
            <h2>Welcome Back</h2>
            <p class="muted">Login to your college account</p>

            <form method="post">
                <input type="hidden" name="action" value="login">

                <label>Username</label>
                <input type="text" name="username"
                       placeholder="Enter username" required>

                <label>Password</label>
                <input type="password" name="password"
                       placeholder="Enter password" required>

                <button class="btn primary full">Login</button>
            </form>

            <div class="demo">
                <b>Demo Accounts</b>
                <p>Admin: admin / admin123</p>
                <p>Student: s101 / 1234</p>
                <p>Student: s102 / 1234</p>
            </div>
        </div>
        """;
    }

    static String adminPage() {

        StringBuilder h = new StringBuilder();

        h.append("""
        <div class="topbar">
            <div>
                <h2>Admin Dashboard</h2>
                <p class="muted">Manage your college system</p>
            </div>
            <form method="post">
                <input type="hidden" name="action" value="logout">
                <button class="btn danger">Logout</button>
            </form>
        </div>
        """);

        h.append("<div class='stats'>");
        h.append(stat("Total Students", students.size(), "👨‍🎓"));
        h.append(stat("Total Courses", courses.size(), "📚"));
        h.append(stat("Total Enrollments", enrollments.size(), "📝"));
        h.append("</div>");

        h.append("""
        <div class="panel">
            <h3>Add New Student</h3>
            <form method="post" class="form-grid">
                <input type="hidden" name="action" value="addStudent">
                <input name="name" placeholder="Student name" required>
                <input name="newUser" placeholder="Username" required>
                <input name="newPass" placeholder="Password" required>
                <button class="btn primary">Add Student</button>
            </form>
        </div>

        <div class="panel">
            <h3>Student Details</h3>
            <div class="table-wrap">
            <table>
                <tr><th>Name</th><th>Username</th>
                <th>Courses</th><th>Action</th></tr>
        """);

        for (Student s : students) {
            long count = enrollments.stream()
                .filter(e -> e.username.equals(s.username)).count();

            h.append("<tr><td>").append(esc(s.name))
             .append("</td><td>").append(esc(s.username))
             .append("</td><td>").append(count)
             .append("</td><td>")
             .append(button("deleteStudent", "username",
                            s.username, "Delete", "danger"))
             .append("</td></tr>");
        }

        h.append("</table></div></div>");

        h.append("""
        <div class="panel">
            <h3>Add New Course</h3>
            <form method="post" class="form-grid">
                <input type="hidden" name="action" value="addCourse">
                <input name="courseId" placeholder="Course ID" required>
                <input name="courseName" placeholder="Course name" required>
                <button class="btn primary">Add Course</button>
            </form>
        </div>

        <div class="panel">
            <h3>Course Details</h3>
            <div class="table-wrap">
            <table>
                <tr><th>Course ID</th><th>Course Name</th>
                <th>Students</th><th>Update</th><th>Delete</th></tr>
        """);

        for (Course c : courses) {

            long count = enrollments.stream()
                .filter(e -> e.courseId.equals(c.id)).count();

            h.append("<tr><td>").append(esc(c.id))
             .append("</td><td>").append(esc(c.name))
             .append("</td><td>").append(count)
             .append("</td><td>")
             .append("<form method='post' class='inline-form'>")
             .append("<input type='hidden' name='action' value='editCourse'>")
             .append("<input type='hidden' name='courseId' value='")
             .append(esc(c.id)).append("'>")
             .append("<input name='newName' placeholder='New name' required>")
             .append("<button class='btn small primary'>Update</button></form>")
             .append("</td><td>")
             .append(button("deleteCourse", "courseId",
                            c.id, "Delete", "danger"))
             .append("</td></tr>");
        }

        h.append("</table></div></div>");

        h.append("<div class='panel'><h3>All Enrollments</h3>");
        enrollmentTable(h, null);
        h.append("</div>");

        return h.toString();
    }

    static String studentPage(String user, String uri) {

        Student s = findStudent(user);

        long count = enrollments.stream()
            .filter(e -> e.username.equals(user)).count();

        StringBuilder h = new StringBuilder();

        h.append("""
        <div class="topbar">
            <div>
                <h2>Student Dashboard</h2>
                <p class="muted">Welcome to your course portal</p>
            </div>
            <form method="post">
                <input type="hidden" name="action" value="logout">
                <button class="btn danger">Logout</button>
            </form>
        </div>
        """);

        h.append("<div class='welcome panel'><h3>Hello, ")
         .append(esc(s.name)).append("!</h3>")
         .append("<p>Manage your courses and track your enrollments.</p>")
         .append("<h2>").append(count).append(" / 5 courses enrolled</h2>")
         .append("</div>");

        h.append("<div class='panel'><h3>Find Courses</h3>")
         .append("<form method='get' class='search-form'>")
         .append("<input name='q' placeholder='Search by course ID or name'>")
         .append("<button class='btn primary'>Search</button></form>");

        String query = "";

        if (uri.contains("?")) {
            for (String part : uri.substring(uri.indexOf('?') + 1).split("&")) {
                String[] pair = part.split("=", 2);
                if (pair[0].equals("q") && pair.length > 1) {
                    query = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                }
            }
        }

        h.append("<div class='course-grid'>");

        for (Course c : courses) {

            if (!query.isEmpty() &&
                !c.id.toLowerCase().contains(query.toLowerCase()) &&
                !c.name.toLowerCase().contains(query.toLowerCase())) {
                continue;
            }

            h.append("<div class='course-card'>")
             .append("<div class='course-icon'>📘</div>")
             .append("<span class='course-id'>").append(esc(c.id))
             .append("</span><h3>").append(esc(c.name)).append("</h3>");

            if (isEnrolled(user, c.id)) {
                h.append("<span class='enrolled'>Already Enrolled</span>");
            } else if (count < 5) {
                h.append(button("enroll", "courseId",
                                c.id, "Enroll Now", "primary"));
            } else {
                h.append("<span class='enrolled'>Limit Reached</span>");
            }

            h.append("</div>");
        }

        h.append("</div></div>");

        h.append("<div class='panel'><h3>My Enrollments</h3>");
        enrollmentTable(h, user);
        h.append("</div>");

        return h.toString();
    }

    static String stat(String label, int value, String icon) {
        return "<div class='stat-card'><div class='stat-icon'>" + icon +
            "</div><p>" + label + "</p><h2>" + value + "</h2></div>";
    }

    static boolean isEnrolled(String user, String course) {
        return enrollments.stream().anyMatch(e ->
            e.username.equals(user) && e.courseId.equals(course));
    }

    static void enrollmentTable(StringBuilder h, String username) {

        h.append("<div class='table-wrap'><table>")
         .append("<tr><th>Student</th><th>Course ID</th>")
         .append("<th>Course Name</th></tr>");

        for (Enrollment e : enrollments) {

            if (username == null || e.username.equals(username)) {

                Student s = findStudent(e.username);
                Course c = findCourse(e.courseId);

                h.append("<tr><td>")
                 .append(esc(s == null ? e.username : s.name))
                 .append("</td><td>").append(esc(e.courseId))
                 .append("</td><td>")
                 .append(esc(c == null ? "" : c.name))
                 .append("</td></tr>");
            }
        }

        h.append("</table></div>");
    }

    static String button(String action, String field,
                         String value, String label, String style) {

        return "<form method='post' class='inline-form'>" +
            "<input type='hidden' name='action' value='" + esc(action) + "'>" +
            "<input type='hidden' name='" + esc(field) +
            "' value='" + esc(value) + "'>" +
            "<button class='btn small " + style + "'>" +
            esc(label) + "</button></form>";
    }

    static Student findStudent(String username) {
        for (Student s : students)
            if (s.username.equals(username)) return s;
        return null;
    }

    static Course findCourse(String id) {
        for (Course c : courses)
            if (c.id.equals(id)) return c;
        return null;
    }

    static Map<String,String> readForm(HttpExchange ex)
            throws IOException {

        String body = new String(
            ex.getRequestBody().readAllBytes(),
            StandardCharsets.UTF_8
        );

        Map<String,String> data = new HashMap<>();

        for (String part : body.split("&")) {
            String[] pair = part.split("=", 2);

            String key = URLDecoder.decode(
                pair[0], StandardCharsets.UTF_8
            );

            String value = pair.length > 1
                ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                : "";

            data.put(key, value);
        }

        return data;
    }

    static void createSession(HttpExchange ex, String username)
            throws IOException {

        String token = UUID.randomUUID().toString();
        sessions.put(token, username);

        ex.getResponseHeaders().add(
            "Set-Cookie",
            "COLLEGE_SESSION=" + token +
            "; Path=/; HttpOnly; SameSite=Lax"
        );

        redirect(ex);
    }

    static String getToken(HttpExchange ex) {

        String cookie = ex.getRequestHeaders().getFirst("Cookie");

        if (cookie == null) return null;

        for (String part : cookie.split(";")) {
            String p = part.trim();

            if (p.startsWith("COLLEGE_SESSION="))
                return p.substring("COLLEGE_SESSION=".length());
        }

        return null;
    }

    static String getLoggedInUser(HttpExchange ex) {
        String token = getToken(ex);
        return token == null ? null : sessions.get(token);
    }

    static void redirect(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Location", "/");
        ex.sendResponseHeaders(303, -1);
        ex.close();
    }

    static String esc(String s) {
        if (s == null) return "";

        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    static void send(HttpExchange ex, String html)
            throws IOException {

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().set(
            "Content-Type", "text/html; charset=UTF-8"
        );

        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}