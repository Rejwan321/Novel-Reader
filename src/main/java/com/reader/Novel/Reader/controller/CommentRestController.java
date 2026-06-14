package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.model.Chapter;
import com.reader.Novel.Reader.model.Comment;
import com.reader.Novel.Reader.model.Notification;
import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.repository.ChapterRepository;
import com.reader.Novel.Reader.repository.CommentRepository;
import com.reader.Novel.Reader.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    private void checkAndCreateNotification(Comment comment) {
        String content = comment.getContent();
        if (content == null) return;
        String lower = content.toLowerCase();
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
                    notificationRepository.save(notification);
                }
            });
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

        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Comment content cannot be empty."));
        }

        if (content.length() > 2000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Comment length cannot exceed 2000 characters."));
        }

        Comment comment = new Comment(chapterId, loggedInUser, content.trim());
        Comment saved = commentRepository.save(comment);
        checkAndCreateNotification(saved);
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
        
        commentRepository.save(comment);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "likes", comment.getLikedUserIds().size(),
            "dislikes", comment.getDislikedUserIds().size(),
            "liked", comment.getLikedUserIds().contains(userId),
            "disliked", comment.getDislikedUserIds().contains(userId)
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
        
        commentRepository.save(comment);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "likes", comment.getLikedUserIds().size(),
            "dislikes", comment.getDislikedUserIds().size(),
            "liked", comment.getLikedUserIds().contains(userId),
            "disliked", comment.getDislikedUserIds().contains(userId)
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
        
        boolean isAuthor = comment.getUser().getId().equals(loggedInUser.getId());
        boolean isAdminOrOwner = "ADMIN".equals(role) || "OWNER".equals(role);

        if (!isAuthor && !isAdminOrOwner) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not have permission to delete this comment."));
        }

        commentRepository.delete(comment);
        return ResponseEntity.ok(Map.of("success", true, "message", "Comment deleted successfully."));
    }
}
