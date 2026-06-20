# LogDispatch Example App

This demo Spring Boot application shows how `logdispatch-spring-boot-starter` captures exceptions from `@RestController` endpoints and dispatches them to the configured APM ingest endpoint.

## Prerequisites

- Java 17 or later
- Maven, or the Maven wrapper from the repository root
- An APM ingest endpoint that accepts LogDispatch payloads

## Run the App

From the repository root, install the starter into your local Maven repository:

```bash
./mvnw -DskipTests -Dgpg.skip install
```

On Windows:

```powershell
.\mvnw.cmd -DskipTests -Dgpg.skip install
```

Then start the example app from the repository root:

```bash
./mvnw -f example-app/pom.xml spring-boot:run
```

On Windows:

```powershell
.\mvnw.cmd -f example-app\pom.xml spring-boot:run
```

If you do not have a real APM ingest endpoint available yet, the app still runs. LogDispatch will attempt to post to the demo URL in `src/main/resources/application.yml`, log a warning if the endpoint is unreachable, and leave the application response flow unaffected.

## Try the Demo Endpoints

```bash
curl http://localhost:8080/api/demo/null-pointer
curl http://localhost:8080/api/demo/illegal-argument
curl http://localhost:8080/api/demo/illegal-state
curl http://localhost:8080/api/demo/annotated
curl -X POST http://localhost:8080/api/demo/body -H "Content-Type: application/json" -d "{\"message\":\"hello\"}"
```

Each endpoint intentionally throws an exception so you can see LogDispatch capture the request details, exception type, stack trace, and optional `@LogDispatch` metadata.

## Configuration

Edit `src/main/resources/application.yml` to point LogDispatch at your own ingest endpoint:

```yaml
logdispatch:
  server-url: "http://localhost:8081/api/v1/ingest/logs"
  api-key: "demo-api-key"
```

The example also masks `authorization`, `cookie`, and `x-api-key` request headers in captured input data.
