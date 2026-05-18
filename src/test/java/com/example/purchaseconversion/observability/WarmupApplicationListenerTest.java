package com.example.purchaseconversion.observability;

import com.example.purchaseconversion.application.port.out.CurrencyAliasPort;
import com.example.purchaseconversion.application.port.out.TreasuryClientPort;
import com.example.purchaseconversion.domain.CurrencyDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WarmupApplicationListenerTest {

    @Test
    @DisplayName("warmOne — resolved currency triggers TreasuryClient.fetchRates")
    void warmsResolved() {
        TreasuryClientPort treasury = mock(TreasuryClientPort.class);
        CurrencyAliasPort alias = mock(CurrencyAliasPort.class);
        when(alias.resolve("CAD")).thenReturn(Optional.of(CurrencyDescriptor.of("Canada-Dollar")));
        when(treasury.fetchRates(any(), any(), any())).thenReturn(List.of());

        WarmupApplicationListener listener = new WarmupApplicationListener(treasury, alias, true);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        listener.warmOne("CAD", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 17), ok, fail);

        verify(treasury).fetchRates(CurrencyDescriptor.of("Canada-Dollar"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 17));
        assertThat(ok.get()).isEqualTo(1);
        assertThat(fail.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("warmOne — alias miss increments fail and skips Treasury")
    void aliasMiss() {
        TreasuryClientPort treasury = mock(TreasuryClientPort.class);
        CurrencyAliasPort alias = mock(CurrencyAliasPort.class);
        when(alias.resolve(anyString())).thenReturn(Optional.empty());

        WarmupApplicationListener listener = new WarmupApplicationListener(treasury, alias, true);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        listener.warmOne("ZZZ", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 17), ok, fail);

        verify(treasury, never()).fetchRates(any(), any(), any());
        assertThat(ok.get()).isEqualTo(0);
        assertThat(fail.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("warmOne — Treasury failure is swallowed and tracked as fail")
    void treasuryFailureSwallowed() {
        TreasuryClientPort treasury = mock(TreasuryClientPort.class);
        CurrencyAliasPort alias = mock(CurrencyAliasPort.class);
        when(alias.resolve(anyString())).thenReturn(Optional.of(CurrencyDescriptor.of("Canada-Dollar")));
        when(treasury.fetchRates(any(), any(), any())).thenThrow(new RuntimeException("upstream-down"));

        WarmupApplicationListener listener = new WarmupApplicationListener(treasury, alias, true);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        // Must not throw — warm-up is fire-and-forget.
        listener.warmOne("CAD", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 17), ok, fail);

        assertThat(ok.get()).isEqualTo(0);
        assertThat(fail.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("disabled listener is a no-op on ApplicationReadyEvent")
    void disabled() {
        TreasuryClientPort treasury = mock(TreasuryClientPort.class);
        CurrencyAliasPort alias = mock(CurrencyAliasPort.class);

        WarmupApplicationListener listener = new WarmupApplicationListener(treasury, alias, false);
        listener.warmUp();

        verify(treasury, never()).fetchRates(any(), any(), any());
        verify(alias, never()).resolve(anyString());
    }

    @Test
    @DisplayName("top-10 currencies enumeration carries USD + EUR + GBP + JPY + CAD + AUD + CHF + CNY + INR")
    void topTenList() {
        assertThat(WarmupApplicationListener.warmUpCurrencies())
                .contains("USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "INR");
    }
}
