package com.polyo.polyo.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ProfileRequest(
        @NotBlank @Size(min = 2, max = 20) String nickname,
        @NotBlank @Size(max = 50) String avatarBlockId,
        @NotBlank @Size(max = 30) String avatarBlockColor,
        @NotBlank @Size(max = 30) String avatarBackgroundColor,
        @NotEmpty @Size(max = 10) List<@NotBlank String> equippedBaseBlocks,
        @Size(max = 2) List<@NotBlank String> equippedSpecialBlocks) {
}
