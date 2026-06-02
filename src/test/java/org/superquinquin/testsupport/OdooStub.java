package org.superquinquin.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public final class OdooStub {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static WireMockServer server;

    private OdooStub() {}

    static void bind(WireMockServer s) { server = s; }
    static void unbind() { server = null; }

    public static void reset() {
        if (server != null) {
            server.resetAll();
            stubLogin(42);
        }
    }

    public static void stubLogin(int uid) {
        server.stubFor(post(urlEqualTo("/jsonrpc"))
                .withRequestBody(containing("\"login\""))
                .willReturn(okJson(jsonResult(uid))));
    }

    public static void stubSearchRead(String model, List<Map<String, Object>> records) {
        server.stubFor(post(urlEqualTo("/jsonrpc"))
                .withRequestBody(containing("\"execute_kw\""))
                .withRequestBody(containing("\"" + model + "\""))
                .withRequestBody(containing("\"search_read\""))
                .willReturn(okJson(jsonResult(records))));
    }

    public static void stubSearchReadMatching(String model, String bodyFragment, List<Map<String, Object>> records) {
        server.stubFor(post(urlEqualTo("/jsonrpc"))
                .atPriority(1)
                .withRequestBody(containing("\"execute_kw\""))
                .withRequestBody(containing("\"" + model + "\""))
                .withRequestBody(containing("\"search_read\""))
                .withRequestBody(containing(bodyFragment))
                .willReturn(okJson(jsonResult(records))));
    }

    public static int searchReadCount(String model) {
        RequestPatternBuilder rp = postRequestedFor(urlEqualTo("/jsonrpc"))
                .withRequestBody(containing("\"execute_kw\""))
                .withRequestBody(containing("\"" + model + "\""))
                .withRequestBody(containing("\"search_read\""));
        return server.findAll(rp).size();
    }

    /** Stubs a successful {@code write} on the given model+id (Odoo returns {@code true}). */
    public static void stubWrite(String model, int id) {
        server.stubFor(post(urlEqualTo("/jsonrpc"))
                .atPriority(1)
                .withRequestBody(containing("\"execute_kw\""))
                .withRequestBody(containing("\"" + model + "\""))
                .withRequestBody(containing("\"write\""))
                .withRequestBody(containing("[" + id + "]"))
                .willReturn(okJson(jsonResult(true))));
    }

    /** Asserts at least one {@code write} call was made on model+id whose body contains the fragment. */
    public static void verifyWrite(String model, int id, String payloadFragment) {
        RequestPatternBuilder rp = postRequestedFor(urlEqualTo("/jsonrpc"))
                .withRequestBody(containing("\"execute_kw\""))
                .withRequestBody(containing("\"" + model + "\""))
                .withRequestBody(containing("\"write\""))
                .withRequestBody(containing("[" + id + "]"))
                .withRequestBody(containing(payloadFragment));
        if (server.findAll(rp).isEmpty()) {
            throw new AssertionError("Expected a write on " + model + " id=" + id
                    + " containing: " + payloadFragment);
        }
    }

    /** Counts {@code write} calls on the given model (used to assert no write happened). */
    public static int writeCount(String model) {
        RequestPatternBuilder rp = postRequestedFor(urlEqualTo("/jsonrpc"))
                .withRequestBody(containing("\"execute_kw\""))
                .withRequestBody(containing("\"" + model + "\""))
                .withRequestBody(containing("\"write\""));
        return server.findAll(rp).size();
    }

    private static String jsonResult(Object payload) {
        try {
            return JSON.writeValueAsString(Map.of("jsonrpc", "2.0", "result", payload));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
