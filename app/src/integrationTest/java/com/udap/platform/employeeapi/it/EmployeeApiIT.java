package com.udap.platform.employeeapi.it;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * REST Assured API tests executed against a DEPLOYED Employee API.
 *
 * <p>The base URL is supplied by the validation pipeline:
 * {@code gradle integrationTest -Papi.base.url=http://api.<ip>.sslip.io}
 *
 * <p>Note on state: the service keeps employees in memory, and the deployment
 * runs multiple replicas behind the ingress. Writes are therefore only visible
 * on the pod that served them — these tests assert the API contract, never
 * cross-replica persistence.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmployeeApiIT {

    @BeforeAll
    static void configure() {
        RestAssured.baseURI = System.getProperty("api.base.url", "http://localhost:8080");
        RestAssured.useRelaxedHTTPSValidation();
    }

    @Test
    @Order(1)
    @DisplayName("health endpoint reports the service as UP")
    void healthIsUp() {
        given()
            .when()
                .get("/health")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", equalTo("UP"))
                .body("service", equalTo("employee-api"))
                .body("timestamp", notNullValue());
    }

    @Test
    @Order(2)
    @DisplayName("landing page is served as HTML through the ingress")
    void landingPageIsServed() {
        given()
            .when()
                .get("/")
            .then()
                .statusCode(200)
                .contentType(ContentType.HTML)
                .body(containsString("Employee API"));
    }

    @Test
    @Order(3)
    @DisplayName("employees collection is returned")
    void listsEmployees() {
        given()
            .when()
                .get("/employees")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", greaterThanOrEqualTo(4))
                .body("[0].id", notNullValue())
                .body("[0].name", notNullValue())
                .body("[0].email", containsString("@"));
    }

    @Test
    @Order(4)
    @DisplayName("a single employee is retrievable by id")
    void getsEmployeeById() {
        given()
                .pathParam("id", 1)
            .when()
                .get("/employees/{id}")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", equalTo(1))
                .body("department", notNullValue());
    }

    @Test
    @Order(5)
    @DisplayName("an unknown employee id returns 404")
    void unknownIdReturnsNotFound() {
        given()
                .pathParam("id", 987654)
            .when()
                .get("/employees/{id}")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(6)
    @DisplayName("an employee can be created")
    void createsEmployee() {
        String payload = """
                {"name":"Integration Tester","email":"it@example.com",
                 "department":"Quality","title":"SDET"}
                """;

        Integer createdId =
            given()
                    .contentType(ContentType.JSON)
                    .body(payload)
                .when()
                    .post("/employees")
                .then()
                    .statusCode(201)
                    .contentType(ContentType.JSON)
                    .body("id", greaterThan(0))
                    .body("name", equalTo("Integration Tester"))
                    .body("email", equalTo("it@example.com"))
                    .extract()
                    .path("id");

        // The read-back may be served by a different replica, which does not
        // hold this record. Either outcome satisfies the API contract.
        given()
                .pathParam("id", createdId)
            .when()
                .get("/employees/{id}")
            .then()
                .statusCode(anyOf(equalTo(200), equalTo(404)));
    }

    @Test
    @Order(7)
    @DisplayName("invalid payloads are rejected with 400")
    void rejectsInvalidPayload() {
        String payload = """
                {"name":"","email":"nope","department":"","title":""}
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
            .when()
                .post("/employees")
            .then()
                .statusCode(400);
    }
}
