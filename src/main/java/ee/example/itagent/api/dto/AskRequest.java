package ee.example.itagent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequest(
        @NotBlank @Size(max = 2000) String question,
        @Size(max = 100) String sessionId) {
}
