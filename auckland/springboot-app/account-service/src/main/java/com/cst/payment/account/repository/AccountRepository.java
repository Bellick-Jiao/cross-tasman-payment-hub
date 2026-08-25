package com.cst.payment.account.repository;

import com.cst.payment.account.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

    // Pessimistic Write Lock to block concurrent modifications (Highly crucial for financial transactions!)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNo = :accountNo")
    Optional<Account> findByAccountNoForUpdate(@Param("accountNo") String accountNo);
}