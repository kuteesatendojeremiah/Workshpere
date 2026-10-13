package com.worksphere.common.security;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link Role} and {@link Permission} are deliberately read-only from Java's perspective (seeded
 * by migration, no public constructor) - this builds throwaway instances for unit tests that
 * don't want to spin up a real database.
 */
public final class TestRoles {

    private TestRoles() {
    }

    public static Role role(String name, String... permissionKeys) {
        Role role = new Role();
        ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(role, "name", name);
        ReflectionTestUtils.setField(role, "description", name);
        ReflectionTestUtils.setField(role, "permissions", permissions(permissionKeys));
        return role;
    }

    private static Set<Permission> permissions(String... keys) {
        return Arrays.stream(keys).map(TestRoles::permission).collect(Collectors.toSet());
    }

    private static Permission permission(String key) {
        Permission permission = new Permission();
        ReflectionTestUtils.setField(permission, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(permission, "key", key);
        ReflectionTestUtils.setField(permission, "description", key);
        return permission;
    }
}
