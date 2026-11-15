package com.opsmind.core.audit;

import com.opsmind.core.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Read-only view of the organization's own audit trail (tenant-scoped, ADMIN/SRE only). */
@RestController
@RequestMapping("/api/audit-events")
public class AuditEventsController {
    private final JdbcTemplate jdbc;
    public AuditEventsController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SRE')")
    public List<Map<String, Object>> list(@AuthenticationPrincipal TenantContext ctx) {
        return jdbc.queryForList(
            "SELECT action, entity_type, entity_id, actor_user_id, actor_label, created_at FROM audit_logs WHERE organization_id=? ORDER BY created_at DESC LIMIT 200",
            ctx.organizationId());
    }
}
