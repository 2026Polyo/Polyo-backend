ALTER TABLE users ADD COLUMN avatar_block_id VARCHAR(50) NOT NULL DEFAULT 'tetromino-t-down';
ALTER TABLE users ADD COLUMN avatar_block_color VARCHAR(30) NOT NULL DEFAULT 'cyan';
ALTER TABLE users ADD COLUMN avatar_background_color VARCHAR(30) NOT NULL DEFAULT '#d9f4ff';
ALTER TABLE users ADD COLUMN equipped_base_blocks VARCHAR(1000) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN equipped_special_blocks VARCHAR(1000) NOT NULL DEFAULT '';
