package com.reader.Novel.Reader.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${app.mail.sender:yukitales@nazuna.dpdns.org}")
    private String fromAddress;

    @Value("${app.mail.recipient:yukitales.novel@gmail.com}")
    private String toAddress;

    @Async
    public void sendMentionEmailAsync(String authorName, String commentContent, String novelTitle, Double chapterNumber, String readLink) {
        if (mailSender == null) {
            System.err.println("JavaMailSender not configured. Skipping email notification.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toAddress);
            message.setSubject("[Yuki Tales] Admin Mention Alert");
            message.setText(String.format(
                "Hello Admin,\n\n" +
                "You were mentioned in a comment on Yuki Tales:\n\n" +
                "User: %s\n" +
                "Comment: \"%s\"\n" +
                "Novel: %s (Chapter %s)\n\n" +
                "You can view the comment here: %s\n\n" +
                "Best regards,\n" +
                "Yuki Tales System",
                authorName, commentContent, novelTitle, chapterNumber, readLink
            ));

            mailSender.send(message);
            System.out.println("Mention notification email sent successfully to " + toAddress);
        } catch (Exception e) {
            System.err.println("Failed to send mention notification email: " + e.getMessage());
        }
    }

    @Async
    public void sendCommentReportEmailAsync(String reporterName, String commentAuthor, String commentContent, String novelTitle, Double chapterNumber, String readLink, Long commentId) {
        if (mailSender == null) {
            System.err.println("JavaMailSender not configured. Skipping email notification.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toAddress);
            message.setSubject("[Yuki Tales] NSFW Comment Report Alert");
            message.setText(String.format(
                "Hello Admin,\n\n" +
                "A comment has been reported as NSFW on Yuki Tales:\n\n" +
                "Reported Comment ID: %d\n" +
                "Reported By: %s\n" +
                "Author of Comment: %s\n" +
                "Content: \"%s\"\n" +
                "Novel: %s (Chapter %s)\n\n" +
                "You can view the comment in context here: %s\n\n" +
                "To delete this comment, please log into the admin panel or delete it directly.\n\n" +
                "Best regards,\n" +
                "Yuki Tales System",
                commentId, reporterName, commentAuthor, commentContent, novelTitle, chapterNumber, readLink
            ));

            mailSender.send(message);
            System.out.println("Comment report email sent successfully to " + toAddress);
        } catch (Exception e) {
            System.err.println("Failed to send comment report email: " + e.getMessage());
        }
    }

    @Async
    public void sendReviewEmailAsync(String reviewerName, Integer rating, String feedbackComment) {
        if (mailSender == null) {
            System.err.println("JavaMailSender not configured. Skipping email notification.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toAddress);
            message.setSubject("[Yuki Tales] New User Review/Feedback Alert");
            message.setText(String.format(
                "Hello Admin,\n\n" +
                "A user has submitted a review on Yuki Tales:\n\n" +
                "Name: %s\n" +
                "Rating: %d/5 Stars\n" +
                "Feedback: \"%s\"\n\n" +
                "Best regards,\n" +
                "Yuki Tales System",
                reviewerName, rating, feedbackComment
            ));

            mailSender.send(message);
            System.out.println("Review alert email sent successfully to " + toAddress);
        } catch (Exception e) {
            System.err.println("Failed to send review alert email: " + e.getMessage());
        }
    }
}
