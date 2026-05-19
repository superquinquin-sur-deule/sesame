package org.superquinquin.members;

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
