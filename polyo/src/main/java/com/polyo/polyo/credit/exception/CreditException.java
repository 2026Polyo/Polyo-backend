package com.polyo.polyo.credit.exception;

import com.polyo.polyo.common.error.ApiException;

public final class CreditException extends ApiException {
    public CreditException(CreditErrorCode errorCode) {
        super(errorCode);
    }
}
