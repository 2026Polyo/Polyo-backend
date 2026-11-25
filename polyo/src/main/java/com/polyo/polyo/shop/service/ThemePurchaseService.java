package com.polyo.polyo.shop.service;

import com.polyo.polyo.common.error.ApiException;
import com.polyo.polyo.credit.service.CoinWallet;
import com.polyo.polyo.shop.dto.ThemeAccount;
import com.polyo.polyo.shop.repository.ThemeShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThemePurchaseService {
    private final ThemeShopRepository themes;
    private final ThemeAccountService accounts;
    private final CoinWallet wallet;

    public ThemePurchaseService(ThemeShopRepository themes, ThemeAccountService accounts, CoinWallet wallet) {
        this.themes = themes;
        this.accounts = accounts;
        this.wallet = wallet;
    }

    @Transactional
    public ThemeAccount purchase(long userId, String themeId) {
        int coin = wallet.lockBalance(userId);
        if (themes.owns(userId, themeId)) return accounts.account(userId, coin);

        int price = themes.findPrice(themeId)
                .orElseThrow(() -> new ApiException(ShopError.SHOP_THEME_NOT_FOUND));
        if (coin < price) throw new ApiException(ShopError.SHOP_INSUFFICIENT_COIN);

        themes.recordPurchase(userId, themeId, price);
        wallet.setBalance(userId, coin - price);
        return accounts.account(userId, coin - price);
    }
}
