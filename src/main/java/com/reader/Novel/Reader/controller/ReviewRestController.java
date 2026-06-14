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

    @PostMapping("/submit")
    public ResponseEntity<?> submitReview(
            @RequestParam(required = false) String name,
            @RequestParam Integer rating,
            @RequestParam String comment) {

        if (rating == null || rating < 1 || rating > 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "Rating must be between 1 and 5."));
        }
        if (comment == null || comment.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Comment cannot be empty."));
        }

        String reviewerName = (name == null || name.trim().isEmpty()) ? "Anonymous Reader" : name.trim();
        Review review = new Review(reviewerName, rating, comment.trim());
        Review saved = reviewRepository.save(review);

        return ResponseEntity.ok(Map.of("success", true, "review", saved));
    }
}
