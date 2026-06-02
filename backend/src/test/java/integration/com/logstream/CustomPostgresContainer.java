package integration.com.logstream;

import org.testcontainers.containers.PostgreSQLContainer;

public class CustomPostgresContainer extends PostgreSQLContainer<CustomPostgresContainer> {
    //This file defines a custom testcontainer for postgres
    //used in PostgresBaseIT to provide a shared container for all integration
    //tests that need a database connection

    private static final String IMAGE_VERSION = "postgres:16";
    private static CustomPostgresContainer container;

    private CustomPostgresContainer() {
        super(IMAGE_VERSION);
        withDatabaseName("testdb");
        withUsername("test");
        withPassword("test");
    }

    public static CustomPostgresContainer getInstance() {
        if (container == null) {
            container = new CustomPostgresContainer();
        }

        return container;
    }
}