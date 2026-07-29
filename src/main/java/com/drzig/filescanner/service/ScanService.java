package com.drzig.filescanner.service;

import com.drzig.filescanner.dto.ScanStatus;
import com.drzig.filescanner.model.FileEntry;
import com.drzig.filescanner.model.NetworkShare;
import com.drzig.filescanner.repository.FileEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private final FileEntryRepository repo;
    private final FileService fileService;
    private final NetworkShareService networkShareService;
    private final EmailService emailService;

    private final ScanStatus scanStatus = new ScanStatus();

    // Pause lock object
    private final Object pauseLock = new Object();

    public ScanService(FileEntryRepository repo, FileService fileService,
                       NetworkShareService networkShareService, EmailService emailService) {
        this.repo = repo;
        this.fileService = fileService;
        this.networkShareService = networkShareService;
        this.emailService = emailService;
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

    /** Blocks the scan thread while paused, until resumed or stopped. */
    private void checkPause() {
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
    private void performExistenceCheck() {
        scanStatus.setMessage("Verifying existing database records against disk…");
        List<FileEntry> all = repo.findAll();
        List<FileEntry> toRemove = new ArrayList<>();
        for (FileEntry entry : all) {
            if (scanStatus.isStopRequested())
                break;
            boolean exists = new File(entry.getFullPath()).exists();
            if (!exists) {
                entry.setExistsOnDisk(false);
                if (entry.getParentId() != null)
                    toRemove.add(entry);
                else
                    repo.save(entry);   // root: flag only, user decides
            } else if (!entry.isExistsOnDisk()) {
                entry.setExistsOnDisk(true);
                repo.save(entry);
            }
        }
        if (!toRemove.isEmpty()) {
            repo.deleteAll(toRemove);
            log.info("Removed {} stale entries", toRemove.size());
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

    private void scanDrive(File drive) {
        String path = drive.getAbsolutePath();
        if (path.endsWith("\\") || path.endsWith("/"))
            path = path.substring(0, path.length() - 1);
        FileEntry root = ensureEntry(path, path, null, path, false, null, 0);
        if (root != null)
            scanDirectory(drive, root.getId(), path, 1);
    }

    private void scanNetShare(NetworkShare share) {
        String path = share.getPath();
        while (path.endsWith("/") || path.endsWith("\\"))
            path = path.substring(0, path.length() - 1);

        // Attempt to connect (net use on Windows with credentials)
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
        FileEntry root = ensureEntry(path, path, null, path, false, null, 0);
        if (root != null)
            scanDirectory(dir, root.getId(), path, 1);
    }

    private void scanDirectory(File dir, Long parentId, String rootPath, int depth) {
        checkPause();
        if (scanStatus.isStopRequested()) return;

        File[] children;
        try {
            children = dir.listFiles();
        } catch (SecurityException e) {
            log.warn("Access denied: {}", dir.getAbsolutePath());
            return;
        }
        if (children == null) return;

        Arrays.sort(children, (a, b) -> {
            if (a.isDirectory() != b.isDirectory())
                return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File child : children) {
            checkPause();
            if (scanStatus.isStopRequested())
                return;

            String childPath = child.getAbsolutePath();
            scanStatus.setCurrentPath(childPath);

            if (child.isDirectory()) {
                if (fileService.isAnyAncestorDoNotProcess(childPath))
                    continue;
                FileEntry folderEntry = ensureEntry(childPath, child.getName(), parentId, rootPath, false, null, depth);
                if (folderEntry == null)
                    continue;
                if (folderEntry.isDoNotProcess()) {
                    fileService.removeChildrenOfDoNotProcess(folderEntry.getId(), childPath);
                    scanStatus.incrementFolders();
                    continue;
                }
                scanStatus.incrementFolders();
                scanDirectory(child, folderEntry.getId(), rootPath, depth + 1);
            } else if (child.isFile()) {
                long size = child.length();
                ensureEntry(childPath, child.getName(), parentId, rootPath, true, size, depth);
                scanStatus.incrementFiles(size);
            }
        }
    }

    @Transactional
    private FileEntry ensureEntry(String fullPath, String name, Long parentId,
                                   String rootPath, boolean isFile, Long sizeBytes, int depth) {
        try {
            Optional<FileEntry> existing = repo.findByFullPath(fullPath);
            if (existing.isPresent()) {
                FileEntry e = existing.get();
                e.setExistsOnDisk(true);
                if (isFile && sizeBytes != null)
                    e.setSizeBytes(sizeBytes);
                return repo.save(e);
            }
            return repo.save(new FileEntry(fullPath, name, parentId, rootPath, isFile, sizeBytes, depth));
        } catch (Exception e) {
            log.error("Failed to save entry: {}", fullPath, e);
            return null;
        }
    }
}
