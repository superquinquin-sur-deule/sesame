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
import static org.hamcrest.Matchers.is;

@QuarkusTest
@QuarkusTestResource(WireMockOdooResource.class)
class MemberPhotoUploadTest {

    // Valid base64 (decodes cleanly) whose JPEG magic "/9j/" makes the read-back a JPEG data URI.
    private static final String RAW_B64 = "/9j/4AAQSkZJRgABAQEAYABgAAD/";
    private static final String JPEG_DATA_URI = "data:image/jpeg;base64," + RAW_B64;

    @BeforeEach
    void resetStub() {
        OdooStub.reset();
    }

    private static Map<String, Object> partner(int id, String image) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("name", "DOE, Alice");
        m.put("email", "alice.doe@example.com");
        m.put("barcode_base", id);
        m.put("cooperative_state", "up_to_date");
        m.put("is_member", true);
        m.put("is_associated_people", false);
        m.put("parent_member_num", 0);
        if (image != null) m.put("image", image);
        return m;
    }

    /** Stubs the read-back that the endpoint performs after writing (findById's three calls). */
    private static void stubReadBack(int id, String imageAfter) {
        OdooStub.stubSearchReadMatching("res.partner", "\"id\",\"=\"," + id, List.of(partner(id, imageAfter)));
        OdooStub.stubSearchReadMatching("res.partner", "\"parent_id\",\"=\"," + id, List.of());
        OdooStub.stubSearchReadMatching("shift.registration", "\"partner_id\",\"=\"," + id, List.of());
    }

    @Test
    @DisplayName("uploading a JPEG data URI writes the raw base64 to res.partner.image and returns the refreshed photo")
    void uploadDataUriWritesImageToOdoo() {
        OdooStub.stubWrite("res.partner", 1247);
        stubReadBack(1247, RAW_B64);

        given()
                .contentType("application/json")
                .body("{\"photo\":\"" + JPEG_DATA_URI + "\"}")
                .when().post("/api/members/1247/photo")
                .then()
                .statusCode(200)
                .body("id", is(1247))
                .body("photo", is(JPEG_DATA_URI));

        // the data-URI prefix must have been stripped before writing to Odoo
        OdooStub.verifyWrite("res.partner", 1247, "\"image\":\"" + RAW_B64 + "\"");
    }

    @Test
    @DisplayName("uploading raw base64 (no data-URI prefix) also writes it to res.partner.image")
    void uploadRawBase64WritesImageToOdoo() {
        OdooStub.stubWrite("res.partner", 1247);
        stubReadBack(1247, RAW_B64);

        given()
                .contentType("application/json")
                .body("{\"photo\":\"" + RAW_B64 + "\"}")
                .when().post("/api/members/1247/photo")
                .then()
                .statusCode(200);

        OdooStub.verifyWrite("res.partner", 1247, "\"image\":\"" + RAW_B64 + "\"");
    }

    @Test
    @DisplayName("an empty photo is rejected with 400 and no write happens")
    void emptyPhotoIsRejected() {
        given()
                .contentType("application/json")
                .body("{\"photo\":\"\"}")
                .when().post("/api/members/1247/photo")
                .then()
                .statusCode(400);

        org.junit.jupiter.api.Assertions.assertEquals(0, OdooStub.writeCount("res.partner"));
    }

    @Test
    @DisplayName("uploading a photo to an unknown member returns 404 and no write happens")
    void unknownMemberReturns404() {
        // member lookup returns nothing → must 404 before any write to Odoo
        OdooStub.stubSearchReadMatching("res.partner", "\"id\",\"=\",99999", List.of());

        given()
                .contentType("application/json")
                .body("{\"photo\":\"" + JPEG_DATA_URI + "\"}")
                .when().post("/api/members/99999/photo")
                .then()
                .statusCode(404);

        org.junit.jupiter.api.Assertions.assertEquals(0, OdooStub.writeCount("res.partner"));
    }

    @Test
    @DisplayName("an invalid base64 photo is rejected with 400 and no write happens")
    void invalidBase64IsRejected() {
        given()
                .contentType("application/json")
                .body("{\"photo\":\"data:image/jpeg;base64,@@@not-base64@@@\"}")
                .when().post("/api/members/1247/photo")
                .then()
                .statusCode(400);

        org.junit.jupiter.api.Assertions.assertEquals(0, OdooStub.writeCount("res.partner"));
    }
}
