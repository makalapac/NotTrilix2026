package com.nottrilix.api.dto;

import com.nottrilix.domain.Customer;
import com.nottrilix.domain.Product;
import com.nottrilix.domain.SupportTicket;
import com.nottrilix.domain.Transaction;
import com.nottrilix.domain.Voucher;

/** Entitet -> DTO. Poziva se unutar transakcije (lazy veze). */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static CustomerDto toDto(Customer c) {
        return new CustomerDto(c.getId(), c.getName(), c.getEmail(), c.getPhone());
    }

    public static VoucherDto toDto(Voucher v) {
        return new VoucherDto(v.getSerial(), v.getCode(), v.getStatus(), v.getSoldAt());
    }

    public static TransactionDto toDto(Transaction t) {
        Product p = t.getProduct();
        return new TransactionDto(t.getId(), t.getCreatedAt(), t.getChannel(), t.getStatus(),
                t.getCustomer().getId(), t.getCustomer().getName(),
                p.getId(), p.getBrand(), p.displayName(), t.getAmountEur(),
                toDto(t.getVoucher()), t.getRefundedAt());
    }

    public static TicketDto toDto(SupportTicket t) {
        return new TicketDto(t.getId(), t.getCreatedAt(), t.getStatus(),
                t.getCustomer().getId(), t.getCustomer().getName(),
                t.getTransaction() == null ? null : t.getTransaction().getId(),
                t.getSubject(), t.getDescription(), t.getResolution(), t.getResolvedAt());
    }
}
