package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.model.Novel;
import com.reader.Novel.Reader.model.Chapter;
import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.model.Rating;
import com.reader.Novel.Reader.service.NovelService;
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

    @GetMapping("/")
    public String home(@org.springframework.web.bind.annotation.RequestParam(required = false) String search, HttpSession session, Model model) {
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
            novels = new java.util.ArrayList<>(novelService.getAllNovels());
            Long featuredId = novelService.getFeaturedNovelId();
            if (featuredId != null) {
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
