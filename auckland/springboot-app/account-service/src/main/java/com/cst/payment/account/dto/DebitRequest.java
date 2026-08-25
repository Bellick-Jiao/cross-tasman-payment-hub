package com.cst.payment.account.dto;

import java.math.BigDecimal;

public record DebitRequest(String accountNo, BigDecimal amount) {}