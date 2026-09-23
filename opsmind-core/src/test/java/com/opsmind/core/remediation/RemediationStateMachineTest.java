package com.opsmind.core.remediation;

import com.opsmind.core.common.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemediationStateMachineTest {

    @Test
    void allowsTheHappyPath() {
        assertThat(RemediationStateMachine.canTransition(RecommendationStatus.PROPOSED, RecommendationStatus.APPROVED)).isTrue();
        assertThat(RemediationStateMachine.canTransition(RecommendationStatus.APPROVED, RecommendationStatus.EXECUTING)).isTrue();
        assertThat(RemediationStateMachine.canTransition(RecommendationStatus.EXECUTING, RecommendationStatus.EXECUTED)).isTrue();
        assertThat(RemediationStateMachine.canTransition(RecommendationStatus.EXECUTED, RecommendationStatus.VERIFYING)).isTrue();
        assertThat(RemediationStateMachine.canTransition(RecommendationStatus.VERIFYING, RecommendationStatus.VERIFIED_RECOVERED)).isTrue();
    }

    @Test
    void allowsTheDocumentedFailurePaths() {
        assertThat(RemediationStateMachine.canTransition(RecommendationStatus.PROPOSED, RecommendationStatus.REJECTED)).isTrue();
        assertThat(RemediationStateMachine.canTransition(RecommendationStatus.EXECUTING, RecommendationStatus.EXECUTION_FAILED)).isTrue();
        assertThat(RemediationStateMachine.canTransition(RecommendationStatus.VERIFYING, RecommendationStatus.VERIFICATION_FAILED)).isTrue();
    }

    @Test
    void rejectsIllegalTransitions() {
        assertThat(RemediationStateMachine.canTransition(RecommendationStatus.PROPOSED, RecommendationStatus.EXECUTING)).isFalse();
        assertThat(RemediationStateMachine.canTransition(RecommendationStatus.REJECTED, RecommendationStatus.APPROVED)).isFalse();
        assertThat(RemediationStateMachine.canTransition(RecommendationStatus.EXECUTED, RecommendationStatus.APPROVED)).isFalse();
        assertThat(RemediationStateMachine.canTransition(RecommendationStatus.VERIFIED_RECOVERED, RecommendationStatus.EXECUTING)).isFalse();
    }

    @Test
    void validateTransitionThrowsOnIllegalMove() {
        assertThatThrownBy(() -> RemediationStateMachine.validateTransition(RecommendationStatus.PROPOSED, RecommendationStatus.EXECUTED))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Illegal remediation state transition");
    }
}
