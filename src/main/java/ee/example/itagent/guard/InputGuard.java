package ee.example.itagent.guard;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Pre-LLM refusal: refuses before any model call on the first
 * {@link InjectionPatterns} match, so SEC-01/03/08 stay deterministic even
 * when the model would have complied with an injection attempt.
 */
@Component
public class InputGuard {

    public Optional<String> matchLabel(String question) {
        return InjectionPatterns.ALL.stream()
                .filter(named -> named.pattern().matcher(question).find())
                .map(InjectionPatterns.NamedPattern::label)
                .findFirst();
    }
}
