package com.big2.server.room;

/**
 * How long rooms pause and wait.
 *
 * @param botDelayMs       pause before each bot move, so people can follow the play
 * @param playShowMs       the least time a play stays on the table before a bot moves after it
 * @param nextDealSeconds  pause between deals while the scores are shown
 * @param awayTurnMs       how long to wait on a disconnected player's turn before the bot plays it
 * @param lobbyGraceMs     how long a disconnected player keeps their seat in the lobby
 * @param gameGraceMs      how long a disconnected player keeps their seat during a game
 */
public record RoomTiming(long botDelayMs, long playShowMs, int nextDealSeconds, long awayTurnMs, long lobbyGraceMs, long gameGraceMs) {

    public static final RoomTiming DEFAULT = new RoomTiming(1000, 2200, 10, 10_000, 60_000, 180_000);
}
