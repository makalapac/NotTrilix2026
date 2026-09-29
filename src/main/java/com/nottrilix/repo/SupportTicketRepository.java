package com.nottrilix.repo;

import com.nottrilix.domain.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    @Query("select t from SupportTicket t join fetch t.customer left join fetch t.transaction "
            + "order by t.createdAt desc")
    List<SupportTicket> findAllWithDetails();

    @Query("select t from SupportTicket t join fetch t.customer left join fetch t.transaction "
            + "where t.customer.id = :customerId order by t.createdAt desc")
    List<SupportTicket> findByCustomerIdWithDetails(@Param("customerId") long customerId);
}
