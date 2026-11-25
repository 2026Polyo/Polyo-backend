package com.polyo.polyo.shop.service;

import com.polyo.polyo.credit.service.CoinWallet;
import com.polyo.polyo.shop.dto.ThemeAccount;
import com.polyo.polyo.shop.repository.ThemeShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThemeAccountService {
    private final ThemeShopRepository themes;
    private final CoinWallet wallet;

    public ThemeAccountService(ThemeShopRepository themes, CoinWallet wallet) {
        this.themes = themes;
        this.wallet = wallet;
    }

    @Transactional(readOnly = true)
    public ThemeAccount get(long userId) {
        return account(userId, wallet.balance(userId));
    }

    public ThemeAccount account(long userId, int coin) {
        return new ThemeAccount(coin, themes.findOwnedThemeIds(userId), themes.equippedThemeId(userId));
    }

    @Transactional(readOnly = true)
    public String equippedThemeId(long userId) {
        return themes.equippedThemeId(userId);
    }
}
