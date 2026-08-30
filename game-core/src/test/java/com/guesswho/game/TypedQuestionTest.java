package com.guesswho.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TypedQuestionTest {
    private TypedQuestion typed;

    @BeforeEach
    void loadBoard() throws Exception {
        typed = new TypedQuestion(new Board().getQuestionsList());
    }

    @Test
    void recognisesAnAttributeAskedPlainly() {
        assertEquals("Does your character wear glasses?", resolved("do they wear glasses?"));
    }

    @Test
    void recognisesAWordForTheSameThing() {
        assertEquals("Does your character wear glasses?", resolved("is he wearing specs"));
        assertEquals("Does your character have facial hair?", resolved("does she have a beard"));
        assertEquals("Is the person wearing a hat?", resolved("any headwear?"));
        assertEquals("Does the person have an ear piercing?", resolved("do they have earrings"));
    }

    @Test
    void picksTheValueWithinACategory() {
        assertEquals("Is your character's eye colour blue?", resolved("are their eyes blue"));
        assertEquals("Is your character's hair colour ginger?", resolved("do they have red hair"));
        assertEquals("Does the person have short hair?", resolved("is their hair short"));
    }

    @Test
    void refusesAColourThatCouldBeEitherEyesOrHair() {
        assertTrue(typed.resolve("are they brown?").isEmpty(),
                "Brown is an eye colour and a hair colour, so this has no single answer");
    }

    @Test
    void resolvesBrownOnceTheAttributeIsNamed() {
        assertEquals("Is your character's hair colour brown?", resolved("is their hair brown"));
        assertEquals("Is your character's eye colour brown?", resolved("are their eyes brown"));
    }

    @Test
    void refusesSomethingTheBoardKnowsNothingAbout() {
        assertTrue(typed.resolve("do they look friendly?").isEmpty());
        assertTrue(typed.resolve("are they tall?").isEmpty());
    }

    @Test
    void refusesTwoAttributesAtOnce() {
        assertTrue(typed.resolve("do they wear glasses and a hat?").isEmpty(),
                "Answering one of two questions asked would mislead about the other");
    }

    @Test
    void refusesNothingAtAll() {
        assertTrue(typed.resolve("").isEmpty());
        assertTrue(typed.resolve("   ").isEmpty());
        assertTrue(typed.resolve(null).isEmpty());
    }

    @Test
    void matchesWholeWordsOnly() {
        assertTrue(typed.resolve("is that the one?").isEmpty(),
                "'hat' sits inside 'that', and matching it would answer a question nobody asked");
    }

    @Test
    void ignoresCapitals() {
        assertEquals("Does your character wear glasses?", resolved("DOES THEY WEAR GLASSES"));
    }

    @Test
    void everyResolvedQuestionIsOneTheBoardCanAnswer() throws Exception {
        Board board = new Board();

        Optional<Question> match = new TypedQuestion(board.getQuestionsList())
                .resolve("is the person bald");

        assertTrue(match.isPresent());
        assertEquals(match.orElseThrow().getQuestion(),
                board.findQuestion(match.orElseThrow().getQuestion()).getQuestion());
    }

    private String resolved(String text) {
        return typed.resolve(text).orElseThrow(
                () -> new AssertionError("Could not resolve: " + text)).getQuestion();
    }
}
