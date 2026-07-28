package com.cacanode.api.recruitment.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;
import java.util.UUID;

@Getter @Setter @Entity
@Table(name = "recruitment_availability_windows")
public class RecruitmentAvailabilityWindow extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "day_of_week", nullable = false) private int dayOfWeek;
    @Column(name = "start_local", nullable = false) private LocalTime startLocal;
    @Column(name = "end_local", nullable = false) private LocalTime endLocal;
}
