package com.javamcp.dbmcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ForeignKeyInfo {
    private String constraintName;
    private String tableName;
    private String columnName;
    private String referencedTable;
    private String referencedColumn;
    private String onDelete;
    private String onUpdate;
    private String schema;
}
