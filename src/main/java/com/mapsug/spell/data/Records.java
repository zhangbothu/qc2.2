package com.mapsug.spell.data;

/** Row types of the four input tables. */
public final class Records {

    private Records() {
    }

    /** Raw user query and the Top1 Google Maps result returned for it. */
    public record QueryTop1(String country, String query, String top1Name, String top1Address) {
    }

    /** Suspected correction triple mined from (query, Top1); {@code count} defaults to 1. */
    public record Triple(String country, String wrong, String right, int editDistance, long count) {
    }

    /** Historical token statistics; {@code df} = number of queries containing the token. */
    public record TokenStat(String country, String token, long df) {
    }

    /** Simulated click: query -> clicked result (= Google Maps Top1), with click count. */
    public record QueryClick(String country, String query, String clickedName, long clicks) {
    }
}
