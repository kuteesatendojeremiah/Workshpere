package com.worksphere.common.security;

import com.worksphere.common.error.MissingPermissionException;
import org.springframework.stereotype.Component;

/**
 * The reusable permission check every module calls instead of inventing its own role
 * comparison. Tenant isolation (is this user in this organization at all?) is a separate,
 * earlier check - this only decides what a caller who's already known to belong there is
 * allowed to do.
 */
@Component
public class PermissionChecker {

    public boolean hasPermission(Role role, String permissionKey) {
        return role.hasPermission(permissionKey);
    }

    public void requirePermission(Role role, String permissionKey) {
        if (!hasPermission(role, permissionKey)) {
            throw new MissingPermissionException(permissionKey);
        }
    }
}
