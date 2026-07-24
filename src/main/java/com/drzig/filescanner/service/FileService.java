package com.drzig.filescanner.service;

import com.drzig.filescanner.dto.FileEntryDto;
import com.drzig.filescanner.model.FileEntry;
import com.drzig.filescanner.repository.FileEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class FileService {

    private final FileEntryRepository repo;
    private final IconService iconService;

    public FileService(FileEntryRepository repo, IconService iconService) {
        this.repo = repo;
        this.iconService = iconService;
    }

    public static String humanReadableSize(Long bytes) {
        if (bytes == null) return "";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        double gb = mb / 1024.0;
        if (gb < 1024) return String.format("%.1f GB", gb);
        return String.format("%.1f TB", gb / 1024.0);
    }

    public List<FileEntryDto> getRoots() {
        return repo.findByParentIdIsNullOrderByNameAsc().stream().map(this::toDto).toList();
    }

    public List<FileEntryDto> getChildren(Long parentId) {
        return repo.findByParentIdOrderByFileAscNameAsc(parentId).stream().map(this::toDto).toList();
    }

    public Optional<FileEntryDto> findById(Long id) {
        return repo.findById(id).map(this::toDto);
    }

    public Optional<FileEntry> findEntityById(Long id) {
        return repo.findById(id);
    }

    @Transactional
    public void toggleDoNotProcess(Long id, boolean value) {
        repo.findById(id).ifPresent(entry -> {
            if (!entry.isFile()) {
                entry.setDoNotProcess(value);
                repo.save(entry);
            }
        });
    }

    @Transactional
    public void removeChildrenOfDoNotProcess(Long folderId, String folderPath) {
        repo.deleteChildrenByPathPrefixes(folderPath + "\\%", folderPath + "/%", folderId);
    }

    @Transactional
    public void deleteByRootPath(String rootPath) {
        repo.deleteByRootPath(rootPath);
    }

    public long countByRootPath(String rootPath) {
        return repo.countByRootPath(rootPath);
    }

    public List<String> getAllRootPaths() {
        return repo.findAllRootPaths();
    }

    public boolean isAnyAncestorDoNotProcess(String fullPath) {
        String path = fullPath;
        while (true) {
            int lastSep = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/'));
            if (lastSep <= 1) break;
            String parent = path.substring(0, lastSep);
            if (parent.length() <= 2 && parent.contains(":")) break;
            path = parent;
            Optional<FileEntry> entry = repo.findByFullPath(path);
            if (entry.isPresent() && entry.get().isDoNotProcess()) return true;
        }
        return false;
    }

    public FileEntryDto toDto(FileEntry e) {
        FileEntryDto dto = new FileEntryDto();
        dto.setId(e.getId());
        dto.setFullPath(e.getFullPath());
        dto.setName(e.getName());
        dto.setParentId(e.getParentId());
        dto.setRootPath(e.getRootPath());
        dto.setFile(e.isFile());
        dto.setSizeBytes(e.getSizeBytes());
        dto.setSizeHuman(humanReadableSize(e.getSizeBytes()));
        dto.setDoNotProcess(e.isDoNotProcess());
        dto.setDepthLevel(e.getDepthLevel());
        dto.setExistsOnDisk(e.isExistsOnDisk());
        dto.setIcon(iconService.getIconForFile(e.getName(), !e.isFile()));
        if (!e.isFile()) {
            dto.setHasChildren(!repo.findByParentIdOrderByFileAscNameAsc(e.getId()).isEmpty());
        }
        return dto;
    }

    public Optional<FileEntry> findByFullPath(String path) {
        return repo.findByFullPath(path);
    }

    @Transactional
    public void deleteById(Long id) {
        repo.deleteById(id);
    }

    public List<FileEntry> findAll() {
        return repo.findAll();
    }

    public List<FileEntryDto> getBreadcrumb(Long id) {
        Deque<FileEntryDto> crumbs = new ArrayDeque<>();
        Optional<FileEntry> cur = repo.findById(id);
        while (cur.isPresent()) {
            crumbs.addFirst(toDto(cur.get()));
            Long pid = cur.get().getParentId();
            cur = pid != null ? repo.findById(pid) : Optional.empty();
        }
        return new ArrayList<>(crumbs);
    }
}
