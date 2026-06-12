package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.service.UserService;
import com.reader.Novel.Reader.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class UserController {

    @Autowired
    UserService service;

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null || (!"ADMIN".equals(loggedInUser.getUser_type()) && !"OWNER".equals(loggedInUser.getUser_type()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
        }
        boolean isOwner = "OWNER".equals(loggedInUser.getUser_type());
        return ResponseEntity.ok(service.getUsers().stream()
                .filter(u -> isOwner || !"OWNER".equals(u.getUser_type()))
                .collect(Collectors.toList()));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<?> getUserById(@PathVariable Long userId, HttpSession session){
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null || (!"ADMIN".equals(loggedInUser.getUser_type()) && !"OWNER".equals(loggedInUser.getUser_type()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
        }
        User u = service.getUserById(userId);
        if (u.getId() == null || "OWNER".equals(u.getUser_type())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found."));
        }
        return ResponseEntity.ok(u);
    }

    @PostMapping("/users")
    public ResponseEntity<?> addUser(@RequestBody User usr, HttpSession session){
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null || (!"ADMIN".equals(loggedInUser.getUser_type()) && !"OWNER".equals(loggedInUser.getUser_type()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
        }
        if ("OWNER".equals(usr.getUser_type())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot create owner."));
        }
        service.addUser(usr);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/users")
    public ResponseEntity<?> updateUser(@RequestBody User usr, HttpSession session){
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null || (!"ADMIN".equals(loggedInUser.getUser_type()) && !"OWNER".equals(loggedInUser.getUser_type()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
        }
        User target = service.getUserById(usr.getId());
        boolean isOwner = "OWNER".equals(loggedInUser.getUser_type());
        if (target != null && "OWNER".equals(target.getUser_type()) && !isOwner) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Cannot modify owner."));
        }
        if ("OWNER".equals(usr.getUser_type()) && !isOwner) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot promote to owner."));
        }
        service.updateUser(usr);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId, HttpSession session){
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null || (!"ADMIN".equals(loggedInUser.getUser_type()) && !"OWNER".equals(loggedInUser.getUser_type()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
        }
        User target = service.getUserById(userId);
        if (target != null && "OWNER".equals(target.getUser_type())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Cannot delete owner."));
        }
        service.deleteUser(userId);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
