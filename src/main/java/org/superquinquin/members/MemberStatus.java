package org.superquinquin.members;

/**
 * Public-facing status displayed at the front desk. The four visible buckets
 * the welcome staff cares about — derived from Odoo's richer cooperative_state
 * selection.
 */
public enum MemberStatus {
    ok, alert, suspended, removed;

    public static MemberStatus fromOdoo(String cooperativeState) {
        if (cooperativeState == null) return ok;
        return switch (cooperativeState) {
            case "up_to_date", "not_concerned", "exempted", "vacation" -> ok;
            case "alert", "delay" -> alert;
            case "suspended", "blocked", "unpayed" -> suspended;
            case "unsubscribed" -> removed;
            default -> ok;
        };
    }
}
