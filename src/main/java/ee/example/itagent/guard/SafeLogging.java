package ee.example.itagent.guard;

import ee.example.itagent.config.AgentProperties;
import ee.example.itagent.guard.SensitiveDataPatterns.Detector;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * CLAUDE.md invariant 7: never log full question text at INFO or above.
 * Full text is DEBUG-only, gated behind agent.logging.verbose (default
 * off). Sensitive-data matches log the detector label and count, never the
 * matched value.
 */
@Component
public class SafeLogging {

    private static final Logger log = LoggerFactory.getLogger("ee.example.itagent.agent");

    private final boolean verbose;

    public SafeLogging(AgentProperties properties) {
        this.verbose = properties.logging().verbose();
    }

    public void logRequest(String sessionId, String question) {
        log.info("session={} questionSha256Prefix={} length={}",
                sessionId, sha256Prefix(question), question.length());
        if (verbose) {
            log.debug("session={} question={}", sessionId, question);
        }
    }

    public void logInjectionMatch(String sessionId, String patternLabel) {
        log.warn("session={} injectionPattern={}", sessionId, patternLabel);
    }

    public void logSensitiveDataMatch(String sessionId, List<Detector> detectors) {
        log.warn("session={} sensitiveDataDetectors={} count={}",
                sessionId, distinctLabels(detectors), detectors.size());
    }

    private String distinctLabels(List<Detector> detectors) {
        return detectors.stream().map(Enum::name).distinct().sorted().toList().toString();
    }

    private String sha256Prefix(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
