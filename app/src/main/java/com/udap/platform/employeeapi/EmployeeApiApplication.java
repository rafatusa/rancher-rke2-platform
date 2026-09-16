package com.udap.platform.employeeapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Employee API — a small Spring Boot service deployed onto the RKE2 cluster
 * via Helm to prove the platform end to end.
 */
@SpringBootApplication
public class EmployeeApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmployeeApiApplication.class, args);
    }
}
