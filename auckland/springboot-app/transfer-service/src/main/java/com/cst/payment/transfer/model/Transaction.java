package com.cst.payment.transfer.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tx_id")
    private Long txId;

    @Column(name = "tx_reference", unique = true, nullable = false)
    private String txReference;

    @Column(name = "sender_account", nullable = false)
    private String senderAccount;

    @Column(name = "receiver_account", nullable = false)
    private String receiverAccount;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}