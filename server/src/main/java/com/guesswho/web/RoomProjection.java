package com.guesswho.web;

import com.guesswho.game.GameSnapshot;
import com.guesswho.persistence.RoomRepository;
import com.guesswho.room.RoomState;
import com.guesswho.room.RoomStatus;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a stored game into what one player is allowed to see of it.
 *
 * <p>The one rule: a player is told their own character and never their
 * opponent's. Everything here follows from that, and the reason it is a
 * separate class with one job is so the rule has somewhere to be tested rather
 * than being spread through a controller.</p>
 */
final class RoomProjection {
    private RoomProjection() {
    }

    /**
     * Projects a room for whoever is asking.
     *
     * @param room      the stored room, including the whole game
     * @param accountId the player asking
     * @return what that player may see
     */
    static RoomState forPlayer(RoomRepository.StoredRoom room, long accountId) {
        boolean asking = room.hostAccountId() == accountId;
        String you = asking ? room.hostName() : room.guestName();
        String opponent = asking ? room.guestName() : room.hostName();

        if (room.gameState() == null) {
            //Nobody has joined, so there is no game to say anything about.
            return new RoomState(room.code(), room.status(), you, opponent,
                    null, false, false, false, null, null, null,
                    List.of(), List.of(), null, room.expiresAt());
        }

        GameSnapshot game = RoomService.deserialise(room.gameState());
        //The snapshot's first player is the host, because that is the order the
        //game was started in when the room was joined.
        GameSnapshot.PlayerState yours = asking ? game.firstPlayer() : game.secondPlayer();
        GameSnapshot.PlayerState theirs = asking ? game.secondPlayer() : game.firstPlayer();

        return new RoomState(
                room.code(),
                room.status(),
                you,
                opponent,
                yours.selectedCharacter(),
                //Whether, never which. A client that knows the opponent has
                //chosen can show a tick; one that knows what they chose has
                //won.
                theirs.selectedCharacter() != null,
                isPresent(asking ? room.guestLastSeen() : room.hostLastSeen()),
                yours.isTurn(),
                currentPlayer(game, room),
                //Split in two so each player is told the same fact in the terms
                //that matter to them: one owes an answer, the other is waiting
                //on one.
                waitingOn(game, you) ? game.pendingQuestionText() : null,
                asking(game, you) ? game.pendingQuestionText() : null,
                questions(yours),
                questions(theirs),
                game.winner(),
                room.expiresAt());
    }

    /**
     * How long since a player was heard from before they count as gone.
     *
     * <p>Clients poll every two seconds, so anything past a few missed polls
     * means their game is not open any more. Long enough not to flicker on a
     * slow network; short enough that the person waiting finds out while they
     * still care.</p>
     */
    private static final java.time.Duration PRESENT_WITHIN = java.time.Duration.ofSeconds(15);

    private static boolean isPresent(java.time.Instant lastSeen) {
        //Never heard from counts as absent: a player who has not managed a
        //single request has not arrived.
        return lastSeen != null
                && lastSeen.isAfter(java.time.Instant.now().minus(PRESENT_WITHIN));
    }

    /** True when this player is the one who owes an answer. */
    private static boolean waitingOn(GameSnapshot game, String you) {
        return game.pendingQuestionAsker() != null
                && !game.pendingQuestionAsker().equals(you);
    }

    /** True when this player is the one waiting to be answered. */
    private static boolean asking(GameSnapshot game, String you) {
        return game.pendingQuestionAsker() != null
                && game.pendingQuestionAsker().equals(you);
    }

    private static String currentPlayer(GameSnapshot game, RoomRepository.StoredRoom room) {
        if (room.status() != RoomStatus.IN_PROGRESS) {
            return null;
        }
        return game.firstPlayer().isTurn() ? room.hostName() : room.guestName();
    }

    /**
     * A player's questions and the answers they got.
     *
     * <p>Both sides see both lists. Every one of these was asked and answered
     * out loud in the game this is modelling, so hiding them would be hiding
     * something neither player was ever meant to be without.</p>
     */
    private static List<RoomState.AskedQuestion> questions(GameSnapshot.PlayerState player) {
        List<RoomState.AskedQuestion> asked = new ArrayList<>();
        for (int index = 0; index < player.questionsAsked().size(); index++) {
            asked.add(new RoomState.AskedQuestion(
                    player.questionsAsked().get(index),
                    player.questionAnswers().get(index)));
        }
        return asked;
    }
}
