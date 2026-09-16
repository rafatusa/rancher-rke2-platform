package com.udap.platform.employeeapi.web;

import com.udap.platform.employeeapi.model.Employee;
import com.udap.platform.employeeapi.repository.EmployeeRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Employee resource endpoints.
 */
@RestController
@RequestMapping("/employees")
public class EmployeeController {

    private final EmployeeRepository repository;

    public EmployeeController(EmployeeRepository repository) {
        this.repository = repository;
    }

    /**
     * Lists every employee.
     */
    @GetMapping
    public List<Employee> list() {
        return repository.findAll();
    }

    /**
     * Returns one employee, or 404 when the id is unknown.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Employee> get(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Creates a new employee.
     */
    @PostMapping
    public ResponseEntity<Employee> create(@Valid @RequestBody Employee employee) {
        Employee saved = repository.save(new Employee(
                null, employee.name(), employee.email(), employee.department(), employee.title()));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
