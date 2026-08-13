package com.drzig.filescanner.service;

import com.drzig.filescanner.dto.ScanStatus;
import com.drzig.filescanner.model.FileEntry;
import com.drzig.filescanner.model.NetworkShare;
import com.drzig.filescanner.repository.FileEntryRepository;
import com.drzig.filescanner.util.DeviceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);
    private static final int BATCH_SIZE = 500;

    private final FileEntryRepository repo;
    private final FileService fileService;
    private final NetworkShareService networkShareService;
    private final EmailService emailService;
    private final TransactionTemplate txTemplate;

    private final ScanStatus scanStatus = new ScanStatus();
    private final String deviceName = DeviceUtil.currentDeviceName();

    private final Object pauseLock = new Object();

    private Map<String, FileEntry> existingPaths = new HashMap<>();
    private final List<FileEntry> pendingBatch = new ArrayList<>();

    public ScanService(FileEntryRepository repo, FileService fileService,
                       NetworkShareService networkShareService, EmailService emailService,
                       PlatformTransactionManager txManager) {
        this.repo = repo;
        this.fileService = fileService;
        this.networkShareService = networkShareService;
        this.emailService = emailService;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    public ScanStatus getStatus() {
        return scanStatus;
    }

    public void requestStop() {
        scanStatus.setStopRequested(true);
        // Wake up if paused so the stop is processed
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    public void requestPause() {
        if (scanStatus.getState() == ScanStatus.State.RUNNING) {
            scanStatus.setPauseRequested(true);
            scanStatus.setState(ScanStatus.State.PAUSED);
            scanStatus.setMessage("Paused — click Resume to continue.");
        }
    }

    public void requestResume() {
        if (scanStatus.getState() == ScanStatus.State.PAUSED) {
            scanStatus.setPauseRequested(false);
            scanStatus.setState(ScanStatus.State.RUNNING);
            scanStatus.setMessage("");
            synchronized (pauseLock) {
                pauseLock.notifyAll();
            }
        }
    }

    /** Blocks the scan thread while paused. Flushes pending writes first so nothing sits unsaved while paused. */
    private void checkPause() {
        if (scanStatus.isPauseRequested() && !scanStatus.isStopRequested()) {
            flushBatch();
        }
        while (scanStatus.isPauseRequested() && !scanStatus.isStopRequested()) {
            synchronized (pauseLock) {
                try {
                    pauseLock.wait(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Async
    public void startScan(boolean sendEmailAfter) {
        if (scanStatus.getState() == ScanStatus.State.RUNNING
                || scanStatus.getState() == ScanStatus.State.PAUSED) {
            return;
        }
        try {
            scanStatus.reset();
            log.info("Scan started");

            // Step 1: pre-scan existence check
            performExistenceCheck();
            if (scanStatus.isStopRequested()) {
                finishScan(ScanStatus.State.COMPLETED, "Stopped by user during existence check.");
                return;
            }

            // Step 2: local drives
            for (File drive : getLocalDrives()) {
                checkPause();
                if (scanStatus.isStopRequested())
                    break;
                scanStatus.setCurrentDrive(drive.getAbsolutePath());
                scanDrive(drive);
            }

            // Step 3: network shares
            if (!scanStatus.isStopRequested()) {
                for (NetworkShare share : networkShareService.findEnabled()) {
                    checkPause();
                    if (scanStatus.isStopRequested())
                        break;
                    scanStatus.setCurrentDrive(share.getPath());
                    scanNetShare(share);
                }
            }

            String result = scanStatus.isStopRequested() ? "Stopped by user." :
                    String.format("Completed: %,d files, %,d folders scanned.",
                            scanStatus.getFilesScanned(), scanStatus.getFoldersScanned());
            finishScan(ScanStatus.State.COMPLETED, result);

            if (sendEmailAfter && !scanStatus.isStopRequested()) {
                emailService.sendReportEmail();
            }

        } catch (Exception e) {
            log.error("Scan error", e);
            finishScan(ScanStatus.State.ERROR, "Error: " + e.getMessage());
        }
    }

    private void finishScan(ScanStatus.State state, String message) {
        scanStatus.setState(state);
        scanStatus.setMessage(message);
        scanStatus.setEndTime(LocalDateTime.now());
        scanStatus.setCurrentPath("");
        scanStatus.setPauseRequested(false);
        log.info("Scan finished: {}", message);
    }

    @Transactional
    protected void performExistenceCheck() {
        scanStatus.setMessage("Verifying existing database records against disk…");
        List<FileEntry> mine = repo.findByDeviceName(deviceName);

        // Check each root (drive letter / UNC path) once. A root that is currently
        // inaccessible — e.g. a network drive letter not connected right now — must
        // never cause its entries to be auto-deleted below. Skip those roots entirely;
        // they can only be removed manually from the Manage Drives page.
        Map<String, Boolean> rootAccessible = new HashMap<>();
        for (FileEntry entry : mine) {
            rootAccessible.computeIfAbsent(entry.getRootPath(), rp -> new File(rp).exists());
        }

        List<FileEntry> toRemove = new ArrayList<>();
        for (FileEntry entry : mine) {
            if (scanStatus.isStopRequested()) {
                break;
            }
            if (!Boolean.TRUE.equals(rootAccessible.get(entry.getRootPath()))) {
                continue;
            }
            boolean exists = new File(entry.getFullPath()).exists();
            if (!exists) {
                entry.setExistsOnDisk(false);
                if (entry.getParentId() != null) {
                    toRemove.add(entry);
                } else {
                    repo.save(entry);
                }
            } else if (!entry.isExistsOnDisk()) {
                entry.setExistsOnDisk(true);
                repo.save(entry);
            }
        }
        if (!toRemove.isEmpty()) {
            repo.deleteAll(toRemove);
            log.info("Removed {} stale entries for device {}", toRemove.size(), deviceName);
        }

        long skippedRoots = rootAccessible.values().stream().filter(a -> !a).count();
        if (skippedRoots > 0) {
            log.info("Skipped existence check for {} inaccessible root(s) on device {} (drive offline — remove manually if intentional)",
                    skippedRoots, deviceName);
        }
    }

    private List<File> getLocalDrives() {
        List<File> drives = new ArrayList<>();
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            File f = root.toFile();
            if (f.exists() && f.canRead())
                drives.add(f);
        }
        return drives;
    }

    private void loadExistingForRoot(String rootPath) {
        existingPaths = new HashMap<>();
        for (FileEntry e : repo.findByDeviceNameAndRootPath(deviceName, rootPath)) {
            existingPaths.put(e.getFullPath(), e);
        }
    }

    private void scanDrive(File drive) {
        String path = drive.getAbsolutePath();
        if (path.endsWith("\\") || path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        loadExistingForRoot(path);
        FileEntry root = ensureEntry(path, path, null, path, false, null, 0);
        if (root != null) {
            scanDirectory(drive, root.getId(), path, 1);
        }
        flushBatch();
        existingPaths = new HashMap<>(); // release memory before next root
    }

    private void scanNetShare(NetworkShare share) {
        String path = share.getPath();
        while (path.endsWith("/") || path.endsWith("\\")) {
            path = path.substring(0, path.length() - 1);
        }

        if (share.isRequiresAuth() && share.getUsername() != null && !share.getUsername().isBlank()) {
            boolean connected = networkShareService.connectShare(share);
            if (!connected) {
                scanStatus.setMessage("⚠ Could not connect to " + path + " — check credentials.");
                log.warn("Failed to connect share {}", path);
                return;
            }
        }

        File dir = new File(path);
        if (!dir.exists() || !dir.canRead()) {
            log.warn("Network share not accessible: {}", path);
            scanStatus.setMessage("⚠ Not accessible: " + path);
            return;
        }
        loadExistingForRoot(path);
        FileEntry root = ensureEntry(path, path, null, path, false, null, 0);
        if (root != null) {
            scanDirectory(dir, root.getId(), path, 1);
        }
        flushBatch();
        existingPaths = new HashMap<>();
    }

    private void scanDirectory(File dir, Long parentId, String rootPath, int depth) {
        checkPause();
        if (scanStatus.isStopRequested()) {
            return;
        }
        if (!new File(rootPath).exists()) {
            log.warn("Root {} became inaccessible during scan — aborting this branch (share may have disconnected or the remote machine went to sleep)", rootPath);
            scanStatus.setMessage("⚠ " + rootPath + " became unreachable mid-scan — stopped, existing records left untouched.");
            return;
        }

        File[] children;
        try {
            children = dir.listFiles();
        } catch (SecurityException e) {
            log.warn("Access denied: {}", dir.getAbsolutePath());
            return;
        }
        if (children == null) {
            return;
        }

        Arrays.sort(children, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) {
                return a.isDirectory() ? -1 : 1;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File child : children) {
            checkPause();
            if (scanStatus.isStopRequested()) {
                return;
            }

            String childPath = child.getAbsolutePath();
            scanStatus.setCurrentPath(childPath);

            if (child.isDirectory()) {
                if (fileService.isAnyAncestorDoNotProcess(childPath, deviceName)) {
                    continue;
                }
                FileEntry folderEntry = ensureEntry(childPath, child.getName(), parentId, rootPath, false, null, depth);
                if (folderEntry == null) {
                    continue;
                }
                if (folderEntry.isDoNotProcess()) {
                    fileService.removeChildrenOfDoNotProcess(folderEntry.getId(), childPath, deviceName);
                    scanStatus.incrementFolders();
                    continue;
                }
                scanStatus.incrementFolders();
                scanDirectory(child, folderEntry.getId(), rootPath, depth + 1);
            } else if (child.isFile()) {
                long size = child.length();
                if (size == 0 && !child.exists()) {
                    // Share likely dropped mid-read (e.g. remote laptop went to sleep) —
                    // don't record a false zero-byte size over a real one.
                    log.warn("Skipping {} — became unreadable mid-scan", childPath);
                    continue;
                }
                ensureEntry(childPath, child.getName(), parentId, rootPath, true, size, depth);
                scanStatus.incrementFiles(size);
            }
        }
    }

    private FileEntry ensureEntry(String fullPath, String name, Long parentId,
                                  String rootPath, boolean isFile, Long sizeBytes, int depth) {
        FileEntry existing = existingPaths.get(fullPath);
        if (existing != null) {
            boolean dirty = false;
            if (!existing.isExistsOnDisk()) {
                existing.setExistsOnDisk(true);
                dirty = true;
            }
            if (isFile && sizeBytes != null && !sizeBytes.equals(existing.getSizeBytes())) {
                existing.setSizeBytes(sizeBytes);
                dirty = true;
            }
            if (dirty) {
                queueSave(existing);
            }
            return existing;
        }

        FileEntry created = new FileEntry(fullPath, name, parentId, rootPath, deviceName, isFile, sizeBytes, depth);
        if (isFile) {
            queueSave(created);
        } else {
            created = saveNow(created);
        }
        existingPaths.put(fullPath, created);
        return created;
    }

    private void queueSave(FileEntry e) {
        pendingBatch.add(e);
        if (pendingBatch.size() >= BATCH_SIZE) {
            flushBatch();
        }
    }

    private FileEntry saveNow(FileEntry e) {
        return txTemplate.execute(status -> repo.save(e));
    }

    private void flushBatch() {
        if (pendingBatch.isEmpty()) {
            return;
        }
        List<FileEntry> toSave = new ArrayList<>(pendingBatch);
        pendingBatch.clear();
        txTemplate.execute(status -> repo.saveAll(toSave));
    }
}
