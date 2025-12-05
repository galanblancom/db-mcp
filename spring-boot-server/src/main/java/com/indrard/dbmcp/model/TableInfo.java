package com.indrard.dbmcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TableInfo {
    private String tableName;
    private String owner;
    private String schema;
    private int rowCount;
    private List<ColumnInfo> columns;
}
