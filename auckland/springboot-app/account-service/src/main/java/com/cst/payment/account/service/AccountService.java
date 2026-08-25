package com.cst.payment.account.service;

import com.cst.payment.account.model.Account;
import com.cst.payment.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;

    // 1. Get Balance - Using Redis Caching (Cache-Aside Pattern)
    @Cacheable(value = "balances", key = "#accountNo", unless = "#result == null")
    @Transactional(readOnly = true)
    public Account getAccountBalance(String accountNo) {
        log.info("[Redis Cache Miss] Fetching account info from PostgreSQL for: {}", accountNo);
        return accountRepository.findById(accountNo)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNo));
    }

    // 2. Debit Account (Deduct balance) with Transaction & Pessimistic Lock
    @Transactional
    @CacheEvict(value = "balances", key = "#accountNo") // Evict Redis cache to keep data consistent
    public Account debit(String accountNo, BigDecimal amount) {
        log.info("[Debit Attempt] Locking account {} to deduct amount: {}", accountNo, amount);

        // Retrieve and Lock row via SELECT ... FOR UPDATE
        Account account = accountRepository.findByAccountNoForUpdate(accountNo)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNo));

        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new IllegalStateException("Account is inactive/frozen");
        }

        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds in account: " + accountNo);
        }

        // Perform balance deduction
        account.setBalance(account.getBalance().subtract(amount));
        Account updatedAccount = accountRepository.save(account);

        log.info("[Debit Success] Account {} balance updated to: {}", accountNo, updatedAccount.getBalance());
        return updatedAccount;
    }

    // 3. Credit Account (Add balance) with Transaction & Pessimistic Lock
    @Transactional
    @CacheEvict(value = "balances", key = "#accountNo") // Evict cache
    public Account credit(String accountNo, BigDecimal amount) {
        log.info("[Credit Attempt] Locking account {} to credit amount: {}", accountNo, amount);

        Account account = accountRepository.findByAccountNoForUpdate(accountNo)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNo));

        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new IllegalStateException("Account is inactive/frozen");
        }

        // Add funds
        account.setBalance(account.getBalance().add(amount));
        Account updatedAccount = accountRepository.save(account);

        log.info("[Credit Success] Account {} balance updated to: {}", accountNo, updatedAccount.getBalance());
        return updatedAccount;
    }
}