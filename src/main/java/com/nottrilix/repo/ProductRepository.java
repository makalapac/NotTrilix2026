package com.nottrilix.repo;

import com.nottrilix.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Catalog order = insertion order (A1, HT, Steam, PlayStation, Xbox; ascending denominations). */
    List<Product> findAllByOrderByIdAsc();
}
