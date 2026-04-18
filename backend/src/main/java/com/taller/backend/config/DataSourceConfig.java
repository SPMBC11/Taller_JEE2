package com.taller.backend.config;

import com.atomikos.jdbc.AtomikosDataSourceBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
public class DataSourceConfig {

    @Bean(name = "examDataSource", initMethod = "init", destroyMethod = "close")
    public DataSource examDataSource(@Value("${app.datasource.exam.host}") String host,
                                     @Value("${app.datasource.exam.port}") int port,
                                     @Value("${app.datasource.exam.database}") String database,
                                     @Value("${app.datasource.exam.username}") String username,
                                     @Value("${app.datasource.exam.password}") String password) {
        Properties xaProperties = new Properties();
        xaProperties.setProperty("serverName", host);
        xaProperties.setProperty("portNumber", String.valueOf(port));
        xaProperties.setProperty("databaseName", database);
        xaProperties.setProperty("user", username);
        xaProperties.setProperty("password", password);

        AtomikosDataSourceBean dataSource = new AtomikosDataSourceBean();
        dataSource.setUniqueResourceName("examXaResource");
        dataSource.setXaDataSourceClassName("org.postgresql.xa.PGXADataSource");
        dataSource.setXaProperties(xaProperties);
        dataSource.setMinPoolSize(2);
        dataSource.setMaxPoolSize(10);
        return dataSource;
    }

    @Bean(name = "studentDataSource", initMethod = "init", destroyMethod = "close")
    public DataSource studentDataSource(@Value("${app.datasource.student.host}") String host,
                                        @Value("${app.datasource.student.port}") int port,
                                        @Value("${app.datasource.student.database}") String database,
                                        @Value("${app.datasource.student.username}") String username,
                                        @Value("${app.datasource.student.password}") String password) {
        Properties xaProperties = new Properties();
        xaProperties.setProperty("serverName", host);
        xaProperties.setProperty("portNumber", String.valueOf(port));
        xaProperties.setProperty("databaseName", database);
        xaProperties.setProperty("user", username);
        xaProperties.setProperty("password", password);

        AtomikosDataSourceBean dataSource = new AtomikosDataSourceBean();
        dataSource.setUniqueResourceName("studentXaResource");
        dataSource.setXaDataSourceClassName("org.postgresql.xa.PGXADataSource");
        dataSource.setXaProperties(xaProperties);
        dataSource.setMinPoolSize(2);
        dataSource.setMaxPoolSize(10);
        return dataSource;
    }
}
