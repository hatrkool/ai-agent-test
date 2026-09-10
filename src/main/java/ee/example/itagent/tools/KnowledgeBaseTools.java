package ee.example.itagent.tools;

import ee.example.itagent.kb.KnowledgeBaseIndex;
import ee.example.itagent.kb.KnowledgeBaseLoader;
import ee.example.itagent.kb.KnowledgeChunk;
import ee.example.itagent.kb.KnowledgeDocument;
import ee.example.itagent.kb.SearchResult;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The complete tool allowlist (plan §6) — the agent can do nothing beyond
 * these three operations. {@code getDocument} is a map lookup only; there is
 * no code path from user input to the filesystem, which is the SEC-06
 * (path-traversal) defense by construction.
 */
@Component
public class KnowledgeBaseTools {

    private final KnowledgeBaseLoader loader;
    private final KnowledgeBaseIndex index;
    private final RetrievalLedger ledger;

    public KnowledgeBaseTools(KnowledgeBaseLoader loader, KnowledgeBaseIndex index, RetrievalLedger ledger) {
        this.loader = loader;
        this.index = index;
        this.ledger = ledger;
    }

    @Tool(name = "listTopics", description = "Loetleb kõik teadmusbaasis olevad teemad koos failinimedega.")
    public List<TopicSummary> listTopics() {
        return loader.documentsByFile().values().stream()
                .map(doc -> {
                    ledger.record(new KnowledgeChunk(doc.file() + "#topics", doc.file(), doc.title(),
                            "teemad", doc.title()));
                    return new TopicSummary(doc.file(), doc.title(), doc.topic());
                })
                .toList();
    }

    @Tool(name = "searchKnowledgeBase", description = "Otsib teadmusbaasist kasutaja küsimusele vastavaid "
            + "lõike. Tagastab lõigu teksti, failinime ja pealkirja.")
    public List<ChunkResult> searchKnowledgeBase(
            @ToolParam(description = "Kasutaja küsimus või otsingufraas") String query) {
        List<SearchResult> hits = index.search(query);
        hits.forEach(hit -> ledger.record(hit.chunk()));
        return hits.stream()
                .map(hit -> new ChunkResult(
                        hit.chunk().file(), hit.chunk().docTitle(), hit.chunk().sectionHeading(), hit.chunk().text()))
                .toList();
    }

    @Tool(name = "getDocument", description = "Tagastab ühe teadmusbaasi dokumendi sisu failinime järgi. "
            + "KOHUSTUSLIK kasutada uuesti ka siis, kui pead kinnitama, tsiteerima või osutama allikale, "
            + "mida oled samas vestluses juba maininud — varasem vestlusmälu ei asenda seda kutset.")
    public DocumentResult getDocument(
            @ToolParam(description = "Teadmusbaasi faili nimi, nt gitlab-access.md") String fileName) {
        KnowledgeDocument doc = loader.documentsByFile().get(fileName);
        if (doc == null) {
            return DocumentResult.notFound(fileName);
        }
        doc.chunks().forEach(ledger::record);
        String content = doc.chunks().stream()
                .map(chunk -> "## " + chunk.sectionHeading() + "\n" + chunk.text())
                .collect(Collectors.joining("\n\n"));
        return DocumentResult.found(doc.file(), doc.title(), content);
    }

    public record TopicSummary(String file, String title, String topic) {
    }

    public record ChunkResult(String file, String title, String sectionHeading, String text) {
    }

    public record DocumentResult(String file, String title, String content, boolean found) {
        public static DocumentResult notFound(String requestedFile) {
            return new DocumentResult(requestedFile, null, null, false);
        }

        public static DocumentResult found(String file, String title, String content) {
            return new DocumentResult(file, title, content, true);
        }
    }
}
