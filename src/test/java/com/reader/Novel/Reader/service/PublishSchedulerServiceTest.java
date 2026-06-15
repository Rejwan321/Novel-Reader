package com.reader.Novel.Reader.service;

import com.reader.Novel.Reader.model.*;
import com.reader.Novel.Reader.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class PublishSchedulerServiceTest {

    @Autowired
    private PublishSchedulerService publishSchedulerService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @org.junit.jupiter.api.BeforeEach
    public void setup() {
        jdbcTemplate.execute("ALTER TABLE reader_internal ALTER COLUMN id RESTART WITH 200");
    }

    @Test
    public void testScheduledChapterPublishNotification() {
        // 1. Clear database status or fetch initial count
        int initialNotifications = notificationRepository.findAll().size();

        // 2. Create User subscribed to updates
        User user = new User();
        user.setName("Subscriber Reader");
        user.setEmail("subscriber@example.com");
        user.setPassword("pass123");
        user.setUser_type("READER");
        user.setSubscribedToUpdates(true);
        user = userRepository.save(user);

        // 3. Create Novel
        Novel novel = new Novel();
        novel.setTitle("Scheduled Story");
        novel.setAuthor("Famous Author");
        novel.setDescription("Interesting description");
        novel.setType("NOVEL");
        novel.setGenre("Fantasy");
        novel.setRating(4.8);
        novel.setStatus("ONGOING");
        novel = novelRepository.save(novel);

        // 4. Bookmark Novel for User
        Bookmark bookmark = new Bookmark();
        bookmark.setUserId(user.getId());
        bookmark.setNovelId(novel.getId());
        bookmark.setUpdatedAt(LocalDateTime.now());
        bookmarkRepository.save(bookmark);

        // 5. Create Chapter with publishAt in the past and publishNotificationSent = false
        Chapter chapter = new Chapter();
        chapter.setNovel(novel);
        chapter.setTitle("Secret Techniques");
        chapter.setChapterNumber(1.0);
        chapter.setContent("Engaging story content...");
        chapter.setPublishAt(LocalDateTime.now().minusMinutes(5));
        chapter.setPublishNotificationSent(false);
        chapter = chapterRepository.save(chapter);

        // 6. Trigger scheduler logic
        publishSchedulerService.checkScheduledChapters();

        // 7. Verify notifications
        List<Notification> notifications = notificationRepository.findAll();
        assertEquals(initialNotifications + 1, notifications.size());

        // Find the newly created notification
        final Long finalUserId = user.getId();
        Notification latest = notifications.stream()
            .filter(n -> finalUserId.equals(n.getUserId()))
            .findFirst()
            .orElse(null);
        
        assertNotNull(latest);
        assertNull(latest.getComment()); // Must be null for publish notifications
        assertEquals("Famous Author", latest.getMentionerName());
        assertEquals("Secret Techniques", latest.getSnippet());
        assertEquals(novel.getId(), latest.getNovelId());
        assertEquals(novel.getTitle(), latest.getNovelTitle());
        assertEquals(1.0, latest.getChapterNumber());
        assertEquals(chapter.getId(), latest.getChapterId());

        // 8. Verify chapter flag updated
        Chapter updatedChapter = chapterRepository.findById(chapter.getId()).orElse(null);
        assertNotNull(updatedChapter);
        assertTrue(updatedChapter.getPublishNotificationSent());
    }
}
