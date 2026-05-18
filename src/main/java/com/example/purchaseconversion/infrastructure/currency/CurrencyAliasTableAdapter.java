package com.example.purchaseconversion.infrastructure.currency;

import com.example.purchaseconversion.application.port.out.CurrencyAliasPort;
import com.example.purchaseconversion.domain.CurrencyDescriptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Loads {@code currency-aliases.json} at startup and resolves
 * {ISO-4217 code | case-insensitive Treasury descriptor} → canonical Treasury
 * {@link CurrencyDescriptor} (ADR-0001 D-8).
 *
 * <p>Phase-3 prototype P-1: the canonical Eurozone descriptor is
 * {@code "Euro Zone-Euro"} (note the space). The reader preserves the form
 * Treasury publishes; the alias table maps input variants to it.
 *
 * <p>Refuse-to-start semantics: if the alias resource is absent or fails to parse,
 * {@link #loadAliases()} raises {@link IllegalStateException} during bean init →
 * readiness DOWN per ADR-0001 D-8.
 *
 * <p>Drift detection (AC-021b/c; G4-P1-8): when an input that looks like a valid
 * Treasury descriptor (a hyphen-separated string with no ISO-4217 match in the
 * table) is rejected, the adapter emits a structured {@code currency_alias.drift.detected}
 * INFO log so an operator can update the table on alias-table-drift.
 */
@Component
public class CurrencyAliasTableAdapter implements CurrencyAliasPort {

    private static final Logger LOG = LoggerFactory.getLogger(CurrencyAliasTableAdapter.class);
    private static final Logger DRIFT_LOG = LoggerFactory.getLogger("currency_alias.drift.detected");

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final String aliasResource;

    /** Case-insensitive lookup table; values are the canonical descriptors. */
    private final TreeMap<String, CurrencyDescriptor> aliases = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public CurrencyAliasTableAdapter(
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper,
            @Value("${wex.alias.resource:classpath:currency-aliases.json}") String aliasResource) {
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.aliasResource = Objects.requireNonNull(aliasResource, "aliasResource must not be null");
    }

    @PostConstruct
    void loadAliases() {
        Resource resource = resourceLoader.getResource(aliasResource);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "currency alias resource not found: " + aliasResource);
        }
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            JsonNode list = root.get("aliases");
            if (list == null || !list.isArray()) {
                throw new IllegalStateException(
                        "currency alias resource missing 'aliases' array: " + aliasResource);
            }
            Map<String, CurrencyDescriptor> staged = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (JsonNode entry : list) {
                String input = textOrThrow(entry, "input");
                String canonical = textOrThrow(entry, "canonical");
                staged.put(input, CurrencyDescriptor.of(canonical));
            }
            aliases.putAll(staged);
            LOG.info("currency_alias.loaded count={} resource={}", aliases.size(), aliasResource);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "failed to parse currency alias resource: " + aliasResource, e);
        }
    }

    @Override
    public Optional<CurrencyDescriptor> resolve(String input) {
        Objects.requireNonNull(input, "input must not be null");
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        CurrencyDescriptor hit = aliases.get(trimmed);
        if (hit != null) {
            return Optional.of(hit);
        }
        // Drift detection (AC-021b/c). A descriptor-shaped input that misses the table
        // is the case we want to alert on; a pure ISO-3-letter miss is also logged.
        emitDriftIfPossiblyValid(trimmed);
        return Optional.empty();
    }

    /** Visible for testing — exposes whether an exact key is present. */
    boolean knowsExactly(String input) {
        return aliases.containsKey(Objects.requireNonNull(input, "input must not be null"));
    }

    private void emitDriftIfPossiblyValid(String input) {
        // Hyphen-separated descriptors are the Treasury convention; ISO codes are 3 letters.
        boolean descriptorShaped = input.contains("-") && input.length() >= 5;
        boolean isoShaped = input.length() == 3 && input.chars().allMatch(Character::isLetter);
        if (descriptorShaped || isoShaped) {
            DRIFT_LOG.info("currency_alias.drift.detected input_shape={} input_length={}",
                    descriptorShaped ? "descriptor" : "iso", input.length());
        }
    }

    private static String textOrThrow(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || !v.isTextual() || v.asText().isBlank()) {
            throw new IllegalStateException("alias entry missing '" + field + "': " + node);
        }
        return v.asText();
    }
}
