package com.big2.core.game;

/**
 * Regional rule variations the room owner can switch on or off.
 *
 * @param twosInStraights        allow J-Q-K-A-2, the highest straight, when 2s count as the top rank
 * @param wrapStraights          allow the low straights A-2-3-4-5 and 2-3-4-5-6, ranked below 3-4-5-6-7
 * @param threeOfDiamondsEveryDeal when true the 3♦ holder opens every deal; when false only the first,
 *                               and the winner of the previous deal leads freely after that
 */
public record HouseRules(boolean twosInStraights, boolean wrapStraights, boolean threeOfDiamondsEveryDeal) {

    /** The rules the game has always used: no 2s in straights, 3♦ opens every deal. */
    public static final HouseRules STANDARD = new HouseRules(false, false, true);
}
