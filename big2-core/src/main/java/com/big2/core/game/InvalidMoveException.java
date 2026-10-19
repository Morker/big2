package com.big2.core.game;

/** Thrown when a player attempts a move that breaks the rules. The message is safe to show to players. */
public class InvalidMoveException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidMoveException(String message) {
        super(message);
    }
}
