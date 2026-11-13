package com.polyo.polyo.credit.web;

import com.polyo.polyo.common.api.ApiSuccess;
import com.polyo.polyo.credit.port.AuthenticatedUserProvider;
import com.polyo.polyo.credit.service.ThemeShop;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("local-coin-grants")
@RestController
public class LocalCoinGrantController {
    private final ThemeShop shop;
    private final AuthenticatedUserProvider users;

    public LocalCoinGrantController(ThemeShop shop, AuthenticatedUserProvider users) {
        this.shop = shop;
        this.users = users;
    }

    @PostMapping("/api/shop/v11/local-coin-grants")
    @Operation(summary = "로컬 개발 계정에 테스트 코인 1000개 지급")
    public ApiSuccess<ThemeShop.Account> grant() {
        return ApiSuccess.of(shop.grantLocalCoins(Long.parseLong(users.requireUserId())));
    }
}
