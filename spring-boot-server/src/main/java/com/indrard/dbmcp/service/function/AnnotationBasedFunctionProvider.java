package com.indrard.dbmcp.service.function;

import com.indrard.dbmcp.model.openai.FunctionDefinition;
import com.indrard.dbmcp.model.openai.FunctionDefinition.FunctionParameters;
import com.indrard.dbmcp.model.openai.FunctionDefinition.PropertyDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Function provider that automatically discovers tools from annotated methods.
 * Scans all Spring beans for methods annotated with @ToolDefinition.
 * 
 * This allows defining all tools in a centralized configuration class
 * without manually maintaining function definitions and execution logic.
 */
@Component
public class AnnotationBasedFunctionProvider implements FunctionProvider {
    
    private final ApplicationContext applicationContext;
    private final Map<String, ToolMethod> toolMethods;
    private final Set<String> supportedFunctions;
    private final int maxPriority;
    
    public AnnotationBasedFunctionProvider(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.toolMethods = new HashMap<>();
        this.supportedFunctions = new HashSet<>();
        
        // Scan all beans for @ToolDefinition annotated methods
        int tempMaxPriority = 0;
        for (Object bean : applicationContext.getBeansOfType(Object.class).values()) {
            for (Method method : bean.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(ToolDefinition.class)) {
                    ToolDefinition tool = method.getAnnotation(ToolDefinition.class);
                    String functionName = tool.name();
                    
                    toolMethods.put(functionName, new ToolMethod(bean, method, tool));
                    supportedFunctions.add(functionName);
                    
                    if (tool.priority() > tempMaxPriority) {
                        tempMaxPriority = tool.priority();
                    }
                }
            }
        }
        this.maxPriority = tempMaxPriority;
    }
    
    @Override
    public List<FunctionDefinition> getFunctionDefinitions() {
        List<FunctionDefinition> definitions = new ArrayList<>();
        
        for (Map.Entry<String, ToolMethod> entry : toolMethods.entrySet()) {
            ToolMethod toolMethod = entry.getValue();
            ToolDefinition tool = toolMethod.annotation;
            
            // Build parameters from method parameters
            Map<String, PropertyDefinition> properties = new HashMap<>();
            List<String> required = new ArrayList<>();
            
            Parameter[] parameters = toolMethod.method.getParameters();
            for (Parameter param : parameters) {
                if (param.isAnnotationPresent(ToolParameter.class)) {
                    ToolParameter paramAnnotation = param.getAnnotation(ToolParameter.class);
                    properties.put(
                        paramAnnotation.name(),
                        new PropertyDefinition(paramAnnotation.type(), paramAnnotation.description())
                    );
                    
                    if (paramAnnotation.required()) {
                        required.add(paramAnnotation.name());
                    }
                }
            }
            
            FunctionParameters params = new FunctionParameters(properties, required);
            definitions.add(new FunctionDefinition(tool.name(), tool.description(), params));
        }
        
        return definitions;
    }
    
    @Override
    public Object executeFunction(String functionName, Map<String, Object> arguments) throws Exception {
        ToolMethod toolMethod = toolMethods.get(functionName);
        if (toolMethod == null) {
            throw new IllegalArgumentException("Unknown function: " + functionName);
        }
        
        if (arguments == null) {
            arguments = new HashMap<>();
        }
        
        // Build method arguments from the arguments map
        Method method = toolMethod.method;
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            if (param.isAnnotationPresent(ToolParameter.class)) {
                ToolParameter paramAnnotation = param.getAnnotation(ToolParameter.class);
                String paramName = paramAnnotation.name();
                Object value = arguments.get(paramName);
                
                // Type conversion if needed
                if (value != null) {
                    Class<?> paramType = param.getType();
                    
                    // Handle String to Integer conversion
                    if ((paramType == Integer.class || paramType == int.class) && value instanceof String) {
                        try {
                            value = Integer.parseInt((String) value);
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Invalid integer value for parameter " + paramName + ": " + value);
                        }
                    }
                    // Handle String to Long conversion
                    else if ((paramType == Long.class || paramType == long.class) && value instanceof String) {
                        try {
                            value = Long.parseLong((String) value);
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Invalid long value for parameter " + paramName + ": " + value);
                        }
                    }
                    // Handle String to Boolean conversion
                    else if ((paramType == Boolean.class || paramType == boolean.class) && value instanceof String) {
                        value = Boolean.parseBoolean((String) value);
                    }
                    // Handle List conversion from String (when AI sends JSON array as string)
                    else if (List.class.isAssignableFrom(paramType) && value instanceof String) {
                        String strValue = (String) value;
                        // Parse JSON array string like "[\"item1\", \"item2\"]" to List
                        if (strValue.startsWith("[") && strValue.endsWith("]")) {
                            strValue = strValue.substring(1, strValue.length() - 1);
                            if (!strValue.trim().isEmpty()) {
                                value = Arrays.stream(strValue.split(","))
                                    .map(s -> s.trim().replaceAll("^\"|\"$", ""))
                                    .collect(Collectors.toList());
                            } else {
                                value = new ArrayList<>();
                            }
                        }
                    }
                    // Handle List conversion from actual List (when properly sent as JSON array)
                    else if (List.class.isAssignableFrom(paramType) && value instanceof List) {
                        // Already a list, no conversion needed
                        value = value;
                    }
                    // Handle Integer/Long conversion
                    else if (paramType == Long.class && value instanceof Integer) {
                        value = ((Integer) value).longValue();
                    } else if (paramType == Integer.class && value instanceof Long) {
                        value = ((Long) value).intValue();
                    }
                }
                
                // Validate required parameters
                if (paramAnnotation.required() && (value == null || (value instanceof String && ((String) value).trim().isEmpty()))) {
                    throw new IllegalArgumentException(paramName + " is required for " + functionName + " function");
                }
                
                args[i] = value;
            }
        }
        
        return method.invoke(toolMethod.bean, args);
    }
    
    @Override
    public boolean supports(String functionName) {
        return supportedFunctions.contains(functionName);
    }
    
    @Override
    public int getPriority() {
        return maxPriority;
    }
    
    /**
     * Helper class to store tool method information
     */
    private static class ToolMethod {
        final Object bean;
        final Method method;
        final ToolDefinition annotation;
        
        ToolMethod(Object bean, Method method, ToolDefinition annotation) {
            this.bean = bean;
            this.method = method;
            this.annotation = annotation;
        }
    }
}
