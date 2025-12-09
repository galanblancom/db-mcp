package com.indrard.dbmcp.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Service for generating files (PDF, Excel, Word, CSV) from chat responses and query results
 */
@Service
public class FileGenerationService {

    private final MessageSource messageSource;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public FileGenerationService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Generate PDF report from chat conversation
     * 
     * @param title Report title
     * @param messages List of messages (each message is a Map with "role" and "content")
     * @return PDF file as byte array
     */
    public byte[] generatePdfReport(String title, List<Map<String, String>> messages) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {
            
            // Title
            Paragraph titlePara = new Paragraph(title)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(20)
                    .setBold();
            document.add(titlePara);
            
            // Generated date
            String dateLabel = getMessage("export.generated.date");
            Paragraph datePara = new Paragraph(dateLabel + ": " + dateFormat.format(new Date()))
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setFontSize(10);
            document.add(datePara);
            
            document.add(new Paragraph("\n"));
            
            // Messages
            for (Map<String, String> message : messages) {
                String role = message.get("role");
                String content = message.get("content");
                
                // Role header
                String roleLabel = getMessage("export.role." + role.toLowerCase());
                Text roleText = new Text(roleLabel + ":\n").setBold().setFontSize(12);
                Paragraph messagePara = new Paragraph()
                        .add(roleText)
                        .add(new Text(content).setFontSize(11));
                
                document.add(messagePara);
                document.add(new Paragraph("\n"));
            }
        }
        
        return baos.toByteArray();
    }

    /**
     * Generate Word document from chat conversation
     * 
     * @param title Document title
     * @param messages List of messages
     * @return Word document as byte array
     */
    public byte[] generateWordDocument(String title, List<Map<String, String>> messages) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (XWPFDocument document = new XWPFDocument()) {
            // Title
            XWPFParagraph titlePara = document.createParagraph();
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText(title);
            titleRun.setBold(true);
            titleRun.setFontSize(20);
            
            // Generated date
            String dateLabel = getMessage("export.generated.date");
            XWPFParagraph datePara = document.createParagraph();
            XWPFRun dateRun = datePara.createRun();
            dateRun.setText(dateLabel + ": " + dateFormat.format(new Date()));
            dateRun.setFontSize(10);
            
            document.createParagraph(); // Empty line
            
            // Messages
            for (Map<String, String> message : messages) {
                String role = message.get("role");
                String content = message.get("content");
                
                XWPFParagraph messagePara = document.createParagraph();
                
                // Role
                String roleLabel = getMessage("export.role." + role.toLowerCase());
                XWPFRun roleRun = messagePara.createRun();
                roleRun.setText(roleLabel + ":");
                roleRun.setBold(true);
                roleRun.setFontSize(12);
                roleRun.addBreak();
                
                // Content
                XWPFRun contentRun = messagePara.createRun();
                contentRun.setText(content);
                contentRun.setFontSize(11);
                
                document.createParagraph(); // Empty line
            }
            
            document.write(baos);
        }
        
        return baos.toByteArray();
    }

    /**
     * Generate CSV from query results
     * 
     * @param headers Column headers
     * @param rows Data rows
     * @return CSV content as string
     */
    public String generateCsv(List<String> headers, List<List<Object>> rows) throws IOException {
        StringWriter writer = new StringWriter();
        
        try (CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(headers.toArray(new String[0])))) {
            for (List<Object> row : rows) {
                csvPrinter.printRecord(row);
            }
        }
        
        return writer.toString();
    }

    /**
     * Generate Excel-compatible CSV from query results
     * Uses semicolon separator and UTF-8 BOM for Excel compatibility
     * 
     * @param headers Column headers
     * @param rows Data rows
     * @return CSV content as byte array with BOM
     */
    public byte[] generateExcelCsv(List<String> headers, List<List<Object>> rows) throws IOException {
        StringWriter writer = new StringWriter();
        
        // Excel-friendly format: semicolon separator, RFC4180 quoting
        CSVFormat format = CSVFormat.EXCEL
                .withDelimiter(';')
                .withHeader(headers.toArray(new String[0]));
        
        try (CSVPrinter csvPrinter = new CSVPrinter(writer, format)) {
            for (List<Object> row : rows) {
                csvPrinter.printRecord(row);
            }
        }
        
        // Add UTF-8 BOM for Excel recognition
        byte[] utf8Bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = writer.toString().getBytes("UTF-8");
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(utf8Bom);
        baos.write(content);
        
        return baos.toByteArray();
    }

    /**
     * Generate PDF report from query results
     * 
     * @param title Report title
     * @param headers Column headers
     * @param rows Data rows
     * @return PDF as byte array
     */
    public byte[] generateQueryResultPdf(String title, List<String> headers, List<List<Object>> rows) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {
            
            // Title
            Paragraph titlePara = new Paragraph(title)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(16)
                    .setBold();
            document.add(titlePara);
            
            // Generated date
            String dateLabel = getMessage("export.generated.date");
            Paragraph datePara = new Paragraph(dateLabel + ": " + dateFormat.format(new Date()))
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setFontSize(9);
            document.add(datePara);
            
            document.add(new Paragraph("\n"));
            
            // Headers
            StringBuilder headerLine = new StringBuilder();
            for (int i = 0; i < headers.size(); i++) {
                if (i > 0) headerLine.append(" | ");
                headerLine.append(headers.get(i));
            }
            
            Paragraph headerPara = new Paragraph(headerLine.toString())
                    .setBold()
                    .setFontSize(10);
            document.add(headerPara);
            
            // Separator
            document.add(new Paragraph("─".repeat(100)).setFontSize(8));
            
            // Data rows
            for (List<Object> row : rows) {
                StringBuilder rowLine = new StringBuilder();
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) rowLine.append(" | ");
                    rowLine.append(row.get(i) != null ? row.get(i).toString() : "");
                }
                
                Paragraph rowPara = new Paragraph(rowLine.toString())
                        .setFontSize(9);
                document.add(rowPara);
            }
            
            // Footer
            document.add(new Paragraph("\n"));
            String footerText = getMessage("export.total.rows") + ": " + rows.size();
            Paragraph footerPara = new Paragraph(footerText)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setFontSize(9)
                    .setItalic();
            document.add(footerPara);
        }
        
        return baos.toByteArray();
    }

    /**
     * Get localized message
     */
    private String getMessage(String code) {
        return messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
    }
}
