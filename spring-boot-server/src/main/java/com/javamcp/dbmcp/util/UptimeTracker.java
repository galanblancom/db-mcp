package com.javamcp.dbmcp.util;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class UptimeTracker {
    private final Instant startTime = Instant.now();

    public long getUptimeMillis() {
        return Duration.between(startTime, Instant.now()).toMillis();
    }

    public String getUptimeFormatted() {
        long ms = getUptimeMillis();
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
        }
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        }
        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        }
        return String.format("%ds", seconds);
    }
}
