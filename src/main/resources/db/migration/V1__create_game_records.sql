CREATE TABLE game_records (
    id BIGSERIAL PRIMARY KEY,
    spin_id VARCHAR(36) NOT NULL UNIQUE,
    player_uid VARCHAR(255) NOT NULL,
    game_id VARCHAR(255) NOT NULL,
    bet_amount NUMERIC(19,4) NOT NULL,
    payout_amount NUMERIC(19,4),
    result_summary TEXT,
    status VARCHAR(50) NOT NULL,
    bank_reserve_status VARCHAR(100),
    bank_settle_status VARCHAR(100),
    engine_status VARCHAR(100),
    correlation_id VARCHAR(36),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_game_records_player_uid ON game_records(player_uid);
CREATE INDEX idx_game_records_game_id ON game_records(game_id);
CREATE INDEX idx_game_records_status ON game_records(status);
CREATE INDEX idx_game_records_created_at ON game_records(created_at);
