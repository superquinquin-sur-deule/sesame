package org.superquinquin.testsupport;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Stubs the Odoo JSON-RPC endpoint so @QuarkusTest scenarios can drive the
 * application end-to-end without hitting the real Odoo instance.
 *
 * Tests interact with this resource via {@link OdooStub} (injected helper)
 * to record the canned responses they expect.
 */
public class WireMockOdooResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer server;

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        OdooStub.bind(server);
        // Default login response — any test gets a valid UID unless it overrides.
        OdooStub.stubLogin(42);
        return Map.of(
                "odoo.url", "http://localhost:" + server.port(),
                "odoo.database", "sqq_test",
                "odoo.login", "test@superquinquin.fr",
                "odoo.password", "test"
        );
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
        }
        OdooStub.unbind();
    }
}
