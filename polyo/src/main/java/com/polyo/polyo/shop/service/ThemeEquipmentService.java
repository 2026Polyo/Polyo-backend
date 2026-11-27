package com.polyo.polyo.shop.service;

import com.polyo.polyo.common.error.ApiException;
import com.polyo.polyo.shop.dto.ThemeAccount;
import com.polyo.polyo.shop.repository.ThemeShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThemeEquipmentService {
    public static final String CLASSIC_THEME = "theme_classic";

    private final ThemeShopRepository themes;
    private final ThemeAccountService accounts;

    public ThemeEquipmentService(ThemeShopRepository themes, ThemeAccountService accounts) {
        this.themes = themes;
        this.accounts = accounts;
    }

    @Transactional
    public ThemeAccount equip(long userId, String themeId) {
        if (!CLASSIC_THEME.equals(themeId) && !themes.owns(userId, themeId)) {
            throw new ApiException(ShopError.SHOP_THEME_NOT_OWNED);
        }
        if (CLASSIC_THEME.equals(themeId)) themes.clearEquipment(userId);
        else themes.equip(userId, themeId);
        return accounts.get(userId);
    }
}
