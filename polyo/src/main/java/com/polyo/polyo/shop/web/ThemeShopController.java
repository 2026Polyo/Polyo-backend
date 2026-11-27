package com.polyo.polyo.shop.web;

import com.polyo.polyo.common.api.ApiResponse;
import com.polyo.polyo.credit.port.AuthenticatedUserProvider;
import com.polyo.polyo.shop.dto.ThemeAccount;
import com.polyo.polyo.shop.dto.ThemeCatalog;
import com.polyo.polyo.shop.dto.ThemeRequest;
import com.polyo.polyo.shop.service.ThemeAccountService;
import com.polyo.polyo.shop.service.ThemeCatalogService;
import com.polyo.polyo.shop.service.ThemeEquipmentService;
import com.polyo.polyo.shop.service.ThemePurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/shop/v11")
public class ThemeShopController {
    private final ThemeCatalogService catalog;
    private final ThemeAccountService accounts;
    private final ThemePurchaseService purchases;
    private final ThemeEquipmentService equipment;
    private final AuthenticatedUserProvider users;

    public ThemeShopController(ThemeCatalogService catalog, ThemeAccountService accounts,
            ThemePurchaseService purchases, ThemeEquipmentService equipment,
            AuthenticatedUserProvider users) {
        this.catalog = catalog;
        this.accounts = accounts;
        this.purchases = purchases;
        this.equipment = equipment;
        this.users = users;
    }

    @GetMapping("/themes")
    @Operation(summary = "판매 테마 조회")
    public ApiResponse<ThemeCatalog> catalog(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name,asc") String sort) {
        return ApiResponse.success(catalog.catalog(page, size, sort));
    }

    @GetMapping("/accounts/me")
    @Operation(summary = "상점의 현재 잔액, 보유 테마 및 장착 테마 조회")
    public ApiResponse<ThemeAccount> account() {
        return ApiResponse.success(accounts.get(userId()));
    }

    @PostMapping("/purchases")
    @Operation(summary = "테마 구매", description = "테마 ID만 받는다. 동일 테마는 한 번만 결제한다.")
    public ApiResponse<ThemeAccount> purchase(@Valid @RequestBody ThemeRequest request) {
        return ApiResponse.success(purchases.purchase(userId(), request.themeId()));
    }

    @PutMapping("/equipment/me")
    @Operation(summary = "보유 테마 장착")
    public ApiResponse<ThemeAccount> equip(@Valid @RequestBody ThemeRequest request) {
        return ApiResponse.success(equipment.equip(userId(), request.themeId()));
    }

    private long userId() {
        return Long.parseLong(users.requireUserId());
    }
}
