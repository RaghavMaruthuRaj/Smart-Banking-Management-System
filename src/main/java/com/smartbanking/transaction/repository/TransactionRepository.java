package com.smartbanking.transaction.repository;

import com.smartbanking.transaction.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    @Query("SELECT t FROM Transaction t WHERE t.sourceAccount.id = :accountId OR t.targetAccount.id = :accountId ORDER BY t.createdAt DESC")
    Page<Transaction> findAllByAccountId(Long accountId, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.sourceAccount.user.id = :userId OR t.targetAccount.user.id = :userId ORDER BY t.createdAt DESC")
    Page<Transaction> findAllByUserId(Long userId, Pageable pageable);

    @Query("SELECT t.category, SUM(t.amount) FROM Transaction t " +
           "WHERE t.sourceAccount.id = :accountId AND t.status = 'COMPLETED' AND t.type = 'TRANSFER' " +
           "GROUP BY t.category")
    List<Object[]> getSpendingByCategory(Long accountId);
}
