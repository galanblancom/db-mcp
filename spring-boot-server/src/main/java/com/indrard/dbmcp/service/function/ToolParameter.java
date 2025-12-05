package com.indrard.dbmcp.service.function;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to define a parameter for a tool function.
 * Used to generate OpenAI function definitions automatically.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParameter {
    
    /**
     * The name of the parameter
     */
    String name();
    
    /**
     * Description of the parameter
     */
    String description();
    
    /**
     * Whether this parameter is required
     */
    boolean required() default false;
    
    /**
     * The type of the parameter (string, integer, boolean, array, object)
     */
    String type() default "string";
}
