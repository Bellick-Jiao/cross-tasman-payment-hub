package com.cst.payment.account.controller;

import com.cst.payment.account.dto.AccountResponse;
import com.cst.payment.account.dto.BalanceResponse;
import com.cst.payment.account.dto.CreditRequest;
import com.cst.payment.account.dto.DebitRequest;
import com.cst.payment.account.model.Account;
import com.cst.payment.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    // Secure balance check
    @GetMapping("/{accountNo}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String accountNo) {
        Account account = accountService.getAccountBalance(accountNo);
        return ResponseEntity.ok(new BalanceResponse(
                account.getAccountNo(),
                account.getBalance(),
                account.getCurrency()
        ));
    }

    // Internal secure endpoint for debiting funds
    @PostMapping("/debit")
    public ResponseEntity<AccountResponse> debitAccount(@RequestBody DebitRequest request) {
        try {
            Account account = accountService.debit(request.accountNo(), request.amount());
            return ResponseEntity.ok(new AccountResponse(
                    "SUCCESS",
                    account.getAccountNo(),
                    account.getBalance(),
                    "Funds successfully debited."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new AccountResponse(
                    "FAILED",
                    request.accountNo(),
                    null,
                    e.getMessage()
            ));
        }
    }

    // Internal secure endpoint for crediting funds
    @PostMapping("/credit")
    public ResponseEntity<AccountResponse> creditAccount(@RequestBody CreditRequest request) {
        try {
            Account account = accountService.credit(request.accountNo(), request.amount());
            return ResponseEntity.ok(new AccountResponse(
                    "SUCCESS",
                    account.getAccountNo(),
                    account.getBalance(),
                    "Funds successfully credited."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new AccountResponse(
                    "FAILED",
                    request.accountNo(),
                    null,
                    e.getMessage()
            ));
        }
    }
}