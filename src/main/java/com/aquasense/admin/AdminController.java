package com.aquasense.admin;

import com.aquasense.admin.AdminDtos.ActivityRow;
import com.aquasense.admin.AdminDtos.PaymentRow;
import com.aquasense.admin.AdminDtos.PlanChange;
import com.aquasense.admin.AdminDtos.RoleChange;
import com.aquasense.admin.AdminDtos.StatusChange;
import com.aquasense.admin.AdminDtos.Summary;
import com.aquasense.admin.AdminDtos.UserDetail;
import com.aquasense.admin.AdminDtos.UserPage;
import com.aquasense.security.CurrentUser;
import com.aquasense.user.Plan;
import com.aquasense.user.Role;
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
 * Administración de usuarios y planes. El acceso se restringe al rol ADMIN en SecurityConfig ({@code /api/v1/admin/**}).
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminUserService service;

    public AdminController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public Summary summary() {
        return service.summary();
    }

    /** Lista paginada, de la cuenta más nueva a la más antigua. {@code status}: ACTIVE o DISABLED. */
    @GetMapping("/users")
    public UserPage users(@RequestParam(required = false) String q, @RequestParam(required = false) Role role,
                          @RequestParam(required = false) Plan plan, @RequestParam(required = false) AccountStatus status,
                          @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        String text = q == null ? null : q.length() > 100 ? q.substring(0, 100) : q;
        return service.list(CurrentUser.id(), text, role, plan, status == null ? null : status == AccountStatus.ACTIVE, page, size);
    }

    @GetMapping("/users/{id}")
    public UserDetail user(@PathVariable Long id) {
        return service.detail(CurrentUser.id(), id);
    }

    /** {@code days}: duración de Premium; sin él, Premium no vence. Con plan FREE se ignora. */
    @PutMapping("/users/{id}/plan")
    public UserDetail plan(@PathVariable Long id, @Valid @RequestBody PlanChange request) {
        return service.changePlan(CurrentUser.id(), id, request);
    }

    @PutMapping("/users/{id}/role")
    public UserDetail role(@PathVariable Long id, @Valid @RequestBody RoleChange request) {
        return service.changeRole(CurrentUser.id(), id, request.role());
    }

    @PutMapping("/users/{id}/status")
    public UserDetail status(@PathVariable Long id, @Valid @RequestBody StatusChange request) {
        return service.changeStatus(CurrentUser.id(), id, request.enabled(), request.reason());
    }

    @GetMapping("/payments")
    public List<PaymentRow> payments(@RequestParam(defaultValue = "30") int limit) {
        return service.recentPayments(limit);
    }

    @GetMapping("/activity")
    public List<ActivityRow> activity(@RequestParam(defaultValue = "30") int limit) {
        return service.adminActivity(limit);
    }

    public enum AccountStatus { ACTIVE, DISABLED }
}
