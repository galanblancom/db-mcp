package com.javamcp.dbmcp.config;

import com.javamcp.dbmcp.adapter.DatabaseAdapter;
import com.javamcp.dbmcp.adapter.impl.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @org.springframework.beans.factory.annotation.Value("${app.db.type:postgres}")
    private String dbType;

    @org.springframework.beans.factory.annotation.Value("${app.db.postgres.host:localhost}")
    private String pgHost;

    @org.springframework.beans.factory.annotation.Value("${app.db.postgres.port:5432}")
    private int pgPort;

    @org.springframework.beans.factory.annotation.Value("${app.db.postgres.database:postgres}")
    private String pgDatabase;

    @org.springframework.beans.factory.annotation.Value("${app.db.postgres.user:postgres}")
    private String pgUser;

    @org.springframework.beans.factory.annotation.Value("${app.db.postgres.password:postgres}")
    private String pgPassword;

    // Oracle Properties
    @org.springframework.beans.factory.annotation.Value("${app.db.oracle.host:localhost}")
    private String oracleHost;
    @org.springframework.beans.factory.annotation.Value("${app.db.oracle.port:1521}")
    private int oraclePort;
    @org.springframework.beans.factory.annotation.Value("${app.db.oracle.serviceName:ORCL}")
    private String oracleServiceName;
    @org.springframework.beans.factory.annotation.Value("${app.db.oracle.user:system}")
    private String oracleUser;
    @org.springframework.beans.factory.annotation.Value("${app.db.oracle.password:oracle}")
    private String oraclePassword;

    // MSSQL Properties
    @org.springframework.beans.factory.annotation.Value("${app.db.mssql.host:localhost}")
    private String mssqlHost;
    @org.springframework.beans.factory.annotation.Value("${app.db.mssql.port:1433}")
    private int mssqlPort;
    @org.springframework.beans.factory.annotation.Value("${app.db.mssql.database:master}")
    private String mssqlDatabase;
    @org.springframework.beans.factory.annotation.Value("${app.db.mssql.user:sa}")
    private String mssqlUser;
    @org.springframework.beans.factory.annotation.Value("${app.db.mssql.password:password}")
    private String mssqlPassword;
    @org.springframework.beans.factory.annotation.Value("${app.db.mssql.encrypt:false}")
    private boolean mssqlEncrypt;
    @org.springframework.beans.factory.annotation.Value("${app.db.mssql.trustServerCertificate:true}")
    private boolean mssqlTrustServerCertificate;

    @Bean
    public DatabaseAdapter databaseAdapter() {
        // Fallback to env var if property is default but env var is set (Spring handles
        // this via property sources usually,
        // but here we are explicit about the logic if needed.
        // Actually Spring's @Value will pick up env vars if they match the property
        // name format APP_DB_TYPE etc.
        // So we can rely on standard Spring injection.)

        String type = dbType.toLowerCase();

        switch (type) {
            case "postgres":
            case "postgresql":
                return new PostgresAdapter(pgHost, pgPort, pgDatabase, pgUser, pgPassword);
            case "oracle":
                return new OracleAdapter(oracleHost, oraclePort, oracleServiceName, oracleUser, oraclePassword);
            case "mssql":
            case "sqlserver":
                return new MssqlAdapter(mssqlHost, mssqlPort, mssqlDatabase, mssqlUser, mssqlPassword, mssqlEncrypt,
                        mssqlTrustServerCertificate);
            default:
                throw new IllegalArgumentException("Unsupported database type: " + type);
        }
    }
}
