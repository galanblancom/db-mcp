package com.indrard.dbmcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TableListItem {
    private String name;
    private Integer rowCount;
    private Date lastAnalyzed;
}
