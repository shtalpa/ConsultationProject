package com.campus.client;

import com.campus.client.llm.AnthropicClient;
import com.campus.client.llm.GeminiClient;
import com.campus.client.llm.LlmClient;
import com.campus.client.llm.OpenAiClient;
import com.campus.client.mcp.CampusMcpClient;
import com.campus.client.rag.RagService;
//import com.campus.client.rag.RagService_old;
import com.campus.client.ui.MainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * JavaFX host for the reference client. Resolves configuration, connects to the Campus MCP server
 * over HTTP/SSE on a background thread, builds the RAG stack, and hands everything to {@link MainView}.
 *
 * <p>Configuration (all overridable):
 * <ul>
 *   <li>{@code -Dmcp.server.url} or env {@code MCP_SERVER_URL} (default http://localhost:8080)</li>
 *   <li>env {@code ANTHROPIC_API_KEY} (required for the RAG tab)</li>
 *   <li>{@code -Danthropic.model} (default claude-sonnet-4-6)</li>
 * </ul>
 */
public final class App extends Application {

    private static final Logger log = LoggerFactory.getLogger(App.class);
    private static final String DEFAULT_URL = "http://localhost:8080";

    //private static final String DEFAULT_MODEL = "claude-sonnet-4-6";
    private static final String DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-4-6";

    private static final String DEFAULT_OPENAI_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";

    /** Default is anthropic. Change this to suit your need - anthropic, gemini, OPENAI, google
        Remember to set the <PROVIDER>_API_Key value in your environment variable.
        Warning: DO NOT store API_Keys in your source code!
    **/
    private static final String DEFAULT_PROVIDER = "gemini"; //anthropic  //openai

    private CampusMcpClient mcp;
    private MainView mainview;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        mainview = new MainView();
        stage.setTitle("Campus MCP Reference Client");
        stage.setScene(new Scene(mainview.getRoot(), 900, 720));
        stage.show();

        Thread t = new Thread(this::bootstrap, "mcp-bootstrap");
        t.setDaemon(true);
        t.start();
    }

    private void bootstrap() {
        try {
            String url = firstNonBlank(System.getProperty("mcp.server.url"),
                    System.getenv("MCP_SERVER_URL"), DEFAULT_URL);
            Platform.runLater(() -> {
                //put the UI code in Platform.runLater()
                 mainview.setStatus("Connecting to MCP server at " + url + " …");
                 System.out.println("Connecting to MCP server at " + url );
            });

            mcp = new CampusMcpClient(url);
            var init = mcp.connect();

            // The LLM is optional: discovery and direct tool calls work without an API key.
            // RagService_old rag = null;
            // String llmNote;

            //Added multiple LLM models
            LlmClient llm = buildLlmClient();
            RagService rag = (llm == null) ? null : new RagService(mcp, llm);
            String llmNote = (llm == null)
                    ? "LLM: disabled (set <PROVIDER>_API_KEY to enable the RAG tab)"
                    : "LLM: " + llm.model();

            final RagService ragFinal = rag;

            /* String apiKey = firstNonBlank(System.getProperty("anthropic.apiKey"),
                    System.getenv("ANTHROPIC_API_KEY"), null);

            if (apiKey != null) {
                String model = firstNonBlank(System.getProperty("anthropic.model"), DEFAULT_MODEL);
                rag = new RagService_old(mcp, new AnthropicClient(apiKey, model, 1024));
                llmNote = "LLM: " + model;
            } else {
                llmNote = "LLM: disabled (set ANTHROPIC_API_KEY to enable the RAG tab)";
            }
            final RagService_old ragFinal = rag;
            */

            Platform.runLater(() -> {
                //put the UI code in Platform.runLater()
                mainview.bind(mcp, ragFinal);
                mainview.setStatus("Connected to '" + init.serverInfo().name() + "'.  " + llmNote);
                mainview.refreshDiscovery();
            });
        } catch (Exception e) {
            log.error("Bootstrap failed", e);
            Platform.runLater(() -> mainview.setStatus("Connection failed: " + e.getMessage()
                    + "  (Is the server running?)"));
        }
    }

    /**
     * Builds an LLM client based on the {@code LLM_PROVIDER} setting and the appropriate API-key
     * environment variable. Returns {@code null} (RAG tab disabled) if no key is configured.
     */
    private LlmClient buildLlmClient() {
        String provider = firstNonBlank(System.getProperty("llm.provider"),
                System.getenv("LLM_PROVIDER"), DEFAULT_PROVIDER).toLowerCase(Locale.ROOT);
        int maxTokens = 2000;

        switch (provider) {
            case "anthropic" -> {
                String key = firstNonBlank(System.getProperty("anthropic.apiKey"),
                        System.getenv("ANTHROPIC_API_KEY"), null);
                if (key == null) return null;
                String model = firstNonBlank(System.getProperty("anthropic.model"), DEFAULT_ANTHROPIC_MODEL);
                return new AnthropicClient(key, model, maxTokens);
            }
            case "openai" -> {
                String key = firstNonBlank(System.getProperty("openai.apiKey"),
                        System.getenv("OPENAI_API_KEY"), null);
                if (key == null) return null;
                String model = firstNonBlank(System.getProperty("openai.model"), DEFAULT_OPENAI_MODEL);
                return new OpenAiClient(key, model, maxTokens);
            }
            case "gemini", "google" -> {
               // String key = firstNonBlank(System.getProperty("gemini.apiKey"),
                        //System.getenv("GEMINI_API_KEY"), System.getenv("GOOGLE_API_KEY"), null);
                String key = "";
                if (key == null) return null;
                String model = firstNonBlank(System.getProperty("gemini.model"), DEFAULT_GEMINI_MODEL);
                return new GeminiClient(key, model, maxTokens);
            }
            default -> {
                log.warn("Unknown LLM_PROVIDER '{}'; RAG tab disabled.", provider);
                return null;
            }
        }
    }
    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    @Override
    public void stop() {
        if (mcp != null) {
            mcp.close();
        }
        Platform.exit();
    }
}
