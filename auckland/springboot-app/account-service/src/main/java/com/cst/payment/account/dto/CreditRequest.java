package com.cst.payment.account.dto;

import java.math.BigDecimal;

public record CreditRequest(String accountNo, BigDecimal amount) {}