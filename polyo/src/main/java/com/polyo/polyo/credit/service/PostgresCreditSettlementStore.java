package com.polyo.polyo.credit.service;

import com.polyo.polyo.credit.domain.GameRewardResult;
import com.polyo.polyo.credit.domain.GameRewardSettlement;
import com.polyo.polyo.credit.port.CreditSettlementStore;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresCreditSettlementStore implements CreditSettlementStore {
    private final JdbcClient jdbc;
    private final CoinWallet wallet;

    public PostgresCreditSettlementStore(JdbcClient jdbc, CoinWallet wallet) {
        this.jdbc = jdbc;
        this.wallet = wallet;
    }

    @Override
    public long getCoinBalance(String userId) {
        return wallet.balance(Long.parseLong(userId));
    }

    @Override
    public Optional<GameRewardSettlement> findSettlement(String userId, String gameId) {
        return jdbc.sql("""
                SELECT reward_id, game_id, earned_coin, coin_after_reward
                FROM game_reward_settlements WHERE user_id = :userId AND game_id = :gameId
                """).param("userId", Long.parseLong(userId)).param("gameId", gameId)
                .query((rs, index) -> new GameRewardSettlement(
                        rs.getString("reward_id"), rs.getString("game_id"),
                        rs.getLong("earned_coin"), rs.getLong("coin_after_reward"))).optional();
    }

    @Override
    @Transactional
    public GameRewardResult settleOnce(String userId, String gameId, long earnedCoin) {
        long id = Long.parseLong(userId);
        int balance = wallet.lockBalance(id);
        var existing = findSettlement(userId, gameId);
        if (existing.isPresent()) return new GameRewardResult(existing.get(), false);

        long updated = balance + earnedCoin;
        wallet.setBalance(id, updated);
        var rewardId = UUID.randomUUID().toString();
        jdbc.sql("""
                INSERT INTO game_reward_settlements
                    (game_id, user_id, reward_id, earned_coin, coin_after_reward)
                VALUES (:gameId, :userId, :rewardId, :earned, :updated)
                """).param("gameId", gameId).param("userId", id).param("rewardId", rewardId)
                .param("earned", Math.toIntExact(earnedCoin)).param("updated", Math.toIntExact(updated)).update();
        return new GameRewardResult(new GameRewardSettlement(rewardId, gameId, earnedCoin, updated), true);
    }
}
