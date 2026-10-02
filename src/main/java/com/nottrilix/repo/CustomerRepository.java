package com.nottrilix.repo;

import com.nottrilix.domain.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findAllByOrderByNameAsc();

    Optional<Customer> findByEmailIgnoreCase(String email);

    /** Customer by phone number compared as international digits ("385911112222" finds "+385 91 111 2222"; see PhoneNumbers). */
    @Query("select c from Customer c where replace(replace(replace(replace(c.phone, ' ', ''), '-', ''), '+', ''), '/', '') = :digits")
    Optional<Customer> findByPhoneDigits(@Param("digits") String digits);

    /** Customer locked in the database (FOR UPDATE) for wallet changes and a race-free daily limit check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") long id);
}
