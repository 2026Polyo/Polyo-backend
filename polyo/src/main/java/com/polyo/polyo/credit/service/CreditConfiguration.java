package com.polyo.polyo.credit.service;

import com.polyo.polyo.common.error.ApiException;
import com.polyo.polyo.common.error.CommonErrorCode;
import com.polyo.polyo.credit.port.AuthenticatedUserProvider;
import com.polyo.polyo.credit.port.CreditSettlementStore;
import com.polyo.polyo.credit.port.GameResultSource;
import com.polyo.polyo.matchroom.MatchRoomUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
class CreditConfiguration {
    @Bean
    AuthenticatedUserProvider authenticatedCreditUser() {
        return () -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof MatchRoomUser user)) {
                throw new ApiException(CommonErrorCode.AUTH_UNAUTHORIZED);
            }
            return user.id();
        };
    }

    @Bean
    CreditService creditService(GameResultSource games, CreditSettlementStore settlements) {
        return new DefaultCreditService(new ValidatingHardModeGameResultReader(games),
                new DefaultHardModeRewardPolicy(), settlements);
    }
}
