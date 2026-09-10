package ee.example.itagent.api;

import ee.example.itagent.api.dto.AgentResponse;
import ee.example.itagent.api.dto.AskRequest;
import ee.example.itagent.api.dto.Confidence;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 1 skeleton: validates the request and returns a placeholder response.
 * Replaced by a call into AgentService once the agent, tools and guards exist (Phase 4).
 */
@RestController
public class AgentController {

    @PostMapping("/api/v1/agent/ask")
    public AgentResponse ask(@Valid @RequestBody AskRequest request) {
        return new AgentResponse(
                "Agent ei ole veel implementeeritud.",
                List.of(),
                Confidence.LOW,
                true,
                "NOT_IMPLEMENTED");
    }
}
