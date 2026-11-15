package com.opsmind.core.faultlab;

import com.opsmind.core.audit.AuditService;
import com.opsmind.core.common.DomainException;
import com.opsmind.core.tenant.Role;
import com.opsmind.core.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Authorization behaviour only; no network is touched because no fault token is configured in these tests. */
class FaultLabServiceTest {
    private final UUID org = UUID.randomUUID();
    private final TenantContext admin = new TenantContext(UUID.randomUUID(), org, Role.ADMIN);
    private FaultLabService svc;

    @BeforeEach
    void setUp() {
        svc = spy(new FaultLabService(mock(JdbcTemplate.class), mock(AuditService.class)));
        ReflectionTestUtils.setField(svc, "enabled", false);
        ReflectionTestUtils.setField(svc, "token", "");
        ReflectionTestUtils.setField(svc, "allowedOrgsCsv", "");
        ReflectionTestUtils.setField(svc, "paymentUrl", "http://localhost:1");
        ReflectionTestUtils.setField(svc, "inventoryUrl", "http://localhost:1");
        ReflectionTestUtils.setField(svc, "orderUrl", "http://localhost:1");
    }

    @Test
    void startIsRefusedWhenKillSwitchIsOff() {
        assertThrows(DomainException.class, () -> svc.start(admin, "payment.force-500", 30, null));
    }

    @Test
    void startIsRefusedForNonAllowListedOrganization() {
        ReflectionTestUtils.setField(svc, "enabled", true);
        ReflectionTestUtils.setField(svc, "token", "t");
        assertThrows(AccessDeniedException.class, () -> svc.start(admin, "payment.force-500", 30, null));
    }

    @Test
    void emergencyStopWorksWithKillSwitchOffAndOrgNoLongerAllowListed() {
        doReturn(true).when(svc).orgHasFaultHistory(any());
        Map<String, Object> r = svc.emergencyStop(admin); // enabled=false, allow-list empty
        assertEquals(false, r.get("stopped"));            // no token => nothing can be confirmed, reported honestly
        assertEquals(3, ((List<?>) r.get("servicesNotConfirmed")).size());
        assertTrue(((List<?>) r.get("servicesConfirmed")).isEmpty());
    }

    @Test
    void emergencyStopIsDeniedForOrganizationWithNoFaultStanding() {
        doReturn(false).when(svc).orgHasFaultHistory(any());
        assertThrows(AccessDeniedException.class, () -> svc.emergencyStop(admin));
    }

    @Test
    void emergencyStopIsAllowedForAllowListedOrganization() {
        ReflectionTestUtils.setField(svc, "allowedOrgsCsv", "x," + org);
        doReturn(false).when(svc).orgHasFaultHistory(any());
        assertEquals(3, ((List<?>) svc.emergencyStop(admin).get("servicesNotConfirmed")).size());
    }
}
