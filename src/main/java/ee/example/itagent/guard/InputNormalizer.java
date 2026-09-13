package ee.example.itagent.guard;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Folds common regex-evasion tricks (invisible characters, letter-by-letter
 * separators, leetspeak digits) before {@link InjectionPatterns} runs, so
 * "i.g.n.o.r.e previous instructions" or "1gn0re previous instructions"
 * still match the same patterns as "ignore previous instructions". The
 * normalised text is used only for pattern matching here — never sent to
 * the model, logged, or shown to the user — so an aggressive fold is safe:
 * a false match only produces an over-cautious refusal, not incorrect
 * behaviour. See README's "Known limitations" for what this still misses
 * (genuine paraphrase, encoded payloads) and the fuller fix for those.
 */
final class InputNormalizer {

    private static final Pattern INVISIBLE = Pattern.compile("[\\u200B\\u200C\\u200D\\uFEFF\\u2060\\u00AD]");
    private static final Pattern LETTER_SEPARATOR = Pattern.compile("(?<=\\p{L})[.\\-_*~]+(?=\\p{L})");

    private InputNormalizer() {
    }

    static String normalize(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        normalized = INVISIBLE.matcher(normalized).replaceAll("");
        normalized = foldLeetspeak(normalized);
        normalized = LETTER_SEPARATOR.matcher(normalized).replaceAll("");
        return normalized;
    }

    private static String foldLeetspeak(String text) {
        StringBuilder folded = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            folded.append(foldChar(text.charAt(i)));
        }
        return folded.toString();
    }

    private static char foldChar(char c) {
        return switch (c) {
            case '0' -> 'o';
            case '1' -> 'i';
            case '3' -> 'e';
            case '4' -> 'a';
            case '5' -> 's';
            case '7' -> 't';
            case '$' -> 's';
            case '@' -> 'a';
            default -> c;
        };
    }
}
