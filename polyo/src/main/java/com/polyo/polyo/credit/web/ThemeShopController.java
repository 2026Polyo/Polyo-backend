package com.polyo.polyo.credit.web;

import com.polyo.polyo.common.api.ApiSuccess;
import com.polyo.polyo.credit.port.AuthenticatedUserProvider;
import com.polyo.polyo.credit.service.ThemeShop;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shop/v11")
public class ThemeShopController {
    public record ThemeRequest(@NotBlank @Size(max = 40) String themeId) {}

    private final ThemeShop shop;
    private final AuthenticatedUserProvider users;

    public ThemeShopController(ThemeShop shop, AuthenticatedUserProvider users) {
        this.shop = shop;
        this.users = users;
    }

    @GetMapping("/themes")
    @Operation(summary = "판매 테마 조회")
    public ApiSuccess<ThemeShop.Catalog> catalog(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name,asc") String sort) {
        return ApiSuccess.of(shop.catalog(page, size, sort));
    }

    @GetMapping("/accounts/me")
    @Operation(summary = "상점의 현재 잔액, 보유 테마 및 장착 테마 조회")
    public ApiSuccess<ThemeShop.Account> account() {
        return ApiSuccess.of(shop.getAccount(userId()));
    }

    @PostMapping("/purchases")
    @Operation(summary = "테마 구매", description = "테마 ID만 받는다. 동일 테마는 한 번만 결제한다.")
    public ApiSuccess<ThemeShop.Account> purchase(@Valid @RequestBody ThemeRequest request) {
        return ApiSuccess.of(shop.purchase(userId(), request.themeId()));
    }

    @PutMapping("/equipment/me")
    @Operation(summary = "보유 테마 장착")
    public ApiSuccess<ThemeShop.Account> equip(@Valid @RequestBody ThemeRequest request) {
        return ApiSuccess.of(shop.equip(userId(), request.themeId()));
    }

    private long userId() {
        return Long.parseLong(users.requireUserId());
    }
}
