package com.nottrilix.service;

import com.nottrilix.api.dto.CatalogItem;
import com.nottrilix.api.dto.StockRow;
import com.nottrilix.domain.Product;
import com.nottrilix.domain.VoucherStatus;
import com.nottrilix.repo.ProductRepository;
import com.nottrilix.repo.VoucherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final ProductRepository productRepo;
    private final VoucherRepository voucherRepo;

    public CatalogService(ProductRepository productRepo, VoucherRepository voucherRepo) {
        this.productRepo = productRepo;
        this.voucherRepo = voucherRepo;
    }

    /** Catalog for the sales channel: active products and whether a voucher is available (stock quantity stays internal). */
    @Transactional(readOnly = true)
    public List<CatalogItem> catalog() {
        Map<Long, Map<VoucherStatus, Long>> stock = stockCounts();
        List<CatalogItem> items = productRepo.findAllByOrderByIdAsc().stream()
                .filter(Product::isActive)
                .map(p -> new CatalogItem(p.getId(), p.getCategory(), p.getBrand(), p.getBrand().displayName(),
                        p.getName(), p.getDenominationEur(), count(stock, p.getId(), VoucherStatus.AVAILABLE) > 0))
                .toList();
        log.info("CATALOG {} proizvoda, {} dostupno", items.size(), items.stream().filter(CatalogItem::available).count());
        return items;
    }

    /** Stock levels per product and status (admin). */
    @Transactional(readOnly = true)
    public List<StockRow> stock() {
        Map<Long, Map<VoucherStatus, Long>> stock = stockCounts();
        List<StockRow> rows = productRepo.findAllByOrderByIdAsc().stream()
                .map(p -> new StockRow(p.getId(), p.getBrand(), p.displayName(), p.getDenominationEur(), p.isActive(),
                        count(stock, p.getId(), VoucherStatus.AVAILABLE),
                        count(stock, p.getId(), VoucherStatus.SOLD),
                        count(stock, p.getId(), VoucherStatus.REDEEMED),
                        count(stock, p.getId(), VoucherStatus.BLOCKED)))
                .toList();
        log.info("STOCK {} proizvoda", rows.size());
        return rows;
    }

    private Map<Long, Map<VoucherStatus, Long>> stockCounts() {
        Map<Long, Map<VoucherStatus, Long>> result = new HashMap<>();
        for (VoucherRepository.StockCount c : voucherRepo.countByProductAndStatus()) {
            result.computeIfAbsent(c.getProductId(), id -> new EnumMap<>(VoucherStatus.class))
                    .put(c.getStatus(), c.getCount());
        }
        return result;
    }

    private static long count(Map<Long, Map<VoucherStatus, Long>> stock, Long productId, VoucherStatus status) {
        return stock.getOrDefault(productId, Map.of()).getOrDefault(status, 0L);
    }
}
