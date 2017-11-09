package com.minitwit.config;

import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

public class DatabaseConfig {
    private final DataSource dataSource;

    public DatabaseConfig() {
        EmbeddedDatabaseBuilder builder = new EmbeddedDatabaseBuilder();
        dataSource = builder
                .setType(EmbeddedDatabaseType.HSQL)
                .addScript("sql/create-db.sql")
				.addScript("sql/insert-data.sql")
				.build();
	}

    public DataSource getDataSource() {
        return dataSource;
    }
}
