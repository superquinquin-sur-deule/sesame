package org.superquinquin.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Test-friendly façade over the embedded WireMock server. Lets a scenario
 * say "when Odoo is asked search_read on res.partner with this domain,
 * answer this list of records" without leaking WireMock plumbing into tests.
 */
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

    /** Stub a search_read on a model with any args — returns the given records. */
    public static void stubSearchRead(String model, List<Map<String, Object>> records) {
        server.stubFor(post(urlEqualTo("/jsonrpc"))
                .withRequestBody(containing("\"execute_kw\""))
                .withRequestBody(containing("\"" + model + "\""))
                .withRequestBody(containing("\"search_read\""))
                .willReturn(okJson(jsonResult(records))));
    }

    /**
     * Stub a search_read on a model where the request body also contains the
     * given fragment (typically a piece of the domain — e.g. a member id or a
     * field name like "parent_id"). Use this to give different answers for
     * different lookups inside the same scenario.
     */
    public static void stubSearchReadMatching(String model, String bodyFragment, List<Map<String, Object>> records) {
        server.stubFor(post(urlEqualTo("/jsonrpc"))
                .atPriority(1)
                .withRequestBody(containing("\"execute_kw\""))
                .withRequestBody(containing("\"" + model + "\""))
                .withRequestBody(containing("\"search_read\""))
                .withRequestBody(containing(bodyFragment))
                .willReturn(okJson(jsonResult(records))));
    }

    /** Returns the number of search_read requests sent to Odoo for the given model. */
    public static int searchReadCount(String model) {
        RequestPatternBuilder rp = postRequestedFor(urlEqualTo("/jsonrpc"))
                .withRequestBody(containing("\"execute_kw\""))
                .withRequestBody(containing("\"" + model + "\""))
                .withRequestBody(containing("\"search_read\""));
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
