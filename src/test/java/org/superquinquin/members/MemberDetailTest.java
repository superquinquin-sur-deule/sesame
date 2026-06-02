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

    private static Map<String, Object> registration(String dateBegin, String shiftType, String ticketName) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", (int) (Math.random() * 100000));
        r.put("date_begin", dateBegin);
        r.put("shift_type", shiftType);
        r.put("state", "open");
        r.put("shift_ticket_id", List.of(1, ticketName));
        return r;
    }

    @Test
    @DisplayName("detail returns the full record with name split, status, joined date and next shift")
    void detailHappyPath() {
        Map<String, Object> p = partner(1247, "DOE, Alice", "up_to_date");
        p.put("create_date", "2018-03-14 09:12:00");
        p.put("current_template_name", "CSam. - 09:00");
        p.put("unsubscription_date", false);
        OdooStub.stubSearchReadMatching("res.partner", "\"id\",\"=\",1247", List.of(p));
        OdooStub.stubSearchReadMatching("res.partner", "\"parent_id\",\"=\",1247", List.of());
        OdooStub.stubSearchReadMatching("shift.registration", "\"partner_id\",\"=\",1247",
                List.of(registration("2026-05-24 07:00:00", "standard", "ABCD")));

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
        p.put("current_template_name", "Mer. - 15:45");
        p.put("unsubscription_date", false);
        OdooStub.stubSearchReadMatching("res.partner", "\"id\",\"=\",1247", List.of(p));
        OdooStub.stubSearchReadMatching("res.partner", "\"parent_id\",\"=\",1247", List.of());
        OdooStub.stubSearchReadMatching("shift.registration", "\"partner_id\",\"=\",1247",
                List.of(registration("2026-05-27 13:45:00", "standard", "ABCD")));

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
        p.put("current_template_name", "Mer. - 01:30");
        p.put("unsubscription_date", false);
        OdooStub.stubSearchReadMatching("res.partner", "\"id\",\"=\",1247", List.of(p));
        OdooStub.stubSearchReadMatching("res.partner", "\"parent_id\",\"=\",1247", List.of());
        OdooStub.stubSearchReadMatching("shift.registration", "\"partner_id\",\"=\",1247",
                List.of(registration("2026-05-26 23:30:00", "standard", "ABCD")));

        given().when().get("/api/members/1247")
                .then()
                .statusCode(200)
                .body("nextShift.date", is("2026-05-27"))
                .body("nextShift.time", is("01:30"));
    }

    @Test
    @DisplayName("upcoming ftop/volant registration is picked when earlier than the next standard slot")
    void nextShiftIncludesAnticipatedVolantSlot() {
        Map<String, Object> p = partner(1247, "DOE, Alice", "up_to_date");
        p.put("create_date", "2018-03-14 09:12:00");
        p.put("current_template_name", "BMer. - 10:45");
        p.put("unsubscription_date", false);
        OdooStub.stubSearchReadMatching("res.partner", "\"id\",\"=\",1247", List.of(p));
        OdooStub.stubSearchReadMatching("res.partner", "\"parent_id\",\"=\",1247", List.of());
        OdooStub.stubSearchReadMatching("shift.registration", "\"partner_id\",\"=\",1247",
                List.of(
                        registration("2026-05-23 13:45:00", "ftop", "Volant"),
                        registration("2026-06-10 08:45:00", "standard", "ABCD")
                ));

        given().when().get("/api/members/1247")
                .then()
                .statusCode(200)
                .body("nextShift.date", is("2026-05-23"))
                .body("nextShift.time", is("15:45"))
                .body("nextShift.role", is("Volant"));
    }

    @Test
    @DisplayName("nextShift is null when the member has no upcoming registration")
    void nextShiftAbsentWhenNoUpcomingRegistration() {
        Map<String, Object> p = partner(1247, "DOE, Alice", "up_to_date");
        p.put("create_date", "2018-03-14 09:12:00");
        p.put("current_template_name", "BMer. - 10:45");
        p.put("unsubscription_date", false);
        OdooStub.stubSearchReadMatching("res.partner", "\"id\",\"=\",1247", List.of(p));
        OdooStub.stubSearchReadMatching("res.partner", "\"parent_id\",\"=\",1247", List.of());
        OdooStub.stubSearchReadMatching("shift.registration", "\"partner_id\",\"=\",1247", List.of());

        given().when().get("/api/members/1247")
                .then()
                .statusCode(200)
                .body("nextShift", nullValue());
    }

    @Test
    @DisplayName("detail exposes the photo as a data URI when image is present (JPEG)")
    void detailIncludesPhotoAsJpegDataUri() {
        Map<String, Object> p = partner(1247, "DOE, Alice", "up_to_date");
        p.put("image", "/9j/4AAQSkZJRgABAQ==");
        OdooStub.stubSearchReadMatching("res.partner", "\"id\",\"=\",1247", List.of(p));
        OdooStub.stubSearchReadMatching("res.partner", "\"parent_id\",\"=\",1247", List.of());
        OdooStub.stubSearchReadMatching("shift.registration", "\"partner_id\",\"=\",1247", List.of());

        given().when().get("/api/members/1247")
                .then()
                .statusCode(200)
                .body("photo", is("data:image/jpeg;base64,/9j/4AAQSkZJRgABAQ=="));
    }

    @Test
    @DisplayName("detail exposes the photo as a data URI when image is present (PNG)")
    void detailIncludesPhotoAsPngDataUri() {
        Map<String, Object> p = partner(1247, "DOE, Alice", "up_to_date");
        p.put("image", "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");
        OdooStub.stubSearchReadMatching("res.partner", "\"id\",\"=\",1247", List.of(p));
        OdooStub.stubSearchReadMatching("res.partner", "\"parent_id\",\"=\",1247", List.of());
        OdooStub.stubSearchReadMatching("shift.registration", "\"partner_id\",\"=\",1247", List.of());

        given().when().get("/api/members/1247")
                .then()
                .statusCode(200)
                .body("photo", startsWith("data:image/png;base64,iVBORw0KGgo"));
    }

    @Test
    @DisplayName("detail photo is null when Odoo returns no image")
    void detailPhotoIsNullWhenAbsent() {
        Map<String, Object> p = partner(1247, "DOE, Alice", "up_to_date");
        p.put("image", false);
        OdooStub.stubSearchReadMatching("res.partner", "\"id\",\"=\",1247", List.of(p));
        OdooStub.stubSearchReadMatching("res.partner", "\"parent_id\",\"=\",1247", List.of());
        OdooStub.stubSearchReadMatching("shift.registration", "\"partner_id\",\"=\",1247", List.of());

        given().when().get("/api/members/1247")
                .then()
                .statusCode(200)
                .body("photo", nullValue());
    }

    // A 2×2 PNG whose decoded SHA-256 the %test profile designates as Odoo's generic "no photo"
    // silhouette (sesame.photo.placeholder-sha256). On prod that placeholder is the grey avatar
    // Odoo stamps on imported cooperators — it must read back as "no photo", not a real picture,
    // so the UI shows initials + "Prendre une photo" rather than "Reprendre".
    private static final String DEFAULT_PLACEHOLDER_B64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVR4nGOs9j3xn4GBgYGJAQoAJtsCk5Kku7oAAAAASUVORK5CYII=";

    @Test
    @DisplayName("detail photo is null when Odoo returns its generic default silhouette (treated as no photo)")
    void detailPhotoIsNullWhenDefaultPlaceholder() {
        Map<String, Object> p = partner(1247, "DOE, Alice", "up_to_date");
        p.put("image", DEFAULT_PLACEHOLDER_B64);
        OdooStub.stubSearchReadMatching("res.partner", "\"id\",\"=\",1247", List.of(p));
        OdooStub.stubSearchReadMatching("res.partner", "\"parent_id\",\"=\",1247", List.of());
        OdooStub.stubSearchReadMatching("shift.registration", "\"partner_id\",\"=\",1247", List.of());

        given().when().get("/api/members/1247")
                .then()
                .statusCode(200)
                .body("photo", nullValue());
    }
    // Note: "a non-placeholder photo is kept" is already pinned by detailIncludesPhotoAsPngDataUri /
    // detailIncludesPhotoAsJpegDataUri above — those fixtures are NOT in the placeholder set, and the
    // PNG one shares the placeholder's format, so together they prove detection keys on the exact
    // hash, not on the image type.

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
        OdooStub.stubSearchReadMatching("shift.registration", "\"partner_id\",\"=\",1247", List.of());

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
