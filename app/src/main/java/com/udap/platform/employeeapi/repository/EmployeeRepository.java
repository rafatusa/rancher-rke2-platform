package com.udap.platform.employeeapi.repository;

import com.udap.platform.employeeapi.model.Employee;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory employee store.
 *
 * <p>The platform demo has no database by design: state would make the
 * application a poor probe of cluster health and would require a managed
 * datastore that was not part of the request. Swap this for a JPA repository
 * when persistence is actually needed.
 */
@Repository
public class EmployeeRepository {

    private final Map<Long, Employee> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @PostConstruct
    void seed() {
        save(new Employee(null, "Ada Lovelace", "ada@example.com", "Engineering", "Principal Engineer"));
        save(new Employee(null, "Grace Hopper", "grace@example.com", "Engineering", "Staff Engineer"));
        save(new Employee(null, "Alan Turing", "alan@example.com", "Research", "Research Lead"));
        save(new Employee(null, "Katherine Johnson", "katherine@example.com", "Operations", "Operations Manager"));
    }

    /**
     * Returns every employee, ordered by id.
     */
    public List<Employee> findAll() {
        List<Employee> employees = new ArrayList<>(store.values());
        employees.sort((left, right) -> Long.compare(left.id(), right.id()));
        return employees;
    }

    /**
     * Finds a single employee by id.
     */
    public Optional<Employee> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    /**
     * Stores an employee, assigning an id when the record is new.
     */
    public Employee save(Employee employee) {
        Long id = employee.id() != null ? employee.id() : sequence.incrementAndGet();
        Employee persisted = employee.withId(id);
        store.put(id, persisted);
        return persisted;
    }

    /**
     * Returns the number of stored employees.
     */
    public long count() {
        return store.size();
    }
}
