package com.polyo.polyo.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ThemeRequest(@NotBlank @Size(max = 40) String themeId) {}
