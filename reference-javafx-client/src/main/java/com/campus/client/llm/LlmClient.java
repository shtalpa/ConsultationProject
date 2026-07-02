package com.campus.client.llm;

/**
 * Common contract for the Large Language Model clients. Implementations talk to one provider
 * (Anthropic, OpenAI, Google Gemini, &hellip;) over HTTP and return generated text.
 *
 * <p>The whole rest of the app depends only on this interface, so swapping providers is just a
 * constructor change in {@link com.campus.client.App}.</p>
 */
public interface LlmClient {

    /**
     * Generates a response for one user prompt with an optional system prompt.
     *
     * @param systemPrompt framing/instructions for the model (may be {@code null} or blank)
     * @param userPrompt   the user message; in this assignment, the retrieved RAG context plus
     *                     the student's question
     * @return the model's generated text
     */
    String complete(String systemPrompt, String userPrompt) throws Exception;

    /** @return the model identifier this client is using (for display in the UI / logs). */
    String model();
}
