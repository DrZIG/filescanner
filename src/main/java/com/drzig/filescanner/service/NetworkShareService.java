package com.drzig.filescanner.service;

import com.drzig.filescanner.model.NetworkShare;
import com.drzig.filescanner.repository.NetworkShareRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NetworkShareService {

    private static final Logger log = LoggerFactory.getLogger(NetworkShareService.class);

    private final NetworkShareRepository repo;

    public NetworkShareService(NetworkShareRepository repo) {
        this.repo = repo;
    }

    public List<NetworkShare> findAll() {
        return repo.findAll();
    }

    public List<NetworkShare> findEnabled() {
        return repo.findByEnabledTrue();
    }

    public Optional<NetworkShare> findById(Long id) {
        return repo.findById(id);
    }

    @Transactional
    public NetworkShare save(NetworkShare share) {
        return repo.save(share);
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }

    /**
     * Probe a UNC path to determine if it is accessible and whether credentials are needed.
     * On Windows, attempts 'net use' with and without credentials.
     * Returns a map with keys: accessible (boolean), requiresAuth (boolean), message (String).
     */
    public Map<String, Object> probeShare(String path, String domain, String username, String password) {
        // Normalise path to use backslashes on Windows for net use
        String uncPath = path.replace('/', '\\');
        if (!uncPath.startsWith("\\\\")) {
            uncPath = "\\\\" + uncPath.replaceAll("^\\\\+", "");
        }

        // First: try accessing without credentials
        File dir = new File(path);
        if (dir.exists() && dir.isDirectory()) {
            return Map.of("accessible", true, "requiresAuth", false,
                    "message", "Share is accessible without credentials.");
        }

        // On Windows, try connecting with net use
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (!isWindows) {
            return Map.of("accessible", false, "requiresAuth", false,
                    "message", "Share not accessible. On non-Windows systems, mount the share manually.");
        }

        // Try net use without credentials to see the error
        try {
            String[] connectCmd = buildNetUseCmd(uncPath, domain, username, password);
            ProcessBuilder pb = new ProcessBuilder(connectCmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes());
            int exitCode = proc.waitFor();

            if (exitCode == 0 || new File(path).exists()) {
                return Map.of("accessible", true, "requiresAuth",
                        username != null && !username.isBlank(),
                        "message", "Connected successfully.");
            }

            // Check if it's an auth error (error 5 = access denied, error 1326 = wrong credentials)
            boolean authRequired = output.contains("1326") || output.contains("Access is denied")
                    || output.contains("error 5") || output.contains("System error 5");
            return Map.of("accessible", false, "requiresAuth", authRequired,
                    "message", "Cannot connect: " + output.trim());

        } catch (Exception e) {
            log.warn("Error probing share {}: {}", path, e.getMessage());
            return Map.of("accessible", false, "requiresAuth", false,
                    "message", "Probe failed: " + e.getMessage());
        }
    }

    /**
     * Connect a share using 'net use' with stored credentials.
     * Called before scanning on Windows.
     */
    public boolean connectShare(NetworkShare share) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (!isWindows) return new File(share.getPath()).exists();

        String uncPath = toUncBackslash(share.getPath());
        try {
            String[] cmd = buildNetUseCmd(uncPath, share.getDomain(), share.getUsername(), share.getPassword());
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes());
            int exitCode = proc.waitFor();
            boolean ok = exitCode == 0 || new File(share.getPath()).exists();
            share.setLastStatus(ok ? "Connected" : "Failed: " + output.trim());
            repo.save(share);
            return ok;
        } catch (Exception e) {
            log.error("Failed to connect share {}", share.getPath(), e);
            share.setLastStatus("Error: " + e.getMessage());
            repo.save(share);
            return false;
        }
    }

    private String[] buildNetUseCmd(String uncPath, String domain, String username, String password) {
        if (username != null && !username.isBlank()) {
            String userArg = (domain != null && !domain.isBlank())
                    ? domain + "\\" + username : username;
            String pwd = (password != null) ? password : "";
            return new String[]{"net", "use", uncPath, pwd, "/user:" + userArg, "/persistent:no"};
        } else {
            return new String[]{"net", "use", uncPath, "/persistent:no"};
        }
    }

    private String toUncBackslash(String path) {
        String p = path.replace('/', '\\');
        if (!p.startsWith("\\\\")) {
            p = "\\\\" + p.replaceAll("^\\\\+", "");
        }
        return p;
    }
}
