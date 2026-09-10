package ee.example.itagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full context load, no OPENAI_API_KEY needed: proves every bean (KB index,
 * tool allowlist, request-scoped ledger, OpenAI autoconfiguration) wires
 * together, not just that each class compiles in isolation.
 */
@SpringBootTest
class ItAgentApplicationTests {

    @Test
    void contextLoads() {
    }
}
