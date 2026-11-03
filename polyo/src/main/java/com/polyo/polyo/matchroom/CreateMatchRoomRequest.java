package com.polyo.polyo.matchroom;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMatchRoomRequest(
        @NotBlank(message = "방 이름은 필수입니다.")
        @Size(max = 30, message = "방 이름은 30자 이하여야 합니다.") String roomName,
        @NotNull(message = "공개 여부는 필수입니다.") Boolean isPrivate) {
    public CreateMatchRoomRequest {
        roomName = roomName == null ? null : roomName.strip();
    }
}
