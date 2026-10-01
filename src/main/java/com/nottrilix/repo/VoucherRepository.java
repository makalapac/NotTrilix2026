package com.nottrilix.repo;

import com.nottrilix.domain.Product;
import com.nottrilix.domain.Voucher;
import com.nottrilix.domain.VoucherStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    /** First available voucher of the product, locked in the database (FOR UPDATE) so two customers never get the same code. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Voucher> findFirstByProductAndStatusOrderByIdAsc(Product product, VoucherStatus status);

    /** Voucher by serial in compact form ("STM25003" finds "STM-25-003"; see VoucherSerial). */
    @Query("select v from Voucher v where replace(upper(v.serial), '-', '') = :compact")
    Optional<Voucher> findBySerialCompact(@Param("compact") String compact);

    /** Whether the product has at least one voucher in the given status (e.g. whether it is available for purchase). */
    boolean existsByProductAndStatus(Product product, VoucherStatus status);

    /** Number of vouchers per product and status (stock). */
    @Query("select v.product.id as productId, v.status as status, count(v) as count "
            + "from Voucher v group by v.product.id, v.status")
    List<StockCount> countByProductAndStatus();

    interface StockCount {
        Long getProductId();

        VoucherStatus getStatus();

        Long getCount();
    }
}
