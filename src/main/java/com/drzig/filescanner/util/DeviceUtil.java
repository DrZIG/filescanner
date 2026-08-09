package com.drzig.filescanner.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

public final class DeviceUtil {

    private DeviceUtil() {}

    /** Best-effort machine identifier: env var first (fast, no DNS), hostname lookup as fallback. */
    public static String currentDeviceName() {
        String name = System.getenv("COMPUTERNAME");
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        try {
            name = InetAddress.getLocalHost().getHostName();
            if (name != null && !name.isBlank()) {
                return name.trim();
            }
        } catch (UnknownHostException e) {
            // fall through
        }
        return "unknown-device";
    }
}
