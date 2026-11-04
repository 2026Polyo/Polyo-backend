package com.polyo.polyo.ranking;

import java.util.List;

public record RankingResponse(String type, List<RankingEntry> rankings) {
}
