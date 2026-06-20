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

    @Autowired
    private com.reader.Novel.Reader.repository.UserRepository userRepository;

    @Autowired
    private com.reader.Novel.Reader.repository.BookmarkRepository bookmarkRepository;

    @Autowired
    private com.reader.Novel.Reader.repository.CommentRepository commentRepository;

    @Autowired
    private com.reader.Novel.Reader.repository.NotificationRepository notificationRepository;

    @Autowired
    private com.reader.Novel.Reader.repository.PurchaseRepository purchaseRepository;

    @Autowired
    private com.reader.Novel.Reader.repository.RatingRepository ratingRepository;

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
            User u = existing.get();
            if (!"GOOGLE".equals(u.getLoginType())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email is already registered."));
            }
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
    public ResponseEntity<?> verifyCode(
            @RequestParam String code, 
            HttpSession session,
            jakarta.servlet.http.HttpServletRequest request) {
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
        User user;
        if (existing.isPresent()) {
            user = existing.get();
            if (!"GOOGLE".equals(user.getLoginType())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email is already registered."));
            }
            // Link password to Google account
            String hashedPassword = com.reader.Novel.Reader.util.PasswordUtils.hashPassword(tempPassword);
            user.setPassword(hashedPassword);
            user.setLoginType("LOCAL,GOOGLE");
            userService.updateUser(user);
        } else {
            // Create user
            String hashedPassword = com.reader.Novel.Reader.util.PasswordUtils.hashPassword(tempPassword);
            user = new User(null, tempName, tempEmail, hashedPassword, tempRole);
            userService.addUser(user);
        }

        // Secure Session Management: Prevent session fixation
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession newSession = request.getSession(true);
        newSession.setAttribute("user", user);

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
            jakarta.servlet.http.HttpServletRequest request) {

        if (email == null || email.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required."));
        }

        Optional<User> userOpt = userService.getUserByEmail(email.trim());
        if (userOpt.isEmpty() || !com.reader.Novel.Reader.util.PasswordUtils.checkPassword(password, userOpt.get().getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid email or password."));
        }

        User user = userOpt.get();
        // Prevent session fixation
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = request.getSession(true);
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
    public ResponseEntity<?> googleLogin(
            @RequestParam("token") String token, 
            HttpSession session,
            jakarta.servlet.http.HttpServletRequest request) {
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

            // Verify issuer
            String iss = (String) payload.get("iss");
            if (!"accounts.google.com".equals(iss) && !"https://accounts.google.com".equals(iss)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid token issuer."));
            }

            // Verify expiration
            if (payload.containsKey("exp")) {
                Object expObj = payload.get("exp");
                long expTime = 0;
                if (expObj instanceof Number) {
                    expTime = ((Number) expObj).longValue();
                } else if (expObj instanceof String) {
                    expTime = Long.parseLong((String) expObj);
                }
                long currentTime = java.time.Instant.now().getEpochSecond();
                if (currentTime > expTime) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token has expired."));
                }
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
                // Link account: if it was strictly local, change to LOCAL,GOOGLE
                if ("LOCAL".equals(user.getLoginType())) {
                    user.setLoginType("LOCAL,GOOGLE");
                }
            } else {
                // First time sign-in, register automatically!
                String randomPassword = java.util.UUID.randomUUID().toString();
                String hashedPassword = com.reader.Novel.Reader.util.PasswordUtils.hashPassword(randomPassword);
                
                user = new User(null, name, email, hashedPassword, "READER");
                user.setLoginType("GOOGLE");
            }

            user.setSubscribedToUpdates(true);
            user.setUpdatesEmail(email);
            userService.addUser(user);

            // Prevent session fixation
            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }
            HttpSession newSession = request.getSession(true);
            newSession.setAttribute("user", user);

            return ResponseEntity.ok(Map.of("success", true, "user", user));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Google authentication error: " + e.getMessage()));
        }
    }

    @GetMapping("/discord/login")
    public void discordLogin(
            jakarta.servlet.http.HttpServletRequest request, 
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        String clientId = systemSettingRepository.findById("discord.client_id")
                .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                .orElse("");
        if (clientId.isEmpty()) {
            response.sendError(400, "Discord Client ID is not configured.");
            return;
        }
        String baseUrl = systemSettingRepository.findById("app.base_url")
                .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                .orElse("");
        if (baseUrl.isEmpty()) {
            String scheme = request.getScheme();
            String serverName = request.getServerName();
            int serverPort = request.getServerPort();
            baseUrl = scheme + "://" + serverName + (serverPort == 80 || serverPort == 443 ? "" : ":" + serverPort);
        }
        String redirectUri = baseUrl + "/api/auth/discord/callback";
        String encodedRedirectUri = java.net.URLEncoder.encode(redirectUri, java.nio.charset.StandardCharsets.UTF_8);
        String authUrl = "https://discord.com/oauth2/authorize?client_id=" + clientId +
                "&redirect_uri=" + encodedRedirectUri +
                "&response_type=code&scope=identify+email";
        response.sendRedirect(authUrl);
    }

    @GetMapping("/discord/callback")
    public void discordCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error,
            HttpSession session,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        
        if (error != null) {
            response.sendRedirect("/?error=" + java.net.URLEncoder.encode("Discord auth cancelled: " + error, java.nio.charset.StandardCharsets.UTF_8));
            return;
        }
        if (code == null || code.trim().isEmpty()) {
            response.sendRedirect("/?error=" + java.net.URLEncoder.encode("No authorization code received.", java.nio.charset.StandardCharsets.UTF_8));
            return;
        }

        try {
            String clientId = systemSettingRepository.findById("discord.client_id")
                    .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                    .orElse("");
            String clientSecret = systemSettingRepository.findById("discord.client_secret")
                    .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                    .orElse("");
            String baseUrl = systemSettingRepository.findById("app.base_url")
                    .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                    .orElse("");
            if (baseUrl.isEmpty()) {
                String scheme = request.getScheme();
                String serverName = request.getServerName();
                int serverPort = request.getServerPort();
                baseUrl = scheme + "://" + serverName + (serverPort == 80 || serverPort == 443 ? "" : ":" + serverPort);
            }
            String redirectUri = baseUrl + "/api/auth/discord/callback";

            // Exchange code for token
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);
            headers.add("User-Agent", "Yuki-Tales-App");

            org.springframework.util.LinkedMultiValueMap<String, String> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("grant_type", "authorization_code");
            body.add("code", code);
            body.add("redirect_uri", redirectUri);

            org.springframework.http.HttpEntity<org.springframework.util.LinkedMultiValueMap<String, String>> requestEntity = 
                    new org.springframework.http.HttpEntity<>(body, headers);

            String tokenUrl = "https://discord.com/api/oauth2/token";
            @SuppressWarnings("unchecked")
            Map<String, Object> tokenResponse = restTemplate.postForObject(tokenUrl, requestEntity, Map.class);

            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                response.sendRedirect("/?error=" + java.net.URLEncoder.encode("Failed to exchange Discord authorization code.", java.nio.charset.StandardCharsets.UTF_8));
                return;
            }

            String accessToken = (String) tokenResponse.get("access_token");

            // Fetch user info from Discord
            org.springframework.http.HttpHeaders userHeaders = new org.springframework.http.HttpHeaders();
            userHeaders.setBearerAuth(accessToken);
            userHeaders.add("User-Agent", "Yuki-Tales-App");
            org.springframework.http.HttpEntity<Void> userRequestEntity = new org.springframework.http.HttpEntity<>(userHeaders);

            String userUrl = "https://discord.com/api/users/@me";
            @SuppressWarnings("unchecked")
            Map<String, Object> userProfile = restTemplate.exchange(userUrl, org.springframework.http.HttpMethod.GET, userRequestEntity, Map.class).getBody();

            if (userProfile == null || !userProfile.containsKey("email")) {
                response.sendRedirect("/?error=" + java.net.URLEncoder.encode("Could not retrieve email from Discord profile. Ensure email scope is authorized.", java.nio.charset.StandardCharsets.UTF_8));
                return;
            }

            String email = (String) userProfile.get("email");
            String username = (String) userProfile.get("username");

            if (email == null || email.trim().isEmpty()) {
                response.sendRedirect("/?error=" + java.net.URLEncoder.encode("Discord account has no email address associated.", java.nio.charset.StandardCharsets.UTF_8));
                return;
            }

            // Check if logged-in first (linking/merging flow)
            User loggedInUser = (User) session.getAttribute("user");
            if (loggedInUser != null) {
                Optional<User> existingOpt = userService.getUserByEmail(email);
                if (existingOpt.isPresent()) {
                    User existingUser = existingOpt.get();
                    if (existingUser.getId().equals(loggedInUser.getId())) {
                        User user = userRepository.findById(loggedInUser.getId()).orElse(loggedInUser);
                        if (!user.getLoginType().contains("DISCORD")) {
                            user.setLoginType(user.getLoginType() + ",DISCORD");
                            userRepository.save(user);
                            session.setAttribute("user", user);
                        }
                        response.sendRedirect("/user/panel?tab=settings");
                    } else {
                        // MERGING REQUIRED
                        session.setAttribute("temp_merge_discord_email", email);
                        response.sendRedirect("/user/panel?tab=settings&mergeRequired=true&provider=discord&sourceUserEmail=" + 
                                java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8));
                    }
                } else {
                    // Discord email does not exist, link it to the current user (update email and loginType)
                    User user = userRepository.findById(loggedInUser.getId()).orElse(loggedInUser);
                    user.setEmail(email);
                    if (!user.getLoginType().contains("DISCORD")) {
                        user.setLoginType(user.getLoginType() + ",DISCORD");
                    }
                    userRepository.save(user);
                    session.setAttribute("user", user);
                    response.sendRedirect("/user/panel?tab=settings");
                }
                return;
            }

            // Regular login/signup flow
            Optional<User> userOpt = userService.getUserByEmail(email);
            User user;
            if (userOpt.isPresent()) {
                user = userOpt.get();
                if (!user.getLoginType().contains("DISCORD")) {
                    user.setLoginType(user.getLoginType() + ",DISCORD");
                    userService.updateUser(user);
                }
            } else {
                String randomPassword = java.util.UUID.randomUUID().toString();
                String hashedPassword = com.reader.Novel.Reader.util.PasswordUtils.hashPassword(randomPassword);
                
                user = new User(null, username, email, hashedPassword, "READER");
                user.setLoginType("DISCORD");
                user.setSubscribedToUpdates(true);
                user.setUpdatesEmail(email);
                userService.addUser(user);
            }

            // Secure Session Management: Prevent session fixation
            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }
            HttpSession newSession = request.getSession(true);
            newSession.setAttribute("user", user);

            response.sendRedirect("/");
        } catch (Exception e) {
            response.sendRedirect("/?error=" + java.net.URLEncoder.encode("Discord authentication error: " + e.getMessage(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @PostMapping("/merge/discord")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> mergeDiscord(@RequestParam("email") String email, HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in first."));
        }

        String tempMergeEmail = (String) session.getAttribute("temp_merge_discord_email");
        if (tempMergeEmail == null || !tempMergeEmail.equals(email)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid or expired merge request."));
        }

        try {
            Optional<User> sourceUserOpt = userService.getUserByEmail(email);
            if (sourceUserOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Source user to merge not found."));
            }

            User sourceUser = sourceUserOpt.get();
            if (sourceUser.getId().equals(loggedInUser.getId())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot merge account into itself."));
            }

            User targetUser = userRepository.findById(loggedInUser.getId()).orElseThrow();

            // 1. Merge Balance
            targetUser.setBalance(targetUser.getBalance() + sourceUser.getBalance());

            // 2. Merge Bookmarks
            java.util.List<com.reader.Novel.Reader.model.Bookmark> sourceBookmarks = bookmarkRepository.findByUserIdOrderByUpdatedAtDesc(sourceUser.getId());
            for (com.reader.Novel.Reader.model.Bookmark b : sourceBookmarks) {
                Optional<com.reader.Novel.Reader.model.Bookmark> targetB = bookmarkRepository.findByUserIdAndNovelId(targetUser.getId(), b.getNovelId());
                if (targetB.isPresent()) {
                    bookmarkRepository.delete(b);
                } else {
                    b.setUserId(targetUser.getId());
                    bookmarkRepository.save(b);
                }
            }

            // 3. Merge Comments
            java.util.List<com.reader.Novel.Reader.model.Comment> sourceComments = commentRepository.findByUserId(sourceUser.getId());
            for (com.reader.Novel.Reader.model.Comment c : sourceComments) {
                c.setUser(targetUser);
                commentRepository.save(c);
            }

            // Update likes/dislikes in comments
            java.util.List<com.reader.Novel.Reader.model.Comment> allComments = commentRepository.findAll();
            for (com.reader.Novel.Reader.model.Comment c : allComments) {
                boolean changed = false;
                if (c.getLikedUserIds().contains(sourceUser.getId())) {
                    c.getLikedUserIds().remove(sourceUser.getId());
                    c.getLikedUserIds().add(targetUser.getId());
                    changed = true;
                }
                if (c.getDislikedUserIds().contains(sourceUser.getId())) {
                    c.getDislikedUserIds().remove(sourceUser.getId());
                    c.getDislikedUserIds().add(targetUser.getId());
                    changed = true;
                }
                if (changed) {
                    commentRepository.save(c);
                }
            }

            // 4. Merge Notifications
            java.util.List<com.reader.Novel.Reader.model.Notification> sourceNotifs = notificationRepository.findByUserIdOrderByCreatedAtDesc(sourceUser.getId());
            for (com.reader.Novel.Reader.model.Notification n : sourceNotifs) {
                n.setUserId(targetUser.getId());
                notificationRepository.save(n);
            }

            // 5. Merge Purchases
            java.util.List<com.reader.Novel.Reader.model.Purchase> sourcePurchases = purchaseRepository.findByUserId(sourceUser.getId());
            for (com.reader.Novel.Reader.model.Purchase p : sourcePurchases) {
                Optional<com.reader.Novel.Reader.model.Purchase> targetP = purchaseRepository.findByUserIdAndChapterId(targetUser.getId(), p.getChapterId());
                if (targetP.isPresent()) {
                    purchaseRepository.delete(p);
                } else {
                    p.setUserId(targetUser.getId());
                    purchaseRepository.save(p);
                }
            }

            // 6. Merge Ratings
            java.util.List<com.reader.Novel.Reader.model.Rating> sourceRatings = ratingRepository.findByUserId(sourceUser.getId());
            for (com.reader.Novel.Reader.model.Rating r : sourceRatings) {
                Optional<com.reader.Novel.Reader.model.Rating> targetR = ratingRepository.findByUserIdAndNovelId(targetUser.getId(), r.getNovelId());
                if (targetR.isPresent()) {
                    ratingRepository.delete(r);
                } else {
                    r.setUserId(targetUser.getId());
                    ratingRepository.save(r);
                }
            }

            // Delete source user
            userRepository.delete(sourceUser);

            // Update target user
            targetUser.setEmail(email);
            if (!targetUser.getLoginType().contains("DISCORD")) {
                targetUser.setLoginType(targetUser.getLoginType() + ",DISCORD");
            }
            userRepository.save(targetUser);

            session.removeAttribute("temp_merge_discord_email");
            session.setAttribute("user", targetUser);

            return ResponseEntity.ok(Map.of("success", true, "message", "Accounts merged successfully.", "user", targetUser));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Merging failed: " + e.getMessage()));
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

    // POST to link Google account to logged-in user
    @PostMapping("/link/google")
    public ResponseEntity<?> linkGoogle(@RequestParam("token") String token, HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in first."));
        }

        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + token;
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = restTemplate.getForObject(url, Map.class);
            
            if (payload == null || payload.containsKey("error")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid Google token."));
            }

            // Verify issuer
            String iss = (String) payload.get("iss");
            if (!"accounts.google.com".equals(iss) && !"https://accounts.google.com".equals(iss)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid token issuer."));
            }

            // Verify expiration
            if (payload.containsKey("exp")) {
                Object expObj = payload.get("exp");
                long expTime = 0;
                if (expObj instanceof Number) {
                    expTime = ((Number) expObj).longValue();
                } else if (expObj instanceof String) {
                    expTime = Long.parseLong((String) expObj);
                }
                long currentTime = java.time.Instant.now().getEpochSecond();
                if (currentTime > expTime) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token has expired."));
                }
            }

            String email = (String) payload.get("email");
            String emailVerified = (String) payload.get("email_verified");

            if (!"true".equals(emailVerified)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Google email is not verified."));
            }

            Optional<User> existingOpt = userService.getUserByEmail(email);
            if (existingOpt.isPresent()) {
                User existingUser = existingOpt.get();
                if (existingUser.getId().equals(loggedInUser.getId())) {
                    // Google email is the same as the current user's email
                    User user = userRepository.findById(loggedInUser.getId()).orElse(loggedInUser);
                    if (!user.getLoginType().contains("GOOGLE")) {
                        user.setLoginType(user.getLoginType() + ",GOOGLE");
                        userRepository.save(user);
                        session.setAttribute("user", user);
                    }
                    return ResponseEntity.ok(Map.of("success", true, "message", "Google account linked successfully."));
                } else {
                    // MERGING REQUIRED: Google email already exists as a separate account
                    return ResponseEntity.ok(Map.of(
                        "mergeRequired", true, 
                        "message", "This Google account is already linked to another user. Would you like to merge all data (coins, purchases, bookmarks) from that account into your current account?",
                        "sourceUserEmail", email
                    ));
                }
            }

            // Google email does not exist in db, link it to the current user (change user's email to this Google email, and update login type)
            User user = userRepository.findById(loggedInUser.getId()).orElse(loggedInUser);
            user.setEmail(email);
            if (!user.getLoginType().contains("GOOGLE")) {
                user.setLoginType(user.getLoginType() + ",GOOGLE");
            }
            userRepository.save(user);
            session.setAttribute("user", user);
            return ResponseEntity.ok(Map.of("success", true, "message", "Google account linked successfully."));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Linking error: " + e.getMessage()));
        }
    }

    // POST to merge Google user account into current user account
    @PostMapping("/merge/google")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> mergeGoogle(@RequestParam("token") String token, HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in first."));
        }

        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + token;
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = restTemplate.getForObject(url, Map.class);
            
            if (payload == null || payload.containsKey("error")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid Google token."));
            }

            String email = (String) payload.get("email");
            Optional<User> sourceUserOpt = userService.getUserByEmail(email);
            if (sourceUserOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Source user to merge not found."));
            }

            User sourceUser = sourceUserOpt.get();
            if (sourceUser.getId().equals(loggedInUser.getId())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot merge account into itself."));
            }

            User targetUser = userRepository.findById(loggedInUser.getId()).orElseThrow();

            // 1. Merge Balance
            targetUser.setBalance(targetUser.getBalance() + sourceUser.getBalance());

            // 2. Merge Bookmarks
            java.util.List<com.reader.Novel.Reader.model.Bookmark> sourceBookmarks = bookmarkRepository.findByUserIdOrderByUpdatedAtDesc(sourceUser.getId());
            for (com.reader.Novel.Reader.model.Bookmark b : sourceBookmarks) {
                Optional<com.reader.Novel.Reader.model.Bookmark> targetB = bookmarkRepository.findByUserIdAndNovelId(targetUser.getId(), b.getNovelId());
                if (targetB.isPresent()) {
                    // target already has bookmark, delete duplicate source bookmark
                    bookmarkRepository.delete(b);
                } else {
                    b.setUserId(targetUser.getId());
                    bookmarkRepository.save(b);
                }
            }

            // 3. Merge Comments
            java.util.List<com.reader.Novel.Reader.model.Comment> sourceComments = commentRepository.findByUserId(sourceUser.getId());
            for (com.reader.Novel.Reader.model.Comment c : sourceComments) {
                c.setUser(targetUser);
                commentRepository.save(c);
            }

            // Update likes/dislikes in all comments
            java.util.List<com.reader.Novel.Reader.model.Comment> allComments = commentRepository.findAll();
            for (com.reader.Novel.Reader.model.Comment c : allComments) {
                boolean changed = false;
                if (c.getLikedUserIds().contains(sourceUser.getId())) {
                    c.getLikedUserIds().remove(sourceUser.getId());
                    c.getLikedUserIds().add(targetUser.getId());
                    changed = true;
                }
                if (c.getDislikedUserIds().contains(sourceUser.getId())) {
                    c.getDislikedUserIds().remove(sourceUser.getId());
                    c.getDislikedUserIds().add(targetUser.getId());
                    changed = true;
                }
                if (changed) {
                    commentRepository.save(c);
                }
            }

            // 4. Merge Notifications
            java.util.List<com.reader.Novel.Reader.model.Notification> sourceNotifs = notificationRepository.findByUserIdOrderByCreatedAtDesc(sourceUser.getId());
            for (com.reader.Novel.Reader.model.Notification n : sourceNotifs) {
                n.setUserId(targetUser.getId());
                notificationRepository.save(n);
            }

            // 5. Merge Purchases
            java.util.List<com.reader.Novel.Reader.model.Purchase> sourcePurchases = purchaseRepository.findByUserId(sourceUser.getId());
            for (com.reader.Novel.Reader.model.Purchase p : sourcePurchases) {
                Optional<com.reader.Novel.Reader.model.Purchase> targetP = purchaseRepository.findByUserIdAndChapterId(targetUser.getId(), p.getChapterId());
                if (targetP.isPresent()) {
                    purchaseRepository.delete(p);
                } else {
                    p.setUserId(targetUser.getId());
                    purchaseRepository.save(p);
                }
            }

            // 6. Merge Ratings
            java.util.List<com.reader.Novel.Reader.model.Rating> sourceRatings = ratingRepository.findByUserId(sourceUser.getId());
            for (com.reader.Novel.Reader.model.Rating r : sourceRatings) {
                Optional<com.reader.Novel.Reader.model.Rating> targetR = ratingRepository.findByUserIdAndNovelId(targetUser.getId(), r.getNovelId());
                if (targetR.isPresent()) {
                    ratingRepository.delete(r);
                } else {
                    r.setUserId(targetUser.getId());
                    ratingRepository.save(r);
                }
            }

            // Delete source user to clear email
            userRepository.delete(sourceUser);

            // Update target user loginType and email
            targetUser.setEmail(email);
            if (!targetUser.getLoginType().contains("GOOGLE")) {
                targetUser.setLoginType(targetUser.getLoginType() + ",GOOGLE");
            }
            userRepository.save(targetUser);

            session.setAttribute("user", targetUser);
            return ResponseEntity.ok(Map.of("success", true, "message", "Accounts merged successfully.", "user", targetUser));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Merging failed: " + e.getMessage()));
        }
    }

    // POST to link local password to Google-only account
    @PostMapping("/link/password")
    public ResponseEntity<?> linkPassword(@RequestParam("password") String password, HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in first."));
        }

        if (password == null || password.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password cannot be empty."));
        }

        User user = userRepository.findById(loggedInUser.getId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found."));
        }

        String hashedPassword = com.reader.Novel.Reader.util.PasswordUtils.hashPassword(password);
        user.setPassword(hashedPassword);
        
        if (!user.getLoginType().contains("LOCAL")) {
            user.setLoginType("LOCAL," + user.getLoginType());
        }
        
        userRepository.save(user);
        session.setAttribute("user", user);

        return ResponseEntity.ok(Map.of("success", true, "message", "Password linked successfully.", "user", user));
    }
}
