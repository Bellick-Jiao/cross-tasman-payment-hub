package com.cst.payment.transfer.service;

import com.cst.payment.transfer.dto.DebitRequest;
import com.cst.payment.transfer.dto.TransferRequest;
import com.cst.payment.transfer.dto.TransferResponse;
import com.cst.payment.transfer.model.Transaction;
import com.cst.payment.transfer.model.TransactionStatus;
import com.cst.payment.transfer.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class TransferService {

    private final TransactionRepository transactionRepository;
    private final JmsTemplate jmsTemplate;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${app.mq.outbound-queue}")
    private String outboundQueue;

    public TransferService(TransactionRepository transactionRepository,
                           JmsTemplate jmsTemplate,
                           @Value("${app.services.account-url}") String accountServiceUrl,
                           ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.jmsTemplate = jmsTemplate;
        this.objectMapper = objectMapper;
        // 使用内置的 RestClient Builder 声明式构建客户端
        this.restClient = RestClient.builder()
                .baseUrl(accountServiceUrl)
                .build();
    }

    @Transactional
    public TransferResponse processTransfer(TransferRequest request, String jwtToken) {
        // 1. 生成全局唯一的金融交易追踪号 (Transaction Reference)
        String txRef = "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        System.out.println("[Transfer Service] Generated unique tx reference: " + txRef);

        // 2. 本地记账 (插入 PENDING 状态的事务记录)
        Transaction transaction = Transaction.builder()
                .txReference(txRef)
                .senderAccount(request.senderAccount())
                .receiverAccount(request.receiverAccount())
                .amount(request.amount())
                .currency(request.currency())
                .status(TransactionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        transactionRepository.save(transaction);

        try {
            // 3. Token Relay (透传 JWT 安全凭证) 扣减本地账户余额
            System.out.println("[Transfer Service] Demanding Account-Service to debit sender balance with Pessimistic Lock...");
            restClient.post()
                    .uri("/api/v1/accounts/debit")
                    .header("Authorization", jwtToken) // 👈 透传 JWT 安全凭证
                    .body(new DebitRequest(request.senderAccount(),request.amount()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw new RuntimeException("Account Service debiting failed. Status: " + resp.getStatusCode());
                    })
                    .toBodilessEntity();

            // 4. 打包投递支付指令到 IBM MQ (奥克兰网关别名队列)
            Map<String, Object> mqMessage = new HashMap<>();
            mqMessage.put("transactionReference", txRef);
            mqMessage.put("senderAccount", request.senderAccount());
            mqMessage.put("receiverAccount", request.receiverAccount());
            mqMessage.put("amount", request.amount());
            mqMessage.put("currency", request.currency());
            mqMessage.put("timestamp", System.currentTimeMillis());

            String jsonPayload = objectMapper.writeValueAsString(mqMessage);
            System.out.println("[Transfer Service] Dispatching payment command payload to IBM MQ: " + jsonPayload);

            // 投递至 PAYMENT.TX.INBOUND 别名队列（后台自动做 1:1 轮询双 PR 节点负载分发）
            //jmsTemplate.convertAndSend(outboundQueue, jsonPayload);
            jmsTemplate.convertAndSend(outboundQueue, jsonPayload, message -> {
                // 强制设置 IBM MQ 的 CCSID 为 1208 (UTF-8)
                message.setIntProperty("JMS_IBM_Character_Set", 1208);
                message.setStringProperty("JMS_IBM_Format", "MQSTR");
                message.setIntProperty("JMS_IBM_Encoding", 546);
                return message;
            });
            // 5. 扣款并发送成功，更新本地事务状态为 PROCESSING (清算处理中)
            transaction.setStatus(TransactionStatus.PROCESSING);
            transactionRepository.save(transaction);

            return new TransferResponse(
                    txRef,
                    "PROCESSING",
                    "Funds debited. Cross-border settlement message dispatched successfully via IBM MQ.",
                    LocalDateTime.now()
            );

        } catch (Exception e) {
            System.err.println("[Transfer Service] CRITICAL: Transaction failed. Initiating Rollback. Reason: " + e.getMessage());
            // 发生异常时，Spring 声明式事务 @Transactional 会自动回滚本地数据库记录
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            throw new RuntimeException("Cross-border payment processing failed. Rollback executed. Detail: " + e.getMessage());
        }
    }
}