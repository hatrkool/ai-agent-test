package ee.example.itagent.api.dto;

import java.util.List;

public record AgentResponse(
        String answer,
        List<SourceRef> sources,
        Confidence confidence,
        boolean refused,
        String refusalReason) {
}
