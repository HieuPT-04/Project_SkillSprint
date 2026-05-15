package com.skillsprint.configuration;

import com.skillsprint.entity.Role;
import com.skillsprint.enums.auth.RoleName;
import com.skillsprint.repository.RoleRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoleSeeder implements ApplicationRunner {

    RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRole(RoleName.ADMIN, "Admin", "Quản trị hệ thống SkillSprint");
        seedRole(RoleName.LEARNER, "Learner", "Người học sử dụng SkillSprint");
    }

    private void seedRole(RoleName roleName, String displayName, String description) {
        if (roleRepository.existsByRoleName(roleName)) {
            return;
        }

        Role role = new Role();
        role.setRoleName(roleName);
        role.setDisplayName(displayName);
        role.setDescription(description);
        role.setActive(true);

        roleRepository.save(role);
        log.info("Seeded role {}", roleName);
    }
}
