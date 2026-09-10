package ee.example.itagent.kb;

import ee.example.itagent.guard.SensitiveDataPatterns;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Parses the Markdown knowledge base at startup: front-matter (title, topic,
 * aliases) plus a body split on "##" headings into one chunk per section.
 *
 * Also enforces requirement 5.4 for the knowledge base itself: every chunk is
 * scanned with {@link SensitiveDataPatterns}, and startup fails if any file
 * contains something that looks like an isikukood, IBAN, API key, JWT or a
 * disclosed password. This turns "no sensitive data in the KB" from a README
 * promise into something that cannot silently regress.
 */
@Component
public class KnowledgeBaseLoader {

    private static final Pattern FRONT_MATTER =
            Pattern.compile("\\A---\\s*\\n(.*?)\\n---\\s*\\n(.*)\\z", Pattern.DOTALL);
    private static final Pattern HEADING = Pattern.compile("(?m)^##\\s+(.+?)\\s*$");

    private final Map<String, KnowledgeDocument> documentsByFile;

    public KnowledgeBaseLoader(@Value("${agent.kb.path:classpath:kb/}") String kbPath) {
        this.documentsByFile = load(kbPath);
    }

    public Map<String, KnowledgeDocument> documentsByFile() {
        return documentsByFile;
    }

    public List<KnowledgeChunk> allChunks() {
        return documentsByFile.values().stream()
                .flatMap(doc -> doc.chunks().stream())
                .toList();
    }

    private Map<String, KnowledgeDocument> load(String kbPath) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources(kbPath + "*.md");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list knowledge base resources at " + kbPath, e);
        }
        Arrays.sort(resources, (a, b) -> a.getFilename().compareTo(b.getFilename()));

        Map<String, KnowledgeDocument> result = new LinkedHashMap<>();
        for (Resource resource : resources) {
            KnowledgeDocument document = parse(resource);
            result.put(document.file(), document);
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("No knowledge base documents found under " + kbPath);
        }
        return result;
    }

    private KnowledgeDocument parse(Resource resource) {
        String fileName = resource.getFilename();
        String raw = readContent(resource);

        Matcher fm = FRONT_MATTER.matcher(raw);
        if (!fm.matches()) {
            throw new IllegalStateException("Knowledge base file " + fileName + " is missing YAML front-matter");
        }
        Map<String, String> meta = parseFrontMatter(fm.group(1));
        String body = fm.group(2);

        String title = meta.getOrDefault("title", fileName);
        String topic = meta.getOrDefault("topic", "");
        List<String> aliases = parseAliases(meta.get("aliases"));

        List<KnowledgeChunk> chunks = chunk(fileName, title, body);
        if (chunks.isEmpty()) {
            throw new IllegalStateException("Knowledge base file " + fileName + " has no ## sections to chunk");
        }

        for (KnowledgeChunk chunk : chunks) {
            failOnSensitiveData(fileName, chunk.text());
        }

        return new KnowledgeDocument(fileName, title, topic, aliases, chunks);
    }

    private List<KnowledgeChunk> chunk(String fileName, String title, String body) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        Matcher matcher = HEADING.matcher(body);
        List<int[]> headingSpans = new ArrayList<>();
        List<String> headings = new ArrayList<>();
        while (matcher.find()) {
            headingSpans.add(new int[] {matcher.start(), matcher.end()});
            headings.add(matcher.group(1).trim());
        }
        for (int i = 0; i < headings.size(); i++) {
            int contentStart = headingSpans.get(i)[1];
            int contentEnd = (i + 1 < headings.size()) ? headingSpans.get(i + 1)[0] : body.length();
            String sectionText = body.substring(contentStart, contentEnd).trim();
            String heading = headings.get(i);
            String id = fileName + "#" + slugify(heading);
            chunks.add(new KnowledgeChunk(id, fileName, title, heading, sectionText));
        }
        return chunks;
    }

    private void failOnSensitiveData(String fileName, String text) {
        List<SensitiveDataPatterns.Match> matches = SensitiveDataPatterns.scan(text);
        if (!matches.isEmpty()) {
            String detector = matches.get(0).detector().name();
            throw new IllegalStateException(
                    "Refusing to start: knowledge base file " + fileName
                            + " contains data matching detector " + detector
                            + ". Remove it before the application can boot.");
        }
    }

    private Map<String, String> parseFrontMatter(String frontMatter) {
        Map<String, String> meta = new LinkedHashMap<>();
        for (String line : frontMatter.split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            meta.put(key, value);
        }
        return meta;
    }

    private List<String> parseAliases(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        List<String> aliases = new ArrayList<>();
        for (String part : trimmed.split(",")) {
            String alias = part.trim();
            if (!alias.isEmpty()) {
                aliases.add(alias);
            }
        }
        return aliases;
    }

    private String slugify(String heading) {
        String folded = heading.toLowerCase()
                .replace('õ', 'o').replace('ä', 'a').replace('ö', 'o')
                .replace('ü', 'u').replace('š', 's').replace('ž', 'z');
        String slug = folded.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "section" : slug;
    }

    private String readContent(Resource resource) {
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read knowledge base file " + resource.getFilename(), e);
        }
    }
}
