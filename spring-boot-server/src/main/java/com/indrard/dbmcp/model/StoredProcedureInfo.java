package com.indrard.dbmcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StoredProcedureInfo {
    private String name;
    private String schema;
    private String type; // PROCEDURE or FUNCTION
    private String returnType;
    private String parameters;
}
