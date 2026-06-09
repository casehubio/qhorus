package io.casehub.qhorus.api.qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;

/**
 * Marks injection points that require cross-tenant data access.
 * Produced by {@code CrossTenantProducer} — only injectable by classes
 * that have been explicitly granted cross-tenant access.
 * See protocol PP-20260520-e6a5f0.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER})
public @interface CrossTenant {}
