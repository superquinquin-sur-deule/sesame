package org.superquinquin.odoo;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "odoo")
public interface OdooConfig {
    String url();
    String database();
    String login();
    String password();
}
