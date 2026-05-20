package org.superquinquin.members;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.superquinquin.testsupport.OdooStub;
import org.superquinquin.testsupport.WireMockOdooResource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(WireMockOdooResource.class)
class MemberDetailTest {

    @BeforeEach
    void resetStub() {
        OdooStub.reset();
    }

    private static Map<String, Object> partner(int id, String name, String state) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("email", "x@y.fr");
        m.put("barcode_base", id);
        m.put("cooperative_state", state);
        m.put("is_member", true);
        m.put("is_associated_people", false);
        m.put("parent_member_num", 0);
        return m;
    }

    @Test
    @DisplayName("detail returns the full record with name split, status, joined date and next shift")
    void detailHappyPath() {
        Map<String, Object> p = partner(1247, "DOE, Alice", "up_to_date");
        p.put("create_date", "2018-03-14 09:12:00");
        p.put("next_shift_time", "2026-05-24 07:00:00");
        p.put("current_template_name", "CSam. - 09:00");
        p.put("unsubscription_date", false);
        OdooStub.stubSearchReadMatching("res.partner", "\"id\",\"=\",1247", List.of(p));
        OdooStub.stubSearchReadMatching("res.partner", "\"parent_id\",\"=\",1247", List.of());

        given().when().get("/api/members/1247")
                .then()
                .statusCode(200)
                .body("id", is(1247))
                .body("number", is(1247))
                .body("firstName", is("Alice"))
                .body("lastName", is("Doe"))
                .body("email", is("x@y.fr"))
                .body("status", is("ok"))
                .body("joinedOn", is("2018-03-14"))
                .body("nextShift.date", is("2026-05-24"))
                .body("nextShift.time", is("09:00"))
                .body("nextShift.role", is("CSam. - 09:00"))
                .body("binome", nullValue());
    }

    @Test
    @DisplayName("next shift time is converted from Odoo UTC to Europe/Paris (CEST in May, +2h)")
    void nextShiftTimeIsConvertedToParisTimezone() {
        Map<String, Object> p = partner(1247, "DOE, Alice", "up_to_date");
        p.put("create_date", "2018-03-14 09:12:00");
        p.put("next_shift_time", "2026-05-27 13:45:00");
        p.put("current_template_name", "Mer. - 15:45");
        p.put("unsubscription_date", false);
        OdooStub.stubSearchReadMatching("res.partner", "\"id\",\"=\",1247", List.of(p));
        OdooStub.stubSearchReadMatching("res.partner", "\"parent_id\",\"=\",1247", List.of());

        given().when().get("/api/members/1247")
                .then()
                .statusCode(200)
                .body("nextShift.date", is("2026-05-27"))
                .body("nextShift.time", is("15:45"));
    }

    @Test
    @DisplayName("UTC date rolls over to next day in Paris when shift is late evening")
    void nextShiftRollsOverDayBoundary() {
        Map<String, Object> p = partner(1247, "DOE, Alice", "up_to_date");
        p.put("create_date", "2018-03-14 09:12:00");
        p.put("next_shift_time", "2026-05-26 23:30:00");
        p.put("current_template_name", "Mer. - 01:30");
        p.put("unsubscription_date", false);
        OdooStub.stubSearchReadMatching("res.partner", "\"id\",\"=\",1247", List.of(p));
        OdooStub.stubSearchReadMatching("res.partner", "\"parent_id\",\"=\",1247", List.of());

        given().when().get("/api/members/1247")
                .then()
                .statusCode(200)
                .body("nextShift.date", is("2026-05-27"))
                .body("nextShift.time", is("01:30"));
    }

    @Test
    @DisplayName("missing record returns 404")
    void detailNotFound() {
        OdooStub.stubSearchRead("res.partner", List.of());

        given().when().get("/api/members/99999")
                .then().statusCode(404);
    }

    @Test
    @DisplayName("when the member has an associated_people child, binôme is expanded")
    void detailWithBinome() {
        Map<String, Object> m = partner(1247, "DOE, Alice", "up_to_date");
        OdooStub.stubSearchReadMatching("res.partner", "\"id\",\"=\",1247", List.of(m));
        Map<String, Object> b = partner(1893, "ROE, Eve", "up_to_date");
        b.put("is_member", false);
        b.put("is_associated_people", true);
        OdooStub.stubSearchReadMatching("res.partner", "\"parent_id\",\"=\",1247", List.of(b));

        given().when().get("/api/members/1247")
                .then()
                .statusCode(200)
                .body("binome.id", is(1893))
                .body("binome.number", is(1893))
                .body("binome.firstName", is("Eve"))
                .body("binome.lastName", is("Roe"))
                .body("binome.status", is("ok"));
    }
}
