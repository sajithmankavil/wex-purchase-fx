package com.example.purchaseconversion.application.port.in;

import com.example.purchaseconversion.application.exception.FutureDateException;
import com.example.purchaseconversion.domain.Purchase;

/**
 * Inbound use-case interface for purchase registration (FR-001; AC-001..AC-006).
 *
 * <p>Implemented by {@code application.purchase.PurchaseService}. Consumed by the API
 * layer (Chunk C) which depends on this interface, not on the service class.
 */
public interface RegisterPurchaseUseCase {

    /**
     * Registers a new purchase with a fresh server-generated {@code PurchaseId}.
     *
     * @throws FutureDateException if {@code command.transactionDate()} is after "today" per
     *                             the injected {@code ClockPort} (AC-006)
     */
    Purchase register(RegisterPurchaseCommand command);
}
