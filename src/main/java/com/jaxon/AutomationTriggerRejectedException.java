package com.jaxon;

public final class AutomationTriggerRejectedException extends RuntimeException {

    public enum Reason {
        INVALID,
        CONFLICT
    }

    private final Reason reason;

    public AutomationTriggerRejectedException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
