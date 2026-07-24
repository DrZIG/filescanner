package com.drzig.filescanner.service;

import com.drzig.filescanner.model.AppSettings;
import com.drzig.filescanner.repository.AppSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SettingsService {

    public static final String KEY_EMAIL_TO       = "email_to";
    public static final String KEY_EMAIL_FROM     = "email_from";
    public static final String KEY_SMTP_HOST      = "smtp_host";
    public static final String KEY_SMTP_PORT      = "smtp_port";
    public static final String KEY_SMTP_USER      = "smtp_user";
    public static final String KEY_SMTP_PASS      = "smtp_pass";
    public static final String KEY_SMTP_TLS       = "smtp_tls";
    public static final String KEY_STATUS_REFRESH = "status_refresh_seconds";

    private final AppSettingsRepository repo;

    public SettingsService(AppSettingsRepository repo) {
        this.repo = repo;
    }

    public String get(String key, String defaultValue) {
        return repo.findByKey(key).map(AppSettings::getValue).orElse(defaultValue);
    }

    @Transactional
    public void set(String key, String value) {
        AppSettings s = repo.findByKey(key).orElse(new AppSettings(key, null));
        s.setValue(value);
        repo.save(s);
    }

    public int getStatusRefreshSeconds() {
        try {
            return Integer.parseInt(get(KEY_STATUS_REFRESH, "10"));
        } catch (NumberFormatException e) {
            return 10;
        }
    }
}
