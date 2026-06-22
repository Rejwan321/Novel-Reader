package com.reader.Novel.Reader.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reader.Novel.Reader.model.Chapter;
import com.reader.Novel.Reader.model.Novel;
import com.reader.Novel.Reader.model.Purchase;
import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.repository.ChapterRepository;
import com.reader.Novel.Reader.repository.NovelRepository;
import com.reader.Novel.Reader.repository.PurchaseRepository;
import com.reader.Novel.Reader.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class UserPanelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private User testUser;
    private Novel testNovel;
    private Chapter testChapter;
    private Purchase testPurchase;
    private MockHttpSession session;

    @BeforeEach
    public void setup() {
        jdbcTemplate.execute("ALTER TABLE reader_internal ALTER COLUMN id RESTART WITH 400");

        // Create User
        testUser = new User();
        testUser.setName("Wallet Owner");
        testUser.setEmail("walletowner@example.com");
        testUser.setPassword("password");
        testUser.setUser_type("READER");
        testUser.setBalance(250);
        testUser = userRepository.save(testUser);

        // Create Novel
        testNovel = new Novel();
        testNovel.setTitle("Wallet Test Novel");
        testNovel.setAuthor("Wallet Author");
        testNovel = novelRepository.save(testNovel);

        // Create Chapter
        testChapter = new Chapter();
        testChapter.setNovel(testNovel);
        testChapter.setTitle("Unlocked Chapter");
        testChapter.setChapterNumber(1.0);
        testChapter.setPrice(15);
        testChapter = chapterRepository.save(testChapter);

        // Create Purchase
        testPurchase = new Purchase(null, testUser.getId(), testChapter.getId(), LocalDateTime.now());
        testPurchase = purchaseRepository.save(testPurchase);

        // Session setup
        session = new MockHttpSession();
        session.setAttribute("user", testUser);
    }

    @Test
    public void testGetUserProfileSuccess() throws Exception {
        mockMvc.perform(get("/api/user/profile")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testUser.getId().intValue()))
                .andExpect(jsonPath("$.name").value("Wallet Owner"))
                .andExpect(jsonPath("$.balance").value(250));
    }

    @Test
    public void testGetUserProfileUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/profile")) // No session
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Please log in first."));
    }

    @Test
    public void testGetUserPurchasesSuccess() throws Exception {
        mockMvc.perform(get("/api/user/purchases")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].novelTitle").value("Wallet Test Novel"))
                .andExpect(jsonPath("$[0].chapterNumber").value(1.0))
                .andExpect(jsonPath("$[0].price").value(15))
                .andExpect(jsonPath("$[0].chapterTitle").value("Unlocked Chapter"));
    }

    @Test
    public void testGetUserPurchasesUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/purchases")) // No session
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Please log in first."));
    }
}
