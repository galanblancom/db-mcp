package com.indrard.dbmcp.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for processing uploaded files and extracting text content
 */
@Service
public class FileProcessingService {

    private static final Set<String> SUPPORTED_TEXT_EXTENSIONS = Set.of(
        "txt", "java", "py", "js", "ts", "jsx", "tsx", "html", "css", "scss", 
        "json", "xml", "yml", "yaml", "md", "properties", "conf", "config",
        "sql", "sh", "bash", "bat", "ps1", "c", "cpp", "h", "hpp", "cs",
        "go", "rs", "rb", "php", "swift", "kt", "gradle", "maven", "log", "csv"
    );

    private static final Set<String> SUPPORTED_DOCUMENT_EXTENSIONS = Set.of(
        "pdf", "docx", "doc"
    );

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB (increased for larger files)
    private static final int MAX_CONTENT_LENGTH = 5 * 1024 * 1024; // 5MB max to hold in memory at once

    /**
     * Extract text content from uploaded file
     */
    public FileContent extractContent(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IOException("File size exceeds maximum allowed size of 10MB");
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IOException("Filename is null");
        }

        String extension = getFileExtension(filename).toLowerCase();
        
        if (!SUPPORTED_TEXT_EXTENSIONS.contains(extension) && !SUPPORTED_DOCUMENT_EXTENSIONS.contains(extension)) {
            Set<String> allSupported = new HashSet<>();
            allSupported.addAll(SUPPORTED_TEXT_EXTENSIONS);
            allSupported.addAll(SUPPORTED_DOCUMENT_EXTENSIONS);
            throw new IOException("Unsupported file type: " + extension + 
                ". Supported types: " + String.join(", ", allSupported));
        }

        String content = extractContentByType(file, extension);
        
        // Add metadata about file type for better chunking strategies
        boolean isStructured = extension.equals("csv") || extension.equals("json") || extension.equals("xml");
        boolean isDocument = SUPPORTED_DOCUMENT_EXTENSIONS.contains(extension);
        
        // Count rows for CSV files
        Integer totalRows = null;
        if (extension.equals("csv")) {
            totalRows = countCsvRows(content);
            System.out.println("📊 CSV file detected: " + filename + " - Total rows: " + totalRows);
        }
        
        return new FileContent(
            filename,
            extension,
            content,
            file.getSize(),
            new Date(),
            isStructured,
            isDocument,
            totalRows
        );
    }

    /**
     * Extract content from multiple files
     */
    public List<FileContent> extractContents(List<MultipartFile> files) {
        List<FileContent> contents = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                contents.add(extractContent(file));
            } catch (IOException e) {
                errors.add(file.getOriginalFilename() + ": " + e.getMessage());
            }
        }

        if (!errors.isEmpty() && contents.isEmpty()) {
            throw new RuntimeException("Failed to process any files: " + String.join("; ", errors));
        }

        return contents;
    }
    
    /**
     * Count the number of data rows in a CSV file (excluding header)
     */
    private Integer countCsvRows(String content) {
        if (content == null || content.trim().isEmpty()) {
            return 0;
        }
        
        String[] lines = content.split("\n");
        int count = 0;
        
        // Start from 1 to skip header row
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) {
                count++;
            }
        }
        
        return count;
    }

    /**
     * Extract content based on file type
     */
    private String extractContentByType(MultipartFile file, String extension) throws IOException {
        if (SUPPORTED_DOCUMENT_EXTENSIONS.contains(extension)) {
            return extractDocumentContent(file, extension);
        } else {
            return readTextContent(file);
        }
    }

    /**
     * Extract text from Word and PDF documents
     */
    private String extractDocumentContent(MultipartFile file, String extension) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            switch (extension) {
                case "pdf":
                    return extractPdfContent(inputStream);
                case "docx":
                    return extractDocxContent(inputStream);
                case "doc":
                    return extractDocContent(inputStream);
                default:
                    throw new IOException("Unsupported document type: " + extension);
            }
        }
    }

    /**
     * Extract text from PDF
     */
    private String extractPdfContent(InputStream inputStream) throws IOException {
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            
            // Apply memory limit
            if (text.length() > MAX_CONTENT_LENGTH) {
                return text.substring(0, MAX_CONTENT_LENGTH) + 
                       "\n\n[... Content truncated due to size. Only first " + 
                       MAX_CONTENT_LENGTH + " characters loaded ...]";
            }
            return text;
        }
    }

    /**
     * Extract text from DOCX (Word 2007+)
     */
    private String extractDocxContent(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder content = new StringBuilder();
            int totalChars = 0;
            
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    if (totalChars + text.length() > MAX_CONTENT_LENGTH) {
                        content.append("\n\n[... Content truncated due to size ...]");
                        break;
                    }
                    content.append(text).append("\n");
                    totalChars += text.length() + 1;
                }
            }
            
            return content.toString();
        }
    }

    /**
     * Extract text from DOC (Word 97-2003)
     */
    private String extractDocContent(InputStream inputStream) throws IOException {
        try (HWPFDocument document = new HWPFDocument(inputStream)) {
            WordExtractor extractor = new WordExtractor(document);
            String text = extractor.getText();
            
            // Apply memory limit
            if (text.length() > MAX_CONTENT_LENGTH) {
                return text.substring(0, MAX_CONTENT_LENGTH) + 
                       "\n\n[... Content truncated due to size ...]";
            }
            return text;
        }
    }

    /**
     * Read text content from file with memory limits
     */
    private String readTextContent(MultipartFile file) throws IOException {
        StringBuilder content = new StringBuilder();
        int totalChars = 0;
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                // Check if we're exceeding memory limit
                if (totalChars + line.length() > MAX_CONTENT_LENGTH) {
                    content.append("\n\n[... Content truncated due to size. Total file size: ")
                           .append(file.getSize())
                           .append(" bytes. Only first ")
                           .append(MAX_CONTENT_LENGTH)
                           .append(" characters loaded ...]");
                    break;
                }
                
                if (content.length() > 0) {
                    content.append("\n");
                    totalChars++;
                }
                content.append(line);
                totalChars += line.length();
            }
            
            return content.toString();
            
        } catch (Exception e) {
            // Try with default charset if UTF-8 fails
            content = new StringBuilder();
            totalChars = 0;
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(file.getInputStream()))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    if (totalChars + line.length() > MAX_CONTENT_LENGTH) {
                        content.append("\n\n[... Content truncated ...]");
                        break;
                    }
                    
                    if (content.length() > 0) {
                        content.append("\n");
                        totalChars++;
                    }
                    content.append(line);
                    totalChars += line.length();
                }
                
                return content.toString();
            }
        }
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1);
        }
        return "";
    }

    /**
     * Check if file type is supported
     */
    public boolean isSupported(String filename) {
        String extension = getFileExtension(filename).toLowerCase();
        return SUPPORTED_TEXT_EXTENSIONS.contains(extension) || SUPPORTED_DOCUMENT_EXTENSIONS.contains(extension);
    }

    /**
     * File content model
     */
    public static class FileContent {
        private final String filename;
        private final String extension;
        private final String content;
        private final long size;
        private final Date uploadedAt;
        private final boolean isStructured;
        private final boolean isDocument;
        private final Integer totalRows; // For CSV files - total number of data rows (excluding header)

        public FileContent(String filename, String extension, String content, long size, Date uploadedAt, boolean isStructured, boolean isDocument) {
            this(filename, extension, content, size, uploadedAt, isStructured, isDocument, null);
        }
        
        public FileContent(String filename, String extension, String content, long size, Date uploadedAt, boolean isStructured, boolean isDocument, Integer totalRows) {
            this.filename = filename;
            this.extension = extension;
            this.content = content;
            this.size = size;
            this.uploadedAt = uploadedAt;
            this.isStructured = isStructured;
            this.isDocument = isDocument;
            this.totalRows = totalRows;
        }

        public String getFilename() { return filename; }
        public String getExtension() { return extension; }
        public String getContent() { return content; }
        public long getSize() { return size; }
        public Date getUploadedAt() { return uploadedAt; }
        public boolean isStructured() { return isStructured; }
        public boolean isDocument() { return isDocument; }
        public Integer getTotalRows() { return totalRows; }

        /**
         * Get content summary for display
         */
        public String getSummary() {
            int maxLength = 500;
            if (content.length() <= maxLength) {
                return content;
            }
            return content.substring(0, maxLength) + "\n... (truncated, " + 
                   (content.length() - maxLength) + " more characters)";
        }

        /**
         * Create metadata map for ChromaDB
         */
        public Map<String, String> toMetadata() {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("filename", filename);
            metadata.put("extension", extension);
            metadata.put("size", String.valueOf(size));
            metadata.put("uploaded_at", uploadedAt.toString());
            
            // Determine file category
            if (isDocument) {
                metadata.put("type", "document");
            } else if (isStructured) {
                metadata.put("type", "structured_data");
            } else {
                metadata.put("type", "text_file");
            }
            
            metadata.put("structured", String.valueOf(isStructured));
            metadata.put("document", String.valueOf(isDocument));
            
            // Add total rows for CSV files
            if (totalRows != null) {
                metadata.put("total_rows", String.valueOf(totalRows));
            }
            
            return metadata;
        }
    }
}
