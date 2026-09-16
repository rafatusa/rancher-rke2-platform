package com.udap.platform.employeeapi.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * An employee record.
 *
 * @param id         stable identifier
 * @param name       full name
 * @param email      contact address
 * @param department owning department
 * @param title      job title
 */
public record Employee(
        Long id,

        @NotBlank(message = "name must not be blank")
        String name,

        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be a valid address")
        String email,

        @NotBlank(message = "department must not be blank")
        String department,

        @NotBlank(message = "title must not be blank")
        String title
) {
    /**
     * Returns a copy of this employee with the given id assigned.
     */
    public Employee withId(Long newId) {
        return new Employee(newId, name, email, department, title);
    }
}
