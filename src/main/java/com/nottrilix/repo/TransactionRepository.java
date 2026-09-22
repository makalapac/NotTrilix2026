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
     * Transakcija zakljucana u bazi (FOR UPDATE) - mora biti PRVO citanje u povratu, da dva istovremena
     * povrata iste transakcije ne prodju oba (drugi ceka i vidi status REFUNDED).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.id = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") long id);

    /** Kupnja u kojoj je bon prodan (bon je u najvise jednoj transakciji). */
    Optional<Transaction> findByVoucherSerial(String serial);

    /** Zbroj kupnji kupca u zadanom statusu od zadanog trenutka (dnevni limit). Prazno ako nema kupnji. */
    @Query("select sum(t.amountEur) from Transaction t "
            + "where t.customer.id = :customerId and t.status = :status and t.createdAt >= :since")
    Optional<BigDecimal> sumAmountSince(@Param("customerId") long customerId,
                                        @Param("status") TransactionStatus status,
                                        @Param("since") Instant since);
}
