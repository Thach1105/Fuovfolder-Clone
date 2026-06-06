package com.fuoverflow.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String value();

    /**
     * When true, unauthenticated visitors may call the endpoint without RBAC lookup.
     * Must still be allowed by {@code SecurityConfig} (typically {@code permitAll} on GET).
     */
    boolean allowAnonymous() default false;
}
