package com.cst.payment.transfer.dto;

import java.math.BigDecimal;

public record TransferRequest(
        String senderAccount,
        String receiverAccount,
        BigDecimal amount,
        String currency
) {}