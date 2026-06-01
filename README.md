# Spring Boot Starter Template
## Stateless OIDC/OAuth(Single or multiple issuers), PostgreSQL, TestContainers, Liquibase, Docker/Postman, OpenAPI
This project serves as a boilerplate template for a scalable spring boot microservice. Configured for integration testing using Postgres SQL in TestContainers as well as OAuth2/OIDC JWT authentication. This specific template is designed around using keycloak for authentication/authorization but can be changed to fit whatever need. Also comes with a base docker image, yaml for kubernetes pods, liquibase(prod and test) starter, and sql init scripts. 

The main goal of this project is to serve as a reusable base for secure microservices. If using this base, you just need to rename/restructure the project to fit your needs and plug in connection information wherever necessary. 

For security purposes, you should never store credentials inside this project - only as an environment variable or key in some other place. 

## Goals
- [x] Starter src and test structure
- [x] Boilerplate service, repository, and DTOs
- [x] Config testcontainers to use postgres for scalable integration tests
- [x] Integrate OpenAPI for documentation and contract based development
- [x] Sample database config in application.yml
- [x] Stateless Spring security JWT authentication boilerplate to support single or multiple issuers
- [x] Dockerfile for base image
- [x] Yaml config for base pod using podman/kubernetes

## How to use
1. Clone this repo into your own branch and use it to develop your own project OR download the zip. 

```git clone (git project link)```

2. Modify project folder structure, change code, replace connection strings, and change /config files to match your use case. 

3. Start developing! 

If you use this and end up releasing, all I ask for is a reference to this project somehow. 

## Project Structure Breakdown

This repository is organized as a reusable Spring Boot microservice template. The root contains repository-level files, while the actual Spring Boot project lives under `your_repo_name/`.

```text
spring-boot-starter-template/
  .vscode/
    # Optional VS Code workspace/editor settings

  .gitignore
  LICENSE
  README.md

  your_repo_name/
    pom.xml
    # Maven build configuration.
    # Defines Spring Boot dependencies, PostgreSQL, Liquibase, Testcontainers,
    # Spring Security OAuth2 Resource Server, OpenFeign, SpringDoc/OpenAPI,
    # validation, and OpenAPI code generation.

    mvnw
    mvnw.cmd
    .mvn/
      wrapper/
        maven-wrapper.properties
        # Maven wrapper configuration so the project can be built
        # without requiring Maven to be installed globally.

    Dockerfile
    # Multi-stage Docker build.
    # Builds the Spring Boot jar with Maven, then runs it from a smaller
    # Eclipse Temurin JRE runtime image.

    podman/
      podman-kube.yaml
      # Local Podman/Kubernetes-style pod configuration.
      # Includes a PostgreSQL pod, persistent volume claim, and Spring app pod.

    src/
      main/
        java/
          com/example_project_name/
            app/
              MyServiceApplication.java
              # Main Spring Boot application entry point.

            config/
              # Application configuration classes.
              # Includes security/auth configuration and other shared Spring beans.

            controller/
              # REST controllers that expose API endpoints.

              dto/
                # Request/response DTOs used by controllers.
                # Keeps API contracts separate from persistence entities.

            model/
              # Domain/entity models.
              # Typically used for Jakarta Persistence/Hibernate mappings.

            repository/
              # Spring Data repositories for database access.

              specification/
                # Spring Data Specifications for dynamic or more complex queries.

            service/
              # Service interfaces that define business operations.

              serviceImpl/
                # Concrete service implementations.
                # Contains business logic and coordinates repositories/external clients.

        resources/
          application.yml
          # Main application configuration.
          # Contains environment-driven database, Liquibase, auth, and app settings.

          db/
            changelog/
              # Liquibase changelog files for database schema management.

          openapi/
            openapi.yaml
            # OpenAPI contract used for API documentation and code generation.

      test/
        java/
          integration/
            com/example_project_name/
              # Integration tests, including database/application-context tests.

          unit/
            com/example_project_name/service/
              # Unit tests for service-layer logic.

        resources/
          db/
            changelog/
              # Test-specific Liquibase changelogs/resources.
```

### Generated Sources

During the Maven build, the OpenAPI generator creates Java sources under:

```text
your_repo_name/
  target/
    generated-sources/
      openapi/
        src/main/java/
          com/example_project_name/generated/
            api/
            model/
```

These files are generated from `src/main/resources/openapi/openapi.yaml` and should generally not be edited directly. Update the OpenAPI contract instead, then regenerate/build the project.

### Main Areas

| Area                                 | Purpose                                                                              |
| ------------------------------------ | ------------------------------------------------------------------------------------ |
| `pom.xml`                            | Central Maven build file for dependencies, plugins, testing, and OpenAPI generation. |
| `Dockerfile`                         | Builds and packages the Spring Boot service into a runnable container image.         |
| `podman/podman-kube.yaml`            | Local Podman/Kubernetes-style setup for the Spring app and PostgreSQL.               |
| `src/main/java`                      | Main application source code.                                                        |
| `config`                             | Security, authentication, and shared Spring configuration.                           |
| `controller`                         | API endpoint definitions.                                                            |
| `controller/dto`                     | API request/response objects.                                                        |
| `model`                              | Database-backed entities/domain models.                                              |
| `repository`                         | Spring Data persistence layer.                                                       |
| `repository/specification`           | Reusable dynamic query logic.                                                        |
| `service`                            | Service interfaces for business operations.                                          |
| `service/serviceImpl`                | Service implementation classes.                                                      |
| `src/main/resources/application.yml` | Main YAML-based application configuration.                                           |
| `src/main/resources/db/changelog`    | Liquibase database migration files.                                                  |
| `src/main/resources/openapi`         | OpenAPI contract used for documentation and generated API/model classes.             |
| `src/test/java/integration`          | Integration tests.                                                                   |
| `src/test/java/unit`                 | Unit tests.                                                                          |
| `src/test/resources`                 | Test-specific resources and database changelogs.                                     |

### Notes

* DTOs are separated from entities so API contracts are not tightly coupled to database models.
* Liquibase changelogs are included to make database schema changes repeatable and version-controlled.
* OpenAPI is used as both documentation and a source for generated API/model classes.
* The Podman YAML is intended for local containerized development, while the Dockerfile provides the application image.
* Secrets and real credentials should not be committed. Use environment variables or an external secret/configuration provider instead.
