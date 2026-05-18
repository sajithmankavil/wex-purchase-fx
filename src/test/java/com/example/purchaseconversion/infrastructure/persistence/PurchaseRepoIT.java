package com.example.purchaseconversion.infrastructure.persistence;

import com.example.purchaseconversion.application.port.out.PurchaseRepositoryPort;
import com.example.purchaseconversion.domain.Money;
import com.example.purchaseconversion.domain.Purchase;
import com.example.purchaseconversion.domain.PurchaseId;
import com.example.purchaseconversion.infrastructure.AbstractPostgresIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PurchaseRepoIT extends AbstractPostgresIT {

    @Autowired
    private PurchaseRepositoryPort repository;

    @Test
    @DisplayName("FR-001 — round-trip a purchase through Postgres")
    void savesAndRetrieves() {
        PurchaseId id = PurchaseId.next();
        Purchase original = new Purchase(id, "Coffee", LocalDate.of(2026, 5, 10), Money.of("4.50"));

        Purchase saved = repository.save(original);
        Optional<Purchase> found = repository.findById(id);

        assertThat(saved).isEqualTo(original);
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(id);
        assertThat(found.get().description()).isEqualTo("Coffee");
        assertThat(found.get().transactionDate()).isEqualTo(LocalDate.of(2026, 5, 10));
        assertThat(found.get().amountUsd()).isEqualTo(Money.of("4.50"));
    }

    @Test
    @DisplayName("FR-002 — missing id returns empty")
    void retrieveNotFound() {
        assertThat(repository.findById(PurchaseId.next())).isEmpty();
    }

    @Test
    @DisplayName("PK uniqueness — re-saving same id raises an integrity violation")
    void duplicateIdIsRejected() {
        PurchaseId id = PurchaseId.next();
        Purchase first = new Purchase(id, "Coffee", LocalDate.of(2026, 5, 10), Money.of("4.50"));
        Purchase second = new Purchase(id, "Tea", LocalDate.of(2026, 5, 11), Money.of("3.25"));

        repository.save(first);

        assertThatThrownBy(() -> repository.save(second))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
