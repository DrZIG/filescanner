package com.drzig.filescanner.model;

import jakarta.persistence.*;

@Entity
@Table(name = "file_entries", indexes = {
        @Index(name = "idx_parent_id", columnList = "parent_id"),
        @Index(name = "idx_device_full_path", columnList = "device_name, full_path"),
        @Index(name = "idx_device_root_path", columnList = "device_name, root_path")
})
public class FileEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_name", nullable = false, length = 128)
    private String deviceName;

    @Column(name = "full_path", nullable = false, length = 2048)
    private String fullPath;

    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "root_path", nullable = false, length = 256)
    private String rootPath;

    @Column(name = "is_file", nullable = false)
    private boolean file;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "do_not_process", nullable = false)
    private boolean doNotProcess = false;

    @Column(name = "depth_level", nullable = false)
    private int depthLevel = 0;

    @Column(name = "exists_on_disk", nullable = false)
    private boolean existsOnDisk = true;

    public FileEntry() {}

    public FileEntry(String fullPath, String name, Long parentId, String rootPath, String deviceName,
                     boolean isFile, Long sizeBytes, int depthLevel) {
        this.fullPath = fullPath;
        this.name = name;
        this.parentId = parentId;
        this.rootPath = rootPath;
        this.deviceName = deviceName;
        this.file = isFile;
        this.sizeBytes = sizeBytes;
        this.depthLevel = depthLevel;
        this.doNotProcess = false;
        this.existsOnDisk = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getFullPath() { return fullPath; }
    public void setFullPath(String fullPath) { this.fullPath = fullPath; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getRootPath() { return rootPath; }
    public void setRootPath(String rootPath) { this.rootPath = rootPath; }
    public boolean isFile() { return file; }
    public void setFile(boolean file) { this.file = file; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public boolean isDoNotProcess() { return doNotProcess; }
    public void setDoNotProcess(boolean doNotProcess) { this.doNotProcess = doNotProcess; }
    public int getDepthLevel() { return depthLevel; }
    public void setDepthLevel(int depthLevel) { this.depthLevel = depthLevel; }
    public boolean isExistsOnDisk() { return existsOnDisk; }
    public void setExistsOnDisk(boolean existsOnDisk) { this.existsOnDisk = existsOnDisk; }
}
