package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthRestController {

    @Autowired
    private UserService userService;

    @Autowired
    private com.reader.Novel.Reader.repository.SystemSettingRepository systemSettingRepository;

    @Autowired
    private com.reader.Novel.Reader.service.EmailService emailService;

    @PostMapping("/signup/send-code")
    public ResponseEntity<?> sendCode(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(required = false, defaultValue = "READER") String user_type,
            HttpSession session) {

        if (name == null || name.trim().isEmpty() ||
            email == null || email.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "All fields are required."));
        }

        Optional<User> existing = userService.getUserByEmail(email.trim());
        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email is already registered."));
        }

        // Validate role
        String role = "READER";
        if ("EDITOR".equals(user_type)) {
            role = "EDITOR";
        } else if ("PROOFREADER".equals(user_type)) {
            role = "PROOFREADER";
        }

        // Generate 6-digit random code
        String code = String.format("%06d", new java.util.Random().nextInt(1000000));

        // Save signup info temporarily in session
        session.setAttribute("temp_signup_name", name.trim());
        session.setAttribute("temp_signup_email", email.trim());
        session.setAttribute("temp_signup_password", password);
        session.setAttribute("temp_signup_role", role);
        session.setAttribute("temp_signup_code", code);

        // Send email asynchronously
        String subject = "[Yuki Tales] Verification Code";
        String body = String.format(
            "Hello %s,\n\n" +
            "Thank you for registering at Yuki Tales!\n\n" +
            "Your 6-digit verification code is: %s\n\n" +
            "Please enter this code on the website to complete your registration.\n\n" +
            "Best regards,\n" +
            "Yuki Tales Support",
            name.trim(), code
        );
        emailService.sendCustomEmailAsync(email.trim(), subject, body);

        // Also print to console for local logs/fallback
        System.out.println("====== YUKI TALES SIGNUP VERIFICATION CODE ======");
        System.out.println("Email: " + email.trim());
        System.out.println("Code: " + code);
        System.out.println("=================================================");

        return ResponseEntity.ok(Map.of("success", true, "message", "Verification code sent to your email."));
    }

    @PostMapping("/signup/verify")
    public ResponseEntity<?> verifyCode(@RequestParam String code, HttpSession session) {
        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Verification code is required."));
        }

        String tempCode = (String) session.getAttribute("temp_signup_code");
        String tempEmail = (String) session.getAttribute("temp_signup_email");
        String tempName = (String) session.getAttribute("temp_signup_name");
        String tempPassword = (String) session.getAttribute("temp_signup_password");
        String tempRole = (String) session.getAttribute("temp_signup_role");

        if (tempCode == null || tempEmail == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Session expired or invalid registration. Please request a new code."));
        }

        if (!tempCode.equals(code.trim())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid verification code."));
        }

        // Verify that the email is still not registered
        Optional<User> existing = userService.getUserByEmail(tempEmail);
        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email is already registered."));
        }

        // Create user
        String hashedPassword = com.reader.Novel.Reader.util.PasswordUtils.hashPassword(tempPassword);
        User user = new User(null, tempName, tempEmail, hashedPassword, tempRole);
        userService.addUser(user);

        // Auto-login
        session.setAttribute("user", user);

        // Clear session variables
        session.removeAttribute("temp_signup_name");
        session.removeAttribute("temp_signup_email");
        session.removeAttribute("temp_signup_password");
        session.removeAttribute("temp_signup_role");
        session.removeAttribute("temp_signup_code");

        return ResponseEntity.ok(Map.of("success", true, "user", user));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(required = false, defaultValue = "READER") String user_type,
            HttpSession session) {

        if (name == null || name.trim().isEmpty() ||
            email == null || email.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "All fields are required."));
        }

        Optional<User> existing = userService.getUserByEmail(email.trim());
        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email is already registered."));
        }

        // Validate selected role
        String role = "READER";
        if ("EDITOR".equals(user_type)) {
            role = "EDITOR";
        } else if ("PROOFREADER".equals(user_type)) {
            role = "PROOFREADER";
        }

        String hashedPassword = com.reader.Novel.Reader.util.PasswordUtils.hashPassword(password);
        User user = new User(null, name.trim(), email.trim(), hashedPassword, role);
        userService.addUser(user);
        
        // Auto-login after signup
        session.setAttribute("user", user);
        return ResponseEntity.ok(Map.of("success", true, "user", user));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestParam String email,
            @RequestParam String password,
            HttpSession session) {

        if (email == null || email.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required."));
        }

        Optional<User> userOpt = userService.getUserByEmail(email.trim());
        if (userOpt.isEmpty() || !com.reader.Novel.Reader.util.PasswordUtils.checkPassword(password, userOpt.get().getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid email or password."));
        }

        User user = userOpt.get();
        session.setAttribute("user", user);
        return ResponseEntity.ok(Map.of("success", true, "user", user));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }
        return ResponseEntity.ok(user);
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestParam("token") String token, HttpSession session) {
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token is required."));
        }

        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + token;
            
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = restTemplate.getForObject(url, Map.class);
            
            if (payload == null || payload.containsKey("error")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid Google token."));
            }

            // Verify audience
            String aud = (String) payload.get("aud");
            String expectedClientId = systemSettingRepository.findById("google.client_id")
                    .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                    .orElse("your-google-client-id");
            if (!expectedClientId.equals(aud)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token was not generated for this application."));
            }

            String email = (String) payload.get("email");
            String name = (String) payload.get("name");
            String emailVerified = (String) payload.get("email_verified");

            if (!"true".equals(emailVerified)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Google email is not verified."));
            }

            // Check if user already exists
            Optional<User> userOpt = userService.getUserByEmail(email);
            User user;
            if (userOpt.isPresent()) {
                user = userOpt.get();
            } else {
                // First time sign-in, register automatically!
                String randomPassword = java.util.UUID.randomUUID().toString();
                String hashedPassword = com.reader.Novel.Reader.util.PasswordUtils.hashPassword(randomPassword);
                
                user = new User(null, name, email, hashedPassword, "READER");
            }

            user.setLoginType("GOOGLE");
            user.setSubscribedToUpdates(true);
            user.setUpdatesEmail(email);
            userService.addUser(user);

            // Log user in
            session.setAttribute("user", user);
            return ResponseEntity.ok(Map.of("success", true, "user", user));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Google authentication error: " + e.getMessage()));
        }
    }

    @PostMapping("/subscribe-updates")
    public ResponseEntity<?> subscribeUpdates(@RequestParam String email, HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please login to subscribe."));
        }
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required."));
        }
        
        Optional<User> userOpt = userService.getUserByEmail(loggedInUser.getEmail());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setSubscribedToUpdates(true);
            user.setUpdatesEmail(email.trim());
            userService.updateUser(user);
            
            // Sync session
            session.setAttribute("user", user);
            return ResponseEntity.ok(user);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found."));
    }
}
