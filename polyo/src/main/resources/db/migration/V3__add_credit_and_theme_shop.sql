CREATE TABLE hard_mode_games (
    game_id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    state TEXT NOT NULL,
    score INTEGER NOT NULL DEFAULT 0 CHECK (score >= 0),
    finished BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE game_reward_settlements (
    game_id VARCHAR(36) PRIMARY KEY REFERENCES hard_mode_games(game_id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reward_id VARCHAR(36) NOT NULL UNIQUE,
    earned_coin INTEGER NOT NULL CHECK (earned_coin >= 0),
    coin_after_reward INTEGER NOT NULL CHECK (coin_after_reward >= 0)
);

CREATE TABLE shop_themes (
    theme_id VARCHAR(40) PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    price INTEGER NOT NULL CHECK (price >= 0)
);

INSERT INTO shop_themes (theme_id, name, price) VALUES
    ('theme_neon', '네온 나이트', 1200),
    ('theme_jelly', '젤리 팝', 1200),
    ('theme_minecraft', '마인크래프트', 1200),
    ('theme_cat', '고양이 드림', 1200);

CREATE TABLE theme_order_history (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    theme_id VARCHAR(40) NOT NULL REFERENCES shop_themes(theme_id),
    paid_coin INTEGER NOT NULL CHECK (paid_coin >= 0),
    ordered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, theme_id)
);

ALTER TABLE users ADD COLUMN equipped_theme_id VARCHAR(40) REFERENCES shop_themes(theme_id);
