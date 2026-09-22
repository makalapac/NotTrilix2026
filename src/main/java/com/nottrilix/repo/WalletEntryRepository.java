package com.nottrilix.repo;

import com.nottrilix.domain.WalletEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletEntryRepository extends JpaRepository<WalletEntry, Long> {

    List<WalletEntry> findByCustomerIdOrderByCreatedAtDescIdDesc(long customerId);
}
