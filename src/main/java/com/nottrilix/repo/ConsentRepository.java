package com.nottrilix.repo;

import com.nottrilix.domain.Consent;
import com.nottrilix.domain.ConsentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

    Optional<Consent> findFirstByCustomerIdAndTypeAndWithdrawnAtIsNullOrderByGivenAtDesc(Long customerId,
                                                                                        ConsentType type);

    List<Consent> findByCustomerIdOrderByGivenAtDesc(Long customerId);
}
