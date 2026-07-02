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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Wraps the MCP {@link McpSyncClient} for the Campus server. It connects over the HTTP/SSE
 * transport and exposes simple methods for discovery (tools, resources, prompts) and invocation.
 *
 * <p>This is the class students study most closely: it shows the full MCP client lifecycle &mdash;
 * build a transport, build a client, {@code initialize()} (the JSON-RPC handshake), then list and
 * call capabilities.</p>
 */
public final class CampusMcpClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CampusMcpClient.class);

    private final String baseUrl;
    private McpSyncClient client;

    public CampusMcpClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** Opens the SSE stream and performs the MCP initialize handshake. */
    public McpSchema.InitializeResult connect() {
        // The SSE transport only needs the base URL; it discovers the message endpoint from the
        // server's first "endpoint" SSE event. Current SDK versions require the builder.
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

    /** Calls a tool and flattens its text content into one string. */
    public String callTool(String name, Map<String, Object> arguments) {
        log.info("callTool {} {}", name, arguments);
        CallToolResult result = client.callTool(new CallToolRequest(name, arguments));
        String text = result.content().stream()
                .filter(c -> c instanceof TextContent)
                .map(c -> ((TextContent) c).text())
                .collect(Collectors.joining("\n"));
        return Boolean.TRUE.equals(result.isError()) ? "ERROR: " + text : text;
    }

    /** Reads a resource and returns its concatenated text contents. */
    public String readResource(String uri) {
        ReadResourceResult result = client.readResource(new ReadResourceRequest(uri));
        return result.contents().stream()
                .filter(c -> c instanceof TextResourceContents)
                .map(c -> ((TextResourceContents) c).text())
                .collect(Collectors.joining("\n"));
    }

    /** Fetches a server-defined prompt template, returning its rendered text. */
    public String getPrompt(String name, Map<String, Object> arguments) {
        GetPromptResult result = client.getPrompt(new GetPromptRequest(name, arguments));
        return result.messages().stream()
                .map(m -> m.content() instanceof TextContent tc ? tc.text() : "")
                .collect(Collectors.joining("\n"));
    }

    @Override
    public void close() {
        if (client != null) {
            client.closeGracefully();
            client = null;
        }
    }
}
