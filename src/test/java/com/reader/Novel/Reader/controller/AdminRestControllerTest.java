package com.reader.Novel.Reader.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reader.Novel.Reader.model.Novel;
import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.repository.NovelRepository;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AdminRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private User adminUser;
    private User editorUser1;
    private User editorUser2;
    private User readerUser;

    private Novel novel1;
    private Novel novel2;
    private Novel novel3;

    private MockHttpSession adminSession;
    private MockHttpSession editorSession1;
    private MockHttpSession editorSession2;
    private MockHttpSession readerSession;

    @BeforeEach
    public void setup() {
        jdbcTemplate.execute("ALTER TABLE reader_internal ALTER COLUMN id RESTART WITH 300");

        // 1. Setup Users
        adminUser = new User();
        adminUser.setName("Admin User");
        adminUser.setEmail("admin@example.com");
        adminUser.setPassword("password");
        adminUser.setUser_type("ADMIN");
        adminUser = userRepository.save(adminUser);

        editorUser1 = new User();
        editorUser1.setName("Editor User 1");
        editorUser1.setEmail("editor1@example.com");
        editorUser1.setPassword("password");
        editorUser1.setUser_type("EDITOR");
        editorUser1 = userRepository.save(editorUser1);

        editorUser2 = new User();
        editorUser2.setName("Editor User 2");
        editorUser2.setEmail("editor2@example.com");
        editorUser2.setPassword("password");
        editorUser2.setUser_type("EDITOR");
        editorUser2 = userRepository.save(editorUser2);

        readerUser = new User();
        readerUser.setName("Reader User");
        readerUser.setEmail("reader@example.com");
        readerUser.setPassword("password");
        readerUser.setUser_type("READER");
        readerUser = userRepository.save(readerUser);

        // 2. Setup Novels
        novel1 = new Novel();
        novel1.setTitle("Novel 1");
        novel1.setAuthor("Author 1");
        novel1.setCreatorId(editorUser1.getId());
        novel1 = novelRepository.save(novel1);

        novel2 = new Novel();
        novel2.setTitle("Novel 2");
        novel2.setAuthor("Author 2");
        novel2.setCreatorId(editorUser1.getId());
        novel2 = novelRepository.save(novel2);

        novel3 = new Novel();
        novel3.setTitle("Novel 3");
        novel3.setAuthor("Author 3");
        novel3.setCreatorId(editorUser2.getId());
        novel3 = novelRepository.save(novel3);

        // 3. Setup Sessions
        adminSession = new MockHttpSession();
        adminSession.setAttribute("user", adminUser);

        editorSession1 = new MockHttpSession();
        editorSession1.setAttribute("user", editorUser1);

        editorSession2 = new MockHttpSession();
        editorSession2.setAttribute("user", editorUser2);

        readerSession = new MockHttpSession();
        readerSession.setAttribute("user", readerUser);
    }

    @Test
    public void testBulkDeleteAsAdmin() throws Exception {
        List<Long> ids = Arrays.asList(novel1.getId(), novel2.getId(), novel3.getId());

        mockMvc.perform(delete("/api/admin/stories/bulk")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Selected stories deleted successfully."));

        // Verify deletion
        assertFalse(novelRepository.findById(novel1.getId()).isPresent());
        assertFalse(novelRepository.findById(novel2.getId()).isPresent());
        assertFalse(novelRepository.findById(novel3.getId()).isPresent());
    }

    @Test
    public void testBulkDeleteAsEditorOwnStories() throws Exception {
        // Editor 1 owns novel1 and novel2
        List<Long> ids = Arrays.asList(novel1.getId(), novel2.getId());

        mockMvc.perform(delete("/api/admin/stories/bulk")
                .session(editorSession1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertFalse(novelRepository.findById(novel1.getId()).isPresent());
        assertFalse(novelRepository.findById(novel2.getId()).isPresent());
        assertTrue(novelRepository.findById(novel3.getId()).isPresent());
    }

    @Test
    public void testBulkDeleteAsEditorUnauthorizedStory() throws Exception {
        // Editor 1 tries to delete novel3 (owned by Editor 2) along with novel1
        List<Long> ids = Arrays.asList(novel1.getId(), novel3.getId());

        mockMvc.perform(delete("/api/admin/stories/bulk")
                .session(editorSession1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You do not own story: Novel 3"));

        // Verify none were deleted (all-or-nothing transactional validation)
        assertTrue(novelRepository.findById(novel1.getId()).isPresent());
        assertTrue(novelRepository.findById(novel3.getId()).isPresent());
    }

    @Test
    public void testBulkDeleteAsReaderForbidden() throws Exception {
        List<Long> ids = Arrays.asList(novel1.getId());

        mockMvc.perform(delete("/api/admin/stories/bulk")
                .session(readerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only admins, editors, and proofreaders can delete stories."));
    }

    @Test
    public void testBulkDeleteWithInvalidId() throws Exception {
        List<Long> ids = Arrays.asList(novel1.getId(), 9999L);

        mockMvc.perform(delete("/api/admin/stories/bulk")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Story with ID 9999 not found."));

        // Verify no stories were deleted
        assertTrue(novelRepository.findById(novel1.getId()).isPresent());
    }

    @Test
    public void testBulkDeleteWithEmptyList() throws Exception {
        mockMvc.perform(delete("/api/admin/stories/bulk")
                .session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Collections.emptyList())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No story IDs provided."));
    }
}
