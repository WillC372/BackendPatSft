package com.aquaassistant.admin;

import com.aquaassistant.admin.AdminDtos.ActivityRow;
import com.aquaassistant.admin.AdminDtos.PaymentRow;
import com.aquaassistant.admin.AdminDtos.PlanChange;
import com.aquaassistant.admin.AdminDtos.RoleChange;
import com.aquaassistant.admin.AdminDtos.StatusChange;
import com.aquaassistant.admin.AdminDtos.Summary;
import com.aquaassistant.admin.AdminDtos.UserDetail;
import com.aquaassistant.admin.AdminDtos.UserPage;
import com.aquaassistant.security.CurrentUser;
import com.aquaassistant.user.Plan;
import com.aquaassistant.user.Role;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User and plan administration. Access is restricted to the ADMIN role in SecurityConfig ({@code /api/v1/admin/**}).
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    static final String DEMO_PRIVATE = "Es la cuenta de una persona real: el administrador de demostración no puede verla ni cambiarla. "
        + "Puede hacerlo con las cuentas de demostración y las de ejemplo (@example.com).";

    private final AdminUserService service;
    private final com.aquaassistant.user.DemoAccounts demo;

    public AdminController(AdminUserService service, com.aquaassistant.user.DemoAccounts demo) {
        this.service = service;
        this.demo = demo;
    }

    /** The demonstration administrator sees the accounts of real people with masked names and emails. */
    private boolean viewerIsDemo() {
        return demo.isDemo(CurrentUser.id());
    }

    private void requireVisible(Long targetId) {
        if (viewerIsDemo() && !demo.isOpenToDemo(targetId)) {
            throw new com.aquaassistant.common.BusinessException(org.springframework.http.HttpStatus.FORBIDDEN, DEMO_PRIVATE);
        }
    }

    @GetMapping("/summary")
    public Summary summary() {
        return service.summary();
    }

    /** Paginated list, from the newest account to the oldest. {@code status}: ACTIVE or DISABLED. */
    @GetMapping("/users")
    public UserPage users(@RequestParam(required = false) String q, @RequestParam(required = false) Role role,
                          @RequestParam(required = false) Plan plan, @RequestParam(required = false) AccountStatus status,
                          @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        String text = q == null ? null : q.length() > 100 ? q.substring(0, 100) : q;
        UserPage result = service.list(CurrentUser.id(), text, role, plan, status == null ? null : status == AccountStatus.ACTIVE, page, size);
        if (!viewerIsDemo()) {
            return result;
        }
        return new UserPage(result.items().stream().map(r -> demo.isOpenToDemo(r.id()) ? r : new com.aquaassistant.admin.AdminDtos.UserRow(r.id(),
            com.aquaassistant.user.DemoAccounts.maskEmail(r.email()), com.aquaassistant.user.DemoAccounts.maskName(r.fullName()), r.role(),
            r.plan(), r.premiumUntil(), r.manualPremium(), r.enabled(), r.createdAt(), r.lastLoginAt(), r.measurements(), r.self(),
            r.configuredAdmin(), false)).toList(), result.total(), result.page(), result.size(), result.totalPages());
    }

    @GetMapping("/users/{id}")
    public UserDetail user(@PathVariable Long id) {
        requireVisible(id);
        return service.detail(CurrentUser.id(), id);
    }

    /** {@code days}: Premium duration; without it, Premium does not expire. Ignored for the FREE plan. */
    @PutMapping("/users/{id}/plan")
    public UserDetail plan(@PathVariable Long id, @Valid @RequestBody PlanChange request) {
        requireVisible(id);
        return service.changePlan(CurrentUser.id(), id, request);
    }

    @PutMapping("/users/{id}/role")
    public UserDetail role(@PathVariable Long id, @Valid @RequestBody RoleChange request) {
        requireVisible(id);
        return service.changeRole(CurrentUser.id(), id, request.role());
    }

    @PutMapping("/users/{id}/status")
    public UserDetail status(@PathVariable Long id, @Valid @RequestBody StatusChange request) {
        requireVisible(id);
        return service.changeStatus(CurrentUser.id(), id, request.enabled(), request.reason());
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/users/{id}")
    public org.springframework.http.ResponseEntity<Void> delete(@PathVariable Long id) {
        requireVisible(id);
        service.deleteUser(CurrentUser.id(), id);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{id}/sessions")
    public com.aquaassistant.user.dto.SessionDtos.SessionPage sessions(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        requireVisible(id);
        return service.sessions(id, page, size);
    }

    @GetMapping("/users/{id}/sessions/{sessionId}")
    public com.aquaassistant.user.dto.SessionDtos.SessionDetail session(@PathVariable Long id, @PathVariable Long sessionId) {
        requireVisible(id);
        return service.session(id, sessionId);
    }

    @GetMapping("/payments")
    public List<PaymentRow> payments(@RequestParam(defaultValue = "30") int limit) {
        List<PaymentRow> rows = service.recentPayments(limit);
        if (!viewerIsDemo()) {
            return rows;
        }
        return rows.stream().map(p -> demo.isOpenToDemo(p.userId()) ? p : new PaymentRow(p.id(), p.userId(), com.aquaassistant.user.DemoAccounts.maskEmail(p.userEmail()),
            p.referenceCode(), p.amount(), p.currency(), p.periodDays(), p.status(), p.statusLabel(), p.paymentMethod(),
            p.testMode(), p.createdAt(), p.premiumUntil())).toList();
    }

    @GetMapping("/activity")
    public List<ActivityRow> activity(@RequestParam(defaultValue = "30") int limit) {
        List<ActivityRow> rows = service.adminActivity(limit);
        // Administration actions name real accounts; the demonstration administrator only sees the open ones.
        return viewerIsDemo() ? rows.stream().filter(r -> demo.isOpenToDemo(r.userId())).toList() : rows;
    }

    public enum AccountStatus { ACTIVE, DISABLED }
}
