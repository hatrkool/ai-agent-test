package ee.example.itagent.kb;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * DATA-03 (plan §10): a knowledge-base document containing something
 * sensitive-data-shaped must fail application startup, naming the offending
 * file and detector but never the matched value (CLAUDE.md invariant 4). The
 * fixture at {@code src/test/resources/kb-data03-fixture/} is a deliberately
 * bad document — never load {@code classpath:kb/} (the real KB) here.
 */
class KnowledgeBaseLoaderTest {

    @Test
    void data03_kbDocumentContainingIsikukood_failsStartupNamingFileAndDetectorNotValue() {
        assertThatThrownBy(() -> new KnowledgeBaseLoader("classpath:kb-data03-fixture/"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bad-document.md")
                .hasMessageContaining("ISIKUKOOD")
                .hasMessageNotContaining("47101010000");
    }
}
