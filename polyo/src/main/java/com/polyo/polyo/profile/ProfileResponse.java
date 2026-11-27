package com.polyo.polyo.profile;

import java.util.List;

public record ProfileResponse(
        String userId,
        String loginId,
        String nickname,
        int normalBestScore,
        int hardBestScore,
        int trophyCount,
        List<String> equippedBaseBlocks,
        List<String> equippedSpecialBlocks,
        String avatarBlockId,
        String avatarBlockColor,
        String avatarBackgroundColor) {
}
