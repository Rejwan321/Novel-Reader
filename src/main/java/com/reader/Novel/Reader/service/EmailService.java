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
}
