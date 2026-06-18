package com.skillsprint.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_point_summaries")
public class UserPointSummary extends BaseAuditEntity {

    @Id
    @Column(name = "user_id", length = 100)
    private String userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "total_points", nullable = false)
    private Integer totalPoints = 0;

    @Column(name = "current_week_points", nullable = false)
    private Integer currentWeekPoints = 0;

    @Column(name = "current_week_start_date")
    private LocalDate currentWeekStartDate;

    @Column(name = "current_month_points", nullable = false)
    private Integer currentMonthPoints = 0;

    @Column(name = "current_month_start_date")
    private LocalDate currentMonthStartDate;

    @Column(name = "streak_days", nullable = false)
    private Integer streakDays = 0;

    @Column(name = "last_point_date")
    private LocalDate lastPointDate;
}
