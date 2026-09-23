package com.opsmind.core.alert.webhook;

import java.util.List;
import java.util.Map;

/** Alertmanager's own webhook_configs JSON shape (v4) - not OpsMind's generic
 *  alert schema. See https://prometheus.io/docs/alerting/latest/configuration/#webhook_config */
public record AlertmanagerWebhookPayload(
        String version,
        String groupKey,
        String status,
        String receiver,
        Map<String, String> groupLabels,
        Map<String, String> commonLabels,
        Map<String, String> commonAnnotations,
        String externalURL,
        List<AlertmanagerAlert> alerts
) {
    public record AlertmanagerAlert(
            String status,
            Map<String, String> labels,
            Map<String, String> annotations,
            String startsAt,
            String endsAt,
            String generatorURL,
            String fingerprint
    ) {}
}
