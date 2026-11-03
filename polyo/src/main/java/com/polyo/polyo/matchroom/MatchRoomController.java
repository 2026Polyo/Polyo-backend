package com.polyo.polyo.matchroom;

import com.polyo.polyo.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/match-room")
public class MatchRoomController {
    private final MatchRoomService service;

    public MatchRoomController(MatchRoomService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<MatchRoomListResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal MatchRoomUser user) {
        requireUser(user);
        return ApiResponse.success(service.list(page, size));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MatchRoomResponse>> create(@Valid @RequestBody CreateMatchRoomRequest request,
            @AuthenticationPrincipal MatchRoomUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(request, requireUser(user))));
    }

    @PostMapping("/{roomId}/members")
    public ApiResponse<MatchRoomResponse> joinById(@PathVariable @Positive long roomId,
            @AuthenticationPrincipal MatchRoomUser user) {
        return ApiResponse.success(service.joinById(roomId, requireUser(user)));
    }

    @PostMapping("/code/members")
    public ApiResponse<MatchRoomResponse> joinByCode(@Valid @RequestBody JoinByCodeRequest request,
            @AuthenticationPrincipal MatchRoomUser user) {
        return ApiResponse.success(service.joinByCode(request, requireUser(user)));
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> delete(@PathVariable @Positive long roomId, @AuthenticationPrincipal MatchRoomUser user) {
        service.delete(roomId, requireUser(user));
        return ResponseEntity.noContent().build();
    }

    private MatchRoomUser requireUser(MatchRoomUser user) {
        if (user == null || user.id() == null || user.id().isBlank()
                || user.nickname() == null || user.nickname().isBlank()) {
            throw new MatchRoomException("UNAUTHENTICATED", "인증된 사용자 정보가 필요합니다.", 401);
        }
        return user;
    }
}
