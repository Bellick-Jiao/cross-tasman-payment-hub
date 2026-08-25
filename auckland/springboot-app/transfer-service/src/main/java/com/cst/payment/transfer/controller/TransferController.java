package com.cst.payment.transfer.controller;

import com.cst.payment.transfer.dto.TransferRequest;
import com.cst.payment.transfer.dto.TransferResponse;
import com.cst.payment.transfer.service.TransferService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> initiateTransfer(
            @RequestBody TransferRequest request,
            @RequestHeader("Authorization") String authHeader) {

        // 传递请求及 Header 中的 Bearer JWT Token 到业务层进行透传验证
        TransferResponse response = transferService.processTransfer(request, authHeader);
        return ResponseEntity.ok(response);
    }
}