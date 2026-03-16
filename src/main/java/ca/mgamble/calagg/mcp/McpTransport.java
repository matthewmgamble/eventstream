package ca.mgamble.calagg.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class McpTransport {

    private static final Logger log = LoggerFactory.getLogger(McpTransport.class);
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final McpToolHandler toolHandler;
    private final Gson gson;
    private final Map<String, SseClient> sessions = new ConcurrentHashMap<>();

    public McpTransport(McpToolHandler toolHandler) {
        this.toolHandler = toolHandler;
        this.gson = new GsonBuilder().create();
    }

    public void handleSse(SseClient client) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, client);
        log.info("MCP SSE client connected: {}", sessionId);

        client.keepAlive();
        client.onClose(() -> {
            sessions.remove(sessionId);
            log.info("MCP SSE client disconnected: {}", sessionId);
        });

        // Send the endpoint event so the client knows where to POST messages
        client.sendEvent("endpoint", "/mcp/messages?sessionId=" + sessionId);
    }

    public void handleMessage(Context ctx) {
        String sessionId = ctx.queryParam("sessionId");
        SseClient client = sessionId != null ? sessions.get(sessionId) : null;

        String body = ctx.body();
        log.debug("MCP message received: {}", body);

        @SuppressWarnings("unchecked")
        Map<String, Object> request = gson.fromJson(body, Map.class);

        String method = (String) request.get("method");
        Object id = request.get("id");

        // Notifications (no id) don't need a response
        if (id == null) {
            log.debug("MCP notification: {}", method);
            ctx.status(202);
            return;
        }

        Map<String, Object> response = dispatch(method, request);

        Map<String, Object> jsonRpcResponse = new LinkedHashMap<>();
        jsonRpcResponse.put("jsonrpc", "2.0");
        jsonRpcResponse.put("id", id);

        if (response.containsKey("error")) {
            jsonRpcResponse.put("error", response.get("error"));
        } else {
            jsonRpcResponse.put("result", response);
        }

        String responseJson = gson.toJson(jsonRpcResponse);
        log.debug("MCP response: {}", responseJson);

        // Send via SSE if client is connected
        if (client != null) {
            client.sendEvent("message", responseJson);
        }

        // Also return as HTTP response
        ctx.contentType("application/json");
        ctx.result(responseJson);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dispatch(String method, Map<String, Object> request) {
        return switch (method) {
            case "initialize" -> handleInitialize();
            case "tools/list" -> handleToolsList();
            case "tools/call" -> handleToolsCall((Map<String, Object>) request.get("params"));
            case "ping" -> Map.of();
            default -> {
                log.warn("Unknown MCP method: {}", method);
                yield Map.of("error", Map.of(
                        "code", -32601,
                        "message", "Method not found: " + method
                ));
            }
        };
    }

    private Map<String, Object> handleInitialize() {
        log.info("MCP initialize handshake");
        return Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of("tools", Map.of()),
                "serverInfo", Map.of(
                        "name", "eventstream-calendar",
                        "version", "1.0.0"
                )
        );
    }

    private Map<String, Object> handleToolsList() {
        return Map.of("tools", CalendarTools.getToolDefinitions());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolsCall(Map<String, Object> params) {
        if (params == null) {
            return Map.of("error", Map.of("code", -32602, "message", "Missing params"));
        }

        String toolName = (String) params.get("name");
        Map<String, Object> arguments = params.get("arguments") != null
                ? (Map<String, Object>) params.get("arguments")
                : Map.of();

        if (toolName == null) {
            return Map.of("error", Map.of("code", -32602, "message", "Missing tool name"));
        }

        try {
            String result = toolHandler.handleToolCall(toolName, arguments);
            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", result
                    ))
            );
        } catch (Exception e) {
            log.error("Tool call failed: {} - {}", toolName, e.getMessage(), e);
            return Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "Error executing " + toolName + ": " + e.getMessage()
                    )),
                    "isError", true
            );
        }
    }
}
