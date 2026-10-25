package com.polyo.polyo.matchroom;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;

public record JoinByCodeRequest(
        @NotBlank(message = "입장 코드는 필수입니다.")
        @Pattern(regexp = "[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}",
                message = "입장 코드는 허용된 영문과 숫자 8자리여야 합니다.") String entryCode) {
    public JoinByCodeRequest {
        entryCode = entryCode == null ? null : entryCode.strip().toUpperCase(Locale.ROOT);
    }
}
