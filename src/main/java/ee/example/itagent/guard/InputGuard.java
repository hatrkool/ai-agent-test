package ee.example.itagent.guard;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Pre-LLM refusal: refuses before any model call on the first
 * {@link InjectionPatterns} match, so SEC-01/03/08 stay deterministic even
 * when the model would have complied with an injection attempt. Matching
 * runs against {@link InputNormalizer}'s output, not the raw question, so
 * common regex-evasion tricks (invisible characters, "i.g.n.o.r.e"-style
 * separators, leetspeak digits) don't bypass the patterns below.
 */
@Component
public class InputGuard {

    public Optional<String> matchLabel(String question) {
        String normalized = InputNormalizer.normalize(question);
        return InjectionPatterns.ALL.stream()
                .filter(named -> named.pattern().matcher(normalized).find())
                .map(InjectionPatterns.NamedPattern::label)
                .findFirst();
    }
}
