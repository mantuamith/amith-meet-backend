// com.algomeet.authservice.service.impl.SmtpEmailSender
package com.algomeet.authservice.notify;

import com.algomeet.authservice.config.MailConfig;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final MailConfig mailConfig;

    @Override
    public void send(String to, String subject, String body) {
        sendHtml(to, subject, body);
    }

    /**
     * Send plain-text email.
     */
    public void sendText(String to, String subject, String textBody) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(new InternetAddress(mailConfig.getFrom(), mailConfig.getFromName()));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textBody, false);
            mailSender.send(msg);
            log.info("EMAIL sent (text) to={} subject='{}'", to, subject);
        } catch (Exception e) {
            log.error("EMAIL send failed to={} subject='{}' err={}", to, subject, e.getMessage(), e);
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    /**
     * Send HTML email (falls back to text if client doesn’t support HTML).
     */
    public void sendHtml(String to, String subject, String htmlBody) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(new InternetAddress(mailConfig.getFrom(), mailConfig.getFromName()));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(stripHtml(htmlBody), htmlBody); // text + HTML
            mailSender.send(msg);
            log.info("EMAIL sent (html) to={} subject='{}'", to, subject);
        } catch (Exception e) {
            log.error("EMAIL send failed to={} subject='{}' err={}", to, subject, e.getMessage(), e);
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    private String stripHtml(String html) {
        return html == null ? "" : html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }
}
