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
     * @param now       the moment to judge presence against
     * @return what that player may see
     */
    static RoomState forPlayer(
            RoomRepository.StoredRoom room, long accountId, java.time.Instant now) {
        boolean asking = room.hostAccountId() == accountId;
        String you = asking ? room.hostName() : room.guestName();
        String opponent = asking ? room.guestName() : room.hostName();

        if (room.gameState() == null) {
            //Nobody has joined, so there is no game to say anything about.
            return RoomState.builder()
                    .code(room.code())
                    .status(room.status())
                    .you(you)
                    .opponent(opponent)
                    .expiresAt(room.expiresAt())
                    .build();
        }

        GameSnapshot game = RoomService.deserialise(room.gameState());
        //The snapshot's first player is the host, because that is the order the
        //game was started in when the room was joined.
        GameSnapshot.PlayerState yours = asking ? game.firstPlayer() : game.secondPlayer();
        GameSnapshot.PlayerState theirs = asking ? game.secondPlayer() : game.firstPlayer();

        return RoomState.builder()
                .code(room.code())
                .status(room.status())
                .you(you)
                .opponent(opponent)
                .yourCharacter(yours.selectedCharacter())
                //Whether, never which. A client that knows the opponent has
                //chosen can show a tick; one that knows what they chose has
                //won.
                .opponentHasChosen(theirs.selectedCharacter() != null)
                .opponentPresent(Presence.isPresent(
                        asking ? room.guestLastSeen() : room.hostLastSeen(), now))
                .yourTurn(yours.isTurn())
                .currentPlayer(currentPlayer(game, room))
                //Split in two so each player is told the same fact in the terms
                //that matter to them: one owes an answer, the other is waiting
                //on one.
                .questionAwaitingYourAnswer(
                        waitingOn(game, you) ? game.pendingQuestionText() : null)
                .yourUnansweredQuestion(asking(game, you) ? game.pendingQuestionText() : null)
                .yourQuestions(questions(yours))
                .opponentQuestions(questions(theirs))
                .winner(game.winner())
                .expiresAt(room.expiresAt())
                .build();
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
