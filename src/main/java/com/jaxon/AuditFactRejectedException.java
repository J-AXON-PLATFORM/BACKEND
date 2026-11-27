package com.jaxon;

/** Raised when an audit fact violates the closed catalog or conflicts with a recorded event. */
public final class AuditFactRejectedException extends RuntimeException {

    private final Reason reason;

    public AuditFactRejectedException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        INVALID,
        CONFLICT
    }
}
