package com.skillsprint.repository;

import com.skillsprint.entity.PointEvent;
import com.skillsprint.enums.points.PointEventType;
import com.skillsprint.enums.points.PointSourceType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointEventRepository extends JpaRepository<PointEvent, UUID> {

    boolean existsByUserUserIdAndEventTypeAndSourceTypeAndSourceId(
            String userId,
            PointEventType eventType,
            PointSourceType sourceType,
            String sourceId
    );

    Optional<PointEvent> findByUserUserIdAndEventTypeAndSourceTypeAndSourceId(
            String userId,
            PointEventType eventType,
            PointSourceType sourceType,
            String sourceId
    );

    List<PointEvent> findByUserUserIdOrderByCreatedAtDesc(String userId);

    @Query("""
            select coalesce(sum(pointEvent.points), 0)
            from PointEvent pointEvent
            where pointEvent.user.userId = :userId
              and pointEvent.weekStartDate = :weekStartDate
            """)
    Long sumWeeklyPoints(@Param("userId") String userId, @Param("weekStartDate") LocalDate weekStartDate);

    @Query("""
            select coalesce(sum(pointEvent.points), 0)
            from PointEvent pointEvent
            where pointEvent.user.userId = :userId
              and pointEvent.monthStartDate = :monthStartDate
            """)
    Long sumMonthlyPoints(@Param("userId") String userId, @Param("monthStartDate") LocalDate monthStartDate);

    @Query(
            value = """
                    select count(*) + 1
                    from (
                        select user_id, sum(points) as total_points
                        from point_events
                        where week_start_date = :weekStartDate
                        group by user_id
                        having sum(points) > :points
                    ) ranked
                    """,
            nativeQuery = true
    )
    Long calculateWeeklyRank(
            @Param("weekStartDate") LocalDate weekStartDate,
            @Param("points") int points
    );

    @Query(
            value = """
                    select count(*) + 1
                    from (
                        select user_id, sum(points) as total_points
                        from point_events
                        where month_start_date = :monthStartDate
                        group by user_id
                        having sum(points) > :points
                    ) ranked
                    """,
            nativeQuery = true
    )
    Long calculateMonthlyRank(
            @Param("monthStartDate") LocalDate monthStartDate,
            @Param("points") int points
    );

    @Query("""
            select pointEvent.user.userId as userId,
                   pointEvent.user.fullName as fullName,
                   pointEvent.user.avatarObjectKey as avatarObjectKey,
                   sum(pointEvent.points) as points
            from PointEvent pointEvent
            where pointEvent.weekStartDate = :weekStartDate
            group by pointEvent.user.userId, pointEvent.user.fullName, pointEvent.user.avatarObjectKey
            order by sum(pointEvent.points) desc, pointEvent.user.fullName asc
            """)
    List<LeaderboardRow> findWeeklyLeaderboard(@Param("weekStartDate") LocalDate weekStartDate, Pageable pageable);

    @Query("""
            select pointEvent.user.userId as userId,
                   pointEvent.user.fullName as fullName,
                   pointEvent.user.avatarObjectKey as avatarObjectKey,
                   sum(pointEvent.points) as points
            from PointEvent pointEvent
            where pointEvent.monthStartDate = :monthStartDate
            group by pointEvent.user.userId, pointEvent.user.fullName, pointEvent.user.avatarObjectKey
            order by sum(pointEvent.points) desc, pointEvent.user.fullName asc
            """)
    List<LeaderboardRow> findMonthlyLeaderboard(@Param("monthStartDate") LocalDate monthStartDate, Pageable pageable);

    interface LeaderboardRow {
        String getUserId();

        String getFullName();

        String getAvatarObjectKey();

        Long getPoints();
    }
}
