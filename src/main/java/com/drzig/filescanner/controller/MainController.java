package com.drzig.filescanner.controller;

import com.drzig.filescanner.dto.FileEntryDto;
import com.drzig.filescanner.dto.ScanStatus;
import com.drzig.filescanner.model.FileEntry;
import com.drzig.filescanner.model.IconMapping;
import com.drzig.filescanner.model.NetworkShare;
import com.drzig.filescanner.service.*;
import com.drzig.filescanner.util.DeviceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private final FileService fileService;
    private final ScanService scanService;
    private final SettingsService settingsService;
    private final EmailService emailService;
    private final IconService iconService;
    private final NetworkShareService networkShareService;
    private final SyncGuardService syncGuardService;

    public MainController(FileService fileService, ScanService scanService,
                          SettingsService settingsService, EmailService emailService,
                          IconService iconService, NetworkShareService networkShareService,
                          SyncGuardService syncGuardService) {
        this.fileService = fileService;
        this.scanService = scanService;
        this.settingsService = settingsService;
        this.emailService = emailService;
        this.iconService = iconService;
        this.networkShareService = networkShareService;
        this.syncGuardService = syncGuardService;
    }

    // ===== HOME =====

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("roots", fileService.getRoots());
        model.addAttribute("scanStatus", scanService.getStatus());
        model.addAttribute("refreshSeconds", settingsService.getStatusRefreshSeconds());
        model.addAttribute("currentPage", "home");
        model.addAttribute("syncWarning", syncGuardService.getStartupWarning());
        return "index";
    }

    // ===== BROWSE =====

    @GetMapping("/browse")
    public String browse(@RequestParam Long id, Model model) {
        Optional<FileEntryDto> entry = fileService.findById(id);
        if (entry.isEmpty()) return "redirect:/";
        model.addAttribute("current", entry.get());
        model.addAttribute("children", fileService.getChildren(id));
        model.addAttribute("breadcrumb", fileService.getBreadcrumb(id));
        model.addAttribute("scanStatus", scanService.getStatus());
        model.addAttribute("refreshSeconds", settingsService.getStatusRefreshSeconds());
        model.addAttribute("currentPage", "home");
        return "browse";
    }

    // ===== SCAN CONTROL =====

    @PostMapping("/scan/start")
    public String startScan(@RequestParam(defaultValue = "false") boolean sendEmail) {
        ScanStatus.State state = scanService.getStatus().getState();
        if (state != ScanStatus.State.RUNNING && state != ScanStatus.State.PAUSED) {
            scanService.startScan(sendEmail);
        }
        return "redirect:/";
    }

    @PostMapping("/scan/stop")
    public String stopScan() {
        scanService.requestStop();
        return "redirect:/";
    }

    @PostMapping("/scan/pause")
    @ResponseBody
    public ResponseEntity<ScanStatus> pauseScan() {
        scanService.requestPause();
        return ResponseEntity.ok(scanService.getStatus());
    }

    @PostMapping("/scan/resume")
    @ResponseBody
    public ResponseEntity<ScanStatus> resumeScan() {
        scanService.requestResume();
        return ResponseEntity.ok(scanService.getStatus());
    }

    @GetMapping("/scan/status")
    @ResponseBody
    public ScanStatus getScanStatus() {
        return scanService.getStatus();
    }

    // ===== DO NOT PROCESS =====

    @PostMapping("/api/folder/donotprocess")
    @ResponseBody
    public ResponseEntity<?> toggleDoNotProcess(@RequestParam Long id, @RequestParam boolean value) {
        Optional<FileEntry> entry = fileService.findEntityById(id);
        if (entry.isEmpty()) return ResponseEntity.notFound().build();
        fileService.toggleDoNotProcess(id, value);
        if (value) {
            fileService.removeChildrenOfDoNotProcess(id, entry.get().getFullPath(), entry.get().getDeviceName());
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ===== SETTINGS =====

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("emailTo",  settingsService.get(SettingsService.KEY_EMAIL_TO, ""));
        model.addAttribute("emailFrom",settingsService.get(SettingsService.KEY_EMAIL_FROM, ""));
        model.addAttribute("smtpHost", settingsService.get(SettingsService.KEY_SMTP_HOST, "smtp.gmail.com"));
        model.addAttribute("smtpPort", settingsService.get(SettingsService.KEY_SMTP_PORT, "587"));
        model.addAttribute("smtpUser", settingsService.get(SettingsService.KEY_SMTP_USER, ""));
        model.addAttribute("smtpTls",  settingsService.get(SettingsService.KEY_SMTP_TLS, "true"));
        model.addAttribute("refreshSeconds", settingsService.getStatusRefreshSeconds());
        model.addAttribute("icons", iconService.findAll());
        model.addAttribute("scanStatus", scanService.getStatus());
        model.addAttribute("currentPage", "settings");
        return "settings";
    }

    @PostMapping("/settings/general")
    public String saveGeneralSettings(
            @RequestParam(defaultValue = "") String emailTo,
            @RequestParam(defaultValue = "") String emailFrom,
            @RequestParam(defaultValue = "smtp.gmail.com") String smtpHost,
            @RequestParam(defaultValue = "587") String smtpPort,
            @RequestParam(defaultValue = "") String smtpUser,
            @RequestParam(defaultValue = "") String smtpPass,
            @RequestParam(defaultValue = "true") String smtpTls,
            @RequestParam(defaultValue = "10") String refreshSeconds) {

        settingsService.set(SettingsService.KEY_EMAIL_TO,   emailTo.trim());
        settingsService.set(SettingsService.KEY_EMAIL_FROM, emailFrom.trim());
        settingsService.set(SettingsService.KEY_SMTP_HOST,  smtpHost.trim());
        settingsService.set(SettingsService.KEY_SMTP_PORT,  smtpPort.trim());
        settingsService.set(SettingsService.KEY_SMTP_USER,  smtpUser.trim());
        if (!smtpPass.isBlank())
            settingsService.set(SettingsService.KEY_SMTP_PASS, smtpPass);
        settingsService.set(SettingsService.KEY_SMTP_TLS,       smtpTls);
        settingsService.set(SettingsService.KEY_STATUS_REFRESH, refreshSeconds.trim());
        return "redirect:/settings?saved=true";
    }

    // ===== ICONS =====

    @PostMapping("/settings/icons/save")
    public String saveIcon(@RequestParam(required = false) Long id,
                           @RequestParam String pattern,
                           @RequestParam String patternType,
                           @RequestParam String icon,
                           @RequestParam(defaultValue = "") String label) {
        IconMapping m = (id != null) ? iconService.findById(id) : null;
        if (m == null) m = new IconMapping();
        m.setPattern(pattern.trim().toLowerCase());
        m.setPatternType(patternType);
        m.setIcon(icon.trim());
        m.setLabel(label.trim());
        iconService.save(m);
        return "redirect:/settings?tab=icons&saved=true";
    }

    @PostMapping("/settings/icons/delete")
    public String deleteIcon(@RequestParam Long id) {
        iconService.delete(id);
        return "redirect:/settings?tab=icons";
    }

    // ===== NETWORK SHARES =====

    @GetMapping("/shares")
    public String shares(Model model) {
        model.addAttribute("shares", networkShareService.findAll());
        model.addAttribute("scanStatus", scanService.getStatus());
        model.addAttribute("refreshSeconds", settingsService.getStatusRefreshSeconds());
        model.addAttribute("currentPage", "shares");
        return "shares";
    }

    @PostMapping("/shares/save")
    public String saveShare(@RequestParam(required = false) Long id,
                            @RequestParam String path,
                            @RequestParam(defaultValue = "") String label,
                            @RequestParam(defaultValue = "") String domain,
                            @RequestParam(defaultValue = "") String username,
                            @RequestParam(defaultValue = "") String password,
                            @RequestParam(defaultValue = "false") boolean requiresAuth,
                            @RequestParam(defaultValue = "true") boolean enabled) {
        NetworkShare share = (id != null) ? networkShareService.findById(id).orElse(new NetworkShare()) : new NetworkShare();
        share.setPath(path.trim());
        share.setLabel(label.trim());
        share.setDomain(domain.trim());
        share.setUsername(username.trim());
        if (!password.isBlank())
            share.setPassword(password);

        share.setRequiresAuth(requiresAuth);
        share.setEnabled(enabled);
        networkShareService.save(share);

        return "redirect:/shares?saved=true";
    }

    @PostMapping("/shares/delete")
    public String deleteShare(@RequestParam Long id) {
        networkShareService.delete(id);
        return "redirect:/shares?deleted=true";
    }

    /** AJAX: probe a network path and return whether it's accessible / needs credentials. */
    @PostMapping("/api/shares/probe")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> probeShare(
            @RequestParam String path,
            @RequestParam(defaultValue = "") String domain,
            @RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "") String password) {
        Map<String, Object> result = networkShareService.probeShare(path, domain, username, password);
        return ResponseEntity.ok(result);
    }

    // ===== ROOT MANAGEMENT =====

    @GetMapping("/manage-roots")
    public String manageRoots(Model model) {
        List<Map<String, Object>> roots = new ArrayList<>();
        String thisDevice = DeviceUtil.currentDeviceName();
        for (Map<String, String> ident : fileService.getAllRootIdentifiers()) {
            String device = ident.get("device");
            String rootPath = ident.get("path");
            boolean isThisDevice = device.equals(thisDevice);

            Map<String, Object> info = new HashMap<>();
            info.put("device", device);
            info.put("path", rootPath);
            info.put("count", fileService.countByDeviceAndRootPath(device, rootPath));
            info.put("thisDevice", isThisDevice);
            info.put("accessible", isThisDevice && new java.io.File(rootPath).exists());
            roots.add(info);
        }
        model.addAttribute("roots", roots);
        model.addAttribute("scanStatus", scanService.getStatus());
        model.addAttribute("refreshSeconds", settingsService.getStatusRefreshSeconds());
        model.addAttribute("currentPage", "roots");
        return "manage-roots";
    }

    @PostMapping("/roots/delete")
    public String deleteRoot(@RequestParam String rootPath,
                             @RequestParam String deviceName,
                             @RequestParam(defaultValue = "false") boolean confirmed) {
        if (!confirmed) {
            return "redirect:/manage-roots?confirm=" + URLEncoder.encode(rootPath, StandardCharsets.UTF_8)
                    + "&confirmDevice=" + URLEncoder.encode(deviceName, StandardCharsets.UTF_8);
        }
        fileService.deleteByDeviceAndRootPath(deviceName, rootPath);
        return "redirect:/manage-roots?deleted=true";
    }

    // ===== EMAIL =====

    @PostMapping("/email/send")
    public String sendEmail() {
        try {
            emailService.sendReportEmail();
            return "redirect:/?emailSent=true";
        } catch (Exception e) {
            log.error("Email failed", e);
            return "redirect:/?emailError=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
    }

    // ===== REST API =====

    @GetMapping("/api/children")
    @ResponseBody
    public List<FileEntryDto> getChildren(@RequestParam Long parentId) {
        return fileService.getChildren(parentId);
    }

    @GetMapping("/api/roots")
    @ResponseBody
    public List<FileEntryDto> getRoots() {
        return fileService.getRoots();
    }
}
