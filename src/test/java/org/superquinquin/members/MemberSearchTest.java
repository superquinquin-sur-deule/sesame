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
                        "name", "DOE, Alice",
                        "email", "alice.doe@example.com",
                        "barcode_base", 1247,
                        "cooperative_state", "up_to_date",
                        "is_member", true,
                        "is_associated_people", false,
                        "parent_member_num", 0
                )
        ));

        given().when().get("/api/members?q=Doe")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].id", is(1247))
                .body("[0].number", is(1247))
                .body("[0].firstName", is("Alice"))
                .body("[0].lastName", is("Doe"))
                .body("[0].email", is("alice.doe@example.com"))
                .body("[0].status", is("ok"));
    }

    @Test
    @DisplayName("a multi-word query matches each token against the name regardless of order")
    void searchByMultipleTokens() {
        OdooStub.stubSearchRead("res.partner", List.of());
        OdooStub.stubSearchReadMatching("res.partner", "\"ilike\",\"Roe\"", List.of(
                Map.of(
                        "id", 9001,
                        "name", "ROE, Bob",
                        "barcode_base", 9001,
                        "cooperative_state", "up_to_date",
                        "is_member", true,
                        "is_associated_people", false,
                        "parent_member_num", 0
                )
        ));

        given().queryParam("q", "Bob Roe")
                .when().get("/api/members")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].firstName", is("Bob"))
                .body("[0].lastName", is("Roe"));
    }

    @Test
    @DisplayName("a numeric query is interpreted as a cooperator number")
    void searchByNumber() {
        OdooStub.stubSearchRead("res.partner", List.of(
                Map.of(
                        "id", 847,
                        "name", "POE, Charlie",
                        "email", "charlie.poe@example.com",
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
        OdooStub.stubSearchRead("res.partner", List.of(
                Map.of(
                        "id", 1669,
                        "name", "MOE, Dana",
                        "barcode_base", 10005,
                        "cooperative_state", "unsubscribed",
                        "is_member", true,
                        "is_associated_people", false,
                        "parent_member_num", 0
                ),
                Map.of(
                        "id", 2276,
                        "name", "MOE, Dana",
                        "barcode_base", 76,
                        "cooperative_state", "up_to_date",
                        "is_member", false,
                        "is_associated_people", true,
                        "parent_member_num", 8
                )
        ));

        given().when().get("/api/members?q=Moe")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].id", is(2276))
                .body("[0].status", is("ok"));
    }

    @Test
    @DisplayName("an unsubscribed titulaire whose binôme child still points at them is kept — not a real ghost")
    void unsubscribedTitulaireWithBinomeChildIsKept() {
        OdooStub.stubSearchRead("res.partner", List.of(
                Map.of(
                        "id", 1924,
                        "name", "VOE, Eve",
                        "barcode_base", 196,
                        "cooperative_state", "unsubscribed",
                        "is_member", true,
                        "is_associated_people", false,
                        "parent_member_num", 0,
                        "child_ids", List.of(2237)
                ),
                Map.of(
                        "id", 2237,
                        "name", "VOE, Frank",
                        "barcode_base", 37,
                        "cooperative_state", "unsubscribed",
                        "is_member", false,
                        "is_associated_people", true,
                        "parent_member_num", 196,
                        "child_ids", List.of()
                )
        ));

        given().when().get("/api/members?q=Voe")
                .then()
                .statusCode(200)
                .body("size()", is(2))
                .body("id", hasItems(1924, 2237));
    }

    @Test
    @DisplayName("technical accounts (neither titulaire nor binôme) are filtered out")
    void technicalAccountsAreFilteredOut() {
        OdooStub.stubSearchRead("res.partner", List.of(
                Map.of(
                        "id", 2486,
                        "name", "Accueil",
                        "email", "accueil@superquinquin-sur-deule.org",
                        "barcode_base", 0,
                        "cooperative_state", "not_concerned",
                        "is_member", false,
                        "is_associated_people", false,
                        "parent_member_num", 0
                ),
                Map.of(
                        "id", 1247,
                        "name", "ACCUEIL, Alice",
                        "barcode_base", 1247,
                        "cooperative_state", "up_to_date",
                        "is_member", true,
                        "is_associated_people", false,
                        "parent_member_num", 0
                )
        ));

        given().when().get("/api/members?q=Accueil")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].id", is(1247));
    }

    @Test
    @DisplayName("cooperative_state values map to the four display statuses")
    void statusMapping() {
        OdooStub.stubSearchRead("res.partner", List.of(
                Map.of("id", 1, "name", "A, A", "barcode_base", 1,
                       "cooperative_state", "up_to_date", "is_member", true),
                Map.of("id", 2, "name", "B, B", "barcode_base", 2,
                       "cooperative_state", "delay", "is_member", true),
                Map.of("id", 3, "name", "C, C", "barcode_base", 3,
                       "cooperative_state", "blocked", "is_member", true),
                Map.of("id", 4, "name", "D, D", "barcode_base", 4,
                       "cooperative_state", "unsubscribed", "is_associated_people", true,
                       "parent_member_num", 99)
        ));

        given().when().get("/api/members?q=test")
                .then()
                .statusCode(200)
                .body("status", contains("ok", "alert", "suspended", "removed"));
    }
}
