package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.model.Novel;
import com.reader.Novel.Reader.model.Chapter;
import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.model.Rating;
import com.reader.Novel.Reader.service.NovelService;
import com.reader.Novel.Reader.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.servlet.http.HttpSession;
import java.util.List;

@Controller
public class NovelController {

    @Autowired
    private NovelService novelService;

    @Autowired
    private UserService userService;

    @GetMapping("/")
    public String home(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String search,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String type,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String genre,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String status,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String year,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String sort,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String tags,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String country,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String source,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String editor,
            HttpSession session, Model model) {
        if (novelService.isSecuredMode()) {
            User loggedInUser = (User) session.getAttribute("user");
            if (loggedInUser == null || !"OWNER".equals(loggedInUser.getUser_type())) {
                model.addAttribute("novels", java.util.Collections.emptyList());
                return "home";
            }
        }
        
        List<Novel> novels;
        if (search != null && !search.trim().isEmpty()) {
            novels = novelService.searchNovels(search.trim());
        } else {
            novels = novelService.getAllNovels();
        }

        User loggedInUser = (User) session.getAttribute("user");
        String userRole = loggedInUser != null ? loggedInUser.getUser_type() : "READER";
        Long currentUserId = loggedInUser != null ? loggedInUser.getId() : -1L;

        
        if (type != null && !type.trim().isEmpty() && !"ALL".equalsIgnoreCase(type)) {
            novels = novels.stream()
                .filter(n -> type.equalsIgnoreCase(n.getType()))
                .toList();
        }
        
        if (genre != null && !genre.trim().isEmpty() && !"ALL".equalsIgnoreCase(genre)) {
            novels = novels.stream()
                .filter(n -> n.getGenre() != null && java.util.Arrays.stream(n.getGenre().split(","))
                    .map(String::trim)
                    .anyMatch(g -> genre.equalsIgnoreCase(g)))
                .toList();
        }
        
        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            novels = novels.stream()
                .filter(n -> status.equalsIgnoreCase(n.getStatus()))
                .toList();
        }

        if (year != null && !year.trim().isEmpty() && !"ALL".equalsIgnoreCase(year)) {
            try {
                int yVal = Integer.parseInt(year.trim());
                novels = novels.stream()
                    .filter(n -> n.getYear() != null && n.getYear().equals(yVal))
                    .toList();
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        if (tags != null && !tags.trim().isEmpty() && !"ALL".equalsIgnoreCase(tags)) {
            novels = novels.stream()
                .filter(n -> n.getTags() != null && java.util.Arrays.stream(n.getTags().split(","))
                    .map(String::trim)
                    .anyMatch(t -> tags.equalsIgnoreCase(t)))
                .toList();
        }

        if (country != null && !country.trim().isEmpty() && !"ALL".equalsIgnoreCase(country)) {
            novels = novels.stream()
                .filter(n -> country.equalsIgnoreCase(n.getCountryOfOrigin()))
                .toList();
        }

        if (source != null && !source.trim().isEmpty() && !"ALL".equalsIgnoreCase(source)) {
            novels = novels.stream()
                .filter(n -> source.equalsIgnoreCase(n.getSource()))
                .toList();
        }

        if (editor != null && !editor.trim().isEmpty() && !"ALL".equalsIgnoreCase(editor)) {
            try {
                Long editorId = Long.parseLong(editor.trim());
                novels = novels.stream()
                    .filter(n -> n.getCreatorId() != null && n.getCreatorId().equals(editorId))
                    .toList();
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        // Sorting
        String activeSort = (sort != null && !sort.trim().isEmpty()) ? sort.trim() : "POPULARITY";
        if ("TITLE".equalsIgnoreCase(activeSort)) {
            novels = novels.stream()
                .sorted((n1, n2) -> {
                    String t1 = n1.getTitle() != null ? n1.getTitle() : "";
                    String t2 = n2.getTitle() != null ? n2.getTitle() : "";
                    return t1.compareToIgnoreCase(t2);
                })
                .toList();
        } else if ("RATING".equalsIgnoreCase(activeSort)) {
            novels = novels.stream()
                .sorted((n1, n2) -> {
                    double r1 = n1.getRating() != null ? n1.getRating() : 0.0;
                    double r2 = n2.getRating() != null ? n2.getRating() : 0.0;
                    return Double.compare(r2, r1);
                })
                .toList();
        } else if ("NEWEST".equalsIgnoreCase(activeSort)) {
            novels = novels.stream()
                .sorted((n1, n2) -> {
                    Long id1 = n1.getId() != null ? n1.getId() : 0L;
                    Long id2 = n2.getId() != null ? n2.getId() : 0L;
                    return Long.compare(id2, id1);
                })
                .toList();
        } else {
            // Default "POPULARITY" (rating desc, pin featured novel at index 0 if not searching)
            novels = novels.stream()
                .sorted((n1, n2) -> {
                    double r1 = n1.getRating() != null ? n1.getRating() : 0.0;
                    double r2 = n2.getRating() != null ? n2.getRating() : 0.0;
                    return Double.compare(r2, r1);
                })
                .toList();
            
            if (search == null || search.trim().isEmpty()) {
                Long featuredId = novelService.getFeaturedNovelId();
                if (featuredId != null) {
                    novels = new java.util.ArrayList<>(novels);
                    int featuredIdx = -1;
                    for (int i = 0; i < novels.size(); i++) {
                        if (novels.get(i).getId().equals(featuredId)) {
                            featuredIdx = i;
                            break;
                        }
                    }
                    if (featuredIdx > 0) {
                        Novel featuredNovel = novels.remove(featuredIdx);
                        novels.add(0, featuredNovel);
                    }
                }
            }
        }
        
        List<User> editors = userService.getUsers().stream()
            .filter(u -> "EDITOR".equals(u.getUser_type()))
            .toList();
        model.addAttribute("editors", editors);
        model.addAttribute("novels", novels);
        model.addAttribute("upcomingChapters", novelService.getUpcomingChapters());
        return "home";
    }

    @GetMapping("/novel/{id}")
    public String novelDetails(@PathVariable Long id, HttpSession session, Model model) {
        User loggedInUser = (User) session.getAttribute("user");
        if (novelService.isSecuredMode()) {
            if (loggedInUser == null || !"OWNER".equals(loggedInUser.getUser_type())) {
                return "redirect:/";
            }
        }
        Novel novel = novelService.getNovelById(id);
        if (novel == null) {
            return "redirect:/";
        }

        model.addAttribute("novel", novel);
        
        User creator = null;
        if (novel.getCreatorId() != null) {
            creator = userService.getUserById(novel.getCreatorId());
        }
        model.addAttribute("creator", creator);
        model.addAttribute("chapters", novelService.getVisibleChapters(novel, loggedInUser));

        boolean isBookmarked = false;
        Double progress = 0.0;
        java.util.List<Long> purchasedChapterIds = new java.util.ArrayList<>();
        if (loggedInUser != null) {
            var bookmarkOpt = novelService.getBookmark(loggedInUser.getId(), id);
            if (bookmarkOpt.isPresent()) {
                isBookmarked = true;
                progress = bookmarkOpt.get().getLastReadChapterNumber();
            }
            purchasedChapterIds = novelService.getPurchasedChapterIds(loggedInUser.getId());
        }
        model.addAttribute("isBookmarked", isBookmarked);
        model.addAttribute("progress", progress);
        model.addAttribute("purchasedChapterIds", purchasedChapterIds);

        Integer userRating = 0;
        if (loggedInUser != null) {
            var ratingOpt = novelService.getUserRating(loggedInUser.getId(), id);
            if (ratingOpt.isPresent()) {
                userRating = ratingOpt.get().getRatingValue();
            }
        }
        model.addAttribute("userRating", userRating);

        return "details";
    }

    @GetMapping("/novel/{id}/read/{chapterNumber}")
    public String readChapter(@PathVariable Long id, @PathVariable Double chapterNumber, HttpSession session, Model model) {
        User loggedInUser = (User) session.getAttribute("user");
        if (novelService.isSecuredMode()) {
            if (loggedInUser == null || !"OWNER".equals(loggedInUser.getUser_type())) {
                return "redirect:/";
            }
        }
        Novel novel = novelService.getNovelById(id);
        if (novel == null) {
            return "redirect:/";
        }

        Chapter chapter = novelService.getChapterByNumber(id, chapterNumber);
        if (chapter == null) {
            return "redirect:/novel/" + id;
        }

        // Security check for scheduled chapters
        if (chapter.getPublishAt() != null && chapter.getPublishAt().isAfter(java.time.LocalDateTime.now())) {
            if (!novelService.isAuthorizedToSeeFutureChapters(loggedInUser, novel)) {
                return "redirect:/novel/" + id + "?error=scheduled_chapter";
            }
        }

        // Security check for paid chapters
        if (chapter.getPrice() > 0) {
            if (loggedInUser == null) {
                return "redirect:/novel/" + id + "?showLogin=true&error=login_required";
            }
            String role = loggedInUser.getUser_type();
            if (!"ADMIN".equals(role) && !"PROOFREADER".equals(role) && !"OWNER".equals(role) && !("EDITOR".equals(role) && loggedInUser.getId().equals(novel.getCreatorId()))) {
                boolean hasPurchased = novelService.hasPurchased(loggedInUser.getId(), chapter.getId());
                if (!hasPurchased) {
                    return "redirect:/novel/" + id + "?error=purchase_required&chapterId=" + chapter.getId();
                }
            }
        }

        model.addAttribute("novel", novel);
        model.addAttribute("chapter", chapter);
        
        List<Chapter> chapters = novelService.getVisibleChapters(novel, loggedInUser);
        model.addAttribute("chapters", chapters);
        
        Double prevChapterNumber = null;
        Double nextChapterNumber = null;
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).getChapterNumber().equals(chapterNumber)) {
                if (i > 0) {
                    prevChapterNumber = chapters.get(i - 1).getChapterNumber();
                }
                if (i < chapters.size() - 1) {
                    nextChapterNumber = chapters.get(i + 1).getChapterNumber();
                }
                break;
            }
        }
        model.addAttribute("prevChapterNumber", prevChapterNumber);
        model.addAttribute("nextChapterNumber", nextChapterNumber);

        // Save progress if logged in
        if (loggedInUser != null) {
            novelService.updateBookmarkProgress(loggedInUser.getId(), id, chapterNumber);
        }

        boolean isImageContent = false;
        if (novel.getType() != null && (novel.getType().equals("COMIC") || novel.getType().equals("MANGA") || novel.getType().equals("MANHWA"))) {
            String contentTrim = chapter.getContent() != null ? chapter.getContent().trim() : "";
            if (!contentTrim.isEmpty()) {
                String firstPart = contentTrim;
                if (contentTrim.contains(",")) {
                    String[] parts = contentTrim.split(",");
                    if (parts.length > 0) {
                        firstPart = parts[0].trim();
                    }
                }
                String lowerFirst = firstPart.toLowerCase();
                if (lowerFirst.startsWith("http") || lowerFirst.startsWith("/uploads") || 
                    lowerFirst.endsWith(".jpg") || lowerFirst.endsWith(".png") || 
                    lowerFirst.endsWith(".jpeg") || lowerFirst.endsWith(".webp") || 
                    lowerFirst.endsWith(".gif")) {
                    isImageContent = true;
                }
            }
        }
        model.addAttribute("isImageContent", isImageContent);

        return "reader";
    }

    @GetMapping("/bookshelf")
    public String bookshelf(HttpSession session, Model model) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return "redirect:/?showLogin=true";
        }
        if (novelService.isSecuredMode() && !"OWNER".equals(loggedInUser.getUser_type())) {
            model.addAttribute("bookmarks", java.util.Collections.emptyList());
            return "bookshelf";
        }

        model.addAttribute("bookmarks", novelService.getBookmarksByUserId(loggedInUser.getId()));
        return "bookshelf";
    }
}
