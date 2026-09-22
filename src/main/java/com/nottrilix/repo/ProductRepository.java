package com.nottrilix.repo;

import com.nottrilix.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Redoslijed kataloga = redoslijed unosa (A1, HT, Steam, PlayStation, Xbox; rastuce denominacije). */
    List<Product> findAllByOrderByIdAsc();
}
