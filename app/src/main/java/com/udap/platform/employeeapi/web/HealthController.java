package com.udap.platform.employeeapi.web;

import com.udap.platform.employeeapi.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plain, dependency-free health endpoint.
 *
 * <p>Kubernetes probes, the Helm deploy script and the validation pipeline all
 * target {@code /health}. Actuator remains available at {@code /actuator/health}
 * for richer detail, but this endpoint is deliberately trivial so a probe never
 * fails because of an actuator configuration change.
 */
@RestController
public class HealthController {

    private final EmployeeRepository repository;
    private final String version;

    public HealthController(EmployeeRepository repository,
                            @Value("${app.version:1.0.0}") String version) {
        this.repository = repository;
        this.version = version;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "employee-api");
        body.put("version", version);
        body.put("employees", repository.count());
        body.put("timestamp", Instant.now().toString());
        return body;
    }
}
