package com.example.purchaseconversion.application.port.in;

import com.example.purchaseconversion.application.exception.PurchaseNotFoundException;
import com.example.purchaseconversion.domain.Purchase;
import com.example.purchaseconversion.domain.PurchaseId;

/**
 * Inbound use-case interface for purchase retrieval (FR-002; AC-007..AC-009).
 *
 * <p>Implemented by {@code application.purchase.PurchaseService}.
 */
public interface RetrievePurchaseUseCase {

    /**
     * Retrieves the purchase with the given id.
     *
     * @throws PurchaseNotFoundException if no purchase with that id exists
     */
    Purchase retrieve(PurchaseId id);
}
