package com.polyo.polyo.shop.dto;

import java.util.List;

public record ThemeAccount(int coin, List<String> ownedThemeIds, String equippedThemeId) {}
