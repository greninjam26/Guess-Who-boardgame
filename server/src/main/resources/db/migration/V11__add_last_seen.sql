-- When each player was last heard from.
--
-- Without this, somebody who has closed their laptop and somebody who is
-- thinking hard look exactly the same to their opponent: a turn that does not
-- come. The waiting player has no way to tell whether to keep waiting, and the
-- game sits there until it expires half an hour later.
--
-- Every request a player makes counts, polling included, so a client that is
-- open and watching keeps its player present without them doing anything.
ALTER TABLE game_rooms ADD COLUMN host_last_seen TIMESTAMP;
ALTER TABLE game_rooms ADD COLUMN guest_last_seen TIMESTAMP;
