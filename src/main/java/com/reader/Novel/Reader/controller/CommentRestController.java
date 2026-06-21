package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.model.Chapter;
import com.reader.Novel.Reader.model.Comment;
import com.reader.Novel.Reader.model.Notification;
import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.repository.ChapterRepository;
import com.reader.Novel.Reader.repository.CommentRepository;
import com.reader.Novel.Reader.repository.NotificationRepository;
import com.reader.Novel.Reader.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.reader.Novel.Reader.service.EmailService;
import com.reader.Novel.Reader.service.SseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class CommentRestController {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SseService sseService;

    @Value("${app.base-url:https://nazuna.dpdns.org}")
    private String baseUrl;

    private void checkAndCreateNotification(Comment comment) {
        String content = comment.getContent();
        if (content == null) return;
        String lower = content.toLowerCase();
        
        // Admin mentions
        if (lower.contains("@admin") || lower.contains("@system admin") || lower.contains("@systemadmin")) {
            chapterRepository.findById(comment.getChapterId()).ifPresent(chapter -> {
                com.reader.Novel.Reader.model.Novel novel = chapter.getNovel();
                if (novel != null) {
                    String snippet = content.length() > 100 ? content.substring(0, 97) + "..." : content;
                    Notification notification = new Notification(
                        comment,
                        comment.getUser().getName(),
                        snippet,
                        novel.getId(),
                        novel.getTitle(),
                        chapter.getChapterNumber(),
                        chapter.getId()
                    );
                    Notification savedNotif = notificationRepository.save(notification);
                    sseService.sendNotification(1L, savedNotif);

                    // Send Async Email Alert
                    String readLink = baseUrl + "/novel/" + novel.getId() + "/read/" + chapter.getChapterNumber();
                    emailService.sendMentionEmailAsync(
                        comment.getUser().getName(),
                        content,
                        novel.getTitle(),
                        chapter.getChapterNumber(),
                        readLink
                    );
                }
            });
        }

        // Mentions of other registered users
        List<User> allUsers = userRepository.findAll();
        for (User u : allUsers) {
            // Avoid notifying the author of the comment
            if (u.getId().equals(comment.getUser().getId())) continue;

            String nameMention = "@" + u.getName().toLowerCase();
            String emailMention = "@" + u.getEmail().toLowerCase();
            
            // Skip general admin role mentions that are already handled by the admin-mentions block above
            if (nameMention.equals("@admin") || nameMention.equals("@system admin") || nameMention.equals("@systemadmin")
                    || emailMention.equals("@admin") || emailMention.equals("@system admin") || emailMention.equals("@systemadmin")) {
                continue;
            }

            if (lower.contains(nameMention) || lower.contains(emailMention)) {
                chapterRepository.findById(comment.getChapterId()).ifPresent(chapter -> {
                    com.reader.Novel.Reader.model.Novel novel = chapter.getNovel();
                    if (novel != null) {
                        String snippet = content.length() > 100 ? content.substring(0, 97) + "..." : content;
                        Notification notification = new Notification(
                            comment,
                            comment.getUser().getName(),
                            snippet,
                            novel.getId(),
                            novel.getTitle(),
                            chapter.getChapterNumber(),
                            chapter.getId(),
                            u.getId()
                        );
                        Notification savedNotif = notificationRepository.save(notification);
                        sseService.sendNotification(u.getId(), savedNotif);

                        // If user is subscribed to mentions, send email notification
                        if (u.getSubscribedToMentions() == null || u.getSubscribedToMentions()) {
                            String targetEmail = (u.getUpdatesEmail() != null && !u.getUpdatesEmail().trim().isEmpty()) 
                                    ? u.getUpdatesEmail() : u.getEmail();
                            String readLink = baseUrl + "/novel/" + novel.getId() + "/read/" + chapter.getChapterNumber();

                            emailService.sendMentionEmailAsyncToRecipient(
                                targetEmail,
                                comment.getUser().getName(),
                                content,
                                novel.getTitle(),
                                chapter.getChapterNumber(),
                                readLink
                            );
                        }
                    }
                });
            }
        }
    }

    @GetMapping("/chapters/{chapterId}/comments")
    public ResponseEntity<List<Comment>> getComments(@PathVariable Long chapterId) {
        List<Comment> comments = commentRepository.findByChapterIdAndParentIsNullOrderByCreatedAtAsc(chapterId);
        return ResponseEntity.ok(comments);
    }

    @PostMapping("/chapters/{chapterId}/comments")
    public ResponseEntity<?> addComment(
            @PathVariable Long chapterId,
            @RequestParam String content,
            HttpSession session) {
        
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please login to post comments."));
        }

        java.util.Optional<Comment> lastCommentOpt = commentRepository.findFirstByUserIdOrderByCreatedAtDesc(loggedInUser.getId());
        if (lastCommentOpt.isPresent()) {
            java.time.LocalDateTime fiveMinutesAgo = java.time.LocalDateTime.now().minusMinutes(5);
            if (lastCommentOpt.get().getCreatedAt().isAfter(fiveMinutesAgo)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("error", "You can only post one comment or reply every 5 minutes."));
            }
        }

        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Comment content cannot be empty."));
        }

        if (content.length() > 2000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Comment length cannot exceed 2000 characters."));
        }

        Comment comment = new Comment(chapterId, loggedInUser, content.trim());
        Comment saved = commentRepository.save(comment);
        checkAndCreateNotification(saved);
        sseService.sendCommentEvent(chapterId, "comment_created", saved);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/comments/{commentId}/like")
    public ResponseEntity<?> likeComment(@PathVariable Long commentId, HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please login to like comments."));
        }
        
        Optional<Comment> commentOpt = commentRepository.findById(commentId);
        if (commentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Comment not found."));
        }
        
        Comment comment = commentOpt.get();
        Long userId = loggedInUser.getId();
        
        if (comment.getLikedUserIds().contains(userId)) {
            comment.getLikedUserIds().remove(userId);
        } else {
            comment.getLikedUserIds().add(userId);
            comment.getDislikedUserIds().remove(userId);
        }
        
        Comment saved = commentRepository.save(comment);
        Map<String, Object> likeEvent = Map.of(
            "commentId", saved.getId(),
            "likes", saved.getLikedUserIds().size(),
            "dislikes", saved.getDislikedUserIds().size()
        );
        sseService.sendCommentEvent(saved.getChapterId(), "comment_liked", likeEvent);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "likes", saved.getLikedUserIds().size(),
            "dislikes", saved.getDislikedUserIds().size(),
            "liked", saved.getLikedUserIds().contains(userId),
            "disliked", saved.getDislikedUserIds().contains(userId)
        ));
    }

    @PostMapping("/comments/{commentId}/dislike")
    public ResponseEntity<?> dislikeComment(@PathVariable Long commentId, HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please login to dislike comments."));
        }
        
        Optional<Comment> commentOpt = commentRepository.findById(commentId);
        if (commentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Comment not found."));
        }
        
        Comment comment = commentOpt.get();
        Long userId = loggedInUser.getId();
        
        if (comment.getDislikedUserIds().contains(userId)) {
            comment.getDislikedUserIds().remove(userId);
        } else {
            comment.getDislikedUserIds().add(userId);
            comment.getLikedUserIds().remove(userId);
        }
        
        Comment saved = commentRepository.save(comment);
        Map<String, Object> likeEvent = Map.of(
            "commentId", saved.getId(),
            "likes", saved.getLikedUserIds().size(),
            "dislikes", saved.getDislikedUserIds().size()
        );
        sseService.sendCommentEvent(saved.getChapterId(), "comment_liked", likeEvent);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "likes", saved.getLikedUserIds().size(),
            "dislikes", saved.getDislikedUserIds().size(),
            "liked", saved.getLikedUserIds().contains(userId),
            "disliked", saved.getDislikedUserIds().contains(userId)
        ));
    }

    @PostMapping("/comments/{commentId}/reply")
    public ResponseEntity<?> addReply(
            @PathVariable Long commentId,
            @RequestParam String content,
            HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please login to post replies."));
        }

        java.util.Optional<Comment> lastCommentOpt = commentRepository.findFirstByUserIdOrderByCreatedAtDesc(loggedInUser.getId());
        if (lastCommentOpt.isPresent()) {
            java.time.LocalDateTime fiveMinutesAgo = java.time.LocalDateTime.now().minusMinutes(5);
            if (lastCommentOpt.get().getCreatedAt().isAfter(fiveMinutesAgo)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("error", "You can only post one comment or reply every 5 minutes."));
            }
        }
        
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Reply content cannot be empty."));
        }

        if (content.length() > 2000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Reply length cannot exceed 2000 characters."));
        }

        Optional<Comment> parentOpt = commentRepository.findById(commentId);
        if (parentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Parent comment not found."));
        }

        Comment parent = parentOpt.get();
        Comment reply = new Comment(parent.getChapterId(), loggedInUser, content.trim());
        reply.setParent(parent);
        parent.getReplies().add(reply);
        
        Comment saved = commentRepository.save(reply);
        checkAndCreateNotification(saved);
        Map<String, Object> replyEvent = Map.of(
            "parentId", commentId,
            "reply", saved
        );
        sseService.sendCommentEvent(saved.getChapterId(), "reply_created", replyEvent);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable Long commentId, HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please login to delete comments."));
        }

        Optional<Comment> commentOpt = commentRepository.findById(commentId);
        if (commentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Comment not found."));
        }

        Comment comment = commentOpt.get();
        String role = loggedInUser.getUser_type();
        
        boolean isAdminOrOwner = "ADMIN".equals(role) || "OWNER".equals(role);

        if (!isAdminOrOwner) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not have permission to delete this comment. Only admins can delete comments."));
        }

        Long chapterId = comment.getChapterId();
        Long commentIdVal = comment.getId();
        commentRepository.delete(comment);
        sseService.sendCommentEvent(chapterId, "comment_deleted", Map.of("commentId", commentIdVal));
        return ResponseEntity.ok(Map.of("success", true, "message", "Comment deleted successfully."));
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<?> editComment(
            @PathVariable Long commentId,
            @RequestParam String content,
            HttpSession session) {
        
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please login to edit comments."));
        }

        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Comment content cannot be empty."));
        }

        if (content.length() > 2000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Comment length cannot exceed 2000 characters."));
        }

        Optional<Comment> commentOpt = commentRepository.findById(commentId);
        if (commentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Comment not found."));
        }

        Comment comment = commentOpt.get();
        
        // Verify user has permission to edit (must be the author)
        if (!comment.getUser().getId().equals(loggedInUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not have permission to edit this comment."));
        }

        comment.setContent(content.trim());
        Comment saved = commentRepository.save(comment);
        sseService.sendCommentEvent(saved.getChapterId(), "comment_edited", saved);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/comments/{commentId}/report")
    public ResponseEntity<?> reportComment(
            @PathVariable Long commentId,
            HttpSession session) {
        
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please login to report comments."));
        }

        Optional<Comment> commentOpt = commentRepository.findById(commentId);
        if (commentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Comment not found."));
        }

        Comment comment = commentOpt.get();
        
        // Increment reportsCount
        int currentCount = comment.getReportsCount() != null ? comment.getReportsCount() : 0;
        comment.setReportsCount(currentCount + 1);
        commentRepository.save(comment);

        // Fetch Novel and Chapter to get context for email
        chapterRepository.findById(comment.getChapterId()).ifPresent(chapter -> {
            com.reader.Novel.Reader.model.Novel novel = chapter.getNovel();
            if (novel != null) {
                String readLink = baseUrl + "/novel/" + novel.getId() + "/read/" + chapter.getChapterNumber();
                emailService.sendCommentReportEmailAsync(
                    loggedInUser.getName(),
                    comment.getUser().getName(),
                    comment.getContent(),
                    novel.getTitle(),
                    chapter.getChapterNumber(),
                    readLink,
                    comment.getId()
                );
            }
        });

        return ResponseEntity.ok(Map.of("success", true, "message", "Comment reported successfully."));
    }
}
