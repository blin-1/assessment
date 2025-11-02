# assessment

## Java and build instructions

This project is built with Maven and targets Java 21 bytecode level.

Default Spring profile

When no Spring profile is supplied, this project falls back to the `dev` profile by default (see `src/main/resources/application.yml`). You can still override the active profile with a Maven profile (for example `-Pdev`) or by setting the JVM/system property `-Dspring.profiles.active=qa`.

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

Integration tests in this project are implemented as IT classes (naming pattern: `*IT.java`) and are executed by the Maven Failsafe plugin during the `verify` phase. This keeps the fast `mvn test` run focused on unit tests (Surefire) while running longer Testcontainers-based end-to-end tests only when explicitly requested (for example in a CI integration job).

Local requirements
- Docker Desktop (or another Docker runtime) accessible to Testcontainers
- Sufficient memory for Kafka containers (2GB+ recommended)

Run integration tests locally

- Run the full verify lifecycle, which executes Failsafe ITs:

```bash
mvn verify
```

- Run a single IT class via Failsafe (example `KafkaIT`):

```bash
mvn -Dit.test=KafkaIT verify
```

Run unit tests only (fast)

```bash
mvn test
```

CI guidance

- Run unit tests in a fast job using `mvn test`.
- Run integration tests in a separate job that has Docker available and executes `mvn verify` (Failsafe will pick up `*IT.java` classes). Example: use a self-hosted or Docker-enabled runner for the integration job.

Example GitHub Actions snippet (integration job using `mvn verify`):

```yaml
integration-tests:
  runs-on: self-hosted # or a runner with Docker available
  steps:
	- uses: actions/checkout@v4
	- name: Set up JDK
	  uses: actions/setup-java@v4
	  with:
		java-version: '23'
	- name: Run integration tests (Failsafe)
	  run: mvn verify
```

If you want, I can add or adapt CI workflow files so unit and integration tests are split into separate jobs (fast unit-test job vs. Docker-enabled integration job).