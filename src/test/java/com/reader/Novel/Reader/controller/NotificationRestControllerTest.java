package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.model.*;
import com.reader.Novel.Reader.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class NotificationRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private User readerUser;
    private User adminUser;
    private Novel testNovel;
    private Chapter testChapter;
    private MockHttpSession readerSession;
    private MockHttpSession adminSession;

    @BeforeEach
    public void setup() {
        jdbcTemplate.execute("ALTER TABLE reader_internal ALTER COLUMN id RESTART WITH 100");

        readerUser = new User();
        readerUser.setName("Reader User");
        readerUser.setEmail("reader@example.com");
        readerUser.setPassword("password123");
        readerUser.setUser_type("READER");
        readerUser = userRepository.save(readerUser);

        adminUser = new User();
        adminUser.setName("Admin User");
        adminUser.setEmail("admin@example.com");
        adminUser.setPassword("password123");
        adminUser.setUser_type("ADMIN");
        adminUser = userRepository.save(adminUser);

        testNovel = new Novel();
        testNovel.setTitle("Test Novel");
        testNovel.setAuthor("Test Author");
        testNovel.setDescription("A test novel description");
        testNovel.setType("NOVEL");
        testNovel.setGenre("Fantasy");
        testNovel.setRating(4.5);
        testNovel.setStatus("ONGOING");
        testNovel = novelRepository.save(testNovel);

        testChapter = new Chapter();
        testChapter.setNovel(testNovel);
        testChapter.setTitle("Chapter 1");
        testChapter.setChapterNumber(1.0);
        testChapter.setContent("Chapter content text");
        testChapter = chapterRepository.save(testChapter);

        readerSession = new MockHttpSession();
        readerSession.setAttribute("user", readerUser);

        adminSession = new MockHttpSession();
        adminSession.setAttribute("user", adminUser);
    }

    @Test
    public void testMentionCreatesNotification() throws Exception {
        // 1. Post comment containing "@admin"
        mockMvc.perform(post("/api/chapters/" + testChapter.getId() + "/comments")
                .session(readerSession)
                .param("content", "Hey @admin, check this chapter!"))
                .andExpect(status().isOk());

        // Verify notification was created
        List<Notification> notifications = notificationRepository.findAll();
        assertEquals(1, notifications.size());
        
        Notification notif = notifications.get(0);
        assertEquals("Reader User", notif.getMentionerName());
        assertEquals("Hey @admin, check this chapter!", notif.getSnippet());
        assertEquals(testNovel.getId(), notif.getNovelId());
        assertEquals(testNovel.getTitle(), notif.getNovelTitle());
        assertEquals(1.0, notif.getChapterNumber());
        assertEquals(testChapter.getId(), notif.getChapterId());
        assertFalse(notif.isRead());
    }

    @Test
    public void testMentionInReplyCreatesNotification() throws Exception {
        Comment parent = new Comment(testChapter.getId(), readerUser, "Nice chapter");
        parent = commentRepository.save(parent);

        // Post a reply containing "@System Admin"
        mockMvc.perform(post("/api/comments/" + parent.getId() + "/reply")
                .session(readerSession)
                .param("content", "I agree, @System Admin see this."))
                .andExpect(status().isOk());

        List<Notification> notifications = notificationRepository.findAll();
        assertEquals(1, notifications.size());
        
        Notification notif = notifications.get(0);
        assertEquals("Reader User", notif.getMentionerName());
        assertEquals("I agree, @System Admin see this.", notif.getSnippet());
        assertFalse(notif.isRead());
    }

    @Test
    public void testAccessControlForNotifications() throws Exception {
        Notification notif = new Notification(
            new Comment(testChapter.getId(), readerUser, "@admin hello"),
            "Reader User", "@admin hello", testNovel.getId(), testNovel.getTitle(), 1.0, testChapter.getId()
        );
        // Save dependency comment first
        Comment mockComment = new Comment(testChapter.getId(), readerUser, "@admin hello");
        mockComment = commentRepository.save(mockComment);
        notif.setComment(mockComment);
        notif = notificationRepository.save(notif);

        // 1. Try to fetch notifications as READER -> should be Forbidden
        mockMvc.perform(get("/api/admin/notifications")
                .session(readerSession))
                .andExpect(status().isForbidden());

        // 2. Try to fetch notifications as ADMIN -> should succeed
        mockMvc.perform(get("/api/admin/notifications")
                .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].snippet").value("@admin hello"));

        // 3. Try to toggle read status as READER -> should be Forbidden
        mockMvc.perform(put("/api/admin/notifications/" + notif.getId() + "/toggle-read")
                .session(readerSession))
                .andExpect(status().isForbidden());

        // 4. Try to toggle read status as ADMIN -> should succeed
        mockMvc.perform(put("/api/admin/notifications/" + notif.getId() + "/toggle-read")
                .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));

        // Verify status in DB
        assertTrue(notificationRepository.findById(notif.getId()).get().isRead());

        // 5. Try to delete notification as READER -> should be Forbidden
        mockMvc.perform(delete("/api/admin/notifications/" + notif.getId() + "/delete") // Wait, route is /api/admin/notifications/{id}
                .session(readerSession))
                .andExpect(status().isNotFound()); // because DELETE /api/admin/notifications/{id}/delete does not exist

        mockMvc.perform(delete("/api/admin/notifications/" + notif.getId())
                .session(readerSession))
                .andExpect(status().isForbidden());

        // 6. Try to delete notification as ADMIN -> should succeed
        mockMvc.perform(delete("/api/admin/notifications/" + notif.getId())
                .session(adminSession))
                .andExpect(status().isOk());

        // Verify notification is deleted
        assertFalse(notificationRepository.findById(notif.getId()).isPresent());
    }

    @Test
    public void testDeleteCommentCascadeDeletesNotification() throws Exception {
        Comment comment = new Comment(testChapter.getId(), readerUser, "Hey @admin inspect");
        comment = commentRepository.save(comment);

        Notification notif = new Notification(
            comment, "Reader User", "Hey @admin inspect", testNovel.getId(), testNovel.getTitle(), 1.0, testChapter.getId()
        );
        comment.getNotifications().add(notif);
        notif = notificationRepository.save(notif);

        // Verify they exist in database
        assertTrue(commentRepository.findById(comment.getId()).isPresent());
        assertTrue(notificationRepository.findById(notif.getId()).isPresent());

        // Delete comment
        commentRepository.delete(comment);

        entityManager.flush();
        entityManager.clear();

        // Verify comment and notification are gone
        assertFalse(commentRepository.findById(comment.getId()).isPresent());
        assertFalse(notificationRepository.findById(notif.getId()).isPresent());
    }
}
