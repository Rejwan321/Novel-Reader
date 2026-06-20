package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.model.*;
import com.reader.Novel.Reader.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AuthRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private SystemSettingRepository systemSettingRepository;

    @BeforeEach
    public void setup() {
        jdbcTemplate.execute("ALTER TABLE reader_internal ALTER COLUMN id RESTART WITH 100");
        bookmarkRepository.deleteAll();
        ratingRepository.deleteAll();
        purchaseRepository.deleteAll();
        commentRepository.deleteAll();
        notificationRepository.deleteAll();
        userRepository.deleteAll();

        // Configure system setting for google client id
        SystemSetting clientIdSetting = new SystemSetting("google.client_id", "test-client-id");
        systemSettingRepository.save(clientIdSetting);
    }

    @Test
    public void testSignupSendCodeAndVerifyNewUser() throws Exception {
        // 1. Send verification code
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/signup/send-code")
                .session(session)
                .param("name", "New User")
                .param("email", "new@example.com")
                .param("password", "password123")
                .param("user_type", "READER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        String code = (String) session.getAttribute("temp_signup_code");
        assertNotNull(code);

        // 2. Verify registration and test Session Fixation Protection
        MvcResult result = mockMvc.perform(post("/api/auth/signup/verify")
                .session(session)
                .param("code", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        HttpSession newSession = result.getRequest().getSession(false);
        assertNotNull(newSession);
        // Session ID should be regenerated
        assertNotEquals(session.getId(), newSession.getId());

        User createdUser = (User) newSession.getAttribute("user");
        assertNotNull(createdUser);
        assertEquals("new@example.com", createdUser.getEmail());
        assertEquals("READER", createdUser.getUser_type());
        assertEquals("LOCAL", createdUser.getLoginType());
    }

    @Test
    public void testSignupVerifyExistingLocalUserFails() throws Exception {
        // 1. Pre-register local user
        User localUser = new User();
        localUser.setName("Local User");
        localUser.setEmail("local@example.com");
        localUser.setPassword("password123");
        localUser.setUser_type("READER");
        localUser.setLoginType("LOCAL");
        userRepository.save(localUser);

        // 2. Try to send code for registered email -> Should fail with conflict status
        mockMvc.perform(post("/api/auth/signup/send-code")
                .param("name", "Another")
                .param("email", "local@example.com")
                .param("password", "password")
                .param("user_type", "READER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Email is already registered."));
    }

    @Test
    public void testSignupVerifyExistingGoogleUserLinks() throws Exception {
        // 1. Pre-register Google-only user
        User googleUser = new User();
        googleUser.setName("Google User");
        googleUser.setEmail("google@example.com");
        googleUser.setPassword("dummy_pass");
        googleUser.setUser_type("READER");
        googleUser.setLoginType("GOOGLE");
        userRepository.save(googleUser);

        // 2. Send code (which should succeed because existing loginType is GOOGLE)
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/signup/send-code")
                .session(session)
                .param("name", "Google User")
                .param("email", "google@example.com")
                .param("password", "newpassword123")
                .param("user_type", "READER"))
                .andExpect(status().isOk());

        String code = (String) session.getAttribute("temp_signup_code");
        assertNotNull(code);

        // 3. Verify code - should link local password to Google account, changing loginType to LOCAL,GOOGLE
        MvcResult result = mockMvc.perform(post("/api/auth/signup/verify")
                .session(session)
                .param("code", code))
                .andExpect(status().isOk())
                .andReturn();

        HttpSession newSession = result.getRequest().getSession(false);
        User user = (User) newSession.getAttribute("user");
        assertNotNull(user);
        assertEquals("LOCAL,GOOGLE", user.getLoginType());
        assertTrue(com.reader.Novel.Reader.util.PasswordUtils.checkPassword("newpassword123", user.getPassword()));
    }

    @Test
    public void testGoogleLoginNewUser() throws Exception {
        Map<String, Object> googlePayload = new HashMap<>();
        googlePayload.put("email", "google_new@example.com");
        googlePayload.put("name", "Google New");
        googlePayload.put("email_verified", "true");
        googlePayload.put("iss", "https://accounts.google.com");
        googlePayload.put("aud", "test-client-id");
        googlePayload.put("exp", java.time.Instant.now().getEpochSecond() + 3600);

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, context) -> {
            when(mock.getForObject(anyString(), eq(Map.class))).thenReturn(googlePayload);
        })) {
            MockHttpSession session = new MockHttpSession();
            MvcResult result = mockMvc.perform(post("/api/auth/google")
                    .session(session)
                    .param("token", "dummy-id-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.email").value("google_new@example.com"))
                    .andExpect(jsonPath("$.user.loginType").value("GOOGLE"))
                    .andReturn();

            HttpSession newSession = result.getRequest().getSession(false);
            assertNotEquals(session.getId(), newSession.getId());
        }
    }

    @Test
    public void testGoogleLoginExistingLocalUserAutoLinks() throws Exception {
        // Pre-register local user
        User localUser = new User();
        localUser.setName("Existing Local");
        localUser.setEmail("local_exist@example.com");
        localUser.setPassword("password123");
        localUser.setUser_type("READER");
        localUser.setLoginType("LOCAL");
        userRepository.save(localUser);

        Map<String, Object> googlePayload = new HashMap<>();
        googlePayload.put("email", "local_exist@example.com");
        googlePayload.put("name", "Existing Local");
        googlePayload.put("email_verified", "true");
        googlePayload.put("iss", "https://accounts.google.com");
        googlePayload.put("aud", "test-client-id");
        googlePayload.put("exp", java.time.Instant.now().getEpochSecond() + 3600);

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, context) -> {
            when(mock.getForObject(anyString(), eq(Map.class))).thenReturn(googlePayload);
        })) {
            mockMvc.perform(post("/api/auth/google")
                    .param("token", "dummy-id-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.loginType").value("LOCAL,GOOGLE"));
        }
    }

    @Test
    public void testLinkGoogleSameAccountSuccess() throws Exception {
        User loggedIn = new User();
        loggedIn.setName("Logged In");
        loggedIn.setEmail("match@example.com");
        loggedIn.setPassword("pass123");
        loggedIn.setUser_type("READER");
        loggedIn.setLoginType("LOCAL");
        loggedIn = userRepository.save(loggedIn);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", loggedIn);

        Map<String, Object> googlePayload = new HashMap<>();
        googlePayload.put("email", "match@example.com");
        googlePayload.put("email_verified", "true");
        googlePayload.put("iss", "https://accounts.google.com");
        googlePayload.put("exp", java.time.Instant.now().getEpochSecond() + 3600);

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, context) -> {
            when(mock.getForObject(anyString(), eq(Map.class))).thenReturn(googlePayload);
        })) {
            mockMvc.perform(post("/api/auth/link/google")
                    .session(session)
                    .param("token", "dummy-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Google account linked successfully."));

            User updatedUser = userRepository.findById(loggedIn.getId()).orElseThrow();
            assertEquals("LOCAL,GOOGLE", updatedUser.getLoginType());
        }
    }

    @Test
    public void testLinkGoogleDifferentAccountMergeRequired() throws Exception {
        User loggedIn = new User();
        loggedIn.setName("Logged In");
        loggedIn.setEmail("logged_in@example.com");
        loggedIn.setPassword("pass123");
        loggedIn.setUser_type("READER");
        loggedIn.setLoginType("LOCAL");
        loggedIn = userRepository.save(loggedIn);

        User existingGoogleUser = new User();
        existingGoogleUser.setName("Google Owner");
        existingGoogleUser.setEmail("google_owner@example.com");
        existingGoogleUser.setPassword("other_pass");
        existingGoogleUser.setUser_type("READER");
        existingGoogleUser.setLoginType("GOOGLE");
        existingGoogleUser = userRepository.save(existingGoogleUser);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", loggedIn);

        Map<String, Object> googlePayload = new HashMap<>();
        googlePayload.put("email", "google_owner@example.com");
        googlePayload.put("email_verified", "true");
        googlePayload.put("iss", "https://accounts.google.com");
        googlePayload.put("exp", java.time.Instant.now().getEpochSecond() + 3600);

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, context) -> {
            when(mock.getForObject(anyString(), eq(Map.class))).thenReturn(googlePayload);
        })) {
            mockMvc.perform(post("/api/auth/link/google")
                    .session(session)
                    .param("token", "dummy-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mergeRequired").value(true))
                    .andExpect(jsonPath("$.sourceUserEmail").value("google_owner@example.com"));
        }
    }

    @Test
    public void testMergeGoogleAccounts() throws Exception {
        // 1. Target user (currently logged in)
        User targetUser = new User();
        targetUser.setName("Target User");
        targetUser.setEmail("target@example.com");
        targetUser.setPassword("pass123");
        targetUser.setUser_type("READER");
        targetUser.setBalance(50);
        targetUser.setLoginType("LOCAL");
        targetUser = userRepository.save(targetUser);

        // 2. Source user (associated with Google account)
        User sourceUser = new User();
        sourceUser.setName("Source User");
        sourceUser.setEmail("source@example.com");
        sourceUser.setPassword("googlepass");
        sourceUser.setUser_type("READER");
        sourceUser.setBalance(150);
        sourceUser.setLoginType("GOOGLE");
        sourceUser = userRepository.save(sourceUser);

        // 3. Add data to source user
        Novel novel = new Novel();
        novel.setTitle("Novel One");
        novel.setAuthor("Author One");
        novel = novelRepository.save(novel);

        Chapter chapter = new Chapter();
        chapter.setNovel(novel);
        chapter.setTitle("Chapter One");
        chapter.setChapterNumber(1.0);
        chapter = chapterRepository.save(chapter);

        // Bookmark
        Bookmark sourceBookmark = new Bookmark();
        sourceBookmark.setUserId(sourceUser.getId());
        sourceBookmark.setNovelId(novel.getId());
        bookmarkRepository.save(sourceBookmark);

        // Rating
        Rating sourceRating = new Rating();
        sourceRating.setUserId(sourceUser.getId());
        sourceRating.setNovelId(novel.getId());
        sourceRating.setRatingValue(5);
        ratingRepository.save(sourceRating);

        // Purchase
        Purchase sourcePurchase = new Purchase();
        sourcePurchase.setUserId(sourceUser.getId());
        sourcePurchase.setChapterId(chapter.getId());
        purchaseRepository.save(sourcePurchase);

        // Comment
        Comment sourceComment = new Comment(chapter.getId(), sourceUser, "Great chapter");
        sourceComment = commentRepository.save(sourceComment);

        // Notification
        Notification sourceNotif = new Notification("System", "You were mentioned", novel.getId(), novel.getTitle(), 1.0, chapter.getId(), sourceUser.getId());
        notificationRepository.save(sourceNotif);

        // Logged-in session of target user
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", targetUser);

        Map<String, Object> googlePayload = new HashMap<>();
        googlePayload.put("email", "source@example.com");
        googlePayload.put("email_verified", "true");
        googlePayload.put("iss", "https://accounts.google.com");
        googlePayload.put("exp", java.time.Instant.now().getEpochSecond() + 3600);

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, context) -> {
            when(mock.getForObject(anyString(), eq(Map.class))).thenReturn(googlePayload);
        })) {
            mockMvc.perform(post("/api/auth/merge/google")
                    .session(session)
                    .param("token", "dummy-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // Verify source user is deleted
            assertFalse(userRepository.findById(sourceUser.getId()).isPresent());

            // Verify target user has updated email and loginType
            User updatedTarget = userRepository.findById(targetUser.getId()).orElseThrow();
            assertEquals("source@example.com", updatedTarget.getEmail());
            assertEquals("LOCAL,GOOGLE", updatedTarget.getLoginType());
            // Verify balance is merged: 50 + 150 = 200
            assertEquals(200, updatedTarget.getBalance());

            // Verify bookmark moved
            Optional<Bookmark> bookmarkOpt = bookmarkRepository.findByUserIdAndNovelId(targetUser.getId(), novel.getId());
            assertTrue(bookmarkOpt.isPresent());

            // Verify rating moved
            Optional<Rating> ratingOpt = ratingRepository.findByUserIdAndNovelId(targetUser.getId(), novel.getId());
            assertTrue(ratingOpt.isPresent());

            // Verify purchase moved
            Optional<Purchase> purchaseOpt = purchaseRepository.findByUserIdAndChapterId(targetUser.getId(), chapter.getId());
            assertTrue(purchaseOpt.isPresent());

            // Verify comment owner is updated
            Comment updatedComment = commentRepository.findById(sourceComment.getId()).orElseThrow();
            assertEquals(targetUser.getId(), updatedComment.getUser().getId());

            // Verify notification moved
            List<Notification> targetNotifs = notificationRepository.findByUserIdOrderByCreatedAtDesc(targetUser.getId());
            assertEquals(1, targetNotifs.size());
            assertEquals("You were mentioned", targetNotifs.get(0).getSnippet());
        }
    }

    @Test
    public void testLinkPassword() throws Exception {
        User loggedIn = new User();
        loggedIn.setName("Google Owner");
        loggedIn.setEmail("google_owner@example.com");
        loggedIn.setPassword("other_pass");
        loggedIn.setUser_type("READER");
        loggedIn.setLoginType("GOOGLE");
        loggedIn = userRepository.save(loggedIn);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", loggedIn);

        mockMvc.perform(post("/api/auth/link/password")
                .session(session)
                .param("password", "newpassword123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User updatedUser = userRepository.findById(loggedIn.getId()).orElseThrow();
        assertEquals("LOCAL,GOOGLE", updatedUser.getLoginType());
        assertTrue(com.reader.Novel.Reader.util.PasswordUtils.checkPassword("newpassword123", updatedUser.getPassword()));
    }

    @Test
    public void testDiscordLoginRedirects() throws Exception {
        systemSettingRepository.save(new SystemSetting("discord.client_id", "test-discord-id"));
        systemSettingRepository.save(new SystemSetting("app.base_url", "http://localhost:8080"));

        mockMvc.perform(get("/api/auth/discord/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("https://discord.com/oauth2/authorize")));
    }

    @Test
    public void testDiscordCallbackNewUser() throws Exception {
        systemSettingRepository.save(new SystemSetting("discord.client_id", "test-discord-id"));
        systemSettingRepository.save(new SystemSetting("discord.client_secret", "test-discord-secret"));

        Map<String, Object> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", "dummy-access-token");

        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("email", "discord_new@example.com");
        userProfile.put("username", "discord_user");

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, context) -> {
            when(mock.postForObject(anyString(), any(), eq(Map.class))).thenReturn(tokenResponse);
            
            org.springframework.http.ResponseEntity<Map> responseEntity = 
                    new org.springframework.http.ResponseEntity<>(userProfile, org.springframework.http.HttpStatus.OK);
            when(mock.exchange(anyString(), any(org.springframework.http.HttpMethod.class), any(org.springframework.http.HttpEntity.class), eq(Map.class)))
                    .thenReturn(responseEntity);
        })) {
            MockHttpSession session = new MockHttpSession();
            mockMvc.perform(get("/api/auth/discord/callback")
                    .session(session)
                    .param("code", "dummy-auth-code"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));

            User createdUser = userRepository.findByEmail("discord_new@example.com").orElse(null);
            assertNotNull(createdUser);
            assertEquals("discord_user", createdUser.getName());
            assertEquals("DISCORD", createdUser.getLoginType());
        }
    }

    @Test
    public void testDiscordCallbackLinkUser() throws Exception {
        systemSettingRepository.save(new SystemSetting("discord.client_id", "test-discord-id"));
        systemSettingRepository.save(new SystemSetting("discord.client_secret", "test-discord-secret"));

        User existingUser = new User();
        existingUser.setName("Existing User");
        existingUser.setEmail("link@example.com");
        existingUser.setPassword("pass");
        existingUser.setUser_type("READER");
        existingUser.setLoginType("LOCAL");
        existingUser = userRepository.save(existingUser);

        Map<String, Object> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", "dummy-access-token");

        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("email", "link@example.com");
        userProfile.put("username", "discord_user");

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, context) -> {
            when(mock.postForObject(anyString(), any(), eq(Map.class))).thenReturn(tokenResponse);
            
            org.springframework.http.ResponseEntity<Map> responseEntity = 
                    new org.springframework.http.ResponseEntity<>(userProfile, org.springframework.http.HttpStatus.OK);
            when(mock.exchange(anyString(), any(org.springframework.http.HttpMethod.class), any(org.springframework.http.HttpEntity.class), eq(Map.class)))
                    .thenReturn(responseEntity);
        })) {
            MockHttpSession session = new MockHttpSession();
            session.setAttribute("user", existingUser);

            mockMvc.perform(get("/api/auth/discord/callback")
                    .session(session)
                    .param("code", "dummy-auth-code"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/user/panel?tab=settings"));

            User updatedUser = userRepository.findById(existingUser.getId()).orElseThrow();
            assertEquals("LOCAL,DISCORD", updatedUser.getLoginType());
        }
    }
}
