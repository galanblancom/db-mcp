package com.indrard.dbmcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SchemaInfo {
    private String name;
    private String owner;
    private Integer tableCount;
    private Integer viewCount;
}
