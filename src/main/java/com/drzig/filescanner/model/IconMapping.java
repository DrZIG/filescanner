package com.drzig.filescanner.model;

import jakarta.persistence.*;

@Entity
@Table(name = "icon_mappings")
public class IconMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pattern", nullable = false, unique = true, length = 128)
    private String pattern;

    @Column(name = "pattern_type", nullable = false, length = 32)
    private String patternType;

    @Column(name = "icon", nullable = false, length = 64)
    private String icon;

    @Column(name = "label", length = 128)
    private String label;

    public IconMapping() {}

    public IconMapping(String pattern, String patternType, String icon, String label) {
        this.pattern = pattern;
        this.patternType = patternType;
        this.icon = icon;
        this.label = label;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public String getPatternType() { return patternType; }
    public void setPatternType(String patternType) { this.patternType = patternType; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
