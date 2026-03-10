package com.enterprise.blog.notification.service;

import com.enterprise.blog.notification.entity.NotificationLog;
import com.enterprise.blog.notification.entity.NotificationStatus;
import com.enterprise.blog.notification.entity.NotificationType;
import com.enterprise.blog.notification.repository.NotificationLogRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final NotificationLogRepository notificationLogRepository;

    @Async
    public void sendWelcomeEmail(String recipient, String username) {
        Context ctx = new Context();
        ctx.setVariable("username", username);
        sendEmail(recipient, "Welcome to Enterprise Blog!", "welcome", ctx, NotificationType.WELCOME);
    }

    @Async
    public void sendBlogPublishedEmail(String recipient, Map<String, Object> blogData) {
        Context ctx = new Context();
        ctx.setVariables(blogData);
        String subject = "New post: " + blogData.get("title");
        sendEmail(recipient, subject, "blog-published", ctx, NotificationType.BLOG_PUBLISHED);
    }

    @Async
    public void sendNewCommentEmail(String recipient, Map<String, Object> commentData) {
        Context ctx = new Context();
        ctx.setVariables(commentData);
        String subject = "New comment on: " + commentData.get("postTitle");
        sendEmail(recipient, subject, "new-comment", ctx, NotificationType.NEW_COMMENT);
    }

    private void sendEmail(String recipient, String subject, String template,
                           Context ctx, NotificationType type) {
        NotificationLog logEntry = NotificationLog.builder()
                .type(type)
                .recipient(recipient)
                .subject(subject)
                .status(NotificationStatus.PENDING)
                .build();

        try {
            String htmlContent = templateEngine.process(template, ctx);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);

            logEntry.setStatus(NotificationStatus.SENT);
            logEntry.setSentAt(LocalDateTime.now());
            log.info("Email sent successfully to {}: {}", recipient, subject);
        } catch (MessagingException e) {
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setErrorMessage(e.getMessage());
            log.error("Failed to send email to {}: {}", recipient, e.getMessage());
        } finally {
            notificationLogRepository.save(logEntry);
        }
    }
}
