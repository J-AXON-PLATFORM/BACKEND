package com.jaxon;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:1/jaxon_context_test",
        "spring.datasource.username=jaxon_test",
        "spring.datasource.password=jaxon_test",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.flyway.enabled=false"
})
class JaxonApplicationTests {

    @Test
    void contextLoads() {
    }
}
