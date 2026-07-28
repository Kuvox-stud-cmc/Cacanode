package com.cacanode.api.recruitment.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter @Setter @Entity
@Table(name = "recruitment_availability_exceptions")
public class RecruitmentAvailabilityException extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "exception_date", nullable = false) private LocalDate exceptionDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RecruitmentEnums.AvailabilityExceptionKind kind;
    @Column(name = "start_local", nullable = false) private LocalTime startLocal;
    @Column(name = "end_local", nullable = false) private LocalTime endLocal;
}
