package com.indrard.dbmcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExplainPlan {
    private String query;
    private String plan;
    private Double estimatedCost;
    private Double estimatedRows;
}
