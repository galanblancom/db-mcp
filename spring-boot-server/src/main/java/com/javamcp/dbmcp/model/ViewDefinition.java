package com.javamcp.dbmcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ViewDefinition {
    private String name;
    private String schema;
    private String owner;
    private String definition;
}
