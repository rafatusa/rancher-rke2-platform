package com.udap.platform.employeeapi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EmployeeApiApplicationTest {

    @Test
    @DisplayName("the Spring application context loads")
    void contextLoads() {
        // Fails the build if any bean wiring, configuration property or
        // component scan in the application is broken.
    }
}
