package org.superquinquin.members;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.superquinquin.testsupport.OdooStub;
import org.superquinquin.testsupport.WireMockOdooResource;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(WireMockOdooResource.class)
class MemberSearchTest {

    @BeforeEach
    void resetStub() {
        OdooStub.reset();
    }

    @Test
    @DisplayName("an empty query returns an empty list (no Odoo round-trip)")
    void emptyQueryReturnsEmpty() {
        given().when().get("/api/members?q=")
                .then()
                .statusCode(200)
                .body("size()", is(0));
    }

    @Test
    @DisplayName("a name query returns matching cooperators with display fields")
    void searchByName() {
        OdooStub.stubSearchRead("res.partner", List.of(
                Map.of(
                        "id", 1247,
                        "name", "VANDENBUSSCHE, Camille",
                        "email", "camille.v@superquinquin.fr",
                        "barcode_base", 1247,
                        "cooperative_state", "up_to_date",
                        "is_member", true,
                        "is_associated_people", false,
                        "parent_member_num", 0
                )
        ));

        given().when().get("/api/members?q=Vanden")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].id", is(1247))
                .body("[0].number", is(1247))
                .body("[0].firstName", is("Camille"))
                .body("[0].lastName", is("Vandenbussche"))
                .body("[0].email", is("camille.v@superquinquin.fr"))
                .body("[0].status", is("ok"));
    }

    @Test
    @DisplayName("a numeric query is interpreted as a cooperator number")
    void searchByNumber() {
        OdooStub.stubSearchRead("res.partner", List.of(
                Map.of(
                        "id", 847,
                        "name", "DELAHAYE, Lucien",
                        "email", "lucien.delahaye@gmail.com",
                        "barcode_base", 847,
                        "cooperative_state", "alert",
                        "is_member", true,
                        "is_associated_people", false,
                        "parent_member_num", 0
                )
        ));

        given().when().get("/api/members?q=847")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].number", is(847))
                .body("[0].status", is("alert"));
    }

    @Test
    @DisplayName("orphan désinscrits (no parent link) are filtered — they are duplicates of associated cooperators")
    void orphanUnsubscribedRecordsAreFilteredOut() {
        // Same person, two records: the historical désinscrit titulaire (orphan)
        // and the currently-active is_associated_people version linked to a binôme.
        OdooStub.stubSearchRead("res.partner", List.of(
                Map.of(
                        "id", 1669,
                        "name", "VANDENBUSSCHE, Marine",
                        "barcode_base", 10005,
                        "cooperative_state", "unsubscribed",
                        "is_member", true,
                        "is_associated_people", false,
                        "parent_member_num", 0
                ),
                Map.of(
                        "id", 2276,
                        "name", "VANDENBUSSCHE, Marine",
                        "barcode_base", 76,
                        "cooperative_state", "up_to_date",
                        "is_member", false,
                        "is_associated_people", true,
                        "parent_member_num", 8
                )
        ));

        given().when().get("/api/members?q=Vanden")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].id", is(2276))
                .body("[0].status", is("ok"));
    }

    @Test
    @DisplayName("cooperative_state values map to the four display statuses")
    void statusMapping() {
        OdooStub.stubSearchRead("res.partner", List.of(
                Map.of("id", 1, "name", "A, A", "barcode_base", 1, "cooperative_state", "up_to_date"),
                Map.of("id", 2, "name", "B, B", "barcode_base", 2, "cooperative_state", "delay"),
                Map.of("id", 3, "name", "C, C", "barcode_base", 3, "cooperative_state", "blocked"),
                // Désinscrit with a parent link → kept (not an orphan duplicate).
                Map.of("id", 4, "name", "D, D", "barcode_base", 4,
                       "cooperative_state", "unsubscribed", "parent_member_num", 99)
        ));

        given().when().get("/api/members?q=test")
                .then()
                .statusCode(200)
                .body("status", contains("ok", "alert", "suspended", "removed"));
    }
}
