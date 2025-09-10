package com.tonyyuan.paymentappbackend.repository;


import com.tonyyuan.paymentappbackend.entity.PaymentInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PaymentInvoiceRepository extends JpaRepository<PaymentInvoice, Long> {

    @Query("select pi from PaymentInvoice pi where pi.tenantId = :tenantId and pi.invoiceId in :ids")
    List<PaymentInvoice> findExisting(@Param("tenantId") String tenantId, @Param("ids") Collection<String> ids);
}
