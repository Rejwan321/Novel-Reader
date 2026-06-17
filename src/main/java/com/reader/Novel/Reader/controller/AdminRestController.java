package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.model.Novel;
import com.reader.Novel.Reader.model.Chapter;
import com.reader.Novel.Reader.model.Purchase;
import com.reader.Novel.Reader.model.FlakePackage;
import com.reader.Novel.Reader.model.Review;
import com.reader.Novel.Reader.model.Notification;
import com.reader.Novel.Reader.repository.ReviewRepository;
import com.reader.Novel.Reader.repository.NotificationRepository;
import com.reader.Novel.Reader.service.UserService;
import com.reader.Novel.Reader.service.NovelService;
import com.reader.Novel.Reader.service.SseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminRestController {

    @Autowired
    private UserService userService;

    @Autowired
    private NovelService novelService;

    @Autowired
    private SseService sseService;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private com.reader.Novel.Reader.repository.SystemSettingRepository systemSettingRepository;

    @Autowired
    private com.reader.Novel.Reader.service.EmailService emailService;

    private boolean isRestricted(HttpSession session) {
        if (novelService.isSecuredMode()) {
            User loggedInUser = (User) session.getAttribute("user");
            return loggedInUser == null || !"OWNER".equals(loggedInUser.getUser_type());
        }
        return false;
    }

    @PostMapping("/self-destruct")
    public ResponseEntity<?> toggleSelfDestruct(HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }
        if (!"OWNER".equals(loggedInUser.getUser_type())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the owner can trigger self-destruct."));
        }
        novelService.toggleSecuredMode();
        boolean current = novelService.isSecuredMode();
        return ResponseEntity.ok(Map.of("success", true, "securedMode", current));
    }

    @GetMapping("/self-destruct/status")
    public ResponseEntity<?> getSelfDestructStatus(HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }
        if (!"OWNER".equals(loggedInUser.getUser_type())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
        }
        return ResponseEntity.ok(Map.of("securedMode", novelService.isSecuredMode()));
    }

    // 1. Promote/Demote User Role (ADMIN only)
    @PostMapping("/users/{id}/role")
    public ResponseEntity<?> updateUserRole(
            @PathVariable Long id,
            @RequestParam String role,
            HttpSession session) {

        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        if (!"ADMIN".equals(loggedInUser.getUser_type()) && !"OWNER".equals(loggedInUser.getUser_type())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only administrators can modify roles."));
        }

        User userToModify = userService.getUserById(id);
        if (userToModify.getId() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found."));
        }

        // Protect OWNER accounts from being modified
        if ("OWNER".equals(userToModify.getUser_type()) && !"OWNER".equals(loggedInUser.getUser_type())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Cannot modify an owner account."));
        }

        // Prevent self-demotion
        if (loggedInUser.getId().equals(userToModify.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "You cannot demote yourself."));
        }

        // Block assigning the OWNER role
        if ("OWNER".equals(role)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot assign the owner role."));
        }

        // Validate role input
        if (!"READER".equals(role) && !"EDITOR".equals(role) && !"ADMIN".equals(role) && !"PROOFREADER".equals(role)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role specified."));
        }

        userToModify.setUser_type(role);
        userService.updateUser(userToModify);

        try {
            sseService.sendGlobalEvent("user_role_updated", Map.of("userId", id, "role", role));
        } catch (Exception e) {
            // Log or ignore
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "User role updated successfully."));
    }

    // 1.2. Create User Account (ADMIN only)
    @PostMapping("/users/add")
    public ResponseEntity<?> createUser(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String role,
            HttpSession session) {

        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        if (!"ADMIN".equals(loggedInUser.getUser_type()) && !"OWNER".equals(loggedInUser.getUser_type())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only administrators can create users."));
        }

        if (name == null || name.trim().isEmpty() ||
            email == null || email.trim().isEmpty() ||
            password == null || password.trim().isEmpty() ||
            role == null || role.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "All fields are required."));
        }

        java.util.Optional<User> existing = userService.getUserByEmail(email.trim());
        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email is already registered."));
        }

        // Block assigning the OWNER role
        if ("OWNER".equals(role)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot assign the owner role."));
        }

        // Validate role input
        if (!"READER".equals(role) && !"EDITOR".equals(role) && !"ADMIN".equals(role) && !"PROOFREADER".equals(role)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role specified."));
        }

        User user = new User(null, name.trim(), email.trim(), com.reader.Novel.Reader.util.PasswordUtils.hashPassword(password), role);
        userService.addUser(user);

        try {
            sseService.sendGlobalEvent("user_created", user);
        } catch (Exception e) {
            // Log or ignore
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "User account created successfully.", "user", user));
    }

    // 1.3. Update User Balance (ADMIN only)
    @PostMapping("/users/{id}/balance")
    public ResponseEntity<?> updateUserBalance(
            @PathVariable Long id,
            @RequestParam Integer balance,
            HttpSession session) {

        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        if (!"ADMIN".equals(loggedInUser.getUser_type()) && !"OWNER".equals(loggedInUser.getUser_type())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only administrators can modify user balances."));
        }

        if (balance == null || balance < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid balance value."));
        }

        User userToModify = userService.getUserById(id);
        if (userToModify.getId() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found."));
        }

        // Protect OWNER accounts from being modified
        if ("OWNER".equals(userToModify.getUser_type()) && !"OWNER".equals(loggedInUser.getUser_type())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Cannot modify an owner account."));
        }

        userToModify.setBalance(balance);
        userService.updateUser(userToModify);

        try {
            sseService.sendGlobalEvent("user_balance_updated", Map.of("userId", id, "balance", balance));
        } catch (Exception e) {
            // Log or ignore
        }

        // Update session if editing self
        if (loggedInUser.getId().equals(userToModify.getId())) {
            loggedInUser.setBalance(balance);
            session.setAttribute("user", loggedInUser);
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "User balance updated successfully."));
    }

    // 1.4. Edit User Details (ADMIN only)
    @PostMapping("/users/edit/{id}")
    public ResponseEntity<?> editUser(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam(required = false) String password,
            @RequestParam String role,
            @RequestParam(required = false) Long newUserId,
            HttpSession session) {

        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        if (!"ADMIN".equals(loggedInUser.getUser_type()) && !"OWNER".equals(loggedInUser.getUser_type())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only administrators can edit user accounts."));
        }

        User userToModify = userService.getUserById(id);
        if (userToModify.getId() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found."));
        }

        // Protect OWNER accounts from being modified by non-owners
        if ("OWNER".equals(userToModify.getUser_type()) && !"OWNER".equals(loggedInUser.getUser_type())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Cannot modify an owner account."));
        }

        // Prevent OWNER from demoting themselves or any OWNER demotion
        if ("OWNER".equals(userToModify.getUser_type()) && !"OWNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "You cannot demote an owner."));
        }

        // Prevent self-demotion for admin
        if (loggedInUser.getId().equals(userToModify.getId()) && !"OWNER".equals(loggedInUser.getUser_type()) && !"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "You cannot demote yourself."));
        }

        // Block assigning the OWNER role to non-owner accounts
        if ("OWNER".equals(role) && !"OWNER".equals(userToModify.getUser_type())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot assign the owner role."));
        }

        // Validate role input
        if (!"READER".equals(role) && !"EDITOR".equals(role) && !"ADMIN".equals(role) && !"PROOFREADER".equals(role) && !"OWNER".equals(role)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role specified."));
        }

        // Check if email already registered by another user
        java.util.Optional<User> existing = userService.getUserByEmail(email.trim());
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email is already registered by another user."));
        }

        // Handle primary key ID update if a new ID is provided and different from the current ID
        if (newUserId != null && !newUserId.equals(id)) {
            User existingUserWithNewId = userService.getUserById(newUserId);
            if (existingUserWithNewId.getId() != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "The ID " + newUserId + " is already in use by another user."));
            }

            userService.updateUserId(id, newUserId);

            // Update session if editing self
            if (loggedInUser.getId().equals(id)) {
                loggedInUser.setId(newUserId);
                session.setAttribute("user", loggedInUser);
            }

            // Fetch the updated user object using the new ID so subsequent updates apply to it
            userToModify = userService.getUserById(newUserId);
        }

        userToModify.setName(name.trim());
        userToModify.setEmail(email.trim());
        userToModify.setUser_type(role);
        if (password != null && !password.trim().isEmpty()) {
            userToModify.setPassword(com.reader.Novel.Reader.util.PasswordUtils.hashPassword(password.trim()));
        }

        userService.updateUser(userToModify);

        try {
            sseService.sendGlobalEvent("user_updated", Map.of("oldUserId", id, "user", userToModify));
        } catch (Exception e) {
            // Log or ignore
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "User details updated successfully."));
    }

    // 1.5. Delete User (ADMIN only)
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(
            @PathVariable Long id,
            HttpSession session) {

        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        if (!"ADMIN".equals(loggedInUser.getUser_type()) && !"OWNER".equals(loggedInUser.getUser_type())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only administrators can delete users."));
        }

        User userToDelete = userService.getUserById(id);
        if (userToDelete.getId() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found."));
        }

        // Protect OWNER accounts from being deleted
        if ("OWNER".equals(userToDelete.getUser_type())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Cannot delete an owner account."));
        }

        // Prevent self-deletion
        if (loggedInUser.getId().equals(userToDelete.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "You cannot delete yourself."));
        }

        userService.deleteUser(id);

        try {
            sseService.sendGlobalEvent("user_deleted", Map.of("userId", id));
        } catch (Exception e) {
            // Log or ignore to avoid blocking response on SSE failure
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "User deleted successfully."));
    }

    @PostMapping("/stories/add")
    public ResponseEntity<?> addStory(
            @RequestParam String title,
            @RequestParam String author,
            @RequestParam String description,
            @RequestParam String coverUrl,
            @RequestParam String type,
            @RequestParam String genre,
            @RequestParam Double rating,
            @RequestParam String status,
            HttpSession session) {

        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        String role = loggedInUser.getUser_type();
        if (!"ADMIN".equals(role) && !"EDITOR".equals(role) && !"PROOFREADER".equals(role) && !"OWNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only admins, editors, and proofreaders can create stories."));
        }

        if (title == null || title.trim().isEmpty() ||
            author == null || author.trim().isEmpty() ||
            coverUrl == null || coverUrl.trim().isEmpty() ||
            type == null || type.trim().isEmpty() ||
            genre == null || genre.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required story fields."));
        }

        String resolvedCoverUrl = handleLocalCoverUrl(coverUrl.trim());
        Novel novel = new Novel(null, title.trim(), author.trim(), description.trim(), resolvedCoverUrl, type.toUpperCase(), genre.trim(), rating, status);
        novel.setCreatorId(loggedInUser.getId());
        Novel saved = novelService.saveNovel(novel);
        syncNovelFoldersAndFiles(saved);

        try {
            sseService.sendGlobalEvent("story_created", Map.of(
                "id", saved.getId(),
                "title", saved.getTitle(),
                "author", saved.getAuthor(),
                "description", saved.getDescription(),
                "coverUrl", saved.getCoverUrl(),
                "type", saved.getType(),
                "genre", saved.getGenre(),
                "rating", saved.getRating(),
                "status", saved.getStatus(),
                "creatorId", saved.getCreatorId() != null ? saved.getCreatorId() : 0L
            ));
        } catch (Exception e) {
            // Log or ignore
        }

        return ResponseEntity.ok(Map.of("success", true, "novel", saved));
    }

    // 2.5. Upload story cover image (ADMIN & EDITOR)
    @PostMapping("/stories/upload-cover")
    public ResponseEntity<?> uploadCover(
            @RequestParam("file") MultipartFile file,
            HttpSession session) {

        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        String role = loggedInUser.getUser_type();
        if (!"ADMIN".equals(role) && !"EDITOR".equals(role) && !"PROOFREADER".equals(role) && !"OWNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only admins, editors, and proofreaders can upload covers."));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please select a file to upload."));
        }

        // Validate file type (image check)
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only image files are allowed."));
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueName = UUID.randomUUID().toString() + extension;

            String userDir = System.getProperty("user.dir");
            Path srcDir = Paths.get(userDir, "src", "main", "resources", "static", "uploads");
            Path rootUploadsDir = Paths.get(userDir, "uploads");

            // Create directories if they do not exist
            Files.createDirectories(srcDir);
            Files.createDirectories(rootUploadsDir);

            // Paths to files
            Path srcFile = srcDir.resolve(uniqueName);
            Path rootFile = rootUploadsDir.resolve(uniqueName);

            // Write files
            byte[] bytes = file.getBytes();
            Files.write(srcFile, bytes);
            Files.write(rootFile, bytes);

            return ResponseEntity.ok(Map.of("success", true, "url", "/uploads/" + uniqueName));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload file: " + e.getMessage()));
        }
    }

    // 2.7. Upload general image (cover or content) (ADMIN & EDITOR)
    @PostMapping("/upload-image")
    public ResponseEntity<?> uploadImage(
            @RequestParam("file") MultipartFile file,
            HttpSession session) {

        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        String role = loggedInUser.getUser_type();
        if (!"ADMIN".equals(role) && !"EDITOR".equals(role) && !"PROOFREADER".equals(role) && !"OWNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only admins, editors, and proofreaders can upload images."));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please select a file to upload."));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only image files are allowed."));
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueName = UUID.randomUUID().toString() + extension;

            String userDir = System.getProperty("user.dir");
            Path srcDir = Paths.get(userDir, "src", "main", "resources", "static", "uploads");
            Path rootUploadsDir = Paths.get(userDir, "uploads");

            Files.createDirectories(srcDir);
            Files.createDirectories(rootUploadsDir);

            Path srcFile = srcDir.resolve(uniqueName);
            Path rootFile = rootUploadsDir.resolve(uniqueName);

            byte[] bytes = file.getBytes();
            Files.write(srcFile, bytes);
            Files.write(rootFile, bytes);

            return ResponseEntity.ok(Map.of("success", true, "url", "/uploads/" + uniqueName));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload file: " + e.getMessage()));
        }
    }


    // 3. Add Chapter to Story (ADMIN & EDITOR)
    @PostMapping("/chapters/add")
    public ResponseEntity<?> addChapter(
            @RequestParam Long novelId,
            @RequestParam String title,
            @RequestParam Double chapterNumber,
            @RequestParam String content,
            @RequestParam(defaultValue = "0") Integer price,
            @RequestParam(required = false) String publishAt,
            HttpSession session) {

        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        String role = loggedInUser.getUser_type();
        if (!"ADMIN".equals(role) && !"EDITOR".equals(role) && !"PROOFREADER".equals(role) && !"OWNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only admins, editors, and proofreaders can add chapters."));
        }

        Novel novel = novelService.getNovelById(novelId);
        if (novel == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Story not found."));
        }

        if (!"ADMIN".equals(role) && !"PROOFREADER".equals(role) && !"OWNER".equals(role) && !loggedInUser.getId().equals(novel.getCreatorId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not own this story."));
        }

        if (title == null || title.trim().isEmpty() ||
            chapterNumber == null ||
            content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required chapter fields."));
        }

        // Check if chapter number already exists for this story to avoid duplication issues
        List<Chapter> existingChaps = novelService.getChaptersByNovelId(novelId);
        boolean duplicate = existingChaps.stream().anyMatch(c -> c.getChapterNumber().equals(chapterNumber));
        if (duplicate) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Chapter number " + chapterNumber + " already exists for this story."));
        }

        Chapter chapter = new Chapter(null, novel, title.trim(), chapterNumber, content.trim(), price);
        boolean canEditPublishAt = "ADMIN".equals(role) || "OWNER".equals(role) || "PROOFREADER".equals(role);
        if (canEditPublishAt && publishAt != null && !publishAt.trim().isEmpty()) {
            try {
                chapter.setPublishAt(java.time.LocalDateTime.parse(publishAt));
            } catch (Exception e) {
                // ignore parsing error
            }
        }
        Chapter saved = chapterServiceHelper(chapter);
        syncChapterFiles(saved);

        try {
            sseService.sendGlobalEvent("chapter_created", Map.of(
                "id", saved.getId(),
                "novelId", novelId,
                "title", saved.getTitle(),
                "chapterNumber", saved.getChapterNumber(),
                "price", saved.getPrice(),
                "publishAt", saved.getPublishAt() != null ? saved.getPublishAt().toString() : ""
            ));
        } catch (Exception e) {
            // Log or ignore
        }

        return ResponseEntity.ok(Map.of("success", true, "chapter", saved));
    }

    // 4. Retrieve chapters for a specific story series (ADMIN & EDITOR)
    @GetMapping("/stories/{id}/chapters")
    public ResponseEntity<?> getStoryChapters(@PathVariable Long id, HttpSession session) {
        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        String role = loggedInUser.getUser_type();
        if (!"ADMIN".equals(role) && !"EDITOR".equals(role) && !"PROOFREADER".equals(role) && !"OWNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
        }

        Novel novel = novelService.getNovelById(id);
        if (novel == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Story not found."));
        }

        if (!"ADMIN".equals(role) && !"PROOFREADER".equals(role) && !"OWNER".equals(role) && !loggedInUser.getId().equals(novel.getCreatorId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
        }

        List<Chapter> chapters = novelService.getChaptersByNovelId(id);
        return ResponseEntity.ok(chapters);
    }

    // 5. Edit story series (ADMIN & EDITOR)
    @PostMapping("/stories/edit/{id}")
    public ResponseEntity<?> editStory(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam String author,
            @RequestParam String description,
            @RequestParam String coverUrl,
            @RequestParam String type,
            @RequestParam String genre,
            @RequestParam Double rating,
            @RequestParam String status,
            HttpSession session) {

        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        String role = loggedInUser.getUser_type();
        if (!"ADMIN".equals(role) && !"EDITOR".equals(role) && !"PROOFREADER".equals(role) && !"OWNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only admins, editors, and proofreaders can edit stories."));
        }

        Novel existingNovel = novelService.getNovelById(id);
        if (existingNovel == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Story not found."));
        }

        if (!"ADMIN".equals(role) && !"PROOFREADER".equals(role) && !"OWNER".equals(role) && !loggedInUser.getId().equals(existingNovel.getCreatorId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not own this story."));
        }

        if (title == null || title.trim().isEmpty() ||
            author == null || author.trim().isEmpty() ||
            coverUrl == null || coverUrl.trim().isEmpty() ||
            type == null || type.trim().isEmpty() ||
            genre == null || genre.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields."));
        }

        existingNovel.setTitle(title.trim());
        existingNovel.setAuthor(author.trim());
        existingNovel.setDescription(description.trim());
        
        String resolvedCoverUrl = handleLocalCoverUrl(coverUrl.trim());
        existingNovel.setCoverUrl(resolvedCoverUrl);
        
        existingNovel.setType(type.toUpperCase());
        existingNovel.setGenre(genre.trim());
        existingNovel.setRating(rating);
        existingNovel.setStatus(status);

        Novel saved = novelService.saveNovel(existingNovel);
        syncNovelFoldersAndFiles(saved);
        try {
            sseService.sendGlobalEvent("story_updated", Map.of(
                "id", saved.getId(),
                "title", saved.getTitle(),
                "author", saved.getAuthor(),
                "description", saved.getDescription(),
                "coverUrl", saved.getCoverUrl(),
                "type", saved.getType(),
                "genre", saved.getGenre(),
                "rating", saved.getRating(),
                "status", saved.getStatus(),
                "creatorId", saved.getCreatorId() != null ? saved.getCreatorId() : 0L
            ));
        } catch (Exception e) {
            // Log or ignore
        }
        return ResponseEntity.ok(Map.of("success", true, "novel", saved));
    }

    // 6. Edit chapter (ADMIN & EDITOR)
    @PostMapping("/chapters/edit/{id}")
    public ResponseEntity<?> editChapter(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam Double chapterNumber,
            @RequestParam String content,
            @RequestParam(defaultValue = "0") Integer price,
            @RequestParam(required = false) String publishAt,
            HttpSession session) {

        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        String role = loggedInUser.getUser_type();
        if (!"ADMIN".equals(role) && !"EDITOR".equals(role) && !"PROOFREADER".equals(role) && !"OWNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only admins, editors, and proofreaders can edit chapters."));
        }

        Chapter existingChapter = novelService.getChapterById(id);
        if (existingChapter == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Chapter not found."));
        }

        if (!"ADMIN".equals(role) && !"PROOFREADER".equals(role) && !"OWNER".equals(role) && !loggedInUser.getId().equals(existingChapter.getNovel().getCreatorId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not own the parent story."));
        }

        if (title == null || title.trim().isEmpty() ||
            chapterNumber == null ||
            content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields."));
        }

        // Check if chapter number changed and if the new number already exists for this story (excluding itself)
        if (!existingChapter.getChapterNumber().equals(chapterNumber)) {
            List<Chapter> existingChaps = novelService.getChaptersByNovelId(existingChapter.getNovel().getId());
            boolean duplicate = existingChaps.stream()
                .anyMatch(c -> !c.getId().equals(id) && c.getChapterNumber().equals(chapterNumber));
            if (duplicate) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Chapter number " + chapterNumber + " already exists for this story."));
            }
        }

        existingChapter.setTitle(title.trim());
        existingChapter.setChapterNumber(chapterNumber);
        existingChapter.setContent(content.trim());
        existingChapter.setPrice(price);
        
        boolean canEditPublishAt = "ADMIN".equals(role) || "OWNER".equals(role) || "PROOFREADER".equals(role);
        if (canEditPublishAt) {
            if (publishAt != null && !publishAt.trim().isEmpty()) {
                try {
                    existingChapter.setPublishAt(java.time.LocalDateTime.parse(publishAt));
                } catch (Exception e) {
                    // ignore parsing error
                }
            } else {
                existingChapter.setPublishAt(null);
            }
        }

        Chapter saved = novelService.saveChapter(existingChapter);
        syncChapterFiles(saved);
        try {
            sseService.sendGlobalEvent("chapter_updated", Map.of(
                "id", saved.getId(),
                "novelId", saved.getNovel().getId(),
                "title", saved.getTitle(),
                "chapterNumber", saved.getChapterNumber(),
                "price", saved.getPrice(),
                "publishAt", saved.getPublishAt() != null ? saved.getPublishAt().toString() : ""
            ));
        } catch (Exception e) {
            // Log or ignore
        }
        return ResponseEntity.ok(Map.of("success", true, "chapter", saved));
    }

    // 7. Delete chapter (ADMIN & EDITOR)
    @DeleteMapping("/chapters/{id}")
    public ResponseEntity<?> deleteChapter(
            @PathVariable Long id,
            HttpSession session) {

        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        String role = loggedInUser.getUser_type();
        if (!"ADMIN".equals(role) && !"EDITOR".equals(role) && !"PROOFREADER".equals(role) && !"OWNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only admins, editors, and proofreaders can delete chapters."));
        }

        Chapter chapter = novelService.getChapterById(id);
        if (chapter == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Chapter not found."));
        }

        if (!"ADMIN".equals(role) && !"PROOFREADER".equals(role) && !"OWNER".equals(role) && !loggedInUser.getId().equals(chapter.getNovel().getCreatorId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not own the parent story."));
        }

        Long novelId = chapter.getNovel().getId();
        novelService.deleteChapter(id);
        try {
            sseService.sendGlobalEvent("chapter_deleted", Map.of(
                "chapterId", id,
                "novelId", novelId
            ));
        } catch (Exception e) {
            // Log or ignore
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Chapter deleted successfully."));
    }

    // 2.8. Delete story series (manga/manhwa/novel/comic) (ADMIN & EDITOR)
    @DeleteMapping("/stories/{id}")
    public ResponseEntity<?> deleteStory(
            @PathVariable Long id,
            HttpSession session) {

        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        String role = loggedInUser.getUser_type();
        if (!"ADMIN".equals(role) && !"EDITOR".equals(role) && !"PROOFREADER".equals(role) && !"OWNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only admins, editors, and proofreaders can delete stories."));
        }

        Novel novel = novelService.getNovelById(id);
        if (novel == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Story not found."));
        }

        if (!"ADMIN".equals(role) && !"PROOFREADER".equals(role) && !"OWNER".equals(role) && !loggedInUser.getId().equals(novel.getCreatorId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not own this story."));
        }

        novelService.deleteNovel(id);
        try {
            sseService.sendGlobalEvent("story_deleted", Map.of("storyId", id));
        } catch (Exception e) {
            // Log or ignore
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Story deleted successfully."));
    }

    // 9. Analytics Summary (ADMIN & EDITOR)
    @GetMapping("/analytics/summary")
    public ResponseEntity<?> getAnalyticsSummary(HttpSession session) {
        if (isRestricted(session)) {
            return ResponseEntity.ok(Map.of(
                "totalSales", 0,
                "totalRevenue", 0,
                "activeReaders", 0,
                "salesByStory", List.of(),
                "salesOverTime", Map.of(),
                "salesByEditor", List.of()
            ));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        String role = loggedInUser.getUser_type();
        if (!"ADMIN".equals(role) && !"EDITOR".equals(role) && !"OWNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
        }

        List<Novel> allNovels = novelService.getAllNovels();
        List<Chapter> allChapters = novelService.getAllChapters();
        List<Purchase> allPurchases = novelService.getAllPurchases();
        List<User> allUsers = userService.getUsers();

        // Map chapters by ID
        java.util.Map<Long, Chapter> chapterMap = new java.util.HashMap<>();
        for (Chapter c : allChapters) {
            chapterMap.put(c.getId(), c);
        }

        // Map novels by ID
        java.util.Map<Long, Novel> novelMap = new java.util.HashMap<>();
        for (Novel n : allNovels) {
            novelMap.put(n.getId(), n);
        }

        // Map users by ID
        java.util.Map<Long, User> userMap = new java.util.HashMap<>();
        for (User u : allUsers) {
            userMap.put(u.getId(), u);
        }

        boolean isAdmin = "ADMIN".equals(role) || "OWNER".equals(role);
        Long editorId = loggedInUser.getId();

        int totalSales = 0;
        int totalRevenue = 0;

        // Sales and revenue by story ID
        java.util.Map<Long, Integer> novelSalesCount = new java.util.HashMap<>();
        java.util.Map<Long, Integer> novelRevenue = new java.util.HashMap<>();

        // Revenue by editor user ID (for admin only)
        java.util.Map<Long, Integer> editorRevenueMap = new java.util.HashMap<>();

        // Daily revenue timeline
        java.util.Map<String, Integer> dailyRevenue = new java.util.TreeMap<>();

        for (Purchase p : allPurchases) {
            Chapter chap = chapterMap.get(p.getChapterId());
            if (chap == null) continue;

            Novel nov = chap.getNovel();
            if (nov == null) continue;
            Novel fullNovel = novelMap.get(nov.getId());
            if (fullNovel == null) continue;

            // Filter for EDITOR
            if (!isAdmin && !editorId.equals(fullNovel.getCreatorId())) {
                continue;
            }

            int price = chap.getPrice() != null ? chap.getPrice() : 0;
            totalSales++;
            totalRevenue += price;

            novelSalesCount.put(fullNovel.getId(), novelSalesCount.getOrDefault(fullNovel.getId(), 0) + 1);
            novelRevenue.put(fullNovel.getId(), novelRevenue.getOrDefault(fullNovel.getId(), 0) + price);

            if (isAdmin && fullNovel.getCreatorId() != null) {
                editorRevenueMap.put(fullNovel.getCreatorId(), editorRevenueMap.getOrDefault(fullNovel.getCreatorId(), 0) + price);
            }

            if (p.getPurchasedAt() != null) {
                String dateStr = p.getPurchasedAt().toLocalDate().toString();
                dailyRevenue.put(dateStr, dailyRevenue.getOrDefault(dateStr, 0) + price);
            }
        }

        // Prepare lists/maps for response
        java.util.List<java.util.Map<String, Object>> salesByStoryList = new java.util.ArrayList<>();
        for (Novel n : allNovels) {
            if (!isAdmin && !editorId.equals(n.getCreatorId())) {
                continue;
            }
            int sales = novelSalesCount.getOrDefault(n.getId(), 0);
            int rev = novelRevenue.getOrDefault(n.getId(), 0);
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("novelId", n.getId());
            item.put("title", n.getTitle());
            item.put("sales", sales);
            item.put("revenue", rev);
            salesByStoryList.add(item);
        }

        java.util.List<java.util.Map<String, Object>> salesByEditorList = new java.util.ArrayList<>();
        if (isAdmin) {
            for (java.util.Map.Entry<Long, Integer> entry : editorRevenueMap.entrySet()) {
                User ed = userMap.get(entry.getKey());
                String editorName = ed != null ? ed.getName() : "Editor #" + entry.getKey();
                java.util.Map<String, Object> item = new java.util.HashMap<>();
                item.put("editorId", entry.getKey());
                item.put("editorName", editorName);
                item.put("revenue", entry.getValue());
                salesByEditorList.add(item);
            }
        }

        // Unique readers count who purchased any of the filtered stories
        java.util.Set<Long> uniqueReaders = new java.util.HashSet<>();
        for (Purchase p : allPurchases) {
            Chapter chap = chapterMap.get(p.getChapterId());
            if (chap == null) continue;
            Novel nov = chap.getNovel();
            if (nov == null) continue;
            Novel fullNovel = novelMap.get(nov.getId());
            if (fullNovel == null) continue;

            if (isAdmin || editorId.equals(fullNovel.getCreatorId())) {
                uniqueReaders.add(p.getUserId());
            }
        }

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("totalSales", totalSales);
        response.put("totalRevenue", totalRevenue);
        response.put("activeReaders", uniqueReaders.size());
        response.put("salesByStory", salesByStoryList);
        response.put("salesOverTime", dailyRevenue);
        if (isAdmin) {
            response.put("salesByEditor", salesByEditorList);
        }

        return ResponseEntity.ok(response);
    }

    // 10. User Economic Analysis (ADMIN only)
    @GetMapping("/analytics/users")
    public ResponseEntity<?> getAnalyticsUsers(HttpSession session) {
        if (isRestricted(session)) {
            return ResponseEntity.ok(List.of());
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        if (!"ADMIN".equals(loggedInUser.getUser_type()) && !"OWNER".equals(loggedInUser.getUser_type())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only administrators can access user analytics."));
        }

        List<User> rawUsers = userService.getUsers();
        java.util.Map<Long, User> userLookup = new java.util.HashMap<>();
        for (User u : rawUsers) {
            userLookup.put(u.getId(), u);
        }

        boolean isOwner = "OWNER".equals(loggedInUser.getUser_type());
        java.util.List<User> allUsers = rawUsers.stream()
            .filter(u -> isOwner || !"OWNER".equals(u.getUser_type()))
            .collect(java.util.stream.Collectors.toList());

        List<Purchase> allPurchases = novelService.getAllPurchases();
        List<Chapter> allChapters = novelService.getAllChapters();

        java.util.Map<Long, Chapter> chapterMap = new java.util.HashMap<>();
        for (Chapter c : allChapters) {
            chapterMap.put(c.getId(), c);
        }

        java.util.Map<Long, Integer> userSpent = new java.util.HashMap<>();
        java.util.Map<Long, Integer> userChaptersCount = new java.util.HashMap<>();

        for (Purchase p : allPurchases) {
            Long userId = p.getUserId();
            User purchaseUser = userLookup.get(userId);
            if (purchaseUser != null && "OWNER".equals(purchaseUser.getUser_type())) {
                continue; // Skip owner transaction analytics
            }
            Chapter chap = chapterMap.get(p.getChapterId());
            int price = (chap != null && chap.getPrice() != null) ? chap.getPrice() : 0;

            userSpent.put(userId, userSpent.getOrDefault(userId, 0) + price);
            userChaptersCount.put(userId, userChaptersCount.getOrDefault(userId, 0) + 1);
        }

        java.util.List<java.util.Map<String, Object>> usersList = new java.util.ArrayList<>();
        java.util.Set<Long> activeUserIds = new java.util.HashSet<>();

        for (User u : allUsers) {
            activeUserIds.add(u.getId());
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("userId", u.getId());
            item.put("name", u.getName());
            item.put("email", u.getEmail());
            item.put("role", u.getUser_type());
            item.put("balance", u.getBalance() != null ? u.getBalance() : 0);
            item.put("totalSpent", userSpent.getOrDefault(u.getId(), 0));
            item.put("chaptersUnlocked", userChaptersCount.getOrDefault(u.getId(), 0));
            usersList.add(item);
        }

        // Add deleted users who have transactions
        for (java.util.Map.Entry<Long, Integer> entry : userSpent.entrySet()) {
            Long uId = entry.getKey();
            if (uId != null && !activeUserIds.contains(uId)) {
                java.util.Map<String, Object> item = new java.util.HashMap<>();
                item.put("userId", uId);
                item.put("name", "Deleted User (ID: " + uId + ")");
                item.put("email", "deleted@yuki-tales.com");
                item.put("role", "DELETED");
                item.put("balance", 0);
                item.put("totalSpent", entry.getValue());
                item.put("chaptersUnlocked", userChaptersCount.getOrDefault(uId, 0));
                usersList.add(item);
            }
        }

        return ResponseEntity.ok(usersList);
    }



    private Chapter chapterServiceHelper(Chapter chapter) {
        return novelService.saveChapter(chapter);
    }

    private String handleLocalCoverUrl(String coverUrl) {
        if (coverUrl == null || coverUrl.trim().isEmpty()) {
            return coverUrl;
        }
        
        coverUrl = coverUrl.trim();
        
        // If it's already a web URL or an uploaded file path, return it as is
        if (coverUrl.startsWith("http://") || coverUrl.startsWith("https://") || coverUrl.startsWith("/uploads/")) {
            return coverUrl;
        }
        
        String userDir = System.getProperty("user.dir");
        Path matchedFile = findLocalFile(coverUrl, userDir);
        
        if (matchedFile != null) {
            try {
                String originalFilename = matchedFile.getFileName().toString();
                String extension = "";
                if (originalFilename.contains(".")) {
                    extension = originalFilename.substring(originalFilename.lastIndexOf("."));
                }
                String uniqueName = UUID.randomUUID().toString() + extension;
                
                Path srcDir = Paths.get(userDir, "src", "main", "resources", "static", "uploads");
                Path targetDir = Paths.get(userDir, "target", "classes", "static", "uploads");
                Path rootUploadsDir = Paths.get(userDir, "uploads");
                
                Files.createDirectories(srcDir);
                Files.createDirectories(targetDir);
                Files.createDirectories(rootUploadsDir);
                
                Path srcFile = srcDir.resolve(uniqueName);
                Path targetFile = targetDir.resolve(uniqueName);
                Path rootFile = rootUploadsDir.resolve(uniqueName);
                
                Files.copy(matchedFile, srcFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.copy(matchedFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.copy(matchedFile, rootFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                
                return "/uploads/" + uniqueName;
            } catch (IOException e) {
                System.err.println("Failed to copy local cover: " + e.getMessage());
            }
        }
        
        return coverUrl;
    }

    private Path findLocalFile(String filename, String userDir) {
        try {
            Path directPath = Paths.get(filename);
            if (Files.exists(directPath) && !Files.isDirectory(directPath)) {
                return directPath;
            }
        } catch (Exception e) {}

        try {
            Path relativePath = Paths.get(userDir, filename);
            if (Files.exists(relativePath) && !Files.isDirectory(relativePath)) {
                return relativePath;
            }
        } catch (Exception e) {}

        try {
            String cleanName = Paths.get(filename).getFileName().toString().toLowerCase();
            File rootDir = new File(userDir);
            File[] files = rootDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) {
                        String name = f.getName().toLowerCase();
                        String normName = name.replaceAll("[ūúùûü]", "u");
                        String normClean = cleanName.replaceAll("[ūúùûü]", "u");
                        if (name.equals(cleanName) || normName.equals(normClean) || name.contains(cleanName) || cleanName.contains(name)) {
                            return f.toPath();
                        }
                    }
                }
            }
        } catch (Exception e) {}
        
        return null;
    }

    // --- SNOW FLAKES PACKAGES ENDPOINTS (ADMIN only) ---

    @PostMapping("/flakes/add")
    public ResponseEntity<?> addFlakePackage(
            @RequestParam Integer amount,
            @RequestParam Double price,
            HttpSession session) {

        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        if (!"ADMIN".equals(loggedInUser.getUser_type()) && !"OWNER".equals(loggedInUser.getUser_type())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only administrators can configure pricing."));
        }

        if (amount == null || amount <= 0 || price == null || price <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Amount and price must be greater than zero."));
        }

        FlakePackage pack = new FlakePackage(null, amount, price);
        novelService.saveFlakePackage(pack);

        return ResponseEntity.ok(Map.of("success", true, "message", "Package added successfully!"));
    }

    @PostMapping("/flakes/edit/{id}")
    public ResponseEntity<?> editFlakePackage(
            @PathVariable Long id,
            @RequestParam Integer amount,
            @RequestParam Double price,
            HttpSession session) {

        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        if (!"ADMIN".equals(loggedInUser.getUser_type()) && !"OWNER".equals(loggedInUser.getUser_type())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only administrators can configure pricing."));
        }

        FlakePackage pack = novelService.getFlakePackageById(id);
        if (pack == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Package not found."));
        }

        if (amount == null || amount <= 0 || price == null || price <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Amount and price must be greater than zero."));
        }

        pack.setAmount(amount);
        pack.setPrice(price);
        novelService.saveFlakePackage(pack);

        return ResponseEntity.ok(Map.of("success", true, "message", "Package details updated successfully!"));
    }

    @DeleteMapping("/flakes/{id}")
    public ResponseEntity<?> deleteFlakePackage(
            @PathVariable Long id,
            HttpSession session) {

        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        if (!"ADMIN".equals(loggedInUser.getUser_type()) && !"OWNER".equals(loggedInUser.getUser_type())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only administrators can configure pricing."));
        }

        FlakePackage pack = novelService.getFlakePackageById(id);
        if (pack == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Package not found."));
        }

        novelService.deleteFlakePackage(id);

        return ResponseEntity.ok(Map.of("success", true, "message", "Package deleted successfully!"));
    }

    @PostMapping("/novels/{id}/feature")
    public ResponseEntity<?> toggleFeatureNovel(
            @PathVariable Long id,
            HttpSession session) {

        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }

        if (!"ADMIN".equals(loggedInUser.getUser_type()) && !"OWNER".equals(loggedInUser.getUser_type())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only administrators can feature stories."));
        }

        Novel novel = novelService.getNovelById(id);
        if (novel == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Story not found."));
        }

        Long currentFeatured = novelService.getFeaturedNovelId();
        if (id.equals(currentFeatured)) {
            novelService.setFeaturedNovelId(null);
            return ResponseEntity.ok(Map.of("success", true, "featured", false, "message", "Story unfeatured successfully!"));
        } else {
            novelService.setFeaturedNovelId(id);
            return ResponseEntity.ok(Map.of("success", true, "featured", true, "message", "Story featured successfully!"));
        }
    }

    private void syncNovelFoldersAndFiles(Novel novel) {
        if (novel == null || novel.getId() == null) return;
        
        String userDir = System.getProperty("user.dir");
        Path srcMainDir = Paths.get(userDir, "src", "main", "resources", "static", "uploads", "stories", "story_" + novel.getId());
        Path targetDir = Paths.get(userDir, "target", "classes", "static", "uploads", "stories", "story_" + novel.getId());
        Path rootBackupDir = Paths.get(userDir, "uploads", "stories", "story_" + novel.getId());
        
        try {
            // Create subdirectories
            Files.createDirectories(srcMainDir.resolve("media"));
            Files.createDirectories(srcMainDir.resolve("text"));
            
            Files.createDirectories(targetDir.resolve("media"));
            Files.createDirectories(targetDir.resolve("text"));
            
            Files.createDirectories(rootBackupDir.resolve("media"));
            Files.createDirectories(rootBackupDir.resolve("text"));
            
            // Sync description
            String desc = novel.getDescription() != null ? novel.getDescription() : "";
            Files.writeString(srcMainDir.resolve("text").resolve("description.txt"), desc);
            Files.writeString(targetDir.resolve("text").resolve("description.txt"), desc);
            Files.writeString(rootBackupDir.resolve("text").resolve("description.txt"), desc);
            
            // Sync cover image if local upload
            String coverUrl = novel.getCoverUrl();
            if (coverUrl != null && coverUrl.startsWith("/uploads/") && !coverUrl.contains("/stories/")) {
                String filename = coverUrl.substring(coverUrl.lastIndexOf("/") + 1);
                String destFilename = "cover_" + filename;
                
                Path srcFile = Paths.get(userDir, "src", "main", "resources", "static", "uploads", filename);
                Path targetFile = Paths.get(userDir, "target", "classes", "static", "uploads", filename);
                Path rootFile = Paths.get(userDir, "uploads", filename);
                
                Path destSrcFile = srcMainDir.resolve("media").resolve(destFilename);
                Path destTargetFile = targetDir.resolve("media").resolve(destFilename);
                Path destRootFile = rootBackupDir.resolve("media").resolve(destFilename);
                
                if (Files.exists(srcFile)) {
                    Files.copy(srcFile, destSrcFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                if (Files.exists(targetFile)) {
                    Files.copy(targetFile, destTargetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                if (Files.exists(rootFile)) {
                    Files.copy(rootFile, destRootFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                
                novel.setCoverUrl("/uploads/stories/story_" + novel.getId() + "/media/" + destFilename);
                novelService.saveNovel(novel);
            }
        } catch (IOException e) {
            System.err.println("Error synchronizing novel folders and files: " + e.getMessage());
        }
    }

    private void syncChapterFiles(Chapter chapter) {
        if (chapter == null || chapter.getNovel() == null) return;
        Novel novel = chapter.getNovel();
        
        // Ensure folders exist
        syncNovelFoldersAndFiles(novel);
        
        String userDir = System.getProperty("user.dir");
        Path srcTextDir = Paths.get(userDir, "src", "main", "resources", "static", "uploads", "stories", "story_" + novel.getId(), "text");
        Path targetTextDir = Paths.get(userDir, "target", "classes", "static", "uploads", "stories", "story_" + novel.getId(), "text");
        Path rootTextDir = Paths.get(userDir, "uploads", "stories", "story_" + novel.getId(), "text");
        
        String chapFilename = "chapter_" + chapter.getChapterNumber() + ".txt";
        String content = chapter.getContent() != null ? chapter.getContent() : "";
        
        try {
            Files.writeString(srcTextDir.resolve(chapFilename), content);
            Files.writeString(targetTextDir.resolve(chapFilename), content);
            Files.writeString(rootTextDir.resolve(chapFilename), content);
        } catch (IOException e) {
            System.err.println("Error synchronizing chapter text file: " + e.getMessage());
        }
    }

    // GET all reviews for Admin/Owner dashboard
    @GetMapping("/reviews")
    public ResponseEntity<?> getAllReviews(HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }
        String role = loggedInUser.getUser_type();
        if (!"ADMIN".equals(role) && !"OWNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
        }
        return ResponseEntity.ok(reviewRepository.findAll());
    }

    // DELETE a review
    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<?> deleteReview(@PathVariable Long id, HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }
        String role = loggedInUser.getUser_type();
        if (!"ADMIN".equals(role) && !"OWNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
        }
        if (!reviewRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Review not found."));
        }
        reviewRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // GET all notifications for Admin/Owner dashboard
    @GetMapping("/notifications")
    public ResponseEntity<?> getAllNotifications(HttpSession session) {
        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }
        String role = loggedInUser.getUser_type();
        if (!"ADMIN".equals(role) && !"OWNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
        }
        return ResponseEntity.ok(notificationRepository.findByOrderByCreatedAtDesc());
    }

    // Toggle read/unread status of notification
    @PutMapping("/notifications/{id}/toggle-read")
    public ResponseEntity<?> toggleNotificationRead(@PathVariable Long id, HttpSession session) {
        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }
        String role = loggedInUser.getUser_type();
        if (!"ADMIN".equals(role) && !"OWNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
        }
        java.util.Optional<Notification> notifOpt = notificationRepository.findById(id);
        if (notifOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Notification not found."));
        }
        Notification notification = notifOpt.get();
        notification.setRead(!notification.isRead());
        notificationRepository.save(notification);
        return ResponseEntity.ok(Map.of("success", true, "isRead", notification.isRead()));
    }

    // DELETE a notification
    @DeleteMapping("/notifications/{id}")
    public ResponseEntity<?> deleteNotification(@PathVariable Long id, HttpSession session) {
        if (isRestricted(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Platform is in secured mode."));
        }
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }
        String role = loggedInUser.getUser_type();
        if (!"ADMIN".equals(role) && !"OWNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
        }
        if (!notificationRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Notification not found."));
        }
        notificationRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // GET credentials settings
    @GetMapping("/credentials")
    public ResponseEntity<?> getCredentials(HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }
        String role = loggedInUser.getUser_type();
        if (!"ADMIN".equals(role) && !"OWNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
        }

        // Return current credentials from DB
        java.util.Map<String, String> creds = new java.util.HashMap<>();
        creds.put("googleClientId", systemSettingRepository.findById("google.client_id").map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue).orElse(""));
        creds.put("googleClientSecret", systemSettingRepository.findById("google.client_secret").map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue).orElse(""));
        creds.put("mailHost", systemSettingRepository.findById("mail.host").map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue).orElse(""));
        creds.put("mailPort", systemSettingRepository.findById("mail.port").map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue).orElse(""));
        creds.put("mailUsername", systemSettingRepository.findById("mail.username").map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue).orElse(""));
        creds.put("mailPassword", systemSettingRepository.findById("mail.password").map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue).orElse(""));
        creds.put("mailSender", systemSettingRepository.findById("mail.sender").map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue).orElse(""));
        creds.put("mailRecipient", systemSettingRepository.findById("mail.recipient").map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue).orElse(""));
        creds.put("appBaseUrl", systemSettingRepository.findById("app.base_url").map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue).orElse(""));

        return ResponseEntity.ok(creds);
    }

    // POST credentials settings
    @PostMapping("/credentials")
    public ResponseEntity<?> saveCredentials(
            @RequestParam(required = false) String googleClientId,
            @RequestParam(required = false) String googleClientSecret,
            @RequestParam(required = false) String mailHost,
            @RequestParam(required = false) String mailPort,
            @RequestParam(required = false) String mailUsername,
            @RequestParam(required = false) String mailPassword,
            @RequestParam(required = false) String mailSender,
            @RequestParam(required = false) String mailRecipient,
            @RequestParam(required = false) String appBaseUrl,
            HttpSession session) {
        
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }
        String role = loggedInUser.getUser_type();
        if (!"ADMIN".equals(role) && !"OWNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
        }

        if (googleClientId != null) systemSettingRepository.save(new com.reader.Novel.Reader.model.SystemSetting("google.client_id", googleClientId.trim()));
        if (googleClientSecret != null) systemSettingRepository.save(new com.reader.Novel.Reader.model.SystemSetting("google.client_secret", googleClientSecret.trim()));
        if (mailHost != null) systemSettingRepository.save(new com.reader.Novel.Reader.model.SystemSetting("mail.host", mailHost.trim()));
        if (mailPort != null) systemSettingRepository.save(new com.reader.Novel.Reader.model.SystemSetting("mail.port", mailPort.trim()));
        if (mailUsername != null) systemSettingRepository.save(new com.reader.Novel.Reader.model.SystemSetting("mail.username", mailUsername.trim()));
        if (mailPassword != null) systemSettingRepository.save(new com.reader.Novel.Reader.model.SystemSetting("mail.password", mailPassword.trim()));
        if (mailSender != null) systemSettingRepository.save(new com.reader.Novel.Reader.model.SystemSetting("mail.sender", mailSender.trim()));
        if (mailRecipient != null) systemSettingRepository.save(new com.reader.Novel.Reader.model.SystemSetting("mail.recipient", mailRecipient.trim()));
        if (appBaseUrl != null) systemSettingRepository.save(new com.reader.Novel.Reader.model.SystemSetting("app.base_url", appBaseUrl.trim()));

        return ResponseEntity.ok(Map.of("success", true));
    }

    // POST /sendmail admin email dispatch
    @PostMapping("/sendmail")
    public ResponseEntity<?> sendAdminEmail(
            @RequestParam String recipientMode,
            @RequestParam(required = false) List<String> selectedEmails,
            @RequestParam(required = false) String customEmails,
            @RequestParam String subject,
            @RequestParam String body,
            HttpSession session) {

        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not logged in."));
        }
        String role = loggedInUser.getUser_type();
        if (!"ADMIN".equals(role) && !"OWNER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied. Only admins or owners can send emails."));
        }

        if (subject == null || subject.trim().isEmpty() || body == null || body.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Subject and message body are required."));
        }

        java.util.Set<String> recipients = new java.util.HashSet<>();
        if ("select".equals(recipientMode)) {
            if (selectedEmails != null) {
                for (String email : selectedEmails) {
                    if (email != null && !email.trim().isEmpty()) {
                        recipients.add(email.trim());
                    }
                }
            }
        } else if ("custom".equals(recipientMode)) {
            if (customEmails != null) {
                String[] parts = customEmails.split(",");
                for (String part : parts) {
                    if (part != null && !part.trim().isEmpty()) {
                        recipients.add(part.trim());
                    }
                }
            }
        }

        if (recipients.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No valid recipients specified."));
        }

        // Dispatch emails asynchronously
        for (String email : recipients) {
            emailService.sendCustomEmailAsync(email, subject.trim(), body.trim());
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Email dispatch initiated successfully for " + recipients.size() + " recipient(s)."));
    }
}
