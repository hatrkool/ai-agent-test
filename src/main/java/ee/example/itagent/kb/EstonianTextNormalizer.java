package ee.example.itagent.kb;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Crude Estonian-aware normaliser used for lexical retrieval scoring.
 * Not a real stemmer — folding diacritics and truncating to 5 characters
 * is a cheap way to collapse common inflections (ligipääs / ligipääsu /
 * ligipääsule -> "ligip"). See IMPLEMENTATION_PLAN.md §5 and §13 for the
 * documented limitation.
 */
public final class EstonianTextNormalizer {

    private static final int STEM_LENGTH = 5;

    private EstonianTextNormalizer() {
    }

    public static Set<String> normalizedTokens(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String folded = foldDiacritics(text.toLowerCase());
        String[] rawTokens = folded.split("[^a-z0-9]+");
        Set<String> tokens = new LinkedHashSet<>();
        Arrays.stream(rawTokens)
                .filter(token -> !token.isBlank())
                .map(EstonianTextNormalizer::stem)
                .forEach(tokens::add);
        return tokens;
    }

    private static String stem(String token) {
        return token.length() <= STEM_LENGTH ? token : token.substring(0, STEM_LENGTH);
    }

    private static String foldDiacritics(String text) {
        return text.replace('õ', 'o').replace('ä', 'a').replace('ö', 'o')
                .replace('ü', 'u').replace('š', 's').replace('ž', 'z');
    }

    /**
     * Lowercase + diacritic-folded, but not tokenised or stemmed. Used for
     * literal-phrase alias matching, which needs to stay precise — the
     * stemmed token overlap above is too coarse for that (a single common
     * stem like "ligip" collides across unrelated documents).
     */
    public static String foldAndLowercase(String text) {
        return text == null ? "" : foldDiacritics(text.toLowerCase());
    }

    /** Overlap score in [0,1]: fraction of query tokens present in the target token set. */
    public static double overlapScore(Set<String> queryTokens, Set<String> targetTokens) {
        if (queryTokens.isEmpty()) {
            return 0.0;
        }
        long hits = queryTokens.stream().filter(targetTokens::contains).count();
        return (double) hits / queryTokens.size();
    }
}
