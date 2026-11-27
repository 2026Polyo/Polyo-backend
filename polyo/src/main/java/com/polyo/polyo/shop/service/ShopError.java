package com.polyo.polyo.shop.service;

import com.polyo.polyo.common.error.ErrorCode;

enum ShopError implements ErrorCode {
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
