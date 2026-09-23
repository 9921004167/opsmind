package com.opsmind.core.incident;

import com.opsmind.core.common.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentStateMachineTest {

    @Test
    void allowsTheFullHappyPathToResolution() {
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.NEW, IncidentStatus.INVESTIGATING)).isTrue();
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.INVESTIGATING, IncidentStatus.AWAITING_APPROVAL)).isTrue();
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.AWAITING_APPROVAL, IncidentStatus.REMEDIATING)).isTrue();
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.REMEDIATING, IncidentStatus.VERIFYING)).isTrue();
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.VERIFYING, IncidentStatus.RESOLVED)).isTrue();
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.RESOLVED, IncidentStatus.CLOSED)).isTrue();
    }

    @Test
    void allowsReInvestigationWhenVerificationFails() {
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.VERIFYING, IncidentStatus.INVESTIGATING)).isTrue();
    }

    @Test
    void allowsEscalationFromEveryActiveState() {
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.NEW, IncidentStatus.ESCALATED)).isTrue();
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.INVESTIGATING, IncidentStatus.ESCALATED)).isTrue();
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.MITIGATING, IncidentStatus.ESCALATED)).isTrue();
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.AWAITING_APPROVAL, IncidentStatus.ESCALATED)).isTrue();
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.REMEDIATING, IncidentStatus.ESCALATED)).isTrue();
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.VERIFYING, IncidentStatus.ESCALATED)).isTrue();
    }

    @Test
    void rejectsSkippingInvestigationEntirely() {
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.NEW, IncidentStatus.RESOLVED)).isFalse();
    }

    @Test
    void rejectsResolvingWithoutVerification() {
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.REMEDIATING, IncidentStatus.RESOLVED)).isFalse();
    }

    @Test
    void closedIsTerminal() {
        assertThat(IncidentStateMachine.allowedNext(IncidentStatus.CLOSED)).isEmpty();
    }

    @Test
    void validateTransitionThrowsDomainExceptionOnIllegalMove() {
        assertThatThrownBy(() -> IncidentStateMachine.validateTransition(IncidentStatus.NEW, IncidentStatus.RESOLVED))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("NEW")
                .hasMessageContaining("RESOLVED");
    }

    @Test
    void validateTransitionDoesNotThrowOnLegalMove() {
        IncidentStateMachine.validateTransition(IncidentStatus.NEW, IncidentStatus.INVESTIGATING);
        // no exception = pass
    }

    @Test
    void escalatedCanReturnToInvestigationOrClose() {
        assertThat(IncidentStateMachine.allowedNext(IncidentStatus.ESCALATED))
                .containsExactlyInAnyOrder(IncidentStatus.INVESTIGATING, IncidentStatus.CLOSED);
    }
}
