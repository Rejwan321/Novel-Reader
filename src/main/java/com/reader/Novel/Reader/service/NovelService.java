package com.reader.Novel.Reader.service;

import com.reader.Novel.Reader.model.Novel;
import com.reader.Novel.Reader.model.Chapter;
import com.reader.Novel.Reader.model.Bookmark;
import com.reader.Novel.Reader.model.Purchase;
import com.reader.Novel.Reader.repository.NovelRepository;
import com.reader.Novel.Reader.repository.ChapterRepository;
import com.reader.Novel.Reader.repository.BookmarkRepository;
import com.reader.Novel.Reader.repository.PurchaseRepository;
import com.reader.Novel.Reader.repository.RatingRepository;
import com.reader.Novel.Reader.model.Rating;
import com.reader.Novel.Reader.model.FlakePackage;
import com.reader.Novel.Reader.repository.FlakePackageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class NovelService {

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private com.reader.Novel.Reader.repository.UserRepository userRepository;

    @Autowired
    private FlakePackageRepository flakePackageRepository;

    @Autowired
    private com.reader.Novel.Reader.repository.SystemSettingRepository systemSettingRepository;

    @Autowired
    private com.reader.Novel.Reader.repository.CommentRepository commentRepository;

    @Autowired
    private com.reader.Novel.Reader.repository.NotificationRepository notificationRepository;

    public boolean isSecuredMode() {
        return systemSettingRepository.findById("secured_mode")
                .map(setting -> "true".equalsIgnoreCase(setting.getSettingValue()))
                .orElse(false);
    }

    @Transactional
    public void toggleSecuredMode() {
        boolean current = isSecuredMode();
        com.reader.Novel.Reader.model.SystemSetting setting = new com.reader.Novel.Reader.model.SystemSetting("secured_mode", String.valueOf(!current));
        systemSettingRepository.save(setting);
    }


    public Optional<Rating> getUserRating(Long userId, Long novelId) {
        return ratingRepository.findByUserIdAndNovelId(userId, novelId);
    }

    @Transactional
    public void submitRating(Long userId, Long novelId, Integer ratingValue) {
        Optional<Rating> existing = ratingRepository.findByUserIdAndNovelId(userId, novelId);
        if (existing.isPresent()) {
            Rating rating = existing.get();
            rating.setRatingValue(ratingValue);
            ratingRepository.save(rating);
        } else {
            ratingRepository.save(new Rating(null, userId, novelId, ratingValue));
        }

        // Recompute average
        List<Rating> ratings = ratingRepository.findByNovelId(novelId);
        if (!ratings.isEmpty()) {
            double sum = 0;
            for (Rating r : ratings) {
                sum += r.getRatingValue();
            }
            double avg = sum / ratings.size();
            avg = Math.round(avg * 10.0) / 10.0; // Round to 1 decimal place

            Novel novel = novelRepository.findById(novelId).orElse(null);
            if (novel != null) {
                novel.setRating(avg);
                novelRepository.save(novel);
            }
        }
    }

    public boolean hasPurchased(Long userId, Long chapterId) {
        return purchaseRepository.findByUserIdAndChapterId(userId, chapterId).isPresent();
    }

    public List<Long> getPurchasedChapterIds(Long userId) {
        return purchaseRepository.findByUserId(userId).stream()
                .map(Purchase::getChapterId)
                .toList();
    }

    public List<Purchase> getPurchasesByUserId(Long userId) {
        return purchaseRepository.findByUserId(userId);
    }

    @Transactional
    public void purchaseChapter(Long userId, Long chapterId) {
        purchaseRepository.save(new Purchase(null, userId, chapterId, LocalDateTime.now()));
    }

    public List<Purchase> getAllPurchases() {
        return purchaseRepository.findAll();
    }

    public List<Chapter> getAllChapters() {
        return chapterRepository.findAll();
    }

    public List<Novel> getAllNovels() {
        return novelRepository.findAll();
    }

    public List<Novel> getNovelsByType(String type) {
        return novelRepository.findByType(type.toUpperCase());
    }

    public List<Novel> getNovelsByGenre(String genre) {
        return novelRepository.findByGenreContainingIgnoreCase(genre);
    }

    public List<Novel> searchNovels(String query) {
        return novelRepository.findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCaseOrGenreContainingIgnoreCase(query, query, query);
    }

    public Novel getNovelById(Long id) {
        return novelRepository.findById(id).orElse(null);
    }

    public List<Chapter> getChaptersByNovelId(Long novelId) {
        return chapterRepository.findByNovelIdOrderByChapterNumberAsc(novelId);
    }

    public Chapter getChapterByNumber(Long novelId, Double chapterNumber) {
        return chapterRepository.findByNovelIdAndChapterNumber(novelId, chapterNumber).orElse(null);
    }

    public Chapter getChapterById(Long id) {
        return chapterRepository.findById(id).orElse(null);
    }

    public Novel saveNovel(Novel novel) {
        return novelRepository.save(novel);
    }

    public Chapter saveChapter(Chapter chapter) {
        if (chapter.getContent() != null && chapter.getContent().contains("<")) {
            String cleanContent = org.jsoup.Jsoup.clean(chapter.getContent(), org.jsoup.safety.Safelist.relaxed());
            chapter.setContent(cleanContent);
        }
        return chapterRepository.save(chapter);
    }

    @Transactional
    public void deleteChapter(Long id) {
        commentRepository.deleteByChapterId(id);
        notificationRepository.deleteByChapterId(id);
        purchaseRepository.deleteByChapterId(id);
        chapterRepository.deleteById(id);
    }

    @Transactional
    public void deleteNovel(Long id) {
        bookmarkRepository.deleteByNovelId(id);
        ratingRepository.deleteByNovelId(id);
        notificationRepository.deleteByNovelId(id);
        List<Chapter> chapters = chapterRepository.findByNovelIdOrderByChapterNumberAsc(id);
        for (Chapter chapter : chapters) {
            commentRepository.deleteByChapterId(chapter.getId());
            notificationRepository.deleteByChapterId(chapter.getId());
            purchaseRepository.deleteByChapterId(chapter.getId());
        }
        novelRepository.deleteById(id);
    }


    @Transactional
    public Bookmark updateBookmarkProgress(Long userId, Long novelId, Double chapterNumber) {
        Optional<Bookmark> existing = bookmarkRepository.findByUserIdAndNovelId(userId, novelId);
        Bookmark bookmark;
        if (existing.isPresent()) {
            bookmark = existing.get();
            bookmark.setLastReadChapterNumber(chapterNumber);
            bookmark.setUpdatedAt(LocalDateTime.now());
        } else {
            bookmark = new Bookmark(null, userId, novelId, chapterNumber, LocalDateTime.now());
        }
        return bookmarkRepository.save(bookmark);
    }

    @Transactional
    public Bookmark toggleBookmark(Long userId, Long novelId) {
        Optional<Bookmark> existing = bookmarkRepository.findByUserIdAndNovelId(userId, novelId);
        if (existing.isPresent()) {
            bookmarkRepository.deleteByUserIdAndNovelId(userId, novelId);
            return null;
        } else {
            Bookmark bookmark = new Bookmark(null, userId, novelId, 0.0, LocalDateTime.now());
            return bookmarkRepository.save(bookmark);
        }
    }

    public Optional<Bookmark> getBookmark(Long userId, Long novelId) {
        return bookmarkRepository.findByUserIdAndNovelId(userId, novelId);
    }

    public List<Bookmark> getBookmarksByUserId(Long userId) {
        List<Bookmark> bookmarks = bookmarkRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        for (Bookmark b : bookmarks) {
            b.setNovel(novelRepository.findById(b.getNovelId()).orElse(null));
        }
        return bookmarks;
    }

    public List<Novel> getNovelsByCreatorId(Long creatorId) {
        return novelRepository.findByCreatorId(creatorId);
    }

    @Transactional
    public void executePurchase(Long userId, Long chapterId, int price) {
        synchronized (this) {
            com.reader.Novel.Reader.model.User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found."));
            String role = user.getUser_type();
            boolean isInfinite = "ADMIN".equals(role) || "OWNER".equals(role);
            if (!isInfinite) {
                if (user.getBalance() < price) {
                    throw new IllegalArgumentException("Insufficient balance.");
                }
                user.setBalance(user.getBalance() - price);
                userRepository.save(user);
            }
            purchaseRepository.save(new Purchase(null, userId, chapterId, java.time.LocalDateTime.now()));
        }
    }

    public List<FlakePackage> getAllFlakePackages() {
        return flakePackageRepository.findAllByOrderByAmountAsc();
    }

    public FlakePackage saveFlakePackage(FlakePackage pack) {
        return flakePackageRepository.save(pack);
    }

    @Transactional
    public void deleteFlakePackage(Long id) {
        flakePackageRepository.deleteById(id);
    }

    public FlakePackage getFlakePackageById(Long id) {
        return flakePackageRepository.findById(id).orElse(null);
    }

    public Long getFeaturedNovelId() {
        return systemSettingRepository.findById("featured_novel_id")
                .map(setting -> {
                    try {
                        return Long.parseLong(setting.getSettingValue());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    @Transactional
    public void setFeaturedNovelId(Long novelId) {
        if (novelId == null) {
            systemSettingRepository.deleteById("featured_novel_id");
        } else {
            systemSettingRepository.save(new com.reader.Novel.Reader.model.SystemSetting("featured_novel_id", String.valueOf(novelId)));
        }
    }

    public boolean isAuthorizedToSeeFutureChapters(com.reader.Novel.Reader.model.User user, Novel novel) {
        if (user == null) return false;
        String role = user.getUser_type();
        if ("ADMIN".equals(role) || "PROOFREADER".equals(role) || "OWNER".equals(role)) {
            return true;
        }
        if ("EDITOR".equals(role) && novel != null && user.getId().equals(novel.getCreatorId())) {
            return true;
        }
        return false;
    }

    public List<Chapter> getVisibleChapters(Novel novel, com.reader.Novel.Reader.model.User user) {
        List<Chapter> all = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novel.getId());
        if (isAuthorizedToSeeFutureChapters(user, novel)) {
            return all;
        }
        LocalDateTime now = LocalDateTime.now();
        return all.stream()
                .filter(c -> c.getPublishAt() == null || !c.getPublishAt().isAfter(now))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<Chapter> getUpcomingChapters() {
        return chapterRepository.findByPublishAtAfterOrderByPublishAtAsc(LocalDateTime.now());
    }
}
