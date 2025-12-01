package com.javamcp.dbmcp.util;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Component
public class QueryLogger {
    private static final int MAX_ENTRIES = 100;
    private final LinkedList<QueryLogEntry> logs = new LinkedList<>();

    @Data
    public static class QueryLogEntry {
        private LocalDateTime timestamp;
        private String sql;
        private long executionTime;
        private boolean success;
        private String error;

        public QueryLogEntry(String sql, long executionTime, boolean success, String error) {
            this.timestamp = LocalDateTime.now();
            this.sql = sql.length() > 500 ? sql.substring(0, 500) : sql;
            this.executionTime = executionTime;
            this.success = success;
            this.error = error;
        }
    }

    @Data
    public static class QueryStats {
        private int totalQueries;
        private double successRate;
        private long avgExecutionTime;
        private QueryLogEntry slowestQuery;
    }

    public void log(String sql, long executionTime, boolean success, String error) {
        synchronized (logs) {
            logs.add(new QueryLogEntry(sql, executionTime, success, error));
            if (logs.size() > MAX_ENTRIES) {
                logs.removeFirst();
            }
        }
    }

    public List<QueryLogEntry> getLogs() {
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }

    public QueryStats getStats() {
        synchronized (logs) {
            if (logs.isEmpty()) {
                QueryStats stats = new QueryStats();
                stats.setTotalQueries(0);
                stats.setSuccessRate(0);
                stats.setAvgExecutionTime(0);
                stats.setSlowestQuery(null);
                return stats;
            }

            long successful = logs.stream().filter(QueryLogEntry::isSuccess).count();
            long avgTime = (long) logs.stream().mapToLong(QueryLogEntry::getExecutionTime).average().orElse(0);
            QueryLogEntry slowest = logs.stream()
                    .max((a, b) -> Long.compare(a.getExecutionTime(), b.getExecutionTime()))
                    .orElse(null);

            QueryStats stats = new QueryStats();
            stats.setTotalQueries(logs.size());
            stats.setSuccessRate((successful * 100.0) / logs.size());
            stats.setAvgExecutionTime(avgTime);
            stats.setSlowestQuery(slowest);
            return stats;
        }
    }
}
