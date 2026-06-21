package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.model.Review;
import com.reader.Novel.Reader.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
public class ReviewRestController {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private com.reader.Novel.Reader.service.EmailService emailService;

    @PostMapping("/submit")
    public ResponseEntity<?> submitReview(
            @RequestParam(required = false) String name,
            @RequestParam Integer rating,
            @RequestParam String comment,
            jakarta.servlet.http.HttpSession session,
            jakarta.servlet.http.HttpServletRequest request) {

        if (rating == null || rating < 1 || rating > 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "Rating must be between 1 and 5."));
        }
        if (comment == null || comment.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Comment cannot be empty."));
        }

        com.reader.Novel.Reader.model.User loggedInUser = (com.reader.Novel.Reader.model.User) session.getAttribute("user");
        java.time.LocalDateTime startOfToday = java.time.LocalDate.now().atStartOfDay();
        String ipAddress = request.getRemoteAddr();

        if (loggedInUser != null) {
            long count = reviewRepository.countByUserIdAndCreatedAtAfter(loggedInUser.getId(), startOfToday);
            if (count >= 5) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("error", "You have reached your limit of 5 reviews today."));
            }
        } else {
            long count = reviewRepository.countByIpAddressAndCreatedAtAfter(ipAddress, startOfToday);
            if (count >= 5) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("error", "Guests can only submit 5 reviews per day from the same IP address."));
            }
        }

        String reviewerName = (name == null || name.trim().isEmpty()) ? "Anonymous Reader" : name.trim();
        Review review = new Review(reviewerName, rating, comment.trim());
        if (loggedInUser != null) {
            review.setUserId(loggedInUser.getId());
        }
        review.setIpAddress(ipAddress);
        Review saved = reviewRepository.save(review);

        // Send email alert to admin
        emailService.sendReviewEmailAsync(reviewerName, rating, comment.trim());

        return ResponseEntity.ok(Map.of("success", true, "review", saved));
    }
}
