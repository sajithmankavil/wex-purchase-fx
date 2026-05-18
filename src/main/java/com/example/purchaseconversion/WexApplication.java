package com.example.purchaseconversion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot bootstrap for the WEX Purchase Currency Conversion Service.
 *
 * <p>Component-scan root is {@code com.example.purchaseconversion}. The
 * {@code domain} and {@code application} packages remain framework-free
 * (ArchUnit enforced from A1/A2). Infrastructure adapters are Spring beans
 * scoped under {@code infrastructure} and {@code config}.
 */
@SpringBootApplication
public class WexApplication {

    public static void main(String[] args) {
        SpringApplication.run(WexApplication.class, args);
    }
}
