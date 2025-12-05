package com.example.custommcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Custom MCP Application
 *
 * Scans service, config, and util packages from base library, plus entire custom package.
 * Excludes base OpenAIConfig and uses our own CustomAIConfig to create AI beans directly.
 */
@SpringBootApplication
@ComponentScan(
    basePackages = {
        "com.indrard.dbmcp.service",
        "com.indrard.dbmcp.service.ai",
        "com.indrard.dbmcp.config",
        "com.indrard.dbmcp.util",
        "com.example.custommcp"
    },
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = com.indrard.dbmcp.config.OpenAIConfig.class
    )
)
public class CustomMcpApplication {    public static void main(String[] args) {
        SpringApplication.run(CustomMcpApplication.class, args);
    }

}
