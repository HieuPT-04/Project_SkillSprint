package com.skillsprint.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.skillsprint.enums.workspace.ConfidenceLevel;
import com.skillsprint.enums.workspace.PreferredLanguage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "onboarding_profiles")
public class OnboardingProfile extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "profile_id")
    private UUID profileId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, unique = true)
    private StudyWorkspace workspace;

    @Column(name = "target_goal", nullable = false)
    private String targetGoal;

    @Column(name = "study_hours_per_week", precision = 4, scale = 1)
    private BigDecimal studyHoursPerWeek;

    @Column(name = "target_deadline")
    private LocalDate targetDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", nullable = false, length = 10)
    private ConfidenceLevel confidence = ConfidenceLevel.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", length = 20)
    private PreferredLanguage preferredLanguage = PreferredLanguage.vi;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_days")
    private String preferredDays;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_time_slots")
    private String preferredTimeSlots;
}
