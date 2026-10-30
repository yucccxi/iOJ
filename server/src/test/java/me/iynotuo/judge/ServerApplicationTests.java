package me.iynotuo.judge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test that the Spring context starts with all beans, mapper XML files and
 * configuration properties wired together.
 */
@SpringBootTest
@ActiveProfiles("test")
class ServerApplicationTests {

    /**
     * Fails when any bean, mapper or configuration property cannot be created.
     */
    @Test
    void contextLoads() {
    }

}
