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
 * OpenAI Chat Completions API client. Same shape as {@link AnthropicClient}: provide an API key,
 * a model name and a token limit; call {@link #complete(String, String)} with a system prompt and
 * a user prompt and get the generated text back.
 *
 * <p>Endpoint: {@code POST https://api.openai.com/v1/chat/completions}.<br>
 * Auth: {@code Authorization: Bearer &lt;apiKey&gt;} header.<br>
 * Request shape (simplified):</p>
 * <pre>{@code
 * {
 *   "model": "gpt-4o-mini",
 *   "max_tokens": 1024,
 *   "messages": [
 *     { "role": "system", "content": "..." },
 *     { "role": "user",   "content": "..." }
 *   ]
 * }
 * }</pre>
 *
 * <p>Set the API key in the {@code OPENAI_API_KEY} environment variable. Model names change over
 * time; confirm the current options in OpenAI's documentation before submitting.</p>
 */
public final class OpenAiClient implements LlmClient {

    private static final String ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public OpenAiClient(String apiKey, String model, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Missing OpenAI API key (set OPENAI_API_KEY).");
        }
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);

        // messages: [ {role: system, content: ...}, {role: user, content: ...} ]
        ArrayNode messages = mapper.createArrayNode();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sys = mapper.createObjectNode();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            messages.add(sys);
        }
        ObjectNode user = mapper.createObjectNode();
        user.put("role", "user");
        user.put("content", userPrompt);
        messages.add(user);
        body.set("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(120))
                .header("content-type", "application/json")
                .header("authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("OpenAI API error " + response.statusCode() + ": " + response.body());
        }

        // Response shape:  { "choices": [ { "message": { "content": "..." } } ] }
        JsonNode root = mapper.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IOException("OpenAI API returned no choices: " + response.body());
        }
        return choices.get(0).path("message").path("content").asText("");
    }

    @Override
    public String model() {
        return model;
    }
}
