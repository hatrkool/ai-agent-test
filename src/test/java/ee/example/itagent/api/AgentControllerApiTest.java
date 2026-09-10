package ee.example.itagent.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.example.itagent.agent.AgentService;
import ee.example.itagent.api.dto.AgentResponse;
import ee.example.itagent.api.dto.Confidence;
import ee.example.itagent.api.dto.SourceRef;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Unit-level API contract tests: AgentService is mocked, so a passing run
 * proves the model is never touched for validation failures. Rate limiting
 * is disabled here — it has its own dedicated test in RateLimitFilterTest
 * and would otherwise make this class order-dependent.
 */
@WebMvcTest(AgentController.class)
@TestPropertySource(properties = "agent.rate-limit.enabled=false")
class AgentControllerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentService agentService;

    @Test
    void api01_emptyQuestion_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/agent/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": ""}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(agentService);
    }

    @Test
    void api02_missingQuestionField_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/agent/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId": "abc"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(agentService);
    }

    @Test
    void sec07_overlongQuestion_returns400BeforeAnyModelCall() throws Exception {
        String tooLong = "a".repeat(3000);
        mockMvc.perform(post("/api/v1/agent/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(agentService);
    }

    @Test
    void validQuestion_delegatesToAgentServiceAndReturnsItsResponse() throws Exception {
        AgentResponse response = new AgentResponse("Vastus [allikas: gitlab-access.md]",
                List.of(new SourceRef("gitlab-access.md", "GitLab ligipääs", "excerpt")),
                Confidence.HIGH, false, null);
        when(agentService.answer(eq("Kuidas saada GitLabi ligipääsu?"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/agent/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "Kuidas saada GitLabi ligipääsu?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(false))
                .andExpect(jsonPath("$.sources[0].file").value("gitlab-access.md"));
    }
}
