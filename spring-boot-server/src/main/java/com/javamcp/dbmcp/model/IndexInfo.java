package com.javamcp.dbmcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IndexInfo {
    private String indexName;
    private String tableName;
    private List<String> columns;
    private boolean isUnique;
    private String indexType;
    private String schema;
}
