package com.indrard.dbmcp.service;

import com.indrard.dbmcp.model.QueryResult;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service to cache export data temporarily with automatic expiration
 * Allows generating simple download URLs without exposing SQL queries
 */
@Service
public class ExportCacheService {
    
    private static final long CACHE_EXPIRATION_MINUTES = 30;
    
    private final Map<String, CachedExport> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    public ExportCacheService() {
        // Clean expired entries every 5 minutes
        scheduler.scheduleAtFixedRate(this::cleanExpiredEntries, 5, 5, TimeUnit.MINUTES);
    }
    
    /**
     * Store export data and return a unique download ID
     */
    public String cacheExport(String sql, String title, QueryResult result) {
        String exportId = UUID.randomUUID().toString();
        CachedExport export = new CachedExport(sql, title, result);
        cache.put(exportId, export);
        return exportId;
    }
    
    /**
     * Retrieve cached export data by ID
     */
    public CachedExport getExport(String exportId) {
        CachedExport export = cache.get(exportId);
        if (export != null && !export.isExpired()) {
            return export;
        }
        // Remove if expired
        if (export != null) {
            cache.remove(exportId);
        }
        return null;
    }
    
    /**
     * Clean all expired entries from cache
     */
    private void cleanExpiredEntries() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * Get cache size for monitoring
     */
    public int getCacheSize() {
        return cache.size();
    }
    
    /**
     * Cached export data with expiration
     */
    public static class CachedExport {
        private final String sql;
        private final String title;
        private final QueryResult result;
        private final long createdAt;
        
        public CachedExport(String sql, String title, QueryResult result) {
            this.sql = sql;
            this.title = title;
            this.result = result;
            this.createdAt = System.currentTimeMillis();
        }
        
        public boolean isExpired() {
            long ageMinutes = (System.currentTimeMillis() - createdAt) / (1000 * 60);
            return ageMinutes > CACHE_EXPIRATION_MINUTES;
        }
        
        public String getSql() {
            return sql;
        }
        
        public String getTitle() {
            return title;
        }
        
        public QueryResult getResult() {
            return result;
        }
    }
}
