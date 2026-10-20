package com.big2.core.ai;

/** How well computer players play. The room owner picks one for the whole table. */
public enum BotLevel {
    /** Always plays the weakest card that beats the table; never waits. */
    EASY,
    /** Plans its hand, protects combinations, saves its 2s and reacts when someone is nearly out. */
    NORMAL,
    /** Like normal, and also counts cards to know when its plays can't be beaten. */
    HARD;

    public Bot create() {
        return switch (this) {
            case EASY -> new SimpleBot();
            case NORMAL -> new SmartBot(false);
            case HARD -> new SmartBot(true);
        };
    }

    public String id() {
        return name().toLowerCase();
    }

    public static BotLevel fromId(String id) {
        for (BotLevel level : values()) {
            if (level.id().equals(id)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Bot level must be easy, normal or hard");
    }
}
