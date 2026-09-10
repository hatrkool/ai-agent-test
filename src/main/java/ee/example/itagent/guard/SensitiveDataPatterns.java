package ee.example.itagent.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detectors for data that must never reach OpenAI or the knowledge base
 * (requirement 5.4). Shared by the request-time scrubber (guards a user
 * question) and the knowledge-base loader (fails startup if a KB file
 * contains any of these shapes). Match values are never exposed by this
 * class — only the detector label, so callers can log safely.
 */
public final class SensitiveDataPatterns {

    public enum Detector {
        ISIKUKOOD,
        PROVIDER_KEY,
        JWT_OR_BEARER_TOKEN,
        PASSWORD_DISCLOSURE,
        IBAN
    }

    public record Match(Detector detector, int start, int end) {
    }

    private static final Pattern ISIKUKOOD =
            Pattern.compile("\\b[1-6]\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{4}\\b");

    private static final Pattern PROVIDER_KEY = Pattern.compile(
            "sk-[A-Za-z0-9_\\-]{20,}|ghp_[A-Za-z0-9]{36}|glpat-[A-Za-z0-9_\\-]{20}|AKIA[0-9A-Z]{16}");

    private static final Pattern JWT_OR_BEARER = Pattern.compile(
            "eyJ[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,}\\.|bearer\\s+[A-Za-z0-9._\\-]{20,}",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PASSWORD_DISCLOSURE = Pattern.compile(
            "(parool|salasõna|password|pwd)\\s*[:=]\\s*\\S+", Pattern.CASE_INSENSITIVE);

    private static final Pattern IBAN = Pattern.compile("\\bEE\\d{18}\\b");

    private SensitiveDataPatterns() {
    }

    /** Scans text for all detectors, returning only labels/positions — never the matched value. */
    public static List<Match> scan(String text) {
        List<Match> matches = new ArrayList<>();
        addMatches(matches, Detector.ISIKUKOOD, ISIKUKOOD, text, SensitiveDataPatterns::isValidIsikukood);
        addMatches(matches, Detector.PROVIDER_KEY, PROVIDER_KEY, text, m -> true);
        addMatches(matches, Detector.JWT_OR_BEARER_TOKEN, JWT_OR_BEARER, text, m -> true);
        addMatches(matches, Detector.PASSWORD_DISCLOSURE, PASSWORD_DISCLOSURE, text, m -> true);
        addMatches(matches, Detector.IBAN, IBAN, text, m -> true);
        return matches;
    }

    private static void addMatches(List<Match> out, Detector detector, Pattern pattern, String text,
            java.util.function.Predicate<String> accept) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (accept.test(matcher.group())) {
                out.add(new Match(detector, matcher.start(), matcher.end()));
            }
        }
    }

    /** Estonian isikukood mod-11 checksum, used to cut false positives on the bare regex. */
    static boolean isValidIsikukood(String code) {
        if (code.length() != 11) {
            return false;
        }
        int[] digits = code.chars().map(c -> c - '0').toArray();
        int check = checksum(digits, new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 1});
        if (check == 10) {
            check = checksum(digits, new int[] {3, 4, 5, 6, 7, 8, 9, 1, 2, 3});
            if (check == 10) {
                check = 0;
            }
        }
        return check == digits[10];
    }

    private static int checksum(int[] digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += digits[i] * weights[i];
        }
        return sum % 11;
    }
}
