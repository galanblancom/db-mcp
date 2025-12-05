package com.indrard.dbmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class DbMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbMcpApplication.class, args);
    }

}
