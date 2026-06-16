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

    @PostMapping("/facebook")
    public ResponseEntity<?> facebookLogin(@RequestParam("token") String token, HttpSession session) {
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token is required."));
        }

        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            
            // 1. Verify app ID from token
            String appUrl = "https://graph.facebook.com/app?access_token=" + token;
            @SuppressWarnings("unchecked")
            Map<String, Object> appInfo = restTemplate.getForObject(appUrl, Map.class);
            if (appInfo == null || !appInfo.containsKey("id")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid Facebook token app check."));
            }
            
            String appId = String.valueOf(appInfo.get("id"));
            String expectedAppId = systemSettingRepository.findById("facebook.app_id")
                    .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                    .orElse("");
            
            if (expectedAppId.isEmpty() || !expectedAppId.equals(appId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token was not generated for this application."));
            }

            // 2. Fetch user profile
            String fbUrl = "https://graph.facebook.com/me?fields=id,name,email&access_token=" + token;
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = restTemplate.getForObject(fbUrl, Map.class);
            if (payload == null || !payload.containsKey("id")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid Facebook token."));
            }

            String fbId = String.valueOf(payload.get("id"));
            String name = (String) payload.get("name");
            String email = (String) payload.get("email");

            if (email == null || email.trim().isEmpty()) {
                email = "fb_" + fbId + "@facebook.com";
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

            user.setLoginType("FACEBOOK");
            user.setSubscribedToUpdates(true);
            user.setUpdatesEmail(email);
            userService.addUser(user);

            // Log user in
            session.setAttribute("user", user);
            return ResponseEntity.ok(Map.of("success", true, "user", user));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Facebook authentication error: " + e.getMessage()));
        }
    }

    @GetMapping("/x/login")
    public void xLogin(HttpSession session, jakarta.servlet.http.HttpServletResponse response) {
        try {
            String xClientId = systemSettingRepository.findById("x.client_id")
                    .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                    .orElse("");
            
            String appBaseUrl = systemSettingRepository.findById("app.base_url")
                    .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                    .orElse("http://localhost:8080");

            if (xClientId.isEmpty()) {
                response.getWriter().write("X (Twitter) Login is not configured.");
                return;
            }

            String state = java.util.UUID.randomUUID().toString();
            String codeVerifier = generateRandomString(50);
            String codeChallenge = generateCodeChallenge(codeVerifier);

            session.setAttribute("x_oauth_state", state);
            session.setAttribute("x_oauth_verifier", codeVerifier);

            String redirectUri = appBaseUrl + "/api/auth/x/callback";
            
            String authorizeUrl = "https://twitter.com/i/oauth2/authorize"
                    + "?response_type=code"
                    + "&client_id=" + java.net.URLEncoder.encode(xClientId, "UTF-8")
                    + "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri, "UTF-8")
                    + "&scope=" + java.net.URLEncoder.encode("users.read tweet.read", "UTF-8")
                    + "&state=" + java.net.URLEncoder.encode(state, "UTF-8")
                    + "&code_challenge=" + java.net.URLEncoder.encode(codeChallenge, "UTF-8")
                    + "&code_challenge_method=s256";

            response.sendRedirect(authorizeUrl);
        } catch (Exception e) {
            try {
                response.getWriter().write("Error initiating X Login: " + e.getMessage());
            } catch (Exception ignored) {}
        }
    }

    @GetMapping("/x/callback")
    public void xCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            HttpSession session,
            jakarta.servlet.http.HttpServletResponse response) {
        try {
            if (error != null && !error.isEmpty()) {
                response.sendRedirect("/?error=" + java.net.URLEncoder.encode("X Login cancelled: " + error, "UTF-8"));
                return;
            }

            String sessionState = (String) session.getAttribute("x_oauth_state");
            String codeVerifier = (String) session.getAttribute("x_oauth_verifier");
            
            session.removeAttribute("x_oauth_state");
            session.removeAttribute("x_oauth_verifier");

            if (state == null || sessionState == null || !state.equals(sessionState)) {
                response.sendRedirect("/?error=" + java.net.URLEncoder.encode("Invalid state parameter.", "UTF-8"));
                return;
            }

            if (code == null || code.isEmpty() || codeVerifier == null) {
                response.sendRedirect("/?error=" + java.net.URLEncoder.encode("Authorization code or verifier is missing.", "UTF-8"));
                return;
            }

            String xClientId = systemSettingRepository.findById("x.client_id")
                    .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                    .orElse("");
            
            String xClientSecret = systemSettingRepository.findById("x.client_secret")
                    .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                    .orElse("");
            
            String appBaseUrl = systemSettingRepository.findById("app.base_url")
                    .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                    .orElse("http://localhost:8080");

            String redirectUri = appBaseUrl + "/api/auth/x/callback";

            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            
            // Exchange code for token
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);
            
            // If Client Secret is configured, use Basic Auth headers
            if (!xClientSecret.isEmpty()) {
                String clientDetails = xClientId + ":" + xClientSecret;
                String encodedCredentials = java.util.Base64.getEncoder().encodeToString(clientDetails.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                headers.set("Authorization", "Basic " + encodedCredentials);
            }

            String body = "code=" + java.net.URLEncoder.encode(code, "UTF-8")
                    + "&grant_type=authorization_code"
                    + "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri, "UTF-8")
                    + "&code_verifier=" + java.net.URLEncoder.encode(codeVerifier, "UTF-8");
            
            // Twitter spec says if Basic Auth header is NOT used, client_id must be in body
            if (xClientSecret.isEmpty()) {
                body += "&client_id=" + java.net.URLEncoder.encode(xClientId, "UTF-8");
            }

            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(body, headers);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> tokenResponse = restTemplate.postForObject("https://api.twitter.com/2/oauth2/token", entity, Map.class);
            
            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                response.sendRedirect("/?error=" + java.net.URLEncoder.encode("Failed to exchange code for token.", "UTF-8"));
                return;
            }

            String accessToken = (String) tokenResponse.get("access_token");

            // Fetch user profile from X API
            org.springframework.http.HttpHeaders profileHeaders = new org.springframework.http.HttpHeaders();
            profileHeaders.set("Authorization", "Bearer " + accessToken);

            org.springframework.http.HttpEntity<Void> profileEntity = new org.springframework.http.HttpEntity<>(profileHeaders);
            @SuppressWarnings("unchecked")
            org.springframework.http.ResponseEntity<Map> profileResponse = restTemplate.exchange(
                    "https://api.twitter.com/2/users/me",
                    org.springframework.http.HttpMethod.GET,
                    profileEntity,
                    Map.class
            );

            Map<String, Object> profileBody = profileResponse.getBody();
            if (profileBody == null || !profileBody.containsKey("data")) {
                response.sendRedirect("/?error=" + java.net.URLEncoder.encode("Failed to fetch user profile from X.", "UTF-8"));
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) profileBody.get("data");
            String twitterId = String.valueOf(data.get("id"));
            String name = (String) data.get("name");
            String username = (String) data.get("username");

            String email = "x_" + twitterId + "@x.com";

            // Check if user already exists
            Optional<User> userOpt = userService.getUserByEmail(email);
            User user;
            if (userOpt.isPresent()) {
                user = userOpt.get();
            } else {
                // First time sign-in, register automatically!
                String randomPassword = java.util.UUID.randomUUID().toString();
                String hashedPassword = com.reader.Novel.Reader.util.PasswordUtils.hashPassword(randomPassword);
                
                user = new User(null, name != null ? name : username, email, hashedPassword, "READER");
            }

            user.setLoginType("X");
            user.setSubscribedToUpdates(true);
            user.setUpdatesEmail(email);
            userService.addUser(user);

            // Log user in
            session.setAttribute("user", user);
            
            response.sendRedirect("/");
        } catch (Exception e) {
            try {
                response.sendRedirect("/?error=" + java.net.URLEncoder.encode("X callback error: " + e.getMessage(), "UTF-8"));
            } catch (Exception ignored) {}
        }
    }

    private String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String generateCodeChallenge(String codeVerifier) throws Exception {
        byte[] bytes = codeVerifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        java.security.MessageDigest messageDigest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] digest = messageDigest.digest(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
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
