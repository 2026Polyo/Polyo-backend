package com.polyo.polyo.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Getter
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, unique = true, length = 30)
    private String nickname;

    @Column(nullable = false)
    private int coin = 0;

    @Column(nullable = false)
    private int trophy = 0;

    @Column(name = "avatar_block_id", nullable = false, length = 50)
    private String avatarBlockId = "tetromino-t-down";

    @Column(name = "avatar_block_color", nullable = false, length = 30)
    private String avatarBlockColor = "cyan";

    @Column(name = "avatar_background_color", nullable = false, length = 30)
    private String avatarBackgroundColor = "#d9f4ff";

    @Column(name = "equipped_base_blocks", nullable = false, length = 1000)
    private String equippedBaseBlocks = "";

    @Column(name = "equipped_special_blocks", nullable = false, length = 1000)
    private String equippedSpecialBlocks = "";

    protected User() {
    }

    private User(String loginId, String password, String nickname) {
        this.loginId = loginId;
        this.password = password;
        this.nickname = nickname;
    }

    public static User create(String loginId, String password, String nickname) {
        return new User(loginId, password, nickname);
    }

    public void updateProfile(String nickname, String avatarBlockId, String avatarBlockColor,
                              String avatarBackgroundColor, List<String> baseBlocks,
                              List<String> specialBlocks) {
        this.nickname = nickname;
        this.avatarBlockId = avatarBlockId;
        this.avatarBlockColor = avatarBlockColor;
        this.avatarBackgroundColor = avatarBackgroundColor;
        this.equippedBaseBlocks = join(baseBlocks);
        this.equippedSpecialBlocks = join(specialBlocks);
    }

    public List<String> getEquippedBaseBlockList() { return split(equippedBaseBlocks); }
    public List<String> getEquippedSpecialBlockList() { return split(equippedSpecialBlocks); }

    private static String join(List<String> values) {
        return String.join(",", values == null ? List.of() : values);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).filter(item -> !item.isBlank()).collect(Collectors.toList());
    }

}
