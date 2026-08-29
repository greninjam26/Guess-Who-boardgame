-- Games played before this migration did not record how questions were chosen,
-- and it is not recoverable from the stored history, so the column stays
-- nullable and existing rows keep a null.
ALTER TABLE game_results ADD COLUMN question_mode VARCHAR(20);
