package com.worksphere.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Test
    void recordSavesAnEntryWithSerializedMetadata() {
        AuditService service = new AuditService(auditLogRepository, new ObjectMapper());
        UUID organizationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record(
                organizationId,
                actorId,
                AuditAction.PROJECT_CREATED,
                AuditResourceType.PROJECT,
                resourceId,
                Map.of("name", "Website"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertThat(saved.getOrganizationId()).isEqualTo(organizationId);
        assertThat(saved.getActorId()).isEqualTo(actorId);
        assertThat(saved.getAction()).isEqualTo(AuditAction.PROJECT_CREATED);
        assertThat(saved.getResourceType()).isEqualTo(AuditResourceType.PROJECT);
        assertThat(saved.getResourceId()).isEqualTo(resourceId);
        assertThat(saved.getMetadata()).contains("Website");
    }

    @Test
    void recordWithNullMetadataStoresNullRatherThanEmptyJson() {
        AuditService service = new AuditService(auditLogRepository, new ObjectMapper());
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record(null, null, AuditAction.LOGIN_FAILED, AuditResourceType.USER, null, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getMetadata()).isNull();
    }
}
