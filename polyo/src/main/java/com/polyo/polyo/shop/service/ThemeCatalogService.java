package com.polyo.polyo.shop.service;

import static com.polyo.polyo.common.error.CommonErrorCode.INVALID_REQUEST_PARAM;

import com.polyo.polyo.common.error.ApiException;
import com.polyo.polyo.shop.dto.ThemeCatalog;
import com.polyo.polyo.shop.repository.ThemeShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThemeCatalogService {
    private final ThemeShopRepository themes;

    public ThemeCatalogService(ThemeShopRepository themes) {
        this.themes = themes;
    }

    @Transactional(readOnly = true)
    public ThemeCatalog catalog(int page, int size, String sort) {
        if (page < 1 || size < 1 || size > 100) throw new ApiException(INVALID_REQUEST_PARAM);
        String order = switch (sort) {
            case "name,asc" -> "name ASC";
            case "price,asc" -> "price ASC";
            case "price,desc" -> "price DESC";
            default -> throw new ApiException(INVALID_REQUEST_PARAM);
        };
        int total = themes.countThemes();
        return new ThemeCatalog(themes.findThemes(order, page, size), page, size, total,
                (total + size - 1) / size);
    }
}
