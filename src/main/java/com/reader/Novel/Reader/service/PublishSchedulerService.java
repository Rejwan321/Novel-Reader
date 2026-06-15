package com.reader.Novel.Reader.service;

import com.reader.Novel.Reader.model.*;
import com.reader.Novel.Reader.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PublishSchedulerService {

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EmailService emailService;

    @Value("${app.base-url:https://nazuna.dpdns.org}")
    private String baseUrl;

    @Scheduled(fixedRate = 30000) // Run every 30 seconds
    @Transactional
    public void checkScheduledChapters() {
        List<Chapter> unnotified = chapterRepository.findUnnotifiedChapters();
        LocalDateTime now = LocalDateTime.now();

        for (Chapter chapter : unnotified) {
            // Check if it is published (publishAt is null or in the past)
            if (chapter.getPublishAt() == null || !chapter.getPublishAt().isAfter(now)) {
                sendPublishNotifications(chapter);
            }
        }
    }

    private void sendPublishNotifications(Chapter chapter) {
        Novel novel = chapter.getNovel();
        if (novel == null) return;

        // Find all bookmarks for this novel
        List<Bookmark> bookmarks = bookmarkRepository.findByNovelId(novel.getId());

        for (Bookmark bookmark : bookmarks) {
            userRepository.findById(bookmark.getUserId()).ifPresent(user -> {
                // Check if user is subscribed to updates
                if (user.getSubscribedToUpdates()) {
                    // 1. Create a notification (comment is null)
                    Notification notification = new Notification(
                        novel.getAuthor() != null ? novel.getAuthor() : "System",
                        chapter.getTitle(),
                        novel.getId(),
                        novel.getTitle(),
                        chapter.getChapterNumber(),
                        chapter.getId(),
                        user.getId()
                    );
                    notificationRepository.save(notification);

                    // 2. Send email notification
                    String recipientEmail = (user.getUpdatesEmail() != null && !user.getUpdatesEmail().trim().isEmpty())
                            ? user.getUpdatesEmail() : user.getEmail();
                    String subject = "[Yuki Tales] New Chapter: " + novel.getTitle() + " - Chapter " + chapter.getChapterNumber();
                    String readLink = baseUrl + "/novel/" + novel.getId() + "/read/" + chapter.getChapterNumber();
                    String body = "Hello " + user.getName() + ",\n\n" +
                            "A new chapter has been published for a story on your bookshelf!\n\n" +
                            "Story: " + novel.getTitle() + "\n" +
                            "Chapter: " + chapter.getChapterNumber() + " - " + chapter.getTitle() + "\n\n" +
                            "Read it here: " + readLink + "\n\n" +
                            "Best regards,\n" +
                            "Yuki Tales Team";

                    emailService.sendCustomEmailAsync(recipientEmail, subject, body);
                }
            });
        }

        // Mark chapter as notified
        chapter.setPublishNotificationSent(true);
        chapterRepository.save(chapter);
    }
}
