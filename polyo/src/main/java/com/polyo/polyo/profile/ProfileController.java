package com.polyo.polyo.profile;

import com.polyo.polyo.auth.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) { this.profileService = profileService; }

    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> get(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(profileService.get(Long.parseLong(authentication.getName()))));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> update(Authentication authentication,
                                                               @Valid @RequestBody ProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                profileService.update(Long.parseLong(authentication.getName()), request)));
    }
}
