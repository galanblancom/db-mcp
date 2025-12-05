package com.indrard.dbmcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ColumnInfo {
    private String name;
    private String type;
    private Integer length;
    private Integer precision;
    private Integer scale;
    private boolean nullable;
    private String defaultValue;
    private boolean isPrimaryKey;
    private boolean isForeignKey;
}
