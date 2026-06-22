package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.model.Notification;
import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.repository.NotificationRepository;
import com.reader.Novel.Reader.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class UserPanelController {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.reader.Novel.Reader.repository.SystemSettingRepository systemSettingRepository;

    @Autowired
    private com.reader.Novel.Reader.service.NovelService novelService;

    @GetMapping("/user/panel")
    public String userPanel(HttpSession session, Model model) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return "redirect:/?showLogin=true";
        }
        
        // Refresh user data from database to be absolutely current
        User freshUser = userRepository.findById(loggedInUser.getId()).orElse(loggedInUser);
        session.setAttribute("user", freshUser);
        
        String googleClientId = systemSettingRepository.findById("google.client_id")
                .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                .orElse("your-google-client-id");
        
        model.addAttribute("currentUser", freshUser);
        model.addAttribute("googleClientId", googleClientId);
        return "user_panel";
    }

    // GET all notifications for the logged-in user
    @GetMapping("/api/user/notifications")
    @ResponseBody
    public ResponseEntity<?> getUserNotifications(HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in first."));
        }
        List<Notification> notifs = notificationRepository.findByUserIdOrderByCreatedAtDesc(loggedInUser.getId());
        return ResponseEntity.ok(notifs);
    }

    // GET count of unread notifications for logged-in user
    @GetMapping("/api/user/notifications/unread-count")
    @ResponseBody
    public ResponseEntity<?> getUnreadCount(HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.ok(Map.of("count", 0));
        }
        long count = notificationRepository.countByUserIdAndIsRead(loggedInUser.getId(), false);
        return ResponseEntity.ok(Map.of("count", count));
    }

    // Toggle read status of a notification
    @PutMapping("/api/user/notifications/{id}/toggle-read")
    @ResponseBody
    public ResponseEntity<?> toggleReadStatus(@PathVariable Long id, HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in first."));
        }

        Optional<Notification> notifOpt = notificationRepository.findById(id);
        if (notifOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Notification not found."));
        }

        Notification notification = notifOpt.get();
        // Security check: ensure notification belongs to the logged-in user
        if (notification.getUserId() == null || !notification.getUserId().equals(loggedInUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
        }

        notification.setRead(!notification.isRead());
        notificationRepository.save(notification);
        return ResponseEntity.ok(Map.of("success", true, "isRead", notification.isRead()));
    }

    // DELETE a notification
    @DeleteMapping("/api/user/notifications/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteNotification(@PathVariable Long id, HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in first."));
        }

        Optional<Notification> notifOpt = notificationRepository.findById(id);
        if (notifOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Notification not found."));
        }

        Notification notification = notifOpt.get();
        // Security check
        if (notification.getUserId() == null || !notification.getUserId().equals(loggedInUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
        }

        notificationRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // POST update settings
    @PostMapping("/api/user/settings")
    @ResponseBody
    public ResponseEntity<?> updateSettings(
            @RequestParam(required = false) Boolean subscribedToUpdates,
            @RequestParam(required = false) Boolean subscribedToMentions,
            @RequestParam(required = false) String updatesEmail,
            HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in first."));
        }

        User user = userRepository.findById(loggedInUser.getId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found."));
        }

        if (subscribedToUpdates != null) {
            user.setSubscribedToUpdates(subscribedToUpdates);
        }
        if (subscribedToMentions != null) {
            user.setSubscribedToMentions(subscribedToMentions);
        }
        if (updatesEmail != null) {
            user.setUpdatesEmail(updatesEmail.trim());
        }

        userRepository.save(user);
        session.setAttribute("user", user);

        return ResponseEntity.ok(Map.of("success", true, "user", user));
    }

    @GetMapping("/api/user/profile")
    @ResponseBody
    public ResponseEntity<?> getUserProfile(HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in first."));
        }
        User freshUser = userRepository.findById(loggedInUser.getId()).orElse(loggedInUser);
        session.setAttribute("user", freshUser);
        return ResponseEntity.ok(freshUser);
    }

    @GetMapping("/api/user/purchases")
    @ResponseBody
    public ResponseEntity<?> getUserPurchases(HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in first."));
        }

        List<com.reader.Novel.Reader.model.Purchase> purchases = novelService.getPurchasesByUserId(loggedInUser.getId());
        List<Map<String, Object>> purchaseDetailsList = new java.util.ArrayList<>();
        for (com.reader.Novel.Reader.model.Purchase p : purchases) {
            com.reader.Novel.Reader.model.Chapter chapter = novelService.getChapterById(p.getChapterId());
            if (chapter != null) {
                com.reader.Novel.Reader.model.Novel novel = chapter.getNovel();
                Map<String, Object> details = new java.util.HashMap<>();
                details.put("id", p.getId());
                details.put("novelTitle", novel != null ? novel.getTitle() : "Unknown Story");
                details.put("novelId", novel != null ? novel.getId() : null);
                details.put("chapterNumber", chapter.getChapterNumber());
                details.put("chapterTitle", chapter.getTitle());
                details.put("price", chapter.getPrice());
                details.put("purchasedAt", p.getPurchasedAt());
                purchaseDetailsList.add(details);
            }
        }
        purchaseDetailsList.sort((a, b) -> {
            java.time.LocalDateTime dtA = (java.time.LocalDateTime) a.get("purchasedAt");
            java.time.LocalDateTime dtB = (java.time.LocalDateTime) b.get("purchasedAt");
            if (dtA == null || dtB == null) return 0;
            return dtB.compareTo(dtA);
        });

        return ResponseEntity.ok(purchaseDetailsList);
    }
}
