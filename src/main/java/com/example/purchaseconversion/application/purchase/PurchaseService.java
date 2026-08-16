package com.example.purchaseconversion.application.purchase;

import com.example.purchaseconversion.application.exception.FutureDateException;
import com.example.purchaseconversion.application.exception.PurchaseNotFoundException;
import com.example.purchaseconversion.application.port.in.RegisterPurchaseCommand;
import com.example.purchaseconversion.application.port.in.RegisterPurchaseUseCase;
import com.example.purchaseconversion.application.port.in.RetrievePurchaseUseCase;
import com.example.purchaseconversion.application.port.out.ClockPort;
import com.example.purchaseconversion.application.port.out.PurchaseRepositoryPort;
import com.example.purchaseconversion.domain.Purchase;
import com.example.purchaseconversion.domain.PurchaseId;

import java.util.Objects;

/**
 * Application service for FR-001 (register purchase) and FR-002 (retrieve purchase).
 *
 * <p>Implements both inbound use-case interfaces. Pure POJO — no Spring annotations,
 * no JPA, no I/O. Wiring is the responsibility of {@code config} (Chunk C).
 *
 * <p>Responsibility split:
 * <ul>
 *   <li>{@code register(...)} applies the FR-001 future-date business rule using
 *       {@link ClockPort} (AC-006); the {@link Purchase} constructor handles
 *       primitive invariants (description non-blank, length, amount scale-2,
 *       positivity).</li>
 *   <li>{@code retrieve(...)} maps an empty repository result to
 *       {@link PurchaseNotFoundException}; the API layer maps it to 404.</li>
 * </ul>
 */
public final class PurchaseService implements RegisterPurchaseUseCase, RetrievePurchaseUseCase {

    private final PurchaseRepositoryPort purchaseRepository;
    private final ClockPort clock;

    public PurchaseService(PurchaseRepositoryPort purchaseRepository, ClockPort clock) {
        this.purchaseRepository = Objects.requireNonNull(purchaseRepository, "purchaseRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Purchase register(RegisterPurchaseCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.transactionDate().isAfter(clock.today())) {
            throw new FutureDateException(command.transactionDate());
        }
        System.out.println("test");
        Purchase candidate = new Purchase(
                PurchaseId.next(),
                command.description(),
                command.transactionDate(),
                command.amountUsd());
        return purchaseRepository.save(candidate);
    }

    @Override
    public Purchase retrieve(PurchaseId id) {
        Objects.requireNonNull(id, "id must not be null");
        return purchaseRepository.findById(id)
                .orElseThrow(() -> new PurchaseNotFoundException(id));
    }
}
