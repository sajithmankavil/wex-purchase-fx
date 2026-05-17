package com.example.purchaseconversion.application.exception;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Thrown by {@code PurchaseService.register(...)} when the request's
 * {@code transactionDate} is strictly after "today" per the injected {@code ClockPort}.
 *
 * <p>Maps to HTTP 422 with {@code errorCode = FUTURE_DATE} (component-design.md §4; AC-006).
 */
public final class FutureDateException extends DomainException {

    private final LocalDate transactionDate;

    public FutureDateException(LocalDate transactionDate) {
        super("transactionDate must not be in the future; got " + Objects.requireNonNull(transactionDate));
        this.transactionDate = transactionDate;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }
}
