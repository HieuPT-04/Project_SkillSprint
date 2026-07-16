package com.skillsprint.repository;

import com.skillsprint.entity.UserWallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface UserWalletRepository extends JpaRepository<UserWallet, java.util.UUID> {

    Optional<UserWallet> findByUserUserId(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from UserWallet w where w.user.userId = :userId")
    Optional<UserWallet> findByUserIdForUpdate(@Param("userId") String userId);
}
