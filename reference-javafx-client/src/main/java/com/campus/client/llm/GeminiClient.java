package com.campus.client.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Clean, production-ready Gemini API client utilizing standard JDK HttpClient.
 * Implements LlmClient perfectly to match the lecturer's interface contracts.
 */
public final class GeminiClient implements LlmClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String modelName;
    private final int maxTokens;

    /**
     * Constructor matching the lecturer's App.java instantiation exactly.
     */
    public GeminiClient(String apiKey, String model, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Missing Gemini API key (set GEMINI_API_KEY).");
        }
        this.apiKey = apiKey;
        this.modelName = (model == null || model.isBlank()) ? "gemini-2.5-flash" : model;
        this.maxTokens = maxTokens <= 0 ? 1024 : maxTokens;
    }

    /**
     * Sends a single user prompt with an optional system prompt and returns the model's text response.
     */
    @Override
    public String complete(String systemPrompt, String userPrompt) throws Exception {
        String jsonPayload = buildJsonPayload(systemPrompt, userPrompt);
        URI targetUri = URI.create(String.format(BASE_URL, modelName, apiKey));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(targetUri)
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() / 100 != 2) {
            throw new IOException("Gemini API error " + response.statusCode() + ": " + response.body());
        }

        try {
            return mapper.readTree(response.body())
                    .at("/candidates/0/content/parts/0/text")
                    .asText();
        } catch (Exception e) {
            throw new IOException("Unexpected empty response structure from Gemini API. Response body: " + response.body(), e);
        }
    }

    /**
     * Fulfills the model() requirement from the LlmClient interface.
     */
    @Override
    public String model() {
        return modelName;
    }

    /**
     * Composes the JSON body cleanly using structured text blocks.
     */
    private String buildJsonPayload(String systemPrompt, String userPrompt) throws IOException {
        // Prevent null string casting references ("null\n[GUARDRAILS]")
        String safeBasePrompt = (systemPrompt == null || systemPrompt.isBlank()) ? "" : systemPrompt.trim() + "\n";

        // Append strict academic guardrails
        String strictSystemPrompt = safeBasePrompt + """
         [CRITICAL ACADEMIC GUARDRAILS]
         1. You are an official university academic assistant from Taylor's University Malaysia. Maintain a professional, polite, and objective tone.
         2. Never provide advice on cheating, plagiarism, or bypassing academic regulations.
         3. Do not engage in casual chat unrelated to university services, scheduling, or campus guidelines.
         4. Output responses using clean, scannable Markdown formatting (bullet points, bold text).
         """;

        // FIX: Corrected JSON payload format nesting to match Google's systemInstruction.parts architecture
        String systemSection = """
        "systemInstruction": {
            "parts": [{ "text": %s }]
        },
        """.formatted(mapper.writeValueAsString(strictSystemPrompt));

        return """
        {
            %s"contents": [{
                "role": "user",
                "parts": [{ "text": %s }]
            }],
            "generationConfig": {
                "maxOutputTokens": %d,
                "temperature": 0.2
            }
        }
        """.formatted(systemSection, mapper.writeValueAsString(userPrompt), maxTokens);
    }
}