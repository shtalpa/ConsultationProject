package com.campus.mcp.server.prompts;

import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptArgument;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import java.util.List;
import java.util.Map;

/**
 * Builds the campus {@code Prompts}. A Prompt is a reusable, parameterised message template the
 * server offers to clients. The client lists prompts, fills in arguments, and receives ready-made
 * messages it can send to the LLM &mdash; keeping prompt wording consistent and server-controlled.
 */
public final class CampusPrompts {

    public List<SyncPromptSpecification> all() {
        return List.of(campusAssistant(), draftLeaveRequest());
    }

    /** A system-style framing prompt that turns the model into a grounded campus assistant. */
    private SyncPromptSpecification campusAssistant() {
        Prompt prompt = new Prompt(
                "campus_assistant",
                "Framing instructions for a campus services assistant that must stay grounded in retrieved context.",
                List.of(new PromptArgument("topic", "The area the student is asking about "
                        + "(booking, appointments, leave, library, general).", false)));

        return new SyncPromptSpecification(prompt, (exchange, request) -> {
            String topic = arg(request.arguments(), "topic", "general campus services");
            String text = """
                You are a university campus assistant. Answer student questions about
                %s. Only use facts contained in the provided context passages; if the context does
                not contain the answer, say so and suggest contacting the relevant office. Be concise
                and cite the source document name in parentheses after each fact.
                """.formatted(topic);
            return new GetPromptResult(
                    "Campus assistant framing for: " + topic,
                    List.of(new PromptMessage(Role.USER, new TextContent(text))));
        });
    }

    /** A template that asks the model to draft a polite leave-request message. */
    private SyncPromptSpecification draftLeaveRequest() {
        Prompt prompt = new Prompt(
                "draft_leave_request",
                "Produces a polite, well-structured leave-request message for a student.",
                List.of(
                        new PromptArgument("studentName", "Student's full name", true),
                        new PromptArgument("fromDate", "Leave start date", true),
                        new PromptArgument("toDate", "Leave end date", true),
                        new PromptArgument("reason", "Reason for leave", true)));

        return new SyncPromptSpecification(prompt, (exchange, request) -> {
            Map<String, Object> a = request.arguments();
            String text = """
                Draft a short, polite leave-request message to the programme office from %s.
                The leave is from %s to %s. Reason: %s. Keep it under 120 words, professional in tone,
                and remind the office that a supporting document can be provided if required.
                """.formatted(
                    arg(a, "studentName", "the student"),
                    arg(a, "fromDate", "(start)"),
                    arg(a, "toDate", "(end)"),
                    arg(a, "reason", "(reason)"));
            return new GetPromptResult(
                    "Leave request draft",
                    List.of(new PromptMessage(Role.USER, new TextContent(text))));
        });
    }

    private static String arg(Map<String, Object> args, String key, String fallback) {
        Object v = args == null ? null : args.get(key);
        return v == null || v.toString().isBlank() ? fallback : v.toString();
    }
}
