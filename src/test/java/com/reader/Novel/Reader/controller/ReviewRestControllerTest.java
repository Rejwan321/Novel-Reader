package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.model.Review;
import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.repository.ReviewRepository;
import com.reader.Novel.Reader.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ReviewRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private User testUser;
    private MockHttpSession session;

    @BeforeEach
    public void setup() {
        jdbcTemplate.execute("ALTER TABLE reader_internal ALTER COLUMN id RESTART WITH 200");

        testUser = new User();
        testUser.setName("Reviewer User");
        testUser.setEmail("reviewer@example.com");
        testUser.setPassword("password123");
        testUser.setUser_type("READER");
        testUser = userRepository.save(testUser);

        session = new MockHttpSession();
        session.setAttribute("user", testUser);
    }

    @Test
    public void testReviewRateLimitForLoggedInUser() throws Exception {
        // Submit 5 reviews successfully
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/reviews/submit")
                    .session(session)
                    .param("name", "Reviewer User")
                    .param("rating", "5")
                    .param("comment", "Review number " + i))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        // The 6th review should be blocked with 429 Too Many Requests
        mockMvc.perform(post("/api/reviews/submit")
                .session(session)
                .param("name", "Reviewer User")
                .param("rating", "5")
                .param("comment", "Review number 6"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("You have reached your limit of 5 reviews today."));
    }

    @Test
    public void testReviewRateLimitForGuest() throws Exception {
        MockHttpSession guestSession = new MockHttpSession(); // No logged-in user

        // Submit 5 reviews successfully
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/reviews/submit")
                    .session(guestSession)
                    .param("name", "Guest Reader")
                    .param("rating", "4")
                    .param("comment", "Guest Review " + i))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        // The 6th review should be blocked with 429 Too Many Requests
        mockMvc.perform(post("/api/reviews/submit")
                .session(guestSession)
                .param("name", "Guest Reader")
                .param("rating", "4")
                .param("comment", "Guest Review 6"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Guests can only submit 5 reviews per day from the same IP address."));
    }
}
