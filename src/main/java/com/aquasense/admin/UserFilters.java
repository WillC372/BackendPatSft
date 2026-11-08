package com.aquasense.admin;

import com.aquasense.user.Plan;
import com.aquasense.user.Role;
import com.aquasense.user.User;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Specification: combinable filters for the user list (text, role, plan and status). An empty filter does not restrict.
 */
final class UserFilters {

    private UserFilters() {
    }

    static Specification<User> matching(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String like = "%" + text.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> cb.or(cb.like(cb.lower(root.get("email")), like, '\\'),
            cb.like(cb.lower(root.get("fullName")), like, '\\'));
    }

    static Specification<User> role(Role role) {
        return role == null ? null : (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    static Specification<User> plan(Plan plan) {
        return plan == null ? null : (root, query, cb) -> cb.equal(root.get("plan"), plan);
    }

    static Specification<User> enabled(Boolean enabled) {
        return enabled == null ? null : (root, query, cb) -> cb.equal(root.get("enabled"), enabled);
    }

    static Specification<User> all(String text, Role role, Plan plan, Boolean enabled) {
        return Specification.allOf(matching(text), role(role), plan(plan), enabled(enabled));
    }
}
