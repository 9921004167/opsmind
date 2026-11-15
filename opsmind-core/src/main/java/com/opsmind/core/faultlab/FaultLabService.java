package com.opsmind.core.faultlab;

import com.opsmind.core.audit.AuditService;
import com.opsmind.core.common.DomainException;
import com.opsmind.core.common.NotFoundException;
import com.opsmind.core.tenant.TenantContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

/**
 * Role-checked, allow-listed, time-boxed wrapper over the existing /faults/** endpoints.
 * FAULT_ADMIN_TOKEN stays server-side.
 *
 * Authorization model
 *  - start:           kill switch ON + token configured + organization allow-listed (+ ADMIN/SRE/ENGINEER in the controller)
 *  - stop (own):      token configured; experiment must belong to the caller's organization
 *  - emergency stop:  ADMIN/SRE (controller) AND (organization allow-listed OR has ever run an experiment).
 *                     Deliberately NOT gated by the kill switch or by current allow-listing: it is the way to make a
 *                     fault safe. It is gated by organization history so a stranger organization cannot trigger a
 *                     global operation.
 *
 * Safety properties: at most one RUNNING/DISABLE_UNCONFIRMED experiment per service (DB-enforced); every experiment
 * expires (timer + 15s watchdog that survives lost timers and also runs when the kill switch is OFF); a fault is never
 * reported STOPPED/EXPIRED unless the service confirmed the disable (otherwise DISABLE_UNCONFIRMED, retried by the
 * watchdog); HTTP calls have timeouts; upstream error text is logged, never returned to the client.
 */
@Service
@Slf4j
public class FaultLabService {
    public static final int DEFAULT_SECONDS = 30, MAX_SECONDS = 120;
    private static final List<String> SERVICES = List.of("payment", "inventory", "order");
    private static final String ACTIVE = "('RUNNING','DISABLE_UNCONFIRMED')";

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final RestClient http;
    private final ScheduledExecutorService timer = Executors.newScheduledThreadPool(2);
    private final Map<UUID, ScheduledFuture<?>> expiries = new ConcurrentHashMap<>();

    @Value("${opsmind.faultlab.enabled:false}") private boolean enabled;
    @Value("${opsmind.faultlab.allowed-org-ids:}") private String allowedOrgsCsv;
    @Value("${opsmind.remediation.fault-admin-token:}") private String token;
    @Value("${opsmind.faultlab.payment-url:http://payment-service:8091}") private String paymentUrl;
    @Value("${opsmind.faultlab.inventory-url:http://inventory-service:8092}") private String inventoryUrl;
    @Value("${opsmind.faultlab.order-url:http://order-service:8093}") private String orderUrl;

    public FaultLabService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(2000);
        f.setReadTimeout(5000);
        this.http = RestClient.builder().requestFactory(f).build();
    }

    /** Runs regardless of the kill switch: experiments already running must still be cleaned up and expired. */
    @PostConstruct
    void init() {
        List<Map<String, Object>> stale = jdbc.queryForList("SELECT DISTINCT target_service FROM fault_experiments WHERE status='RUNNING'");
        for (Map<String, Object> r : stale) {
            String svc = (String) r.get("target_service");
            boolean ok = disable(svc); // false when no token is configured: we cannot prove the fault is off
            jdbc.update("UPDATE fault_experiments SET status=?, status_detail='cleared by startup sweep', ended_at=now() WHERE status='RUNNING' AND target_service=?",
                    ok ? "EXPIRED" : "DISABLE_UNCONFIRMED", svc);
        }
        if (!token.isBlank()) timer.scheduleWithFixedDelay(this::watchdog, 15, 15, TimeUnit.SECONDS);
    }

    @PreDestroy void shutdown() { timer.shutdownNow(); }

    /** Expires overdue experiments (even if their in-memory timer was lost) and retries unconfirmed disables. */
    void watchdog() {
        try {
            List<Map<String, Object>> due = jdbc.queryForList(
                "SELECT id, target_service FROM fault_experiments WHERE status='RUNNING' AND started_at + make_interval(secs => duration_seconds) < now()");
            for (Map<String, Object> r : due) finish((UUID) r.get("id"), (String) r.get("target_service"), "EXPIRED", "expired by watchdog", null);
            List<Map<String, Object>> unconfirmed = jdbc.queryForList("SELECT DISTINCT target_service FROM fault_experiments WHERE status='DISABLE_UNCONFIRMED'");
            for (Map<String, Object> r : unconfirmed) {
                String svc = (String) r.get("target_service");
                if (disable(svc)) jdbc.update("UPDATE fault_experiments SET status='EXPIRED', status_detail='disable confirmed on watchdog retry', ended_at=COALESCE(ended_at, now()) WHERE status='DISABLE_UNCONFIRMED' AND target_service=?", svc);
            }
        } catch (Exception e) { log.error("Fault Lab watchdog failed", e); }
    }

    public Map<String, Object> capabilities(TenantContext ctx) {
        return Map.of("enabled", enabled, "orgAllowed", orgAllowed(ctx), "tokenConfigured", !token.isBlank(),
                "emergencyStopAllowed", emergencyAllowed(ctx),
                "defaultSeconds", DEFAULT_SECONDS, "maxSeconds", MAX_SECONDS,
                "minLatencyMs", FaultCatalog.MIN_MS, "maxLatencyMs", FaultCatalog.MAX_MS,
                "faults", FaultCatalog.ALL);
    }

    public Map<String, Object> start(TenantContext ctx, String faultKey, Integer seconds, Long ms) {
        if (!enabled) throw new DomainException("Fault Lab is disabled on this deployment");
        if (token.isBlank()) throw new DomainException("Fault Lab is not configured");
        if (!orgAllowed(ctx)) throw new AccessDeniedException("Organization is not allow-listed for fault injection");
        var fault = FaultCatalog.find(faultKey).orElseThrow(() -> new DomainException("Unknown fault"));
        int dur = seconds == null ? DEFAULT_SECONDS : seconds;
        if (dur < 1 || dur > MAX_SECONDS) throw new DomainException("Duration must be 1-" + MAX_SECONDS + " seconds");
        if (fault.needsMs() && (ms == null || ms < FaultCatalog.MIN_MS || ms > FaultCatalog.MAX_MS))
            throw new DomainException("Latency must be " + FaultCatalog.MIN_MS + "-" + FaultCatalog.MAX_MS + " ms");

        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO fault_experiments(id,organization_id,started_by,fault_key,target_service,param_ms,duration_seconds,status) VALUES (?,?,?,?,?,?,?,'RUNNING')",
                    id, ctx.organizationId(), ctx.userId(), fault.key(), fault.service(), fault.needsMs() ? ms : null, dur);
        } catch (DuplicateKeyException e) {
            throw new DomainException("An experiment is already active or unconfirmed on " + fault.service() + "; use Emergency stop if a fault may still be on.");
        }
        audit.record(ctx.organizationId(), ctx.userId(), "FAULT_EXPERIMENT_STARTED", "FaultExperiment", id.toString(), "{\"fault\":\"" + fault.key() + "\",\"seconds\":" + dur + "}");
        try {
            call(baseUrl(fault.service()) + fault.path() + (fault.needsMs() ? "?ms=" + ms : ""));
        } catch (Exception e) {
            log.warn("Fault apply failed for {}: {}", fault.key(), e.getMessage());
            boolean cleared = disable(fault.service());
            jdbc.update("UPDATE fault_experiments SET status=?, status_detail=?, ended_at=now() WHERE id=?",
                    cleared ? "FAILED_TO_START" : "DISABLE_UNCONFIRMED", "target service did not accept the fault", id);
            throw new DomainException("The target service did not accept the fault" + (cleared ? "; nothing is running." : "; disable could not be confirmed - use Emergency stop."));
        }
        expiries.put(id, timer.schedule(() -> finish(id, fault.service(), "EXPIRED", "auto-expired", null), dur, TimeUnit.SECONDS));
        return get(ctx, id);
    }

    /** Stops one experiment of the caller's own organization. Allowed even if the kill switch is now OFF. */
    public Map<String, Object> stop(TenantContext ctx, UUID id) {
        Map<String, Object> exp = get(ctx, id); // organization-scoped: 404 for other organizations' experiments
        if (!"RUNNING".equals(exp.get("status"))) return exp;
        finish(id, (String) exp.get("target_service"), "STOPPED", "stopped by user", ctx);
        return get(ctx, id);
    }

    /**
     * Global operation: tries to disable EVERY faultable service (fault state is process-wide, so it affects every
     * organization's experiments) and reports exactly which services were confirmed and which were not.
     * Authorization: ADMIN/SRE (controller) + allow-listed or experiment history (here). Not gated by the kill switch.
     */
    public Map<String, Object> emergencyStop(TenantContext ctx) {
        if (!emergencyAllowed(ctx)) throw new AccessDeniedException("Organization is not authorized for fault operations");
        List<Map<String, Object>> affected = jdbc.queryForList("SELECT id, organization_id, target_service FROM fault_experiments WHERE status IN " + ACTIVE);
        List<String> confirmed = new ArrayList<>(), unconfirmed = new ArrayList<>();
        for (String svc : SERVICES) {
            boolean ok = disable(svc); // every service is attempted, even if an earlier one failed
            (ok ? confirmed : unconfirmed).add(svc);
            jdbc.update("UPDATE fault_experiments SET status=?, status_detail=?, ended_at=COALESCE(ended_at, now()) WHERE status IN " + ACTIVE + " AND target_service=?",
                    ok ? "STOPPED" : "DISABLE_UNCONFIRMED", ok ? "emergency stop" : "emergency stop: service did not confirm disable", svc);
        }
        expiries.values().forEach(f -> f.cancel(false));
        expiries.clear();
        audit.record(ctx.organizationId(), ctx.userId(), "FAULT_EMERGENCY_STOP", "FaultLab", "all",
                "{\"confirmed\":" + confirmed.size() + ",\"unconfirmed\":" + unconfirmed.size() + "}");
        for (Map<String, Object> r : affected) { // leave a trail in every organization whose experiment was touched
            boolean ok = confirmed.contains((String) r.get("target_service"));
            audit.recordSystemAction((UUID) r.get("organization_id"), "emergency-stop:" + ctx.userId(),
                    ok ? "FAULT_EXPERIMENT_EMERGENCY_STOPPED" : "FAULT_EXPERIMENT_DISABLE_UNCONFIRMED", "FaultExperiment", r.get("id").toString(), null);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stopped", unconfirmed.isEmpty());
        out.put("servicesConfirmed", confirmed);
        out.put("servicesNotConfirmed", unconfirmed);
        out.put("experimentsAffected", affected.size());
        return out;
    }

    public List<Map<String, Object>> list(TenantContext ctx) {
        return jdbc.queryForList("SELECT * FROM fault_experiments WHERE organization_id=? ORDER BY started_at DESC LIMIT 50", ctx.organizationId());
    }

    private Map<String, Object> get(TenantContext ctx, UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM fault_experiments WHERE id=? AND organization_id=?", id, ctx.organizationId());
        if (rows.isEmpty()) throw new NotFoundException("Experiment not found");
        return rows.get(0);
    }

    private void finish(UUID id, String service, String status, String detail, TenantContext ctx) {
        ScheduledFuture<?> f = expiries.remove(id);
        if (f != null) f.cancel(false);
        boolean ok = disable(service);
        jdbc.update("UPDATE fault_experiments SET status=?, status_detail=?, ended_at=now() WHERE id=? AND status='RUNNING'",
                ok ? status : "DISABLE_UNCONFIRMED", ok ? detail : detail + "; service did not confirm disable - use Emergency stop", id);
        List<Map<String, Object>> o = jdbc.queryForList("SELECT organization_id FROM fault_experiments WHERE id=?", id);
        if (!o.isEmpty()) audit.recordSystemAction((UUID) o.get(0).get("organization_id"),
                ctx == null ? "fault-lab-timer" : ctx.userId().toString(), "FAULT_EXPERIMENT_" + (ok ? status : "DISABLE_UNCONFIRMED"), "FaultExperiment", id.toString(), null);
    }

    boolean orgAllowed(TenantContext ctx) {
        if (allowedOrgsCsv == null || allowedOrgsCsv.isBlank()) return false;
        return Arrays.stream(allowedOrgsCsv.split(",")).map(String::trim).anyMatch(ctx.organizationId().toString()::equals);
    }

    /** True if the organization has ever run an experiment (covers orgs removed from the allow-list mid-experiment). */
    boolean orgHasFaultHistory(UUID organizationId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM fault_experiments WHERE organization_id=?", Integer.class, organizationId);
        return n != null && n > 0;
    }

    boolean emergencyAllowed(TenantContext ctx) { return orgAllowed(ctx) || orgHasFaultHistory(ctx.organizationId()); }

    private String baseUrl(String svc) {
        return switch (svc) { case "payment" -> paymentUrl; case "inventory" -> inventoryUrl; case "order" -> orderUrl;
            default -> throw new DomainException("Unknown service"); };
    }
    private void call(String url) { http.post().uri(URI.create(url)).header("X-Fault-Admin-Token", token).retrieve().toBodilessEntity(); }

    /** True only if the service answered 2xx. With no token configured nothing can be confirmed. */
    private boolean disable(String svc) {
        if (token == null || token.isBlank()) { log.error("Cannot disable {}: no fault admin token configured", svc); return false; }
        try { call(baseUrl(svc) + FaultCatalog.disablePath(svc)); return true; }
        catch (Exception e) { log.error("Disable of {} not confirmed: {}", svc, e.getMessage()); return false; }
    }
}
