package com.skillsprint.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @Entity @Table(name = "user_wallets")
public class UserWallet extends BaseAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "wallet_id")
    private UUID walletId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
    @Column(nullable = false) private Integer balance = 0;
}
