package com.javamcp.dbmcp.model.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FunctionDefinition {
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("parameters")
    private FunctionParameters parameters;

    public FunctionDefinition() {
    }

    public FunctionDefinition(String name, String description, FunctionParameters parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public FunctionParameters getParameters() {
        return parameters;
    }

    public void setParameters(FunctionParameters parameters) {
        this.parameters = parameters;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionParameters {
        @JsonProperty("type")
        private String type = "object";
        
        @JsonProperty("properties")
        private Map<String, PropertyDefinition> properties;
        
        @JsonProperty("required")
        private java.util.List<String> required;

        public FunctionParameters() {
        }

        public FunctionParameters(Map<String, PropertyDefinition> properties, java.util.List<String> required) {
            this.properties = properties;
            this.required = required;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Map<String, PropertyDefinition> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, PropertyDefinition> properties) {
            this.properties = properties;
        }

        public java.util.List<String> getRequired() {
            return required;
        }

        public void setRequired(java.util.List<String> required) {
            this.required = required;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PropertyDefinition {
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("items")
        private PropertyDefinition items;

        public PropertyDefinition() {
        }

        public PropertyDefinition(String type, String description) {
            this.type = type;
            this.description = description;
        }

        public PropertyDefinition(String type, String description, PropertyDefinition items) {
            this.type = type;
            this.description = description;
            this.items = items;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public PropertyDefinition getItems() {
            return items;
        }

        public void setItems(PropertyDefinition items) {
            this.items = items;
        }
    }
}
