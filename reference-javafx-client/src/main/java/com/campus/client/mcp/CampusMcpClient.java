package com.campus.client.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public final class CampusMcpClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CampusMcpClient.class);

    private final String baseUrl;
    private McpSyncClient client;

    public CampusMcpClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public McpSchema.InitializeResult connect() {

        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(baseUrl)
                .jsonMapper(new JacksonMcpJsonMapper(JsonMapper.builder().build()))
                .sseEndpoint("/sse")
                .build();

        this.client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .capabilities(ClientCapabilities.builder().build())
                .build();

        McpSchema.InitializeResult init = client.initialize();
        log.info("Connected to '{}' (protocol {})", init.serverInfo().name(), init.protocolVersion());
        return init;
    }

    public List<McpSchema.Tool> listTools() {
        return client.listTools().tools();
    }

    public List<McpSchema.Resource> listResources() {
        return client.listResources().resources();
    }

    public List<McpSchema.Prompt> listPrompts() {
        return client.listPrompts().prompts();
    }

    public String callTool(String name, Map<String, Object> arguments) {
        log.info("callTool {} {}", name, arguments);
        CallToolResult result = client.callTool(new CallToolRequest(name, arguments));
        String text = result.content().stream()
                .filter(c -> c instanceof TextContent)
                .map(c -> ((TextContent) c).text())
                .collect(Collectors.joining("\n"));
        return Boolean.TRUE.equals(result.isError()) ? "ERROR: " + text : text;
    }

    public String readResource(String uri) {
        ReadResourceResult result = client.readResource(new ReadResourceRequest(uri));
        return result.contents().stream()
                .filter(c -> c instanceof TextResourceContents)
                .map(c -> ((TextResourceContents) c).text())
                .collect(Collectors.joining("\n"));
    }

   
    public String getPrompt(String name, Map<String, Object> arguments) {
        GetPromptResult result = client.getPrompt(new GetPromptRequest(name, arguments));
        return result.messages().stream()
                .map(m -> m.content() instanceof TextContent tc ? tc.text() : "")
                .collect(Collectors.joining("\n"));
    }

    public String checkRoomAvailability(String date, String building) {
        return checkRoomAvailability(date, building, null, null);
    }

  
    public String checkRoomAvailability(String date, String building,
                                        String startTime, String endTime) {

        Map<String, Object> args = new HashMap<>();
        args.put("date", date);

        if (building != null && !building.isBlank()) {
            args.put("building", building);
        }
        if (startTime != null && !startTime.isBlank()
                && endTime != null && !endTime.isBlank()) {
            args.put("startTime", startTime);
            args.put("endTime", endTime);
        }

        return callTool("check_room_availability", args);
    }

    public String bookResource(
            String resourceId,
            String date,
            String startTime,
            String endTime,
            String studentId) {

        return callTool(
                "book_resource",
                Map.of(
                        "resourceId", resourceId,
                        "date", date,
                        "startTime", startTime,
                        "endTime", endTime,
                        "studentId", studentId
                )
        );
    }

    @Override
    public void close() {
        if (client != null) {
            client.closeGracefully();
            client = null;
        }
    }
}

