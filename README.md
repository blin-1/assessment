# assessment

## Java and build instructions

This project is built with Maven and targets Java 21 bytecode level.

The development machine used for verification in this repository has Oracle JDK 23 installed. You can build and test the project using that JDK while still compiling for Java 21 by using the Maven Compiler Plugin's `release` option (already configured in `pom.xml`).

Local (Git Bash / WSL) quick-start

1. Make sure JDK 23 is installed on your machine. On Windows a common install path is:

	`C:\Program Files\Java\jdk-23`

2. In a Bash shell, set JAVA_HOME and update PATH for the current session then run Maven:

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-23"
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package
```

3. Run tests:

```bash
mvn test
```

CI note

In CI you can either:

- Use a JDK 21 image to match the target runtime exactly, or
- Use a JDK 23 image (if available) and build with the same `mvn clean package` commands — the POM is set to compile with `--release 21` so produced artifacts will be Java 21-compatible.

CI status

![CI](https://github.com/blin-1/assessment/actions/workflows/ci.yml/badge.svg)

CI extras

The workflow runs a matrix across Java 21 and Java 23 to validate both the target bytecode level (21) and the developer/runtime (23). Artifacts produced by each matrix cell are retained for 7 days and named with the Java version for easy retrieval.

Docker & Kubernetes

Build a container locally (example using Docker Desktop):

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-23"
export PATH="$JAVA_HOME/bin:$PATH"
mvn -DskipTests package
docker build -t ghcr.io/owner/assessment:latest .
```

Deploy to a Kubernetes cluster (example):

```bash
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

Notes:
- The Kubernetes manifests assume a container image is available at `ghcr.io/owner/assessment:latest`. Replace the image with your registry and tag.
- Prometheus scrape annotations are added to the Pod template to make metrics (Actuator Prometheus) discoverable.

Integration tests (Kafka / Testcontainers)

This project includes an end-to-end Kafka integration test that uses Testcontainers to start a local Kafka broker. The test is tagged and run separately because it requires Docker to be available.

Local requirements
- Docker Desktop running and accessible to Testcontainers
- Sufficient memory for the Kafka container (2GB+ recommended)

Run the Kafka integration test locally

Activate the `test` Spring profile (the integration test already uses `@ActiveProfiles("test")`) and run the single test with Maven:

```bash
# run only the KafkaIntegrationTest
mvn -Dtest=KafkaIntegrationTest test
```

If you'd prefer to run all unit tests (fast) and skip integration tests, use the `-DskipITs` or tag-based filtering in your CI to exclude tests marked with `@Tag("integration")`.

CI guidance

- Only run Kafka integration tests on runners that have Docker available. On GitHub Actions use a self-hosted runner or a job with Docker-in-Docker privileges.
- Alternatively mark the test with `@Tag("integration")` and configure your CI workflow to run integration tests in a separate job with the required Docker support.

Example GitHub Actions snippet (run integration tests on Docker-enabled runner):

```yaml
name: integration-tests
on: [workflow_dispatch]
jobs:
	kafka-integration:
		runs-on: self-hosted # or a runner image with Docker
		steps:
			- uses: actions/checkout@v4
			- name: Set up JDK
				uses: actions/setup-java@v4
				with:
					distribution: temurin
					java-version: '23'
			- name: Run Kafka integration tests
				run: mvn -Dtest=KafkaIntegrationTest test
```

If you want, I can add CI config that runs integration tests behind a conditional or in a separate workflow.

Excluding integration tests in CI

You can keep integration tests (Testcontainers, Kafka) out of the default fast unit-test job by tagging them with JUnit tags (we use `@Tag("integration")`). Use Maven / Surefire tag filtering to exclude or include them in specific jobs:

- Run unit tests only (exclude integration):

```bash
mvn -DexcludeTags=integration test
```

- Run integration tests only (separate CI job with Docker):

```bash
mvn -DincludeTags=integration test
```

Example GitHub Actions usage

- Fast unit-test job (no Docker required):

```yaml
	unit-tests:
		runs-on: ubuntu-latest
		steps:
			- uses: actions/checkout@v4
			- name: Set up JDK
				uses: actions/setup-java@v4
				with:
					java-version: '23'
			- name: Run unit tests (exclude integration)
				run: mvn -DexcludeTags=integration test
```

- Integration job (needs Docker / self-hosted or Docker-enabled runner):

```yaml
	integration-tests:
		runs-on: self-hosted # or a runner with Docker available
		steps:
			- uses: actions/checkout@v4
			- name: Set up JDK
				uses: actions/setup-java@v4
				with:
					java-version: '23'
			- name: Run integration tests
				run: mvn -DincludeTags=integration test
```

Note: Surefire (3.x) supports `-DincludeTags` / `-DexcludeTags` for JUnit 5 tag filtering. You can also configure tag filtering in the `maven-surefire-plugin` configuration inside `pom.xml` if you want a default behavior there.