package com.polyo.polyo.shop.repository;

import com.polyo.polyo.shop.domain.Theme;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ThemeShopRepository {
    private final JdbcClient jdbc;

    public ThemeShopRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public int countThemes() {
        return jdbc.sql("SELECT COUNT(*) FROM shop_themes").query(Integer.class).single();
    }

    public List<Theme> findThemes(String order, int page, int size) {
        return jdbc.sql("SELECT theme_id, name, price FROM shop_themes ORDER BY " + order
                        + ", theme_id ASC LIMIT :size OFFSET :offset")
                .param("size", size).param("offset", (long) (page - 1) * size)
                .query((rs, index) -> new Theme(rs.getString("theme_id"), rs.getString("name"), rs.getInt("price")))
                .list();
    }

    public Optional<Integer> findPrice(String themeId) {
        return jdbc.sql("SELECT price FROM shop_themes WHERE theme_id = :themeId")
                .param("themeId", themeId).query(Integer.class).optional();
    }

    public boolean owns(long userId, String themeId) {
        return jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM theme_order_history WHERE user_id = :userId AND theme_id = :themeId)
                """).param("userId", userId).param("themeId", themeId).query(Boolean.class).single();
    }

    public void recordPurchase(long userId, String themeId, int price) {
        jdbc.sql("""
                INSERT INTO theme_order_history (user_id, theme_id, paid_coin)
                VALUES (:userId, :themeId, :price)
                """).param("userId", userId).param("themeId", themeId).param("price", price).update();
    }

    public void equip(long userId, String themeId) {
        jdbc.sql("UPDATE users SET equipped_theme_id = :themeId WHERE id = :userId")
                .param("themeId", themeId).param("userId", userId).update();
    }

    public void clearEquipment(long userId) {
        jdbc.sql("UPDATE users SET equipped_theme_id = NULL WHERE id = :userId")
                .param("userId", userId).update();
    }

    public String equippedThemeId(long userId) {
        return jdbc.sql("SELECT COALESCE(equipped_theme_id, 'theme_classic') FROM users WHERE id = :userId")
                .param("userId", userId).query(String.class).optional().orElse("theme_classic");
    }

    public List<String> findOwnedThemeIds(long userId) {
        return jdbc.sql("SELECT theme_id FROM theme_order_history WHERE user_id = :userId ORDER BY theme_id")
                .param("userId", userId).query(String.class).list();
    }
}
