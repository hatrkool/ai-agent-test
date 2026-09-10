package ee.example.itagent.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Unit-level API contract tests: no Spring AI / OpenAI beans are loaded by this slice,
 * so a passing run already proves the model is never touched.
 */
@WebMvcTest(AgentController.class)
class AgentControllerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void api01_emptyQuestion_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/agent/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": ""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void api02_missingQuestionField_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/agent/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId": "abc"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sec07_overlongQuestion_returns400BeforeAnyModelCall() throws Exception {
        String tooLong = "a".repeat(3000);
        mockMvc.perform(post("/api/v1/agent/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
