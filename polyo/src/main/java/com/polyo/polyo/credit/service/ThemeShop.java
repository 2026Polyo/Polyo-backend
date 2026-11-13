package com.polyo.polyo.credit.service;

import static com.polyo.polyo.common.error.CommonErrorCode.INVALID_REQUEST_PARAM;

import com.polyo.polyo.common.error.ApiException;
import com.polyo.polyo.common.error.ErrorCode;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThemeShop {
    public static final String CLASSIC_THEME = "theme_classic";

    public record Theme(String themeId, String name, int price) {}
    public record Catalog(List<Theme> items, int page, int size, int totalElements, int totalPages) {}
    public record Account(int coin, List<String> ownedThemeIds, String equippedThemeId) {}

    private enum ShopError implements ErrorCode {
        SHOP_THEME_NOT_FOUND(404, "테마를 찾을 수 없습니다."),
        SHOP_INSUFFICIENT_COIN(409, "코인이 부족합니다."),
        SHOP_THEME_NOT_OWNED(403, "보유한 테마만 장착할 수 있습니다.");

        private final int status;
        private final String message;

        ShopError(int status, String message) {
            this.status = status;
            this.message = message;
        }

        public String code() { return name(); }
        public int status() { return status; }
        public String message() { return message; }
    }

    private final JdbcClient jdbc;
    private final CoinWallet wallet;

    public ThemeShop(JdbcClient jdbc, CoinWallet wallet) {
        this.jdbc = jdbc;
        this.wallet = wallet;
    }

    public Catalog catalog(int page, int size, String sort) {
        if (page < 1 || size < 1 || size > 100) throw new ApiException(INVALID_REQUEST_PARAM);
        String order = switch (sort) {
            case "name,asc" -> "name ASC";
            case "price,asc" -> "price ASC";
            case "price,desc" -> "price DESC";
            default -> throw new ApiException(INVALID_REQUEST_PARAM);
        };
        int total = jdbc.sql("SELECT COUNT(*) FROM shop_themes").query(Integer.class).single();
        var items = jdbc.sql("SELECT theme_id, name, price FROM shop_themes ORDER BY " + order
                        + ", theme_id ASC LIMIT :size OFFSET :offset")
                .param("size", size).param("offset", (long) (page - 1) * size)
                .query((rs, index) -> new Theme(rs.getString("theme_id"), rs.getString("name"), rs.getInt("price")))
                .list();
        return new Catalog(items, page, size, total, (total + size - 1) / size);
    }

    @Transactional
    public Account getAccount(long userId) {
        return account(userId, wallet.lockBalance(userId));
    }

    @Transactional
    public Account purchase(long userId, String themeId) {
        int coin = wallet.lockBalance(userId);
        if (owns(userId, themeId)) return account(userId, coin);

        int price = jdbc.sql("SELECT price FROM shop_themes WHERE theme_id = :themeId")
                .param("themeId", themeId).query(Integer.class).optional()
                .orElseThrow(() -> new ApiException(ShopError.SHOP_THEME_NOT_FOUND));
        if (coin < price) throw new ApiException(ShopError.SHOP_INSUFFICIENT_COIN);

        jdbc.sql("""
                INSERT INTO theme_order_history (user_id, theme_id, paid_coin)
                VALUES (:userId, :themeId, :price)
                """).param("userId", userId).param("themeId", themeId).param("price", price).update();
        wallet.setBalance(userId, coin - price);
        return account(userId, coin - price);
    }

    @Transactional
    public Account equip(long userId, String themeId) {
        int coin = wallet.lockBalance(userId);
        if (CLASSIC_THEME.equals(themeId)) {
            jdbc.sql("UPDATE users SET equipped_theme_id = NULL WHERE id = :userId").param("userId", userId).update();
        } else {
            if (!owns(userId, themeId)) throw new ApiException(ShopError.SHOP_THEME_NOT_OWNED);
            jdbc.sql("UPDATE users SET equipped_theme_id = :themeId WHERE id = :userId")
                    .param("themeId", themeId).param("userId", userId).update();
        }
        return account(userId, coin);
    }

    @Transactional
    public Account grantLocalCoins(long userId) {
        long balance = (long) wallet.lockBalance(userId) + 1_000;
        wallet.setBalance(userId, balance);
        return account(userId, (int) balance);
    }


    private boolean owns(long userId, String themeId) {
        return jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM theme_order_history WHERE user_id = :userId AND theme_id = :themeId)
                """).param("userId", userId).param("themeId", themeId).query(Boolean.class).single();
    }

    private Account account(long userId, int coin) {
        String equipped = jdbc.sql("SELECT COALESCE(equipped_theme_id, 'theme_classic') FROM users WHERE id = :userId")
                .param("userId", userId).query(String.class).single();
        var owned = jdbc.sql("SELECT theme_id FROM theme_order_history WHERE user_id = :userId ORDER BY theme_id")
                .param("userId", userId).query(String.class).list();
        return new Account(coin, owned, equipped);
    }
}
