package com.polyo.polyo.profile;

import com.polyo.polyo.auth.User;
import com.polyo.polyo.auth.UserRepository;
import com.polyo.polyo.score.UserGameScore;
import com.polyo.polyo.score.UserGameScoreRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final UserRepository userRepository;
    private final UserGameScoreRepository scoreRepository;

    public ProfileService(UserRepository userRepository, UserGameScoreRepository scoreRepository) {
        this.userRepository = userRepository;
        this.scoreRepository = scoreRepository;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(long userId) {
        User user = user(userId);
        UserGameScore scores = scoreRepository.findByUserId(userId).orElse(new UserGameScore(userId));
        return response(user, scores);
    }

    @Transactional
    public ProfileResponse update(long userId, ProfileRequest request) {
        return update(userId, userId, request);
    }

    @Transactional
    public ProfileResponse update(long authenticatedUserId, long targetUserId, ProfileRequest request) {
        if (authenticatedUserId != targetUserId) {
            throw new ProfileException("FORBIDDEN", "본인 프로필만 수정할 수 있습니다.", 403);
        }
        User user = user(targetUserId);
        if (userRepository.existsByNickname(request.nickname()) && !user.getNickname().equals(request.nickname())) {
            throw new ProfileException("DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다.", 409);
        }
        try {
            user.updateProfile(request.nickname().strip(), request.avatarBlockId().strip(),
                    request.avatarBlockColor().strip(), request.avatarBackgroundColor().strip(),
                    request.equippedBaseBlocks(), request.equippedSpecialBlocks() == null
                            ? List.of() : request.equippedSpecialBlocks());
            UserGameScore scores = scoreRepository.findByUserId(targetUserId).orElse(new UserGameScore(targetUserId));
            return response(user, scores);
        } catch (DataIntegrityViolationException exception) {
            throw new ProfileException("DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다.", 409);
        }
    }

    private User user(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ProfileException("UNAUTHENTICATED", "로그인이 필요합니다.", 401));
    }

    private ProfileResponse response(User user, UserGameScore scores) {
        return new ProfileResponse(String.valueOf(user.getId()), user.getLoginId(), user.getNickname(),
                scores.getNormalBestScore(), scores.getHardBestScore(), user.getTrophy(),
                user.getEquippedBaseBlockList(), user.getEquippedSpecialBlockList(),
                user.getAvatarBlockId(), user.getAvatarBlockColor(), user.getAvatarBackgroundColor());
    }
}
