# Config directory

This folder contains environment-specific Spring Boot configuration files used by this project.

Files
- `application-dev.yml` — development environment settings (port 8082).
- `application-qa.yml` — QA environment settings (port 8083).
- `application-prod.yml` — Production settings (port 8080). Secrets should be provided via environment variables or external configuration.

How Spring Boot picks these up
- Spring Boot will load `application.yml` (if present) and any `config/application-<profile>.yml` when a profile is active.
-- We expose these profiles via Maven profiles in `pom.xml` (`-Pdev`, `-Pqa`, `-Pprod`) which set the `spring.profiles.active` property.

How to run
-- Run locally with the `dev` profile:

```bash
mvn -Pdev spring-boot:run
```

- Run integration tests (Failsafe) with a profile active (example `dev`):

```bash
mvn -Pdev verify
```

Changing the active Spring profile in other contexts
- You can override `spring.profiles.active` with an environment variable, JVM system property, or external config server. Example JVM property:

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=dev"
```

Notes
- Keep secrets out of these committed files. Use environment variables, a secrets manager, or an externalized configuration system for credentials and sensitive settings.
