package com.drzig.filescanner.dto;

import java.time.LocalDateTime;

public class ScanStatus {

    public enum State { IDLE, RUNNING, PAUSED, COMPLETED, ERROR }

    private State state = State.IDLE;
    private String currentPath = "";
    private String currentDrive = "";
    private long filesScanned = 0;
    private long foldersScanned = 0;
    private long totalBytes = 0;
    private String message = "";
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private volatile boolean stopRequested = false;
    private volatile boolean pauseRequested = false;

    public synchronized void incrementFiles(long bytes) {
        filesScanned++;
        totalBytes += bytes;
    }

    public synchronized void incrementFolders() {
        foldersScanned++;
    }

    public void reset() {
        state = State.RUNNING;
        currentPath = "";
        currentDrive = "";
        filesScanned = 0;
        foldersScanned = 0;
        totalBytes = 0;
        message = "";
        startTime = LocalDateTime.now();
        endTime = null;
        stopRequested = false;
        pauseRequested = false;
    }

    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public String getCurrentPath() { return currentPath; }
    public void setCurrentPath(String currentPath) { this.currentPath = currentPath; }
    public String getCurrentDrive() { return currentDrive; }
    public void setCurrentDrive(String currentDrive) { this.currentDrive = currentDrive; }
    public long getFilesScanned() { return filesScanned; }
    public void setFilesScanned(long filesScanned) { this.filesScanned = filesScanned; }
    public long getFoldersScanned() { return foldersScanned; }
    public void setFoldersScanned(long foldersScanned) { this.foldersScanned = foldersScanned; }
    public long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public boolean isStopRequested() { return stopRequested; }
    public void setStopRequested(boolean stopRequested) { this.stopRequested = stopRequested; }
    public boolean isPauseRequested() { return pauseRequested; }
    public void setPauseRequested(boolean pauseRequested) { this.pauseRequested = pauseRequested; }
}
