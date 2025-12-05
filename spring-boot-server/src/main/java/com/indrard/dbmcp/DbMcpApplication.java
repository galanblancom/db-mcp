package com.indrard.dbmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Main application class for running db-mcp as a standalone server.
 * When used as a library, you don't need to reference this class.
 * Just add @ComponentScan(basePackages = {"com.indrard.dbmcp", "your.package"}) 
 * to your main application class.
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class DbMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbMcpApplication.class, args);
    }

}
