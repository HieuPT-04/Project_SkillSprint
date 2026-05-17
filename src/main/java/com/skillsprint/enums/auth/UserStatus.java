package com.skillsprint.enums.auth;

public enum UserStatus {
    ACTIVE,
    DISABLED;

    public boolean canBeManagedByAdmin() {
        return this == ACTIVE || this == DISABLED;
    }
}
