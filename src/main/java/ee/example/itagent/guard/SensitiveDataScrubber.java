package ee.example.itagent.guard;

import ee.example.itagent.guard.SensitiveDataPatterns.Detector;
import ee.example.itagent.guard.SensitiveDataPatterns.Match;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Request-time half of requirement 5.4 (the KB startup half lives in
 * KnowledgeBaseLoader, sharing {@link SensitiveDataPatterns}). Identifiers
 * (isikukood, IBAN) are redacted and the request continues, since a
 * question can be legitimate while incidentally containing one. Credential
 * shapes (provider keys, JWT/bearer tokens, disclosed passwords) refuse
 * outright, since a leaked secret is treated as an incident rather than
 * quietly forwarded to the model.
 */
@Component
public class SensitiveDataScrubber {

    private static final Set<Detector> CREDENTIAL_DETECTORS =
            EnumSet.of(Detector.PROVIDER_KEY, Detector.JWT_OR_BEARER_TOKEN, Detector.PASSWORD_DISCLOSURE);

    public record Result(String scrubbedText, boolean refuse, List<Detector> matchedDetectors) {
    }

    public Result scrub(String text) {
        List<Match> matches = SensitiveDataPatterns.scan(text);
        if (matches.isEmpty()) {
            return new Result(text, false, List.of());
        }
        List<Detector> detectors = matches.stream().map(Match::detector).toList();
        if (detectors.stream().anyMatch(CREDENTIAL_DETECTORS::contains)) {
            return new Result(null, true, detectors);
        }
        return new Result(redact(text, matches), false, detectors);
    }

    private String redact(String text, List<Match> matches) {
        StringBuilder result = new StringBuilder(text);
        matches.stream()
                .sorted((a, b) -> Integer.compare(b.start(), a.start()))
                .forEach(match -> result.replace(match.start(), match.end(), replacementFor(match.detector())));
        return result.toString();
    }

    private String replacementFor(Detector detector) {
        return switch (detector) {
            case ISIKUKOOD -> "[isikukood eemaldatud]";
            case IBAN -> "[konto eemaldatud]";
            default -> "[eemaldatud]";
        };
    }
}
