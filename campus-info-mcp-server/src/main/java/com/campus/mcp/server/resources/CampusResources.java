package com.campus.mcp.server.resources;

import com.campus.mcp.server.kb.KnowledgeBase;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;

import java.util.List;

/**
 * Builds the campus {@code Resources}. A Resource is read-only context identified by a URI that
 * a client can list and read. Here each knowledge-base text file is published as a resource so
 * a client can pull whole documents (as opposed to the {@code search_campus_info} tool, which
 * returns only the most relevant passages).
 */
public final class CampusResources {

    private static final String MIME = "text/plain";

    private final KnowledgeBase kb;

    public CampusResources(KnowledgeBase kb) {
        this.kb = kb;
    }

    public List<SyncResourceSpecification> all() {
        return List.of(
                document("campus://handbook", "Student Handbook",
                        "General campus rules, booking, appointments and leave policy.", "handbook.txt"),
                document("campus://facilities", "Facilities & Hours",
                        "Buildings, bookable rooms, capacities and opening hours.", "facilities.txt"),
                document("campus://faq", "Frequently Asked Questions",
                        "Common student questions about campus services.", "faq.txt"));
    }

    private SyncResourceSpecification document(String uri, String name, String description, String fileName) {
        Resource resource = Resource.builder()
                .uri(uri)
                .name(name)
                .description(description)
                .mimeType(MIME)
                .build();

        return new SyncResourceSpecification(resource, (exchange, request) -> {
            String text = kb.rawDocument(fileName);
            return new ReadResourceResult(List.of(new TextResourceContents(uri, MIME, text)));
        });
    }
}
