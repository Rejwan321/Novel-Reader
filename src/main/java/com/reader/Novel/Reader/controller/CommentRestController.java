package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.model.Comment;
import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.repository.CommentRepository;
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

    @GetMapping("/chapters/{chapterId}/comments")
    public ResponseEntity<List<Comment>> getComments(@PathVariable Long chapterId) {
        List<Comment> comments = commentRepository.findByChapterIdOrderByCreatedAtAsc(chapterId);
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
