CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    login_id VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(30) NOT NULL UNIQUE,
    coin INTEGER NOT NULL DEFAULT 0,
    trophy INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS user_game_scores (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    normal_best_score INTEGER NOT NULL DEFAULT 0 CHECK (normal_best_score >= 0),
    hard_best_score INTEGER NOT NULL DEFAULT 0 CHECK (hard_best_score >= 0)
);
