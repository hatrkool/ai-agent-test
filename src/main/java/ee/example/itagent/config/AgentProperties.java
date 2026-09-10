package ee.example.itagent.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        int maxQuestionLength,
        Duration sessionTtl,
        int maxSessions,
        Kb kb,
        RateLimit rateLimit,
        Logging logging) {

    public record Kb(String path, boolean semanticSearchEnabled, int topK, double scoreThreshold) {
    }

    public record RateLimit(boolean enabled, int requestsPerMinute) {
    }

    public record Logging(boolean verbose) {
    }
}
