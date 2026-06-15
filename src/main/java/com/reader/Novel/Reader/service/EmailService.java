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

    @Autowired
    private com.reader.Novel.Reader.repository.SystemSettingRepository systemSettingRepository;

    @Value("${app.mail.sender:yukitales@nazuna.dpdns.org}")
    private String fromAddress;

    @Value("${app.mail.recipient:yukitales.novel@gmail.com}")
    private String toAddress;

    private JavaMailSender getDynamicMailSender() {
        String host = systemSettingRepository.findById("mail.host").map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue).orElse("");
        String portStr = systemSettingRepository.findById("mail.port").map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue).orElse("587");
        String username = systemSettingRepository.findById("mail.username").map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue).orElse("");
        String password = systemSettingRepository.findById("mail.password").map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue).orElse("");

        if (host.isEmpty() || username.isEmpty() || password.isEmpty()) {
            return mailSender;
        }

        org.springframework.mail.javamail.JavaMailSenderImpl sender = new org.springframework.mail.javamail.JavaMailSenderImpl();
        sender.setHost(host);
        try {
            sender.setPort(Integer.parseInt(portStr));
        } catch (NumberFormatException e) {
            sender.setPort(587);
        }
        sender.setUsername(username);
        sender.setPassword(password);

        java.util.Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.debug", "false");

        return sender;
    }

    private String getFromAddress() {
        return systemSettingRepository.findById("mail.sender")
                .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                .orElse(fromAddress);
    }

    private String getToAddress() {
        return systemSettingRepository.findById("mail.recipient")
                .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                .orElse(toAddress);
    }

    @Async
    public void sendMentionEmailAsync(String authorName, String commentContent, String novelTitle, Double chapterNumber, String readLink) {
        JavaMailSender activeSender = getDynamicMailSender();
        if (activeSender == null) {
            System.err.println("JavaMailSender not configured. Skipping email notification.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String activeFrom = getFromAddress();
            String activeTo = getToAddress();
            message.setFrom(activeFrom);
            message.setTo(activeTo);
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

            activeSender.send(message);
            System.out.println("Mention notification email sent successfully to " + activeTo);
        } catch (Exception e) {
            System.err.println("Failed to send mention notification email: " + e.getMessage());
        }
    }

    @Async
    public void sendMentionEmailAsyncToRecipient(String recipientEmail, String authorName, String commentContent, String novelTitle, Double chapterNumber, String readLink) {
        JavaMailSender activeSender = getDynamicMailSender();
        if (activeSender == null) {
            System.err.println("JavaMailSender not configured. Skipping email notification.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String activeFrom = getFromAddress();
            message.setFrom(activeFrom);
            message.setTo(recipientEmail);
            message.setSubject("[Yuki Tales] You were mentioned in a comment!");
            message.setText(String.format(
                "Hello,\n\n" +
                "You were mentioned in a comment on Yuki Tales:\n\n" +
                "User: %s\n" +
                "Comment: \"%s\"\n" +
                "Novel: %s (Chapter %s)\n\n" +
                "You can view the comment here: %s\n\n" +
                "If you wish to change your notification preferences, you can do so in your User Panel.\n\n" +
                "Best regards,\n" +
                "Yuki Tales Support",
                authorName, commentContent, novelTitle, chapterNumber, readLink
            ));

            activeSender.send(message);
            System.out.println("Mention notification email sent successfully to recipient: " + recipientEmail);
        } catch (Exception e) {
            System.err.println("Failed to send mention notification email to recipient: " + e.getMessage());
        }
    }

    @Async
    public void sendCommentReportEmailAsync(String reporterName, String commentAuthor, String commentContent, String novelTitle, Double chapterNumber, String readLink, Long commentId) {
        JavaMailSender activeSender = getDynamicMailSender();
        if (activeSender == null) {
            System.err.println("JavaMailSender not configured. Skipping email notification.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String activeFrom = getFromAddress();
            String activeTo = getToAddress();
            message.setFrom(activeFrom);
            message.setTo(activeTo);
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

            activeSender.send(message);
            System.out.println("Comment report email sent successfully to " + activeTo);
        } catch (Exception e) {
            System.err.println("Failed to send comment report email: " + e.getMessage());
        }
    }

    @Async
    public void sendReviewEmailAsync(String reviewerName, Integer rating, String feedbackComment) {
        JavaMailSender activeSender = getDynamicMailSender();
        if (activeSender == null) {
            System.err.println("JavaMailSender not configured. Skipping email notification.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String activeFrom = getFromAddress();
            String activeTo = getToAddress();
            message.setFrom(activeFrom);
            message.setTo(activeTo);
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

            activeSender.send(message);
            System.out.println("Review alert email sent successfully to " + activeTo);
        } catch (Exception e) {
            System.err.println("Failed to send review alert email: " + e.getMessage());
        }
    }

    @Async
    public void sendCustomEmailAsync(String to, String subject, String body) {
        JavaMailSender activeSender = getDynamicMailSender();
        if (activeSender == null) {
            System.err.println("JavaMailSender not configured. Skipping custom email.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String activeFrom = getFromAddress();
            message.setFrom(activeFrom);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            activeSender.send(message);
            System.out.println("Custom email sent successfully to " + to);
        } catch (Exception e) {
            System.err.println("Failed to send custom email to " + to + ": " + e.getMessage());
        }
    }
}
