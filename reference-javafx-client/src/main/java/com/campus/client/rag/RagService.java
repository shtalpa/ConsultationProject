package com.campus.client.rag;

import com.campus.client.llm.LlmClient;
import com.campus.client.mcp.CampusMcpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

public final class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    public record RagResult(String retrievedContext, String systemPrompt, String answer) {}

    private final CampusMcpClient mcp;
    private final LlmClient llm;

    public RagService(CampusMcpClient mcp, LlmClient llm) {
        this.mcp = mcp;
        this.llm = llm;
    }

    public RagResult ask(String question) throws Exception {
        String lowerQuestion = question.toLowerCase();
        StringBuilder contextBuilder = new StringBuilder();

        // DYNAMIC FILENAME IDENTIFICATION
        String domainKeywords = "";
        if (lowerQuestion.contains("facilit") || lowerQuestion.contains("room") || lowerQuestion.contains("lab")) {
            domainKeywords = "room type capacity building open close facilities blocks";
        } else if (lowerQuestion.contains("lectur") || lowerQuestion.contains("hour") || lowerQuestion.contains("consult") || lowerQuestion.contains("slot")) {
            domainKeywords = "Dr Computer Science Monday Tuesday Wednesday Thursday Friday slots";
        } else if (lowerQuestion.contains("handbook") || lowerQuestion.contains("rule") || lowerQuestion.contains("policy")) {
            domainKeywords = "handbook rules booking parameters policy guidelines cancellation";
        }

        // BUNDLED SINGLE SEARCH CALL (Protects your Quota entirely)
        try {
            String combinedQuery = domainKeywords.isEmpty() ? lowerQuestion : domainKeywords + " " + lowerQuestion;

            String mergedContext = mcp.callTool("search_campus_info", Map.of(
                    "query", combinedQuery.trim(),
                    "topK", 3
            ));

            if (mergedContext != null && !mergedContext.isBlank()) {
                contextBuilder.append("[RETRIEVED CAMPUS KNOWLEDGE BASE BLOCKS]\n")
                        .append(mergedContext)
                        .append("\n\n");
            }
        } catch (Exception e) {
            log.error("Knowledge base retrieval failed", e);
            contextBuilder.append("[SYSTEM NOTE: Primary Knowledge Base search is temporarily unavailable]\n\n");
        }

        // TARGETED LIVE LOOKUP FOR SPECIFIC SCHEDULES
        String extractedName = extractLecturerName(question);
        String dayFilter = extractDayFilter(lowerQuestion);
        boolean isGeneralListQuery = lowerQuestion.contains("list") || lowerQuestion.contains("all") || lowerQuestion.contains("hours");

        if (!extractedName.isBlank() && !isGeneralListQuery &&
                (lowerQuestion.contains("free") || lowerQuestion.contains("slot") || lowerQuestion.contains("consult"))) {
            try {
                String liveSlots = mcp.callTool("list_lecturer_slots", Map.of(
                        "lecturerName", extractedName,
                        "day", dayFilter
                ));
                if (liveSlots != null && !liveSlots.isBlank()) {
                    contextBuilder.append("[REAL-TIME PUBLISHED SLOTS]\n").append(liveSlots).append("\n\n");
                }
            } catch (Exception e) {
                log.error("Live schedule lookup failed for lecturer: {}", extractedName, e);
                contextBuilder.append("[SYSTEM NOTE: Unable to fetch real-time availability sync matrices for this lecturer]\n\n");
            }
        }

        // GENERATION (Exactly 1 LLM request execution)
        String systemPrompt;
        try {
            systemPrompt = mcp.getPrompt("campus_assistant", Map.of("topic", "general"));
        } catch (Exception e) {
            log.warn("Failed to fetch system prompt template from MCP server, using static fallback.");
            systemPrompt = "You are a helpful campus assistant from Taylor's University Malaysia.";
        }

        String userPrompt = """
            You are a helpful campus assistant from Taylor's University Malaysia. Analyze the retrieved database blocks below to answer the student's question.
            ----------------------------------------------------
            %s
            ----------------------------------------------------
            
            Strict Behavioral Guidelines:
            1. Rely ONLY on the facts and raw data rows visible in the context blocks above. If the context is empty or insufficient, state clearly that you do not have access to that information.
            2. Format all structured listings beautifully using Markdown tables or bullet points.
            3. Prioritize displaying the contents of the file that explicitly answers the user's primary intent, followed by any alternative context matches found.

            Student Question: %s
            """.formatted(contextBuilder.toString(), question);

        String answer = llm.complete(systemPrompt, userPrompt);

        return new RagResult(contextBuilder.toString(), systemPrompt, answer);
    }

    private String extractLecturerName(String text) {
        // Clean text of punctuation and titles safely
        String clean = text.replaceAll("[.,!?]", "")
                .replaceAll("(?i)\\b(show|list|display|out|me|all|get|who|is|are|when|free|slots|slot|hours|schedule|timetable|for|available|on|the|of|dr|mr|ms|prof|professor|appointment|consultation|with)\\b", "")
                .trim();

        // Return the clean multi-word name string (e.g. "Steve Rogers" instead of truncation)
        return clean.replaceAll("\\s+", " ");
    }

    private String extractDayFilter(String lowerText) {
        for (String day : new String[]{"monday", "tuesday", "wednesday", "thursday", "friday"}) {
            if (lowerText.contains(day)) return day;
        }
        return ""; // Downstream MCP handles empty string as "all days"
    }
}