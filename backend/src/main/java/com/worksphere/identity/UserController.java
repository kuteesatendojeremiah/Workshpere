package com.worksphere.identity;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Profile view/update, restricted to the authenticated caller viewing/editing their own profile. */
@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/api/v1/users/{userId}")
    public UserProfileResponse getProfile(
            @PathVariable UUID userId, @AuthenticationPrincipal UUID authenticatedUserId) {
        requireSelf(userId, authenticatedUserId);
        return UserProfileResponse.from(userService.getProfile(userId));
    }

    @PutMapping("/api/v1/users/{userId}")
    public UserProfileResponse updateProfile(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UUID authenticatedUserId,
            @Valid @RequestBody UpdateProfileRequest request) {
        requireSelf(userId, authenticatedUserId);
        return UserProfileResponse.from(userService.updateProfile(userId, request.displayName()));
    }

    private void requireSelf(UUID pathUserId, UUID authenticatedUserId) {
        if (!pathUserId.equals(authenticatedUserId)) {
            throw new AccessDeniedException("You may only access your own profile");
        }
    }
}
