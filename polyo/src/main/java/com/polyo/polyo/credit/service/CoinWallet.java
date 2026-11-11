package com.polyo.polyo.credit.service;

import com.polyo.polyo.common.error.ApiException;
import com.polyo.polyo.common.error.CommonErrorCode;
import com.polyo.polyo.credit.exception.CreditErrorCode;
import com.polyo.polyo.credit.exception.CreditException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class CoinWallet {
    private final JdbcClient jdbc;

    public CoinWallet(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public int balance(long userId) {
        return read(userId, false);
    }

    /** Call inside the transaction that also writes the purchase or reward. */
    public int lockBalance(long userId) {
        return read(userId, true);
    }

    private int read(long userId, boolean lock) {
        var sql = "SELECT coin FROM users WHERE id = :userId";
        return jdbc.sql(lock ? sql + " FOR UPDATE" : sql).param("userId", userId)
                .query(Integer.class).optional()
                .orElseThrow(() -> new ApiException(CommonErrorCode.AUTH_UNAUTHORIZED));
    }

    public void setBalance(long userId, long balance) {
        if (balance < 0 || balance > Integer.MAX_VALUE) {
            throw new CreditException(CreditErrorCode.CREDIT_INVALID_BALANCE);
        }
        jdbc.sql("UPDATE users SET coin = :coin WHERE id = :userId")
                .param("coin", (int) balance).param("userId", userId).update();
    }
}
