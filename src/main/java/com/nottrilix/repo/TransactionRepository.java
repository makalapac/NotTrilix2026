package com.nottrilix.repo;

import com.nottrilix.domain.Transaction;
import com.nottrilix.domain.TransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("select t from Transaction t join fetch t.customer join fetch t.product join fetch t.voucher "
            + "order by t.createdAt desc")
    List<Transaction> findAllWithDetails();

    @Query("select t from Transaction t join fetch t.customer join fetch t.product join fetch t.voucher "
            + "where t.customer.id = :customerId order by t.createdAt desc")
    List<Transaction> findByCustomerIdWithDetails(@Param("customerId") long customerId);

    @Query("select t from Transaction t join fetch t.customer join fetch t.product join fetch t.voucher "
            + "where t.id = :id")
    Optional<Transaction> findByIdWithDetails(@Param("id") long id);

    /**
     * Transaction locked in the database (FOR UPDATE) - must be the FIRST read in a refund, so that two concurrent
     * refunds of the same transaction do not both go through (the second one waits and sees status REFUNDED).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.id = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") long id);

    /** The purchase in which the voucher was sold (a voucher is in at most one transaction). */
    Optional<Transaction> findByVoucherSerial(String serial);

    /** Sum of the customer's purchases in the given status since the given instant (daily limit). Empty if there are none. */
    @Query("select sum(t.amountEur) from Transaction t "
            + "where t.customer.id = :customerId and t.status = :status and t.createdAt >= :since")
    Optional<BigDecimal> sumAmountSince(@Param("customerId") long customerId,
                                        @Param("status") TransactionStatus status,
                                        @Param("since") Instant since);
}
