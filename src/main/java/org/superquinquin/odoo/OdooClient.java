package org.superquinquin.odoo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class OdooClient {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Inject OdooConfig config;
    private HttpClient http;
    private final AtomicInteger uid = new AtomicInteger(0);

    @PostConstruct
    void init() {
        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void resetAuth() { uid.set(0); }

    private int ensureLogin() {
        int current = uid.get();
        if (current != 0) return current;
        ObjectNode params = JSON.createObjectNode()
                .put("service", "common")
                .put("method", "login");
        params.putArray("args")
                .add(config.database())
                .add(config.login())
                .add(config.password());
        JsonNode result = call(params);
        if (!result.isInt()) {
            throw new OdooException("Odoo login failed: " + result);
        }
        int next = result.asInt();
        uid.set(next);
        return next;
    }

    public JsonNode executeKw(String model, String method, List<Object> args, Map<String, Object> kwargs) {
        int authedUid = ensureLogin();
        ObjectNode params = JSON.createObjectNode()
                .put("service", "object")
                .put("method", "execute_kw");
        params.putArray("args")
                .add(config.database())
                .add(authedUid)
                .add(config.password())
                .add(model)
                .add(method)
                .add(JSON.valueToTree(args))
                .add(JSON.valueToTree(kwargs == null ? Map.of() : kwargs));
        return call(params);
    }
    
    public boolean write(String model, int id, Map<String, Object> values) {
        JsonNode result = executeKw(model, "write", List.of(List.of(id), values), Map.of());
        return result != null && result.asBoolean(false);
    }

    private JsonNode call(ObjectNode params) {
        ObjectNode body = JSON.createObjectNode()
                .put("jsonrpc", "2.0")
                .put("method", "call");
        body.set("params", params);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(config.url() + "/jsonrpc"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new OdooException("Odoo HTTP " + resp.statusCode() + " — " + resp.body());
            }
            JsonNode root = JSON.readTree(resp.body());
            if (root.has("error")) {
                throw new OdooException("Odoo error: " + root.get("error").toString());
            }
            return root.get("result");
        } catch (OdooException e) {
            throw e;
        } catch (Exception e) {
            throw new OdooException("Odoo call failed: " + e.getMessage(), e);
        }
    }

    public static class OdooException extends RuntimeException {
        public OdooException(String msg) { super(msg); }
        public OdooException(String msg, Throwable cause) { super(msg, cause); }
    }
}
