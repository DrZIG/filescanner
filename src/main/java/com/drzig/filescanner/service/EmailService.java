package com.drzig.filescanner.service;

import com.drzig.filescanner.model.FileEntry;
import com.drzig.filescanner.repository.FileEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final FileEntryRepository repo;
    private final SettingsService settingsService;

    public EmailService(FileEntryRepository repo, SettingsService settingsService) {
        this.repo = repo;
        this.settingsService = settingsService;
    }

    public void sendReportEmail() {
        String to = settingsService.get(SettingsService.KEY_EMAIL_TO, "");
        if (to == null || to.isBlank()) {
            throw new RuntimeException("No recipient email configured. Set it in Settings → Email.");
        }
        try {
            JavaMailSenderImpl sender = buildSender();
            String text = buildReportText();
            File zip = createZip(text);

            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(msg, true, "UTF-8");
            String from = settingsService.get(SettingsService.KEY_EMAIL_FROM, "filescanner@localhost");
            mimeMessageHelper.setFrom(from.isBlank() ? "filescanner@localhost" : from);
            mimeMessageHelper.setTo(to.split("[,;\\s]+"));
            mimeMessageHelper.setSubject("File Scanner Report — " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            mimeMessageHelper.setText("File scanner hierarchical report attached.", false);
            mimeMessageHelper.addAttachment("filescanner-report.zip", zip);
            sender.send(msg);
            log.info("Report sent to {}", to);
            zip.delete();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Email failed", e);
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    private JavaMailSenderImpl buildSender() {
        JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
        javaMailSender.setHost(settingsService.get(SettingsService.KEY_SMTP_HOST, "localhost"));
        String port = settingsService.get(SettingsService.KEY_SMTP_PORT, "587");
        javaMailSender.setPort(Integer.parseInt(port.isBlank() ? "587" : port));
        javaMailSender.setUsername(settingsService.get(SettingsService.KEY_SMTP_USER, ""));
        javaMailSender.setPassword(settingsService.get(SettingsService.KEY_SMTP_PASS, ""));
        Properties javaMailProperties = javaMailSender.getJavaMailProperties();
        javaMailProperties.put("mail.transport.protocol", "smtp");
        if ("true".equalsIgnoreCase(settingsService.get(SettingsService.KEY_SMTP_TLS, "true"))) {
            javaMailProperties.put("mail.smtp.auth", "true");
            javaMailProperties.put("mail.smtp.starttls.enable", "true");
        }
        return javaMailSender;
    }

    public String buildReportText() {
        StringBuilder sb = new StringBuilder();
        sb.append("File Scanner Report\nGenerated: ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append("\n").append("=".repeat(60)).append("\n\n");
        for (Object[] row : repo.findAllRootPathsWithDevice()) {
            String device = (String) row[0];
            String root = (String) row[1];
            sb.append("[Device: ").append(device).append("]\n");
            repo.findRootEntry(device, root).ifPresent(e -> appendEntry(sb, e));
            sb.append("\n");
        }
        return sb.toString();
    }

    private void appendEntry(StringBuilder sb, FileEntry e) {
        sb.append(e.getFullPath());
        if (e.isFile() && e.getSizeBytes() != null) sb.append(" ").append(e.getSizeBytes());
        sb.append("\n");
        if (!e.isFile()) {
            for (FileEntry child : repo.findByParentIdOrderByFileAscNameAsc(e.getId())) {
                appendEntry(sb, child);
            }
        }
    }

    private File createZip(String content) throws IOException {
        File zip = Files.createTempFile("filescanner-", ".zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("filescanner-report.txt"));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return zip;
    }
}
