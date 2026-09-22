package com.nottrilix.repo;

import com.nottrilix.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findAllByOrderByNameAsc();

    Optional<Customer> findByEmailIgnoreCase(String email);
}
