package com.indrard.dbmcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QueryResult {
    private List<Map<String, Object>> rows;
    private int rowCount;
    private List<String> columns;
}
