package com.example.purchaseconversion.application.purchase;

import com.example.purchaseconversion.application.exception.FutureDateException;
import com.example.purchaseconversion.application.exception.PurchaseNotFoundException;
import com.example.purchaseconversion.application.port.in.RegisterPurchaseCommand;
import com.example.purchaseconversion.application.port.out.ClockPort;
import com.example.purchaseconversion.application.port.out.PurchaseRepositoryPort;
import com.example.purchaseconversion.domain.Money;
import com.example.purchaseconversion.domain.Purchase;
import com.example.purchaseconversion.domain.PurchaseId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 17);

    @Mock
    private PurchaseRepositoryPort purchaseRepository;
    @Mock
    private ClockPort clock;

    private PurchaseService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseService(purchaseRepository, clock);
    }

    @Nested
    @DisplayName("register (FR-001; AC-001..AC-006)")
    class Register {

        @Test
        @DisplayName("happy path — past transactionDate: persists and returns saved")
        void registersPastDated() {
            when(clock.today()).thenReturn(TODAY);
            RegisterPurchaseCommand command = new RegisterPurchaseCommand(
                    "Coffee", LocalDate.of(2026, 5, 10), Money.of("4.50"));
            ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
            when(purchaseRepository.save(captor.capture()))
                    .thenAnswer(inv -> captor.getValue());

            Purchase result = service.register(command);

            assertThat(result.description()).isEqualTo("Coffee");
            assertThat(result.transactionDate()).isEqualTo(LocalDate.of(2026, 5, 10));
            assertThat(result.amountUsd()).isEqualTo(Money.of("4.50"));
            assertThat(result.id()).isNotNull();
            assertThat(result.id().value().version()).isEqualTo(7);
            verify(purchaseRepository).save(any(Purchase.class));
        }

        @Test
        @DisplayName("happy path — transactionDate == today is accepted (AC-005)")
        void registersTodayDated() {
            when(clock.today()).thenReturn(TODAY);
            RegisterPurchaseCommand command = new RegisterPurchaseCommand(
                    "Today's coffee", TODAY, Money.of("3.25"));
            when(purchaseRepository.save(any(Purchase.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Purchase result = service.register(command);

            assertThat(result.transactionDate()).isEqualTo(TODAY);
        }

        @Test
        @DisplayName("AC-006 — rejects transactionDate strictly after today")
        void rejectsFutureDate() {
            when(clock.today()).thenReturn(TODAY);
            LocalDate tomorrow = TODAY.plusDays(1);
            RegisterPurchaseCommand command = new RegisterPurchaseCommand(
                    "Future coffee", tomorrow, Money.of("4.00"));

            assertThatThrownBy(() -> service.register(command))
                    .isInstanceOf(FutureDateException.class)
                    .satisfies(t -> assertThat(((FutureDateException) t).getTransactionDate()).isEqualTo(tomorrow));

            verify(purchaseRepository, never()).save(any());
        }

        @Test
        @DisplayName("AC-006 — rejects transactionDate one year in the future")
        void rejectsFarFutureDate() {
            when(clock.today()).thenReturn(TODAY);
            RegisterPurchaseCommand command = new RegisterPurchaseCommand(
                    "Far future", TODAY.plusYears(1), Money.of("100.00"));

            assertThatThrownBy(() -> service.register(command))
                    .isInstanceOf(FutureDateException.class);
            verify(purchaseRepository, never()).save(any());
        }

        @Test
        @DisplayName("AC-002 — 50-char description is accepted (delegated to Purchase invariant)")
        void accepts50CharDescription() {
            when(clock.today()).thenReturn(TODAY);
            String exactly50 = "A".repeat(50);
            RegisterPurchaseCommand command = new RegisterPurchaseCommand(
                    exactly50, LocalDate.of(2026, 5, 10), Money.of("4.50"));
            when(purchaseRepository.save(any(Purchase.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Purchase result = service.register(command);

            assertThat(result.description()).hasSize(50);
        }

        @Test
        @DisplayName("AC-003 — 51-char description is rejected via Purchase invariant")
        void rejects51CharDescription() {
            when(clock.today()).thenReturn(TODAY);
            String tooLong = "A".repeat(51);
            RegisterPurchaseCommand command = new RegisterPurchaseCommand(
                    tooLong, LocalDate.of(2026, 5, 10), Money.of("4.50"));

            assertThatThrownBy(() -> service.register(command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("description length");
            verify(purchaseRepository, never()).save(any());
        }

        @Test
        @DisplayName("issued id is UUID v7 and unique per call")
        void issuesUniqueV7Ids() {
            when(clock.today()).thenReturn(TODAY);
            RegisterPurchaseCommand command = new RegisterPurchaseCommand(
                    "Test", TODAY, Money.of("1.00"));
            when(purchaseRepository.save(any(Purchase.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Purchase first = service.register(command);
            Purchase second = service.register(command);

            assertThat(first.id()).isNotEqualTo(second.id());
            assertThat(first.id().value().version()).isEqualTo(7);
            assertThat(second.id().value().version()).isEqualTo(7);
        }

        @Test
        @DisplayName("null command is rejected")
        void rejectsNullCommand() {
            assertThatThrownBy(() -> service.register(null))
                    .isInstanceOf(NullPointerException.class);
            verify(purchaseRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("retrieve (FR-002; AC-007..AC-009)")
    class Retrieve {

        @Test
        @DisplayName("AC-007 — happy path returns the stored purchase")
        void returnsStored() {
            PurchaseId id = PurchaseId.next();
            Purchase stored = new Purchase(id, "Coffee", LocalDate.of(2026, 5, 10), Money.of("4.50"));
            when(purchaseRepository.findById(id)).thenReturn(Optional.of(stored));

            Purchase result = service.retrieve(id);

            assertThat(result).isSameAs(stored);
        }

        @Test
        @DisplayName("AC-008 — missing id raises PurchaseNotFoundException")
        void raisesNotFound() {
            PurchaseId id = PurchaseId.next();
            when(purchaseRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.retrieve(id))
                    .isInstanceOf(PurchaseNotFoundException.class)
                    .satisfies(t -> assertThat(((PurchaseNotFoundException) t).getId()).isEqualTo(id));
        }

        @Test
        @DisplayName("null id is rejected")
        void rejectsNullId() {
            assertThatThrownBy(() -> service.retrieve(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
