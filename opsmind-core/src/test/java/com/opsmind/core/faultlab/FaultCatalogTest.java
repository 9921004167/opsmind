package com.opsmind.core.faultlab;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FaultCatalogTest {
    @Test void onlyRealFaultsAreExecutable() {
        assertEquals(5, FaultCatalog.ALL.size());
        assertTrue(FaultCatalog.find("payment.force-500").isPresent());
        assertTrue(FaultCatalog.find("catalog.anything").isEmpty());
        assertTrue(FaultCatalog.find("service.kill").isEmpty());
    }
    @Test void limitsMatchApprovedPolicy() {
        assertEquals(30, FaultLabService.DEFAULT_SECONDS);
        assertEquals(120, FaultLabService.MAX_SECONDS);
    }
    @Test void disablePathIsFixedPerService() {
        assertEquals("/faults/payment/disable", FaultCatalog.disablePath("payment"));
    }
}
