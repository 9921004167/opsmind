package com.opsmind.core.remediation;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers testing requirements #5, #6 (Section 19) at the annotation level:
 * ENGINEER must never be authorized to approve/reject/execute, while ADMIN/SRE
 * always are. (End-to-end enforcement of @PreAuthorize itself is Spring
 * Security's own well-tested behavior - this test guards against someone
 * loosening or removing the annotation on these three endpoints.)
 */
class RemediationControllerAuthorizationTest {

    @Test
    void approveRejectExecuteAreRestrictedToAdminAndSreOnly() throws Exception {
        for (String methodName : new String[]{"approve", "reject", "execute"}) {
            Method method = findMethod(methodName);
            PreAuthorize auth = method.getAnnotation(PreAuthorize.class);
            assertThat(auth).as("@PreAuthorize on %s", methodName).isNotNull();
            assertThat(auth.value()).contains("ADMIN").contains("SRE").doesNotContain("ENGINEER");
        }
    }

    @Test
    void creatingARecommendationAllowsEngineerButNotViewer() throws Exception {
        Method method = findMethod("create");
        PreAuthorize auth = method.getAnnotation(PreAuthorize.class);
        assertThat(auth).isNotNull();
        assertThat(auth.value()).contains("ADMIN").contains("SRE").contains("ENGINEER").doesNotContain("VIEWER");
    }

    private Method findMethod(String name) {
        for (Method m : RemediationController.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) return m;
        }
        throw new IllegalStateException("Method not found: " + name);
    }
}
