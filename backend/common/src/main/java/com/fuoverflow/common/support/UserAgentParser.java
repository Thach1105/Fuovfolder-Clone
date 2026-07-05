package com.fuoverflow.common.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UserAgentParser {

    private UserAgentParser() {}

    public record DeviceLabel(String browser, String os, String label) {}

    private static final Pattern EDGE = Pattern.compile("Edg/(\\d+)");
    private static final Pattern CHROME = Pattern.compile("Chrome/(\\d+)");
    private static final Pattern FIREFOX = Pattern.compile("Firefox/(\\d+)");
    private static final Pattern SAFARI_VERSION = Pattern.compile("Version/(\\d+)");
    private static final Pattern OPERA = Pattern.compile("OPR/(\\d+)");

    public static DeviceLabel parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new DeviceLabel("Unknown browser", "Unknown OS", "Unknown browser on Unknown OS");
        }
        String browser = parseBrowser(userAgent);
        String os = parseOs(userAgent);
        return new DeviceLabel(browser, os, browser + " on " + os);
    }

    private static String parseBrowser(String ua) {
        Matcher m;
        m = EDGE.matcher(ua);
        if (m.find()) return "Edge " + m.group(1);
        m = OPERA.matcher(ua);
        if (m.find()) return "Opera " + m.group(1);
        m = CHROME.matcher(ua);
        if (m.find()) {
            if (!ua.contains("Safari")) return "Chrome " + m.group(1);
            return "Chrome " + m.group(1);
        }
        m = FIREFOX.matcher(ua);
        if (m.find()) return "Firefox " + m.group(1);
        if (ua.contains("Safari")) {
            m = SAFARI_VERSION.matcher(ua);
            if (m.find()) return "Safari " + m.group(1);
        }
        return "Unknown browser";
    }

    private static String parseOs(String ua) {
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) return "iOS";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) return "macOS";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Linux")) return "Linux";
        if (ua.contains("CrOS")) return "Chrome OS";
        return "Unknown OS";
    }
}
