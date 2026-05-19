package org.superquinquin.members;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.superquinquin.odoo.OdooClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter between Odoo res.partner records and the application's {@link MemberSummary}
 * / {@link MemberDetail} view models.
 */
@ApplicationScoped
public class MemberRepository {

    private static final List<String> SUMMARY_FIELDS = List.of(
            "id", "name", "email", "barcode_base", "cooperative_state",
            "is_member", "is_associated_people", "parent_member_num"
    );

    private static final List<String> DETAIL_FIELDS = List.of(
            "id", "name", "email", "barcode_base", "cooperative_state",
            "is_member", "is_associated_people", "is_former_member",
            "parent_id", "parent_member_num",
            "next_shift_time", "current_template_name", "shift_type",
            "create_date", "unsubscription_date"
    );

    @Inject OdooClient odoo;

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
                if (!isOrphanGhost(node)) out.add(toSummary(node));
            }
        }
        return out;
    }

    /**
     * Many former titulaires now appear as a binôme of someone else. Odoo
     * keeps both records (the old désinscrit titulaire and the current
     * is_associated_people one), so they show up twice in search.
     *
     * The désinscrit duplicate has no link back to a current main coop —
     * we drop it. The active is_associated_people record (which carries
     * the up-to-date status and the parent link) stays.
     */
    private static boolean isOrphanGhost(JsonNode node) {
        boolean isUnsubscribed = "unsubscribed".equals(textField(node, "cooperative_state"));
        boolean isAssociated   = boolField(node, "is_associated_people");
        boolean hasParentLink  = intField(node, "parent_member_num") > 0;
        return isUnsubscribed && !isAssociated && !hasParentLink;
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
        return Optional.of(toDetail(node, binome));
    }

    private MemberDetail.Binome resolveBinome(JsonNode node) {
        // If this member has associated people (binôme child), look it up.
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
        // Otherwise, if this record IS the binôme child, expose the parent member.
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
        // Cooperators only — exclude companies and non-members noise, and
        // drop unsubscribed records that have no parent_id link (these are
        // historical titulaire duplicates of people now registered as
        // is_associated_people on someone else's account).
        List<Object> base = List.of(
                List.of("is_company", "=", false),
                List.of("active", "=", true)
        );
        // ("cooperative_state" != "unsubscribed") OR (parent_member_num > 0)
        List<Object> dedupClause = List.of("|",
                List.of("cooperative_state", "!=", "unsubscribed"),
                List.of("parent_member_num", ">", 0));
        // Numeric query → barcode_base exact + name contains; otherwise name only.
        List<Object> term;
        Integer asNumber = tryParseInt(q);
        if (asNumber != null) {
            term = List.of("|",
                    List.of("barcode_base", "=", asNumber),
                    List.of("name", "ilike", q));
        } else {
            term = List.of(List.of("name", "ilike", q));
        }
        List<Object> domain = new ArrayList<>();
        // 3 base conjunctions (is_company, active, dedup) + the term group
        domain.add("&");
        domain.add("&");
        domain.add("&");
        domain.addAll(base);
        domain.addAll(dedupClause);
        domain.addAll(term);
        return domain;
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

    private static MemberDetail toDetail(JsonNode n, MemberDetail.Binome binome) {
        ParsedName name = splitName(textField(n, "name"));
        LocalDate joinedOn = parseDate(textField(n, "create_date"));
        LocalDate leftOn = parseDate(textField(n, "unsubscription_date"));
        MemberDetail.NextShift shift = parseNextShift(
                textField(n, "next_shift_time"),
                textField(n, "current_template_name"));
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
                shift,
                binome
        );
    }

    private static MemberDetail.NextShift parseNextShift(String dateTime, String role) {
        if (dateTime == null || dateTime.isBlank()) return null;
        // Odoo datetime: "YYYY-MM-DD HH:MM:SS"
        String[] parts = dateTime.split(" ", 2);
        LocalDate date = parseDate(parts[0]);
        String time = parts.length > 1 ? parts[1].substring(0, 5) : "";
        return new MemberDetail.NextShift(date, time, role);
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
        // No comma — fall back to a "First Last" heuristic.
        int sp = trimmed.lastIndexOf(' ');
        if (sp > 0) {
            return new ParsedName(trimmed.substring(0, sp), trimmed.substring(sp + 1));
        }
        return new ParsedName(trimmed, "");
    }

    /** "VANDENBUSSCHE" → "Vandenbussche" (preserves multi-word surnames). */
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
