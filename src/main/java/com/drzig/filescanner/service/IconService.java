package com.drzig.filescanner.service;

import com.drzig.filescanner.model.IconMapping;
import com.drzig.filescanner.repository.IconMappingRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class IconService {

    private final IconMappingRepository repo;

    public IconService(IconMappingRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    @Transactional
    public void initDefaults() {
        if (repo.count() > 0) return;
        // Archives
        sd("zip","extension","🗜️","ZIP Archive"); sd("rar","extension","🗜️","RAR Archive");
        sd("7z","extension","🗜️","7-Zip Archive"); sd("tar","extension","🗜️","TAR Archive");
        sd("gz","extension","🗜️","GZip Archive");
        // Video
        sd("mp4","extension","🎬","Video MP4"); sd("mkv","extension","🎬","Video MKV");
        sd("avi","extension","🎬","Video AVI"); sd("mov","extension","🎬","Video MOV");
        sd("wmv","extension","🎬","Video WMV"); sd("flv","extension","🎬","Video FLV");
        sd("m4v","extension","🎬","Video M4V");
        // Audio
        sd("mp3","extension","🎵","Audio MP3"); sd("flac","extension","🎵","Audio FLAC");
        sd("wav","extension","🎵","Audio WAV"); sd("aac","extension","🎵","Audio AAC");
        sd("ogg","extension","🎵","Audio OGG"); sd("m4a","extension","🎵","Audio M4A");
        // Images
        sd("jpg","extension","🖼️","Image JPEG"); sd("jpeg","extension","🖼️","Image JPEG");
        sd("png","extension","🖼️","Image PNG"); sd("gif","extension","🖼️","Image GIF");
        sd("bmp","extension","🖼️","Image BMP"); sd("svg","extension","🖼️","Image SVG");
        sd("webp","extension","🖼️","Image WebP"); sd("tiff","extension","🖼️","Image TIFF");
        sd("raw","extension","🖼️","RAW Photo"); sd("cr2","extension","🖼️","Canon RAW");
        sd("nef","extension","🖼️","Nikon RAW");
        // Documents
        sd("pdf","extension","📄","PDF Document"); sd("doc","extension","📝","Word Document");
        sd("docx","extension","📝","Word Document"); sd("xls","extension","📊","Excel");
        sd("xlsx","extension","📊","Excel"); sd("ppt","extension","📑","PowerPoint");
        sd("pptx","extension","📑","PowerPoint"); sd("txt","extension","📄","Text File");
        sd("md","extension","📄","Markdown"); sd("csv","extension","📊","CSV Data");
        // Code
        sd("java","extension","☕","Java Source"); sd("py","extension","🐍","Python");
        sd("js","extension","📜","JavaScript"); sd("ts","extension","📜","TypeScript");
        sd("html","extension","🌐","HTML"); sd("css","extension","🎨","CSS");
        sd("xml","extension","📋","XML"); sd("json","extension","📋","JSON");
        sd("sql","extension","🗄️","SQL"); sd("sh","extension","⚙️","Shell Script");
        sd("bat","extension","⚙️","Batch Script"); sd("ps1","extension","⚙️","PowerShell");
        // Executables
        sd("exe","extension","⚡","Executable"); sd("msi","extension","📦","Installer");
        sd("dll","extension","🔧","DLL"); sd("iso","extension","💿","Disk Image");
        // Fonts
        sd("ttf","extension","🔤","TrueType Font"); sd("otf","extension","🔤","OpenType Font");
        // Defaults
        sd("_default_file","extension","📄","File");
        sd("_default_folder","extension","📁","Folder");
    }

    private void sd(String pattern, String type, String icon, String label) {
        if (repo.findByPattern(pattern).isEmpty()) {
            repo.save(new IconMapping(pattern, type, icon, label));
        }
    }

    public String getIconForFile(String name, boolean isFolder) {
        if (isFolder) {
            for (IconMapping m : repo.findByPatternType("folder_name")) {
                if (name.equalsIgnoreCase(m.getPattern())) return m.getIcon();
            }
            return repo.findByPattern("_default_folder").map(IconMapping::getIcon).orElse("📁");
        }
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) ext = name.substring(dot + 1).toLowerCase();
        return repo.findByPattern(ext).map(IconMapping::getIcon)
                .orElse(repo.findByPattern("_default_file").map(IconMapping::getIcon).orElse("📄"));
    }

    public List<IconMapping> findAll() { return repo.findAll(); }

    @Transactional
    public IconMapping save(IconMapping mapping) { return repo.save(mapping); }

    @Transactional
    public void delete(Long id) { repo.deleteById(id); }

    public IconMapping findById(Long id) { return repo.findById(id).orElse(null); }
}
