package com.cadeteria.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test: levanta todo el contexto de Spring contra H2 en memoria. Sirve para
 * detectar entidades JPA mal mapeadas o consultas derivadas de Spring Data invalidas
 * (esas fallan recien al arrancar, no en tiempo de compilacion).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:cadeteria_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=false",
        "app.jwt.secret=test-secret-de-al-menos-32-caracteres-para-el-test",
        "app.seed.enabled=true"
})
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
