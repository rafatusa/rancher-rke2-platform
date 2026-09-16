package com.udap.platform.employeeapi.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class EmployeeApiWebTest {

    private final MockMvc mockMvc;

    @Autowired
    EmployeeApiWebTest(WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("GET / serves the HTML landing page")
    void homeServesHtml() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Employee API")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ada Lovelace")));
    }

    @Test
    @DisplayName("GET /health reports UP")
    void healthReportsUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("employee-api"))
                .andExpect(jsonPath("$.employees").isNumber())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("GET /employees returns the seeded collection")
    void listsEmployees() throws Exception {
        mockMvc.perform(get("/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Ada Lovelace"))
                .andExpect(jsonPath("$[0].department").value("Engineering"));
    }

    @Test
    @DisplayName("GET /employees/{id} returns a single employee")
    void getsSingleEmployee() throws Exception {
        mockMvc.perform(get("/employees/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("Grace Hopper"));
    }

    @Test
    @DisplayName("GET /employees/{id} returns 404 for an unknown id")
    void unknownEmployeeIsNotFound() throws Exception {
        mockMvc.perform(get("/employees/424242"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /employees creates a record")
    void createsEmployee() throws Exception {
        String payload = """
                {"name":"Radia Perlman","email":"radia@example.com",
                 "department":"Networking","title":"Distinguished Engineer"}
                """;

        mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Radia Perlman"));
    }

    @Test
    @DisplayName("POST /employees rejects an invalid payload")
    void rejectsInvalidPayload() throws Exception {
        String payload = """
                {"name":"","email":"not-an-email","department":"","title":""}
                """;

        mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("actuator health probe is exposed for Kubernetes")
    void actuatorHealthExposed() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
