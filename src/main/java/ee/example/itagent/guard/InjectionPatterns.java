package ee.example.itagent.guard;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Case-insensitive prompt-injection and path-traversal regexes (plan §8), in
 * both languages the agent must defend against. {@link InputGuard} refuses
 * on the first match, before any model call — see CLAUDE.md invariant 3 for
 * why this stays deterministic rather than delegated to the model.
 */
public final class InjectionPatterns {

    public record NamedPattern(String label, Pattern pattern) {
    }

    public static final List<NamedPattern> ALL = List.of(
            named("ignore_previous_instructions", "ignore (all )?previous instructions"),
            named("forget_rules", "forget your rules"),
            named("you_are_now", "you are now"),
            named("act_as", "act as"),
            named("system_colon", "system\\s*:"),
            named("dan", "\\bDAN\\b"),
            named("reveal_prompt", "reveal.*(system )?prompt"),
            named("list_tools", "list (all )?(available )?tools"),
            named("repeat_verbatim", "repeat.*(verbatim|word for word)"),
            named("ignoreeri_juhis", "ignoreeri.*juhis"),
            named("unusta_reegel", "unusta.*reegl"),
            named("sa_oled_nyyd", "sa oled nüüd"),
            named("kaitu_nagu", "käitu nagu"),
            named("susteem_koolon", "süsteem\\s*:"),
            named("avalda_prompt", "ava(lda)?.*prompt"),
            named("korda_sona_sonalt", "korda.*sõna-sõnalt"),
            named("path_traversal", "\\.\\./"),
            named("etc_path", "/etc/"));

    private InjectionPatterns() {
    }

    private static NamedPattern named(String label, String regex) {
        return new NamedPattern(label, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }
}
