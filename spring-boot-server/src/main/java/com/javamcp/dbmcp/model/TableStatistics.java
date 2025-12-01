package com.javamcp.dbmcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TableStatistics {
    private String tableName;
    private String schema;
    private int rowCount;
    private Double sizeInMB;
    private Double indexSizeInMB;
    private Date lastAnalyzed;
    private Double fragmentation;
}
