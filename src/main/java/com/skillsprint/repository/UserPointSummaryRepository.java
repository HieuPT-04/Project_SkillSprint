package com.skillsprint.repository;

import com.skillsprint.entity.UserPointSummary;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserPointSummaryRepository extends JpaRepository<UserPointSummary, String> {

    @Query("""
            select summary
            from UserPointSummary summary
            where summary.totalPoints > 0
            order by summary.totalPoints desc, summary.user.fullName asc
            """)
    List<UserPointSummary> findAllTimeLeaderboard(Pageable pageable);

    @Query("""
            select summary
            from UserPointSummary summary
            where summary.totalPoints > 0
              and (
                    :search is null
                    or lower(summary.user.userId) like lower(concat('%', :search, '%'))
                    or lower(summary.user.email) like lower(concat('%', :search, '%'))
                    or lower(summary.user.fullName) like lower(concat('%', :search, '%'))
              )
            order by summary.totalPoints desc, summary.user.fullName asc
            """)
    Page<UserPointSummary> searchAllTimeAdminLeaderboard(
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
            select count(summary) + 1
            from UserPointSummary summary
            where summary.totalPoints > :points
            """)
    Long calculateAllTimeRank(@Param("points") int points);
}
