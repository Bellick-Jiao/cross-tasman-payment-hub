package com.cst.payment.account.dto;

import java.math.BigDecimal;

public record BalanceResponse(String accountNo, BigDecimal balance, String currency) {}