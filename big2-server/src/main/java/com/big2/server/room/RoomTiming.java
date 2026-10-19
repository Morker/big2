package com.big2.server.room;

/**
 * How long rooms pause for pacing.
 *
 * @param botDelayMs       pause before each bot move, so people can follow the play
 * @param nextDealSeconds  pause between deals while the scores are shown
 */
public record RoomTiming(long botDelayMs, int nextDealSeconds) {

    public static final RoomTiming DEFAULT = new RoomTiming(900, 8);
}
