package com.javamcp.dbmcp.model.request;

import lombok.Data;

import java.util.List;

@Data
public class FolderContextRequest {
    private String message;
    private List<String> contextFolders; // List of folder paths to include as context
    private List<String> contextFiles; // List of individual file paths to include as context
    private String threadId; // Optional thread ID for conversation continuity
}
