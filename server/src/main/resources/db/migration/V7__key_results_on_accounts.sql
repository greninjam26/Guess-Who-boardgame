-- Attribute a played game to an account rather than to whatever name was typed.
--
-- Nullable on purpose: guests are a supported way to play, and a guest's game
-- still counts as a game. What changes is that a signed-in player's row on the
-- leaderboard belongs to them, and cannot be claimed by somebody typing their
-- name.
ALTER TABLE game_result_participants ADD COLUMN account_id BIGINT;

ALTER TABLE game_result_participants
    ADD CONSTRAINT fk_participant_account
    FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE SET NULL;

CREATE INDEX idx_participant_account ON game_result_participants (account_id);

-- Everything recorded before this point was played without accounts, so no row
-- can honestly be attributed to one. They are left with a null account_id and
-- therefore drop off the signed-in leaderboard, which is the truthful outcome:
-- guessing which account an old 'Player 1' belonged to would put somebody
-- else's games on somebody's record.
