package com.campus.client.rag;

import com.campus.client.llm.AnthropicClient;
import com.campus.client.mcp.CampusMcpClient;

import java.util.Map;

/**
 * Implements Retrieval-Augmented Generation (RAG) by combining the MCP client and the LLM client:
 *
 * <ol>
 *   <li><b>Retrieve</b> &mdash; call the server's {@code search_campus_info} tool to fetch the most
 *       relevant knowledge-base passages for the question.</li>
 *   <li><b>Augment</b> &mdash; fetch the server's {@code campus_assistant} prompt as the system
 *       framing, and build a user prompt that embeds the retrieved passages as context.</li>
 *   <li><b>Generate</b> &mdash; ask the LLM to answer using only that context.</li>
 * </ol>
 *
 * Retrieval and prompt wording both come from the MCP server, so the client stays thin and the
 * "knowledge" lives in one place.
 */
public final class RagService_old {

    /** Bundles the answer with the context used, so the UI can show how the answer was grounded. */
    public record RagResult(String retrievedContext, String systemPrompt, String answer) {
    }

    private final CampusMcpClient mcp;
    private final AnthropicClient llm;

    public RagService_old(CampusMcpClient mcp, AnthropicClient llm) {
        this.mcp = mcp;
        this.llm = llm;
    }

    public RagResult ask(String question, String topic) throws Exception {
        // 1. RETRIEVE: pull grounding passages from the knowledge base via the MCP tool.
        String context = mcp.callTool("search_campus_info",
                Map.of("query", question, "topK", 3));

        // 2. AUGMENT: use the server-provided prompt template as the system instruction.
        String systemPrompt = mcp.getPrompt("campus_assistant",
                Map.of("topic", topic == null || topic.isBlank() ? "general campus services" : topic));

        String userPrompt = """
            Context passages from the campus knowledge base:
            ----------------------------------------------------
            %s
            ----------------------------------------------------
            Using only the context above, answer the student's question. If the answer is not in the
            context, say you are not sure and suggest who to contact.

            Question: %s
            """.formatted(context, question);

        // 3. GENERATE: ask the LLM for a grounded answer.
        String answer = llm.complete(systemPrompt, userPrompt);

        return new RagResult(context, systemPrompt, answer);
    }
}
