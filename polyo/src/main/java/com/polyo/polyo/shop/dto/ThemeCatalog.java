package com.polyo.polyo.shop.dto;

import com.polyo.polyo.shop.domain.Theme;
import java.util.List;

public record ThemeCatalog(List<Theme> items, int page, int size, int totalElements, int totalPages) {}
