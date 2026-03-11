package com.citycare;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * CityCareApplicationTests – Spring context load test.
 *
 * Uses H2 in-memory database (test/resources/application.properties).
 * Hibernate creates all 5 tables in H2 on startup.
 * If this test passes, all beans are wired correctly.
 *
 * Run with: mvn test
 */
@SpringBootTest
@ActiveProfiles("test")
class CityCareApplicationTests {

    @Test
    void contextLoads() {
        // Passes if Spring context starts without errors.
        // Verifies: Hibernate creates tables, Security config loads, all beans wired.
    }
}
