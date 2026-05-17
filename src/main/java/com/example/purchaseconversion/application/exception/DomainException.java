package com.example.purchaseconversion.application.exception;

/**
 * Abstract base for application-layer exceptions that map deterministically to HTTP
 * responses via {@code ProblemDetailsExceptionHandler} (component-design.md §4; Chunk C).
 *
 * <p>Each concrete subclass corresponds to a stable {@code errorCode} + HTTP status pair
 * in the error decision table (api-contracts.md §Error Decision Table). The decision
 * table is the contract; this class hierarchy is the implementation of it.
 *
 * <p>The package is {@code application.exception} (sub-package of {@code application})
 * so that the ArchUnit rule {@code applicationOnlyDependsOnDomainAndJdk} continues to
 * pass without modification. The design-doc §1 layout shows a top-level {@code exception/}
 * package; the chosen location is functionally equivalent and avoids an ArchUnit rule
 * change in A2.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
