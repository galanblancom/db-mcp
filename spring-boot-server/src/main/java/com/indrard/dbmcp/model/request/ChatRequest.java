package com.indrard.dbmcp.model.request;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String threadId;
    private Boolean useChromaDB;
    private String collectionName;
}
