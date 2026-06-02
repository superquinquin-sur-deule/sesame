package org.superquinquin.members;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.superquinquin.odoo.OdooClient;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@ApplicationScoped
public class MemberRepository {

    private static final List<String> SUMMARY_FIELDS = List.of(
            "id", "name", "email", "barcode_base", "cooperative_state",
            "is_member", "is_associated_people", "parent_member_num", "child_ids"
    );

    private static final List<String> DETAIL_FIELDS = List.of(
            "id", "name", "email", "barcode_base", "cooperative_state",
            "is_member", "is_associated_people", "is_former_member",
            "parent_id", "parent_member_num",
            "current_template_name",
            "create_date", "unsubscription_date", "image"
    );

    private static final List<String> REGISTRATION_FIELDS = List.of(
            "id", "date_begin", "shift_type", "state", "shift_ticket_id"
    );

    private static final List<String> ACTIVE_REGISTRATION_STATES = List.of(
            "draft", "open", "waiting", "replacing"
    );

    @Inject OdooClient odoo;

    /**
     * SHA-256 (of the decoded image bytes) of each known Odoo "no photo" placeholder silhouette.
     * Members carrying one of these are reported as having no photo — see {@link #photoDataUri}.
     */
    @Inject
    @ConfigProperty(name = "sesame.photo.placeholder-sha256")
    Set<String> placeholderSha256;

    public List<MemberSummary> search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return List.of();

        List<Object> domain = buildSearchDomain(q);
        JsonNode result = odoo.executeKw(
                "res.partner", "search_read",
                List.of(domain),
                Map.of("fields", SUMMARY_FIELDS, "limit", 25)
        );
        List<MemberSummary> out = new ArrayList<>();
        if (result != null && result.isArray()) {
            for (JsonNode node : result) {
                if (isOrphanGhost(node) || isTechnicalAccount(node)) continue;
                out.add(toSummary(node));
            }
        }
        return out;
    }

    private static boolean isOrphanGhost(JsonNode node) {
        boolean isUnsubscribed = "unsubscribed".equals(textField(node, "cooperative_state"));
        boolean isAssociated   = boolField(node, "is_associated_people");
        boolean hasParentLink  = intField(node, "parent_member_num") > 0;
        boolean hasBinomeChild = hasChildren(node);
        return isUnsubscribed && !isAssociated && !hasParentLink && !hasBinomeChild;
    }

    private static boolean hasChildren(JsonNode node) {
        JsonNode v = node.get("child_ids");
        return v != null && v.isArray() && v.size() > 0;
    }

    private static boolean isTechnicalAccount(JsonNode node) {
        return !boolField(node, "is_member") && !boolField(node, "is_associated_people");
    }

    /**
     * Writes a pre-validated base64 photo into Odoo ({@code res.partner.image}) so Odoo recomputes
     * {@code image_medium}/{@code image_small}. The argument must already be cleaned by
     * {@link #extractBase64} (the resource validates before checking existence). Throws if Odoo
     * refuses the write.
     */
    public void updatePhoto(int id, String base64) {
        if (!odoo.write("res.partner", id, Map.of("image", base64))) {
            throw new IllegalStateException("Odoo refused the photo write for member " + id);
        }
    }

    private static final Pattern DATA_URI_PREFIX = Pattern.compile("^data:[^;,]+;base64,");
    // A legitimate ~512px JPEG is tens of KB of base64; this bounds the single production write path.
    private static final int MAX_PHOTO_BASE64_CHARS = 4_000_000;

    /**
     * Validates and normalises a photo payload: strips an optional {@code data:…;base64,} prefix and
     * returns the bare, single-line base64. Throws {@link IllegalArgumentException} (→ HTTP 400) for
     * an empty, oversized, or non-base64 payload — before any Odoo call.
     */
    static String extractBase64(String photo) {
        if (photo == null) throw new IllegalArgumentException("photo is required");
        String base64 = DATA_URI_PREFIX.matcher(photo.trim()).replaceFirst("").trim();
        if (base64.isEmpty()) throw new IllegalArgumentException("photo is empty");
        if (base64.length() > MAX_PHOTO_BASE64_CHARS) throw new IllegalArgumentException("photo is too large");
        try {
            Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("photo is not valid base64");
        }
        return base64;
    }

    public Optional<MemberDetail> findById(int id) {
        JsonNode result = odoo.executeKw(
                "res.partner", "search_read",
                List.of(List.of(List.of("id", "=", id))),
                Map.of("fields", DETAIL_FIELDS, "limit", 1)
        );
        if (result == null || !result.isArray() || result.size() == 0) return Optional.empty();
        JsonNode node = result.get(0);
        MemberDetail.Binome binome = resolveBinome(node);
        MemberDetail.NextShift nextShift = findNextShift(id, textField(node, "current_template_name"));
        String photo = photoDataUri(textField(node, "image"));
        return Optional.of(toDetail(node, binome, nextShift, photo));
    }

    private MemberDetail.NextShift findNextShift(int partnerId, String currentTemplateName) {
        String nowUtc = LocalDateTime.now(ZoneOffset.UTC).format(ODOO_DATETIME);
        JsonNode result = odoo.executeKw(
                "shift.registration", "search_read",
                List.of(List.of(
                        List.of("partner_id", "=", partnerId),
                        List.of("date_begin", ">=", nowUtc),
                        List.of("state", "in", ACTIVE_REGISTRATION_STATES)
                )),
                Map.of("fields", REGISTRATION_FIELDS, "limit", 1, "order", "date_begin asc")
        );
        if (result == null || !result.isArray() || result.size() == 0) return null;
        JsonNode reg = result.get(0);
        String role = "ftop".equals(textField(reg, "shift_type"))
                ? many2oneName(reg.get("shift_ticket_id"))
                : currentTemplateName;
        return parseNextShift(textField(reg, "date_begin"), role);
    }

    private static String many2oneName(JsonNode ref) {
        if (ref == null || !ref.isArray() || ref.size() < 2) return null;
        return ref.get(1).asText();
    }

    private MemberDetail.Binome resolveBinome(JsonNode node) {
        if (boolField(node, "is_member")) {
            int memberId = node.get("id").asInt();
            JsonNode kids = odoo.executeKw(
                    "res.partner", "search_read",
                    List.of(List.of(
                            List.of("parent_id", "=", memberId),
                            List.of("is_associated_people", "=", true)
                    )),
                    Map.of("fields", SUMMARY_FIELDS, "limit", 1)
            );
            if (kids != null && kids.isArray() && kids.size() > 0) {
                return toBinome(kids.get(0));
            }
        }
        if (boolField(node, "is_associated_people")) {
            JsonNode parent = node.get("parent_id");
            if (parent != null && parent.isArray() && parent.size() > 0) {
                int parentId = parent.get(0).asInt();
                JsonNode parents = odoo.executeKw(
                        "res.partner", "search_read",
                        List.of(List.of(List.of("id", "=", parentId))),
                        Map.of("fields", SUMMARY_FIELDS, "limit", 1)
                );
                if (parents != null && parents.isArray() && parents.size() > 0) {
                    return toBinome(parents.get(0));
                }
            }
        }
        return null;
    }

    private List<Object> buildSearchDomain(String q) {
        List<List<Object>> conjuncts = new ArrayList<>();
        conjuncts.add(List.of(List.of("is_company", "=", false)));
        conjuncts.add(List.of(List.of("active", "=", true)));
        conjuncts.add(List.of("|",
                List.of("is_member", "=", true),
                List.of("is_associated_people", "=", true)));

        Integer asNumber = tryParseInt(q);
        if (asNumber != null) {
            conjuncts.add(List.of("|",
                    List.of("barcode_base", "=", asNumber),
                    List.of("name", "ilike", q)));
        } else {
            for (String token : tokenize(q)) {
                conjuncts.add(List.of(List.of("name", "ilike", token)));
            }
        }

        List<Object> domain = new ArrayList<>();
        for (int i = 0; i < conjuncts.size() - 1; i++) domain.add("&");
        for (List<Object> conj : conjuncts) domain.addAll(conj);
        return domain;
    }

    private static List<String> tokenize(String q) {
        List<String> tokens = new ArrayList<>();
        for (String t : q.split("[\\s,]+")) {
            if (!t.isBlank()) tokens.add(t);
        }
        return tokens.isEmpty() ? List.of(q) : tokens;
    }

    private static Integer tryParseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static MemberSummary toSummary(JsonNode n) {
        ParsedName name = splitName(textField(n, "name"));
        return new MemberSummary(
                n.get("id").asInt(),
                intField(n, "barcode_base"),
                name.first(),
                name.last(),
                textField(n, "email"),
                MemberStatus.fromOdoo(textField(n, "cooperative_state"))
        );
    }

    private static MemberDetail.Binome toBinome(JsonNode n) {
        ParsedName name = splitName(textField(n, "name"));
        return new MemberDetail.Binome(
                n.get("id").asInt(),
                intField(n, "barcode_base"),
                name.first(),
                name.last(),
                MemberStatus.fromOdoo(textField(n, "cooperative_state"))
        );
    }

    private static MemberDetail toDetail(JsonNode n, MemberDetail.Binome binome, MemberDetail.NextShift nextShift, String photo) {
        ParsedName name = splitName(textField(n, "name"));
        LocalDate joinedOn = parseDate(textField(n, "create_date"));
        LocalDate leftOn = parseDate(textField(n, "unsubscription_date"));
        return new MemberDetail(
                n.get("id").asInt(),
                intField(n, "barcode_base"),
                name.first(),
                name.last(),
                textField(n, "email"),
                MemberStatus.fromOdoo(textField(n, "cooperative_state")),
                null,
                joinedOn,
                leftOn,
                nextShift,
                binome,
                photo
        );
    }

    /**
     * Resolves a partner {@code image} into a renderable data URI, or {@code null} when there is no
     * usable photo. Beyond the empty/unrecognised cases, Odoo stamps a generic grey silhouette on
     * many imported cooperators; that placeholder (matched by {@link #isDefaultPlaceholder}) is
     * reported as absent so the UI shows initials and offers "Prendre une photo", not "Reprendre".
     */
    private String photoDataUri(String base64) {
        if (base64 == null || base64.isBlank()) return null;
        if (isDefaultPlaceholder(base64)) return null;
        return toPhotoDataUri(base64);
    }

    private boolean isDefaultPlaceholder(String base64) {
        if (placeholderSha256 == null || placeholderSha256.isEmpty()) return false;
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return false; // not decodable here → let toPhotoDataUri decide
        }
        String hex = sha256Hex(decoded);
        for (String known : placeholderSha256) {
            if (known != null && known.trim().equalsIgnoreCase(hex)) return true;
        }
        return false;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e); // guaranteed by the JDK
        }
    }

    private static String toPhotoDataUri(String base64) {
        if (base64 == null || base64.isBlank()) return null;
        String mime;
        if (base64.startsWith("/9j/")) mime = "image/jpeg";
        else if (base64.startsWith("iVBORw0KGgo")) mime = "image/png";
        else if (base64.startsWith("R0lGOD")) mime = "image/gif";
        else return null;
        return "data:" + mime + ";base64," + base64;
    }

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter ODOO_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static MemberDetail.NextShift parseNextShift(String dateTime, String role) {
        if (dateTime == null || dateTime.isBlank()) return null;
        LocalDateTime utc = LocalDateTime.parse(dateTime, ODOO_DATETIME);
        var paris = utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(PARIS);
        String time = String.format("%02d:%02d", paris.getHour(), paris.getMinute());
        return new MemberDetail.NextShift(paris.toLocalDate(), time, role);
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        return LocalDate.parse(s.substring(0, 10));
    }

    private record ParsedName(String first, String last) {}

    private static ParsedName splitName(String raw) {
        if (raw == null) return new ParsedName("", "");
        String trimmed = raw.trim();
        int comma = trimmed.indexOf(',');
        if (comma > 0) {
            String last = capitalize(trimmed.substring(0, comma).trim());
            String first = trimmed.substring(comma + 1).trim();
            return new ParsedName(first, last);
        }
        int sp = trimmed.lastIndexOf(' ');
        if (sp > 0) {
            return new ParsedName(trimmed.substring(0, sp), trimmed.substring(sp + 1));
        }
        return new ParsedName(trimmed, "");
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder out = new StringBuilder(s.length());
        boolean atWordStart = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == '-' || c == '\'') {
                out.append(c);
                atWordStart = true;
            } else {
                out.append(atWordStart ? Character.toUpperCase(c) : Character.toLowerCase(c));
                atWordStart = false;
            }
        }
        return out.toString();
    }

    private static String textField(JsonNode n, String name) {
        JsonNode v = n.get(name);
        if (v == null || v.isNull() || v.isBoolean()) return null;
        return v.asText();
    }

    private static int intField(JsonNode n, String name) {
        JsonNode v = n.get(name);
        return (v == null || v.isNull() || v.isBoolean()) ? 0 : v.asInt();
    }

    private static boolean boolField(JsonNode n, String name) {
        JsonNode v = n.get(name);
        return v != null && v.isBoolean() && v.asBoolean();
    }
}
