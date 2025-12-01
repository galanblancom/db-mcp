package com.javamcp.dbmcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchemaComparisonResult {
    private String table1;
    private String schema1;
    private String table2;
    private String schema2;
    private List<ColumnDifference> columnDifferences;
    private List<String> columnsOnlyInTable1;
    private List<String> columnsOnlyInTable2;
    private String summary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnDifference {
        private String columnName;
        private String difference;
        private String table1Value;
        private String table2Value;
    }
}
