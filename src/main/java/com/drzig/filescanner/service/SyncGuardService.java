package com.drzig.filescanner.service;

import com.drzig.filescanner.util.DeviceUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Guards against silent corruption when the H2 database file is synced
 * across machines via Dropbox/OneDrive. H2 cannot detect a second machine
 * writing to a synced copy of the same file, so this raises a visible
 * warning whenever the app is opened on a different device than last time.
 */
@Service
public class SyncGuardService {

    private static final Logger log = LoggerFactory.getLogger(SyncGuardService.class);

    public static final String KEY_LAST_ACTIVE_DEVICE    = "last_active_device";
    public static final String KEY_LAST_ACTIVE_TIMESTAMP = "last_active_timestamp";

    private final SettingsService settingsService;
    private String startupWarning;

    public SyncGuardService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @PostConstruct
    public void checkOnStartup() {
        String thisDevice = DeviceUtil.currentDeviceName();
        String lastDevice = settingsService.get(KEY_LAST_ACTIVE_DEVICE, "");
        String lastTimestamp = settingsService.get(KEY_LAST_ACTIVE_TIMESTAMP, "");

        if (!lastDevice.isBlank() && !lastDevice.equals(thisDevice)) {
            startupWarning = "This database was last opened on \"" + lastDevice + "\""
                    + (lastTimestamp.isBlank() ? "" : " at " + lastTimestamp)
                    + ". Before making changes here, confirm FileScanner is fully closed on \""
                    + lastDevice + "\" and that Dropbox/OneDrive shows it as fully synced on both machines. "
                    + "Opening it here before that sync finishes can corrupt the database or silently lose data.";
            log.warn("Sync guard: DB last active on '{}', now opened on '{}'", lastDevice, thisDevice);
        }

        settingsService.set(KEY_LAST_ACTIVE_DEVICE, thisDevice);
        settingsService.set(KEY_LAST_ACTIVE_TIMESTAMP, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    /** Non-null only on the first page load after a device switch is detected. */
    public String getStartupWarning() {
        return startupWarning;
    }
}
