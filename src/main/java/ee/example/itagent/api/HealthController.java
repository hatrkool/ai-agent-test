package ee.example.itagent.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness check. Deliberately independent of the ChatClient / OpenAI beans
 * so it stays 200 even without an OPENAI_API_KEY configured (API-03).
 */
@RestController
public class HealthController {

    @GetMapping("/api/v1/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
