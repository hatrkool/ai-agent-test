package ee.example.itagent.tools;

import ee.example.itagent.kb.KnowledgeChunk;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Records every chunk a {@link KnowledgeBaseTools} call actually returned
 * during the current request. {@code OutputGuard} (Phase 4) verifies the
 * model's cited sources and excerpts against this ledger after the model
 * runs — without it, "sources" would be unverifiable model output.
 */
@Component
@RequestScope
public class RetrievalLedger {

    private final List<KnowledgeChunk> chunks = new ArrayList<>();

    public void record(KnowledgeChunk chunk) {
        chunks.add(chunk);
    }

    public List<KnowledgeChunk> chunks() {
        return List.copyOf(chunks);
    }
}
