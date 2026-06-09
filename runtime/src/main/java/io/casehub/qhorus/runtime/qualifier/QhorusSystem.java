package io.casehub.qhorus.runtime.qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;

/**
 * Selects the qhorus-internal system-actor CurrentPrincipal implementation.
 * Used by CrossTenantProducer to inject QhorusSystemCurrentPrincipal specifically,
 * without displacing the @DefaultBean mock.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER})
public @interface QhorusSystem {}
