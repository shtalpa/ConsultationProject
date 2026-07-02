package com.campus.client.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Minimal Anthropic Messages API client built on the JDK {@link HttpClient}. This is the
 * "Generation" half of RAG: given a prompt that already contains retrieved context, it asks the
 * model for a grounded answer.
 *
 * <p>Set the API key in the {@code ANTHROPIC_API_KEY} environment variable. Students may swap this
 * class for any other LLM provider; nothing else in the client depends on Anthropic specifically.</p>
 */

// public final class AnthropicClient  {
public final class AnthropicClient implements LlmClient {

    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public AnthropicClient(String apiKey, String model, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Missing Anthropic API key (set ANTHROPIC_API_KEY).");
        }
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    /**
     * Sends a single user prompt with an optional system prompt and returns the model's text.
     */
    public String complete(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("system", systemPrompt);
        }
        ArrayNode messages = mapper.createArrayNode();
        ObjectNode userMsg = mapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);
        body.set("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(120))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Anthropic API error " + response.statusCode() + ": " + response.body());
        }

        // Concatenate all text blocks from the response content array.
        JsonNode content = mapper.readTree(response.body()).path("content");
        StringBuilder out = new StringBuilder();
        for (JsonNode block : content) {
            if ("text".equals(block.path("type").asText())) {
                out.append(block.path("text").asText());
            }
        }
        return out.toString();
    }

    public String model() {
        return model;
    }
}
