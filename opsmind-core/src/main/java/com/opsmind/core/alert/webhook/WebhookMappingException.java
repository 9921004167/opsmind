package com.opsmind.core.alert.webhook;

/** Thrown when an Alertmanager alert's labels don't map to a known
 *  organization/project/environment/service in OpsMind's tenant model. */
public class WebhookMappingException extends RuntimeException {
    public WebhookMappingException(String message) {
        super(message);
    }
}
