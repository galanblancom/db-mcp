package com.javamcp.dbmcp.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class FolderContextService {

    private static final int MAX_FILE_SIZE = 50 * 1024; // 50KB max per file for summary
    private static final int MAX_TOTAL_SIZE = 500 * 1024; // 500KB total max
    private static final List<String> IGNORED_EXTENSIONS = List.of(
            ".class", ".jar", ".war", ".exe", ".bin", ".so", ".dll", ".dylib",
            ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".ico", ".svg",
            ".mp3", ".mp4", ".avi", ".mov", ".wav",
            ".zip", ".tar", ".gz", ".rar", ".7z"
    );

    /**
     * Generate a summary of folder contents including all subfolders
     *
     * @param folderPaths List of folder paths to summarize
     * @return A formatted string containing the folder structure and file summaries
     */
    public String generateFolderSummary(List<String> folderPaths) {
        return generateContext(folderPaths, null);
    }

    /**
     * Generate a summary of specific files
     *
     * @param filePaths List of file paths to summarize
     * @return A formatted string containing the file summaries
     */
    public String generateFileSummary(List<String> filePaths) {
        return generateContext(null, filePaths);
    }

    /**
     * Generate context from both folders and individual files
     *
     * @param folderPaths List of folder paths to summarize
     * @param filePaths List of individual file paths to summarize
     * @return A formatted string containing the context
     */
    public String generateContext(List<String> folderPaths, List<String> filePaths) {
        if ((folderPaths == null || folderPaths.isEmpty()) && (filePaths == null || filePaths.isEmpty())) {
            return "";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("=== FOLDER CONTEXT ===\n\n");

        // Process folders
        if (folderPaths != null && !folderPaths.isEmpty()) {
            for (String folderPath : folderPaths) {
                try {
                    Path path = Paths.get(folderPath);
                    if (!Files.exists(path)) {
                        summary.append(String.format("⚠️ Folder not found: %s\n\n", folderPath));
                        continue;
                    }

                    if (!Files.isDirectory(path)) {
                        summary.append(String.format("⚠️ Not a directory: %s\n\n", folderPath));
                        continue;
                    }

                    summary.append(String.format("📁 Folder: %s\n", folderPath));
                    summary.append(generateFolderStructure(path));
                    summary.append("\n");
                    summary.append(generateFileSummaries(path));
                    summary.append("\n");

                } catch (Exception e) {
                    summary.append(String.format("❌ Error reading folder %s: %s\n\n", folderPath, e.getMessage()));
                }
            }
        }

        // Process individual files
        if (filePaths != null && !filePaths.isEmpty()) {
            summary.append("📄 Individual Files:\n\n");
            for (String filePath : filePaths) {
                try {
                    Path path = Paths.get(filePath);
                    if (!Files.exists(path)) {
                        summary.append(String.format("⚠️ File not found: %s\n\n", filePath));
                        continue;
                    }

                    if (!Files.isRegularFile(path)) {
                        summary.append(String.format("⚠️ Not a file: %s\n\n", filePath));
                        continue;
                    }

                    summary.append(String.format("--- %s ---\n", filePath));
                    String content = Files.readString(path);
                    String fileSummary = summarizeFileContent(content, filePath);
                    summary.append(fileSummary);
                    summary.append("\n\n");

                } catch (Exception e) {
                    summary.append(String.format("❌ Error reading file %s: %s\n\n", filePath, e.getMessage()));
                }
            }
        }

        summary.append("=== END FOLDER CONTEXT ===\n");
        return summary.toString();
    }

    /**
     * Generate a tree structure of the folder
     */
    private String generateFolderStructure(Path rootPath) throws IOException {
        StringBuilder structure = new StringBuilder();
        structure.append("\n📂 Structure:\n");

        List<Path> allPaths = new ArrayList<>();
        Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!shouldIgnoreFile(file)) {
                    allPaths.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (shouldIgnoreDirectory(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                allPaths.add(dir);
                return FileVisitResult.CONTINUE;
            }
        });

        for (Path path : allPaths) {
            String relativePath = rootPath.relativize(path).toString();
            if (relativePath.isEmpty()) continue;
            
            int depth = relativePath.split(Pattern.quote(path.getFileSystem().getSeparator())).length - 1;
            String indent = "  ".repeat(depth);
            String icon = Files.isDirectory(path) ? "📁" : "📄";
            structure.append(String.format("%s%s %s\n", indent, icon, path.getFileName()));
        }

        return structure.toString();
    }

    /**
     * Generate summaries of file contents
     */
    private String generateFileSummaries(Path rootPath) throws IOException {
        StringBuilder summaries = new StringBuilder();
        summaries.append("📄 File Summaries:\n\n");

        List<Path> files = new ArrayList<>();
        Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!shouldIgnoreFile(file) && attrs.size() < MAX_FILE_SIZE) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (shouldIgnoreDirectory(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });

        long totalSize = 0;
        for (Path file : files) {
            try {
                long fileSize = Files.size(file);
                if (totalSize + fileSize > MAX_TOTAL_SIZE) {
                    summaries.append("\n⚠️ Context limit reached. Remaining files omitted.\n");
                    break;
                }

                String relativePath = rootPath.relativize(file).toString();
                summaries.append(String.format("--- %s ---\n", relativePath));

                String content = Files.readString(file);
                String summary = summarizeFileContent(content, file.toString());
                summaries.append(summary);
                summaries.append("\n\n");

                totalSize += fileSize;

            } catch (IOException e) {
                summaries.append(String.format("⚠️ Could not read file: %s\n\n", e.getMessage()));
            }
        }

        return summaries.toString();
    }

    /**
     * Summarize file content - truncate if too long and provide key information
     */
    private String summarizeFileContent(String content, String filePath) {
        int lines = content.split("\n").length;
        int chars = content.length();

        // For small files, include full content
        if (chars <= 1000) {
            return String.format("Lines: %d | Chars: %d\n%s", lines, chars, content);
        }

        // For larger files, provide summary
        String preview = content.substring(0, Math.min(500, content.length()));
        String suffix = content.length() > 500 ? "\n... (truncated)" : "";

        return String.format("Lines: %d | Chars: %d | Preview:\n%s%s", 
                lines, chars, preview, suffix);
    }

    /**
     * Check if file should be ignored based on extension or name
     */
    private boolean shouldIgnoreFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        
        // Ignore hidden files
        if (fileName.startsWith(".")) {
            return true;
        }

        // Ignore by extension
        for (String ext : IGNORED_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if directory should be ignored
     */
    private boolean shouldIgnoreDirectory(Path dir) {
        String dirName = dir.getFileName().toString();
        
        // Ignore common directories
        List<String> ignoredDirs = List.of(
                ".git", ".svn", ".hg",
                "node_modules", "target", "build", "dist", "out",
                ".idea", ".vscode", ".settings",
                "__pycache__", ".pytest_cache",
                "bin", "obj"
        );

        return ignoredDirs.contains(dirName) || dirName.startsWith(".");
    }
}
