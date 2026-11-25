package com.control360.domain;

public class RuleViolation extends RuntimeException {
    public enum Kind { INVALID, MISSING, CONFLICT, FORBIDDEN }
    private final Kind kind;

    public RuleViolation(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() { return kind; }

    public static RuleViolation missing(String message) { return new RuleViolation(Kind.MISSING, message); }
    public static RuleViolation conflict(String message) { return new RuleViolation(Kind.CONFLICT, message); }
    public static RuleViolation invalid(String message) { return new RuleViolation(Kind.INVALID, message); }
    public static RuleViolation forbidden(String message) { return new RuleViolation(Kind.FORBIDDEN, message); }
}
