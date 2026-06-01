package integration.com.example_project_name;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.example_project_name.app.MyServiceApplication;

@SpringBootTest(classes = MyServiceApplication.class)
public abstract class PostgresBaseIT { //base class for Postgres integration tests

    private static final CustomPostgresContainer postgres =
            CustomPostgresContainer.getInstance();

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");

        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log",
                () -> "classpath:db/changelog/db.changelog-test.xml");

        registry.add("spring.liquibase.url", postgres::getJdbcUrl);
        registry.add("spring.liquibase.user", postgres::getUsername);
        registry.add("spring.liquibase.password", postgres::getPassword);
    }
}