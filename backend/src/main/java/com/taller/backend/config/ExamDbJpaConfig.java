package com.taller.backend.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.taller.backend.bd1.repository",
        entityManagerFactoryRef = "examEntityManagerFactory",
        transactionManagerRef = "transactionManager"
)
public class ExamDbJpaConfig {

    @Bean(name = "examEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean examEntityManagerFactory(
            @Qualifier("examDataSource") DataSource examDataSource) {
        LocalContainerEntityManagerFactoryBean factoryBean = new LocalContainerEntityManagerFactoryBean();
        factoryBean.setJtaDataSource(examDataSource);
        factoryBean.setPackagesToScan("com.taller.backend.bd1.entity");
        factoryBean.setPersistenceUnitName("examPersistenceUnit");
        factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factoryBean.setJpaPropertyMap(commonJpaProperties());
        return factoryBean;
    }

    private Map<String, Object> commonJpaProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "validate");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.show_sql", false);
        properties.put("hibernate.format_sql", true);
        properties.put("hibernate.transaction.jta.platform", "org.hibernate.engine.transaction.jta.platform.internal.AtomikosJtaPlatform");
        properties.put("jakarta.persistence.transactionType", "JTA");
        return properties;
    }
}
