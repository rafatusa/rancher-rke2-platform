package com.udap.platform.employeeapi.repository;

import com.udap.platform.employeeapi.model.Employee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeRepositoryTest {

    private EmployeeRepository repository;

    @BeforeEach
    void setUp() {
        repository = new EmployeeRepository();
        repository.seed();
    }

    @Test
    @DisplayName("seeds a non-empty set of employees")
    void seedsEmployees() {
        assertThat(repository.count()).isEqualTo(4);
        assertThat(repository.findAll()).hasSize(4);
    }

    @Test
    @DisplayName("returns employees ordered by id")
    void returnsOrderedById() {
        List<Employee> employees = repository.findAll();

        assertThat(employees).extracting(Employee::id).containsExactly(1L, 2L, 3L, 4L);
        assertThat(employees.get(0).name()).isEqualTo("Ada Lovelace");
    }

    @Test
    @DisplayName("finds an existing employee by id")
    void findsById() {
        Optional<Employee> found = repository.findById(1L);

        assertThat(found).isPresent();
        assertThat(found.get().email()).isEqualTo("ada@example.com");
        assertThat(found.get().department()).isEqualTo("Engineering");
    }

    @Test
    @DisplayName("returns empty for an unknown id")
    void returnsEmptyForUnknownId() {
        assertThat(repository.findById(9999L)).isEmpty();
    }

    @Test
    @DisplayName("assigns a new id when saving a record without one")
    void assignsIdOnSave() {
        Employee saved = repository.save(
                new Employee(null, "Margaret Hamilton", "margaret@example.com", "Engineering", "Director"));

        assertThat(saved.id()).isEqualTo(5L);
        assertThat(repository.count()).isEqualTo(5);
        assertThat(repository.findById(5L)).contains(saved);
    }

    @Test
    @DisplayName("keeps the supplied id when one is provided")
    void keepsSuppliedId() {
        Employee saved = repository.save(
                new Employee(42L, "Barbara Liskov", "barbara@example.com", "Research", "Professor"));

        assertThat(saved.id()).isEqualTo(42L);
        assertThat(repository.findById(42L)).contains(saved);
    }

    @Test
    @DisplayName("withId returns a copy leaving other fields intact")
    void withIdCopies() {
        Employee original = new Employee(null, "Test Person", "test@example.com", "QA", "Engineer");
        Employee copy = original.withId(7L);

        assertThat(copy.id()).isEqualTo(7L);
        assertThat(copy.name()).isEqualTo(original.name());
        assertThat(copy.email()).isEqualTo(original.email());
        assertThat(copy.department()).isEqualTo(original.department());
        assertThat(copy.title()).isEqualTo(original.title());
    }
}
