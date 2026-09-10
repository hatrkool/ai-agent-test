package ee.example.itagent.api;

import ee.example.itagent.agent.AgentService;
import ee.example.itagent.api.dto.AgentResponse;
import ee.example.itagent.api.dto.AskRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/api/v1/agent/ask")
    public AgentResponse ask(@Valid @RequestBody AskRequest request) {
        return agentService.answer(request.question(), request.sessionId());
    }
}
