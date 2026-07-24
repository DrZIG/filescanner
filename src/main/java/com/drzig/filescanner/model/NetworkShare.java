package com.drzig.filescanner.model;

import jakarta.persistence.*;

/**
 * Stores a configured network share path with optional Windows credentials.
 * Credentials are used to call 'net use' on Windows before scanning.
 */
@Entity
@Table(name = "network_shares")
public class NetworkShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UNC path, e.g. //drzig-nas or //drzig-nas/Films */
    @Column(name = "path", nullable = false, unique = true, length = 512)
    private String path;

    /** Optional display label */
    @Column(name = "label", length = 256)
    private String label;

    /** Windows domain or workgroup (optional) */
    @Column(name = "domain", length = 128)
    private String domain;

    /** Username for authentication (optional) */
    @Column(name = "username", length = 256)
    private String username;

    /** Password for authentication — stored in plain text in DB.
     *  For home/lab use this is acceptable; for production encrypt at rest. */
    @Column(name = "password", length = 512)
    private String password;

    /** Whether credentials are required (set after probe) */
    @Column(name = "requires_auth", nullable = false)
    private boolean requiresAuth = false;

    /** Whether this share is enabled for scanning */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Last connection status */
    @Column(name = "last_status", length = 256)
    private String lastStatus;

    public NetworkShare() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isRequiresAuth() { return requiresAuth; }
    public void setRequiresAuth(boolean requiresAuth) { this.requiresAuth = requiresAuth; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getLastStatus() { return lastStatus; }
    public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }
}
