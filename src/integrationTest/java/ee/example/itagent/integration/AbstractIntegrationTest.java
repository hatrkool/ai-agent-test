package ee.example.itagent.integration;

import ee.example.itagent.api.dto.AgentResponse;
import ee.example.itagent.api.dto.AskRequest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/**
 * Shared setup for the live-model integration suite (plan §10 / task brief
 * §7.2). Rate limiting is disabled here — it has its own unit test
 * (RateLimitFilterTest) and would otherwise make this suite's pass/fail
 * depend on how many scenarios ran in the last minute rather than on the
 * scenario each test actually names.
 *
 * <p>{@link #ask(String)} generates a fresh session id per call rather than
 * leaving it null: {@code AgentService} maps a null/blank sessionId to the
 * same literal "anonymous" chat-memory conversation, which would otherwise
 * pile every single-turn test's question into one shared, incoherent
 * conversation history — discovered when the full suite failed almost
 * entirely on tests that passed reliably in isolation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = "agent.rate-limit.enabled=false")
abstract class AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    protected AgentResponse ask(String question) {
        return ask(question, "isolated-" + UUID.randomUUID());
    }

    protected AgentResponse ask(String question, String sessionId) {
        AskRequest request = new AskRequest(question, sessionId);
        return restTemplate.postForObject(askUrl(), request, AgentResponse.class);
    }

    private String askUrl() {
        return "http://localhost:" + port + "/api/v1/agent/ask";
    }
}
