package com.opsmind.core.alert;

import com.opsmind.core.alert.dto.AlertIngestRequest;
import com.opsmind.core.alert.dto.AlertResponse;
import com.opsmind.core.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Generic alert ingestion endpoint (Section 6). Any monitoring source can POST here
 * once authenticated with a project-scoped credential (Phase 1 uses the same user
 * JWT; a dedicated machine-to-machine ingestion credential is Phase 5+ work).
 */
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertIngestionService alertIngestionService;
    private final AlertRepository alertRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlertResponse ingest(@AuthenticationPrincipal TenantContext ctx,
                                 @Valid @RequestBody AlertIngestRequest request) {
        return alertIngestionService.ingest(ctx.organizationId(), request);
    }

    @GetMapping
    public List<Alert> list(@AuthenticationPrincipal TenantContext ctx) {
        return alertRepository.findByOrganizationIdOrderByReceivedAtDesc(ctx.organizationId());
    }
}
