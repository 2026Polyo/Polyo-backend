package com.polyo.polyo.credit.port;

import com.polyo.polyo.credit.domain.GameRewardResult;
import com.polyo.polyo.credit.domain.GameRewardSettlement;

import java.util.Optional;

/** MySQL 연결 시 구현할 저장 접점. 다른 서비스 소유 데이터는 해당 API로 조회한다. */
public interface CreditSettlementStore {
    long getCoinBalance(String authenticatedUserId);

    Optional<GameRewardSettlement> findSettlement(String authenticatedUserId, String gameId);

    /**
     * 최초 지급은 created=true, 동일 사용자·게임의 재요청은 저장된 결과와 created=false를 반환한다.
     *
     * <p>MySQL 구현 시 다음 조건을 저장소 안에서 보장한다.
     *
     * <ul>
     *   <li>사용자·게임 조합의 유일성 제약으로 여러 서버의 중복 지급을 막는다.
     *   <li>정산 기록과 잔액 갱신을 하나의 트랜잭션으로 처리하고 실패 시 함께 롤백한다.
     *   <li>동일 사용자의 다른 게임이 동시에 정산돼도 잔액 갱신이 유실되지 않도록 한다.
     *   <li>0코인을 포함한 지급량과 정산 당시 잔액을 저장하며 재요청 시 재계산하지 않는다.
     *   <li>커밋 전에 금액이 0 이상, JavaScript 안전 정수 범위 이하인지 확인한다.
     * </ul>
     *
     * <p>잔액이 별도 서비스 소유라면 직접 DB에 접근하지 않고 API와 복구 전략으로 연결한다.
     */
    GameRewardResult settleOnce(String authenticatedUserId, String gameId, long earnedCoin);
}
