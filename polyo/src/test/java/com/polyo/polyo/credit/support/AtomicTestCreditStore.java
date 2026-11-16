package com.polyo.polyo.credit.support;

import com.polyo.polyo.credit.domain.GameRewardResult;
import com.polyo.polyo.credit.domain.GameRewardSettlement;
import com.polyo.polyo.credit.port.CreditSettlementStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 테스트 전용 원자적 저장소. 프로덕션 Bean이나 영속 저장소가 아니다. */
public final class AtomicTestCreditStore implements CreditSettlementStore {
    private record Key(String userId, String gameId) {}

    private final Map<String, Long> balances = new HashMap<>();
    private final Map<Key, GameRewardSettlement> settlements = new HashMap<>();
    private boolean failNext;

    public synchronized void setBalance(String userId, long coin) {
        balances.put(userId, coin);
    }

    public synchronized void failNextSettlement() {
        failNext = true;
    }

    public synchronized int settlementCount() {
        return settlements.size();
    }

    @Override
    public synchronized long getCoinBalance(String userId) {
        return balances.getOrDefault(userId, 0L);
    }

    @Override
    public synchronized Optional<GameRewardSettlement> findSettlement(
            String userId, String gameId) {
        return Optional.ofNullable(settlements.get(new Key(userId, gameId)));
    }

    @Override
    public synchronized GameRewardResult settleOnce(String userId, String gameId, long earnedCoin) {
        var key = new Key(userId, gameId);
        var existing = settlements.get(key);
        if (existing != null) return new GameRewardResult(existing, false);
        long updated = Math.addExact(getCoinBalance(userId), earnedCoin);
        if (earnedCoin < 0 || updated < 0 || updated > 9_007_199_254_740_991L) {
            throw new ArithmeticException("Unsafe coin balance");
        }
        var settled =
                new GameRewardSettlement(
                        "reward_" + (settlements.size() + 1), gameId, earnedCoin, updated);
        if (failNext) {
            failNext = false;
            throw new IllegalStateException("Simulated failure before atomic commit");
        }
        settlements.put(key, settled);
        balances.put(userId, updated);
        return new GameRewardResult(settled, true);
    }
}
