# Testing Guidelines

We welcome and encourage contributions to the LogDispatch project! To maintain the quality and reliability of the SDK, we strictly adhere to automation testing best practices. 

If you are adding new features, fixing bugs, or refactoring code, please ensure your tests comply with the following guidelines.

## 1. Run Tests Locally
Before opening a Pull Request, always verify that your changes haven't broken existing functionality. You can execute the entire test suite locally using the Maven Wrapper:
```bash
./mvnw test
```

## 2. File Structure & Single Responsibility
Do not create or add to large, monolithic test classes. Each test file should focus on testing a specific responsibility (Single Responsibility Principle). 
If you are adding a completely new behavior, create a new, appropriately named test file.

## 3. Use the Base Test Class
When writing tests related to the `LogDispatchFilter`, extend the abstract `LogDispatchFilterBaseTest.java` class. 
This base class contains:
* Shared constants (API keys, URLs).
* Pre-configured mocks (e.g., `RestTemplate`).
* Helper methods for creating dummy `HttpServletRequest` and `FilterChain` objects.
* Utility methods for capturing and verifying the dispatched JSON payload.

Reusing these utilities keeps the test suite DRY (Don't Repeat Yourself).

## 4. Naming Conventions
* **Classes:** Test classes must end in `*Test.java` (e.g., `LogDispatchFilterHeaderMaskingTest.java`). The `maven-surefire-plugin` is configured to automatically detect and execute these classes.
* **Methods:** Test methods should be descriptively named (e.g., `shouldMaskConfiguredHeaderInApmPayload`).
* **Display Names:** Always decorate your test classes and methods with the JUnit 5 `@DisplayName` annotation to provide clean, human-readable output in the test logs.

## 5. Testing the AOP Aspect
Any tests verifying the AOP interception logic (which intercepts `@RestController` exceptions and reads `@LogDispatch` annotations) should be placed inside `LogDispatchAspectTest.java` or in similarly named isolated files.

---

Thank you for helping us keep LogDispatch robust!
