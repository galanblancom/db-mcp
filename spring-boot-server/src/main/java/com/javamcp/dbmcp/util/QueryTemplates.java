package com.javamcp.dbmcp.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class QueryTemplates {

    @Data
    @AllArgsConstructor
    public static class QueryTemplate {
        private String id;
        private String name;
        private String description;
        private String sql;
        private List<String> parameters;
    }

    private static final Map<String, QueryTemplate> templates = new HashMap<>();

    static {
        templates.put("top-rows", new QueryTemplate(
                "top-rows",
                "Get Top N Rows",
                "Retrieve the first N rows from a table",
                "SELECT * FROM {{table}} LIMIT {{limit}}",
                Arrays.asList("table", "limit")
        ));

        templates.put("filter-equals", new QueryTemplate(
                "filter-equals",
                "Filter by Exact Match",
                "Select rows where a column equals a specific value",
                "SELECT * FROM {{table}} WHERE {{column}} = {{value}}",
                Arrays.asList("table", "column", "value")
        ));

        templates.put("filter-like", new QueryTemplate(
                "filter-like",
                "Filter by Pattern",
                "Select rows where a column matches a pattern",
                "SELECT * FROM {{table}} WHERE {{column}} LIKE {{pattern}}",
                Arrays.asList("table", "column", "pattern")
        ));

        templates.put("date-range", new QueryTemplate(
                "date-range",
                "Filter by Date Range",
                "Select rows within a date range",
                "SELECT * FROM {{table}} WHERE {{dateColumn}} BETWEEN {{startDate}} AND {{endDate}}",
                Arrays.asList("table", "dateColumn", "startDate", "endDate")
        ));

        templates.put("aggregate-count", new QueryTemplate(
                "aggregate-count",
                "Count by Group",
                "Count rows grouped by a column",
                "SELECT {{groupColumn}}, COUNT(*) as count FROM {{table}} GROUP BY {{groupColumn}} ORDER BY count DESC",
                Arrays.asList("table", "groupColumn")
        ));

        templates.put("aggregate-sum", new QueryTemplate(
                "aggregate-sum",
                "Sum by Group",
                "Sum a column grouped by another column",
                "SELECT {{groupColumn}}, SUM({{sumColumn}}) as total FROM {{table}} GROUP BY {{groupColumn}} ORDER BY total DESC",
                Arrays.asList("table", "groupColumn", "sumColumn")
        ));

        templates.put("join-tables", new QueryTemplate(
                "join-tables",
                "Join Two Tables",
                "Inner join two tables on specified columns",
                "SELECT * FROM {{table1}} t1 INNER JOIN {{table2}} t2 ON t1.{{joinColumn1}} = t2.{{joinColumn2}}",
                Arrays.asList("table1", "table2", "joinColumn1", "joinColumn2")
        ));

        templates.put("distinct-values", new QueryTemplate(
                "distinct-values",
                "Get Distinct Values",
                "Get unique values from a column",
                "SELECT DISTINCT {{column}} FROM {{table}} ORDER BY {{column}}",
                Arrays.asList("table", "column")
        ));

        templates.put("null-check", new QueryTemplate(
                "null-check",
                "Filter Null Values",
                "Select rows where a column is or is not null",
                "SELECT * FROM {{table}} WHERE {{column}} {{operator}}",
                Arrays.asList("table", "column", "operator")
        ));

        templates.put("recent-records", new QueryTemplate(
                "recent-records",
                "Get Recent Records",
                "Get most recent N records ordered by date column",
                "SELECT * FROM {{table}} ORDER BY {{dateColumn}} DESC LIMIT {{limit}}",
                Arrays.asList("table", "dateColumn", "limit")
        ));
    }

    public List<QueryTemplate> list() {
        return new ArrayList<>(templates.values());
    }

    public QueryTemplate get(String id) {
        return templates.get(id);
    }

    public String execute(String id, Map<String, String> params) throws Exception {
        QueryTemplate template = templates.get(id);
        if (template == null) {
            throw new Exception("Template '" + id + "' not found");
        }

        // Validate required parameters
        List<String> missing = new ArrayList<>();
        for (String param : template.getParameters()) {
            if (!params.containsKey(param)) {
                missing.add(param);
            }
        }
        if (!missing.isEmpty()) {
            throw new Exception("Missing required parameters: " + String.join(", ", missing));
        }

        // Replace parameters in SQL
        String sql = template.getSql();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            String placeholder = "{{" + key + "}}";

            // Check if value needs quotes (not a number)
            boolean needsQuotes = !isNumeric(value) && !value.startsWith("'") && !value.startsWith("\"");
            String replacementValue = needsQuotes ? "'" + value + "'" : value;

            sql = sql.replace(placeholder, replacementValue);
        }

        return sql;
    }

    private boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
