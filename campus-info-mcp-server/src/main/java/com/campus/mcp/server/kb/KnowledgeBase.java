package com.campus.mcp.server.kb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The local, file-based knowledge base. It loads plain-text files bundled under
 * {@code /knowledgebase} on the classpath and provides a very small retrieval function.
 *
 * <p>This is the "Retrieval" component of RAG (Retrieval-Augmented Generation). To honour
 * the assignment's "no database" rule, retrieval here is a simple keyword-overlap score over
 * paragraph-sized chunks &mdash; no vector database, no embeddings service. It is deliberately
 * easy to read so students can see exactly how retrieval works.</p>
 */
public final class KnowledgeBase {

    /** A retrievable unit of text and where it came from. */
    public record Chunk(String source, String text) {
    }

    /** A retrieved chunk together with its relevance score. */
    public record Hit(String source, String text, double score) {
    }

    /** Common words ignored when scoring, so they do not dominate the overlap. */
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "is", "are",
            "i", "my", "can", "how", "do", "does", "with", "at", "be", "by", "this", "that");

    private final Map<String, String> rawDocuments = new LinkedHashMap<>();
    private final List<Chunk> chunks = new ArrayList<>();

    /** Loads every named text file from the classpath {@code /knowledgebase} folder. */
    public KnowledgeBase(List<String> fileNames) {
        for (String name : fileNames) {
            String content = readClasspath("/knowledgebase/" + name);
            rawDocuments.put(name, content);
            for (String chunk : splitIntoChunks(content)) {
                chunks.add(new Chunk(name, chunk));
            }
        }
    }

    /** @return the full raw text of a named knowledge file (used by MCP Resources). */
    public String rawDocument(String fileName) {
        String doc = rawDocuments.get(fileName);
        if (doc == null) {
            throw new IllegalArgumentException("Unknown knowledge document: " + fileName);
        }
        return doc;
    }

    public Set<String> documentNames() {
        return rawDocuments.keySet();
    }

    /**
     * Returns the {@code topK} chunks most relevant to the query, ranked by keyword overlap.
     * This is intentionally simple; a production system would use embeddings + a vector index.
     */
    public List<Hit> retrieve(String query, int topK) {
        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }
        return chunks.stream()
                .map(chunk -> new Hit(chunk.source(), chunk.text(), score(queryTerms, chunk.text())))
                .filter(hit -> hit.score() > 0)
                .sorted(Comparator.comparingDouble(Hit::score).reversed())
                .limit(Math.max(1, topK))
                .collect(Collectors.toList());
    }

    // ---- scoring & tokenisation -----------------------------------------

    private double score(List<String> queryTerms, String chunkText) {
        List<String> chunkTerms = tokenize(chunkText);
        if (chunkTerms.isEmpty()) {
            return 0;
        }
        Set<String> chunkSet = Set.copyOf(chunkTerms);
        long overlap = queryTerms.stream().filter(chunkSet::contains).count();
        // Normalise by chunk length so short, focused chunks are not unfairly penalised.
        return overlap == 0 ? 0 : (double) overlap / Math.sqrt(chunkTerms.size());
    }

    private List<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9]+"))
                .filter(t -> t.length() > 1 && !STOP_WORDS.contains(t))
                .collect(Collectors.toList());
    }

    /** Splits a document into chunks on blank lines, keeping non-trivial chunks. */
    private List<String> splitIntoChunks(String content) {
        return Arrays.stream(content.split("\\r?\\n\\s*\\r?\\n"))
                .map(String::strip)
                .filter(s -> s.length() > 3)
                .collect(Collectors.toList());
    }

    private static String readClasspath(String path) {
        try (InputStream in = KnowledgeBase.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Knowledge file not found on classpath: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
