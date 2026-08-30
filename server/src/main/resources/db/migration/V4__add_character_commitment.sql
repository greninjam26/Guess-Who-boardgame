-- The promise a player made about their character before play began. Null for
-- the computer opponent, which makes none, and for games recorded before
-- commitments existed.
ALTER TABLE game_result_participants ADD COLUMN commitment_hash VARCHAR(64);
ALTER TABLE game_result_participants ADD COLUMN commitment_nonce VARCHAR(64);
