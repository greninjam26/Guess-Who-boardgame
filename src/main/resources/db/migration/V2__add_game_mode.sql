ALTER TABLE game_results ADD COLUMN mode VARCHAR(20);
ALTER TABLE game_results ADD COLUMN difficulty VARCHAR(20);

-- Games played before this migration did not record a mode. A participant
-- named 'AI' identifies a player-versus-computer game; everything else was
-- played on one machine. Difficulty is not recoverable and stays null.
UPDATE game_results SET mode = 'PVE'
WHERE id IN (
    SELECT game_result_id
    FROM game_result_participants
    WHERE name = 'AI'
);
UPDATE game_results SET mode = 'PVP_LOCAL' WHERE mode IS NULL;

ALTER TABLE game_results ALTER COLUMN mode SET NOT NULL;
