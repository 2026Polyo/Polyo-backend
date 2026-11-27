package com.polyo.polyo.profile;

import com.polyo.polyo.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) { this.profileService = profileService; }

    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> get(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(profileService.get(Long.parseLong(authentication.getName()))));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<ProfileResponse>> getByUserId(@PathVariable long userId) {
        return ResponseEntity.ok(ApiResponse.success(profileService.get(userId)));
    }
    @PatchMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> update(Authentication authentication,
                                                               @Valid @RequestBody ProfileRequest request) {
        long authenticatedUserId = authenticatedUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success(
                profileService.update(authenticatedUserId, authenticatedUserId, request)));
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateByUserId(Authentication authentication,
                                                                       @PathVariable long userId,
                                                                       @Valid @RequestBody ProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                profileService.update(authenticatedUserId(authentication), userId, request)));
    }

    private long authenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ProfileException("UNAUTHENTICATED", "로그인이 필요합니다.", 401);
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ProfileException("UNAUTHENTICATED", "로그인이 필요합니다.", 401);
        }
    }
}
