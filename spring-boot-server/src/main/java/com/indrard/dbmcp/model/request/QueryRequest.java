package com.indrard.dbmcp.model.request;

import lombok.Data;

@Data
public class QueryRequest {
    private String sql;
    private Integer maxRows;
    private Boolean excludeLargeColumns;
    private String format; // json, csv, table
    private Boolean dryRun;
    private Integer page;
    private Integer pageSize;
}
