package com.cst.payment.account.dto;

import java.math.BigDecimal;

public record AccountResponse(String status, String accountNo, BigDecimal remainingBalance, String message) {}