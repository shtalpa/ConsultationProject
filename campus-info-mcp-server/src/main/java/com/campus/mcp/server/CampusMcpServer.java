package com.campus.mcp.server;

import com.campus.mcp.server.kb.DataStore;
import com.campus.mcp.server.kb.KnowledgeBase;
import com.campus.mcp.server.prompts.CampusPrompts;
import com.campus.mcp.server.resources.CampusResources;
import com.campus.mcp.server.tools.CampusTools;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;

/**
 * Entry point for the <b>Campus Information MCP Server</b>.
 *
 * <p>Transport: <b>HTTP with Server-Sent Events (SSE)</b>. The MCP SDK ships a Jakarta Servlet
 * implementation of the SSE transport; we host that servlet in an embedded Jetty container.
 * Messages are JSON-RPC 2.0:
 * <ul>
 *   <li>{@code GET /sse} &mdash; the long-lived event stream (server &rarr; client). On connect
 *       the server sends an {@code endpoint} event telling the client where to POST requests.</li>
 *   <li>{@code POST /mcp/message?sessionId=...} &mdash; client &rarr; server JSON-RPC requests.</li>
 * </ul>
 *
 * <p>The server advertises four capability areas: <b>Tools</b>, <b>Resources</b>, <b>Prompts</b>
 * and <b>Logging</b>. Students connect their JavaFX MCP client to {@code http://localhost:8080}.</p>
 */
public final class CampusMcpServer {

    private static final Logger log = LoggerFactory.getLogger(CampusMcpServer.class);

    private static final String SSE_ENDPOINT = "/sse";
    private static final String MESSAGE_ENDPOINT = "/mcp/message";

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(
                System.getProperty("mcp.server.port", args.length > 0 ? args[0] : "8080"));

        // 1. Load the file-based knowledge base and the file-based data store (no database).
        KnowledgeBase kb = new KnowledgeBase(
                List.of("handbook.txt", "facilities.txt", "faq.txt", "lecturers.txt"));
        DataStore dataStore = new DataStore(Path.of("data"));

        // 2. Build the capability handlers.
        //    A shared JSON mapper (Jackson 3 binding) is used both by the transport and to
        //    parse the tools' JSON input schemas.
        var jsonMapper = new JacksonMcpJsonMapper(JsonMapper.builder().build());

        CampusTools tools = new CampusTools(kb, dataStore, jsonMapper);
        CampusResources resources = new CampusResources(kb);
        CampusPrompts prompts = new CampusPrompts();

        // 3. Create the HTTP/SSE transport provider (a Jakarta Servlet).
        //    NOTE: current SDK versions use a builder and the SDK's own McpJsonMapper
        //    abstraction. This build uses the Jackson 3 binding (mcp-json-jackson3), whose
        //    implementation wraps a Jackson 3 JsonMapper. messageEndpoint is required;
        //    sseEndpoint defaults to "/sse".
        HttpServletSseServerTransportProvider transport =
                HttpServletSseServerTransportProvider.builder()
                        .jsonMapper(jsonMapper)
                        .baseUrl("")
                        .messageEndpoint(MESSAGE_ENDPOINT)
                        .sseEndpoint(SSE_ENDPOINT)
                        .build();

        // 4. Assemble the MCP server and register everything it advertises.
        McpSyncServer mcpServer = McpServer.sync(transport)
                .serverInfo("campus-info-server", "1.0.0")
                .capabilities(ServerCapabilities.builder()
                        .tools(true)            // Tools, with list-changed notifications
                        .resources(false, true) // Resources: subscribe=false, listChanged=true
                        .prompts(true)          // Prompts, with list-changed notifications
                        .logging()              // structured logging to clients
                        .build())
                .tools(tools.all().toArray(new SyncToolSpecification[0]))
                .resources(resources.all().toArray(new SyncResourceSpecification[0]))
                .prompts(prompts.all().toArray(new SyncPromptSpecification[0]))
                .build();

        // 5. Host the transport servlet in embedded Jetty.
        Server jetty = new Server(port);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(new ServletHolder(transport), "/*");
        jetty.setHandler(context);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down Campus MCP server");
            try {
                mcpServer.close();
                jetty.stop();
            } catch (Exception e) {
                log.warn("Error during shutdown", e);
            }
        }));

        jetty.start();
        log.info("Campus Information MCP server is running:");
        log.info("  SSE stream     : http://localhost:{}{}", port, SSE_ENDPOINT);
        log.info("  Message POST   : http://localhost:{}{}", port, MESSAGE_ENDPOINT);
        log.info("  Tools          : search_campus_info, check_room_availability, book_resource,");
        log.info("                   list_lecturer_slots, submit_leave_application");
        log.info("  Resources      : campus://handbook, campus://facilities, campus://faq");
        log.info("  Prompts        : campus_assistant, draft_leave_request");
        log.info("Connect a client to http://localhost:{}", port);
        jetty.join();
    }

    private CampusMcpServer() {
    }
}
