package com.example.purchaseconversion.application.port.out;

import com.example.purchaseconversion.domain.Purchase;
import com.example.purchaseconversion.domain.PurchaseId;

import java.util.Optional;

/**
 * Outbound port for purchase persistence (component-design.md §1, §3.1).
 *
 * <p>Implemented by {@code PurchaseRepositoryAdapter} in {@code infrastructure.persistence}
 * (Chunk B). Application services depend on this port; they do not depend on JPA, Spring Data,
 * or any specific persistence technology.
 */
public interface PurchaseRepositoryPort {

    /**
     * Persists a new purchase. The returned instance is the canonical post-persist state.
     */
    Purchase save(Purchase purchase);

    /**
     * Retrieves a purchase by id. Returns {@link Optional#empty()} if none exists.
     */
    Optional<Purchase> findById(PurchaseId id);
}
