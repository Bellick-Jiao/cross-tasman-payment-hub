package com.cst.payment.transfer.dto;

import java.time.LocalDateTime;

public record TransferResponse(
        String transactionReference,
        String status,
        String message,
        LocalDateTime timestamp
) {}