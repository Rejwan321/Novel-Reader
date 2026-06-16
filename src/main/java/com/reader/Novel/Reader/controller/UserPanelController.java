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

    @GetMapping("/user/panel")
    public String userPanel(HttpSession session, Model model) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return "redirect:/?showLogin=true";
        }
        
        // Refresh user data from database to be absolutely current
        User freshUser = userRepository.findById(loggedInUser.getId()).orElse(loggedInUser);
        session.setAttribute("user", freshUser);
        
        model.addAttribute("currentUser", freshUser);
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
}
