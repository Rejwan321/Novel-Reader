package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.model.Novel;
import com.reader.Novel.Reader.model.Bookmark;
import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.service.NovelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api")
public class NovelRestController {

    @Autowired
    private NovelService novelService;

    @Autowired
    private com.reader.Novel.Reader.service.UserService userService;

    @GetMapping("/novels")
    public List<Novel> getNovels(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            HttpSession session) {
        
        if (isRestricted(session)) {
            return java.util.Collections.emptyList();
        }
        
        List<Novel> results;
        if (search != null && !search.trim().isEmpty()) {
            results = novelService.searchNovels(search.trim());
        } else {
            results = novelService.getAllNovels();
        }
        
        if (type != null && !type.trim().isEmpty() && !"ALL".equalsIgnoreCase(type)) {
            results = results.stream()
                .filter(n -> type.equalsIgnoreCase(n.getType()))
                .toList();
        }
        
        if (genre != null && !genre.trim().isEmpty() && !"ALL".equalsIgnoreCase(genre)) {
            results = results.stream()
                .filter(n -> n.getGenre() != null && java.util.Arrays.stream(n.getGenre().split(","))
                    .map(String::trim)
                    .anyMatch(g -> genre.equalsIgnoreCase(g)))
                .toList();
        }
        
        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            results = results.stream()
                .filter(n -> status.equalsIgnoreCase(n.getStatus()))
                .toList();
        }
        
        return results;
    }

    private boolean isRestricted(HttpSession session) {
        if (novelService.isSecuredMode()) {
            User loggedInUser = (User) session.getAttribute("user");
            return loggedInUser == null || !"OWNER".equals(loggedInUser.getUser_type());
        }
        return false;
    }

    @PostMapping("/bookmarks/toggle")
    public ResponseEntity<?> toggleBookmark(@RequestParam Long novelId, HttpSession session) {
        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please login first."));
        }
        
        Bookmark bookmark = novelService.toggleBookmark(loggedInUser.getId(), novelId);
        boolean isBookmarked = (bookmark != null);
        
        Map<String, Object> response = new HashMap<>();
        response.put("bookmarked", isBookmarked);
        response.put("reading", isBookmarked && bookmark.getLastReadChapterNumber() > 0.0);
        response.put("message", isBookmarked ? "Added to bookshelf" : "Removed from bookshelf");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/bookmarks/progress")
    public ResponseEntity<?> updateProgress(@RequestParam Long novelId, @RequestParam Double chapterNumber, HttpSession session) {
        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please login first."));
        }
        
        Bookmark bookmark = novelService.updateBookmarkProgress(loggedInUser.getId(), novelId, chapterNumber);
        Double progressVal = bookmark != null ? bookmark.getLastReadChapterNumber() : chapterNumber;
        return ResponseEntity.ok(Map.of("success", true, "progress", progressVal));
    }

    @PostMapping("/chapters/{chapterId}/purchase")
    public ResponseEntity<?> purchaseChapter(@PathVariable Long chapterId, HttpSession session) {
        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please login first."));
        }

        com.reader.Novel.Reader.model.Chapter chapter = novelService.getChapterById(chapterId);
        if (chapter == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Chapter not found."));
        }

        if (chapter.getPrice() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "This chapter is free."));
        }

        // Check if user already purchased
        if (novelService.hasPurchased(loggedInUser.getId(), chapterId)) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Chapter already purchased."));
        }

        try {
            novelService.executePurchase(loggedInUser.getId(), chapterId, chapter.getPrice());
            
            User updatedUser = userService.getUserById(loggedInUser.getId());
            loggedInUser.setBalance(updatedUser.getBalance());
            session.setAttribute("user", loggedInUser);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Chapter unlocked successfully!",
                "newBalance", updatedUser.getBalance()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Transaction failed: " + e.getMessage()));
        }
    }

    @PostMapping("/novels/{novelId}/rate")
    public ResponseEntity<?> rateNovel(
            @PathVariable Long novelId,
            @RequestParam Integer ratingValue,
            HttpSession session) {
        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please login first."));
        }

        if (ratingValue == null || ratingValue < 1 || ratingValue > 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "Rating must be between 1 and 5."));
        }

        Novel novel = novelService.getNovelById(novelId);
        if (novel == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Story not found."));
        }

        novelService.submitRating(loggedInUser.getId(), novelId, ratingValue);
        Novel updated = novelService.getNovelById(novelId);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Thank you for rating!",
            "newRating", updated.getRating()
        ));
    }

    @PostMapping("/user/purchase-flakes")
    public ResponseEntity<?> purchaseFlakes(@RequestParam Integer amount, HttpSession session) {
        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please login first."));
        }
        
        if (amount == null || amount <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid purchase amount."));
        }
        
        User user = userService.getUserById(loggedInUser.getId());
        if (user.getId() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found."));
        }
        
        user.setBalance((user.getBalance() != null ? user.getBalance() : 0) + amount);
        userService.updateUser(user);
        
        loggedInUser.setBalance(user.getBalance());
        session.setAttribute("user", loggedInUser);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "newBalance", user.getBalance(),
            "message", "Successfully purchased " + amount + " Snow Flakes!"
        ));
    }
}
