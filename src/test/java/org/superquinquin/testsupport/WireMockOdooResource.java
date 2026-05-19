package org.superquinquin.testsupport;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class WireMockOdooResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer server;

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        OdooStub.bind(server);
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
