package com.bodego.platform.iam.interfaces.operations;

import com.bodego.platform.iam.application.internal.commandservices.UserCommandServiceImpl;
import com.bodego.platform.iam.infrastructure.hashing.BCryptPasswordHashingService;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import com.bodego.platform.shared.infrastructure.persistence.jpa.configuration.strategy.SnakeCaseWithPluralizedTablePhysicalNamingStrategy;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.*;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.util.Map;
import java.util.Properties;

/** Package-private operational runtime. No Boot scan, application.properties, JWT, HTTP or Flyway. */
final class ProvisioningRuntime {
    private ProvisioningRuntime() {}
    static AnnotationConfigApplicationContext open(String url,String username,char[] password) {
        return open(url,username,password,new Properties());
    }
    static AnnotationConfigApplicationContext open(String url,String username,char[] password,Properties connectionProperties) {
        var context=new AnnotationConfigApplicationContext();
        var dataSource=new HikariDataSource();
        dataSource.setJdbcUrl(url); dataSource.setUsername(username); dataSource.setPassword(new String(password));
        dataSource.setDataSourceProperties(connectionProperties);
        dataSource.setSchema("public"); dataSource.setMaximumPoolSize(2); dataSource.setPoolName("iam-controlled-provisioning");
        context.registerBean("dataSource",DataSource.class,() -> dataSource,definition -> definition.setDestroyMethodName("close"));
        context.register(Persistence.class);
        try {context.refresh();return context;}
        catch(RuntimeException exception) {context.close();dataSource.close();throw exception;}
    }

    // Deliberately NOT @Configuration/@Component: normal application scanning must not discover this runtime.
    @EnableJpaRepositories(basePackageClasses=UserRepository.class)
    @EnableJpaAuditing
    @EnableTransactionManagement
    @Import({UserCommandServiceImpl.class,BCryptPasswordHashingService.class,
            com.bodego.platform.iam.application.internal.commandservices.BusinessCommandServiceImpl.class})
    static class Persistence {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory=new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource); factory.setPackagesToScan("com.bodego.platform.iam.domain.model");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.show_sql",false,
                    "hibernate.physical_naming_strategy",SnakeCaseWithPluralizedTablePhysicalNamingStrategy.class.getName()));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }
    }
}
