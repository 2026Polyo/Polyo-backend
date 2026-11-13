UPDATE shop_themes AS theme
SET price = new_prices.price
FROM (VALUES
    ('theme_jelly', 300),
    ('theme_neon', 500),
    ('theme_cat', 750),
    ('theme_minecraft', 750)
) AS new_prices(theme_id, price)
WHERE theme.theme_id = new_prices.theme_id;
