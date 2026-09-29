package com.nottrilix.repo;

import com.nottrilix.domain.Product;
import com.nottrilix.domain.Voucher;
import com.nottrilix.domain.VoucherStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    /** Prvi slobodan bon proizvoda, zakljucan u bazi (FOR UPDATE) da dva kupca ne dobiju isti kod. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Voucher> findFirstByProductAndStatusOrderByIdAsc(Product product, VoucherStatus status);

    Optional<Voucher> findBySerial(String serial);

    /** Ima li proizvod barem jedan bon u zadanom statusu (npr. je li dostupan za kupnju). */
    boolean existsByProductAndStatus(Product product, VoucherStatus status);

    /** Broj bonova po proizvodu i statusu (zaliha). */
    @Query("select v.product.id as productId, v.status as status, count(v) as count "
            + "from Voucher v group by v.product.id, v.status")
    List<StockCount> countByProductAndStatus();

    interface StockCount {
        Long getProductId();

        VoucherStatus getStatus();

        Long getCount();
    }
}
