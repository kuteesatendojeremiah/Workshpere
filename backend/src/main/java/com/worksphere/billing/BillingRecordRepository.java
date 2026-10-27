package com.worksphere.billing;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingRecordRepository extends JpaRepository<BillingRecord, UUID> {

    Page<BillingRecord> findAllByOrganizationId(UUID organizationId, Pageable pageable);
}
