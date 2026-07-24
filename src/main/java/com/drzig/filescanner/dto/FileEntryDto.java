package com.drzig.filescanner.dto;

public class FileEntryDto {
    private Long id;
    private String fullPath;
    private String name;
    private Long parentId;
    private String rootPath;
    private boolean file;
    private Long sizeBytes;
    private String sizeHuman;
    private boolean doNotProcess;
    private int depthLevel;
    private boolean existsOnDisk;
    private String icon;
    private boolean hasChildren;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public String getSizeHuman() { return sizeHuman; }
    public void setSizeHuman(String sizeHuman) { this.sizeHuman = sizeHuman; }
    public boolean isDoNotProcess() { return doNotProcess; }
    public void setDoNotProcess(boolean doNotProcess) { this.doNotProcess = doNotProcess; }
    public int getDepthLevel() { return depthLevel; }
    public void setDepthLevel(int depthLevel) { this.depthLevel = depthLevel; }
    public boolean isExistsOnDisk() { return existsOnDisk; }
    public void setExistsOnDisk(boolean existsOnDisk) { this.existsOnDisk = existsOnDisk; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public boolean isHasChildren() { return hasChildren; }
    public void setHasChildren(boolean hasChildren) { this.hasChildren = hasChildren; }
}
