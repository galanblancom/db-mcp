package com.indrard.dbmcp.service.function;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to define a tool/function that can be called by AI.
 * Methods annotated with this will be automatically discovered and registered.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolDefinition {
    
    /**
     * The name of the function as it will be called by AI
     */
    String name();
    
    /**
     * Description of what the function does
     */
    String description();
    
    /**
     * Priority of this function provider (higher = checked first)
     * Database functions use priority 100
     */
    int priority() default 0;
}
