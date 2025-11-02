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

If you want, I can add a sample GitHub Actions workflow that sets up Java 23 for the build.
CI status

![CI](https://github.com/blin-1/assessment/actions/workflows/ci.yml/badge.svg)

CI extras

The workflow runs a matrix across Java 21 and Java 23 to validate both the target bytecode level (21) and the developer/runtime (23). Artifacts produced by each matrix cell are retained for 7 days and named with the Java version for easy retrieval.

If you'd like different retention, additional matrix axes (OS, Maven versions), or artifact promotion steps, tell me and I can add them.
