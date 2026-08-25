package com.cst.payment.transfer.dto;

import java.math.BigDecimal;

public record DebitRequest(String accountNo, BigDecimal amount) {}