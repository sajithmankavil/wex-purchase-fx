package com.example.purchaseconversion.application.exception;

import com.example.purchaseconversion.domain.PurchaseId;

import java.util.Objects;

/**
 * Thrown when no purchase with the given id exists. Maps to HTTP 404 with
 * {@code errorCode = PURCHASE_NOT_FOUND} (component-design.md §4; AC-008).
 */
public final class PurchaseNotFoundException extends DomainException {

    private final PurchaseId id;

    public PurchaseNotFoundException(PurchaseId id) {
        super("purchase not found: " + Objects.requireNonNull(id));
        this.id = id;
    }

    public PurchaseId getId() {
        return id;
    }
}
