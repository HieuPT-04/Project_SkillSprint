package com.skillsprint.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.skillsprint.entity.Role;
import com.skillsprint.entity.ServicePlan;
import com.skillsprint.entity.Subscription;
import com.skillsprint.entity.User;
import com.skillsprint.entity.UserRole;
import com.skillsprint.enums.auth.RoleName;
import com.skillsprint.enums.plan.ServicePlanType;
import com.skillsprint.enums.plan.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class AdminUserRepositoryTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    UserRoleRepository userRoleRepository;

    @Autowired
    ServicePlanRepository servicePlanRepository;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Test
    void filtersAdminPlanByGlobalAdminRoleAndOtherPlansByLatestSubscription() {
        User admin = userRepository.save(user("admin-user", "admin@example.com"));
        User premiumUser = userRepository.save(user("premium-user", "premium@example.com"));
        Role adminRole = roleRepository.save(role(RoleName.ADMIN));
        ServicePlan premiumPlan = servicePlanRepository.save(plan(ServicePlanType.PREMIUM));

        userRoleRepository.save(globalRole(admin, adminRole));
        subscriptionRepository.save(subscription(premiumUser, premiumPlan));

        List<String> adminIds = userRepository.findAdminUsers(
                        false,
                        "",
                        false,
                        RoleName.LEARNER,
                        true,
                        true,
                        ServicePlanType.ADMIN_DEFAULT,
                        PageRequest.of(0, 10)
                )
                .map(User::getUserId)
                .getContent();
        List<String> premiumIds = userRepository.findAdminUsers(
                        false,
                        "",
                        false,
                        RoleName.LEARNER,
                        true,
                        false,
                        ServicePlanType.PREMIUM,
                        PageRequest.of(0, 10)
                )
                .map(User::getUserId)
                .getContent();

        assertEquals(List.of(admin.getUserId()), adminIds);
        assertEquals(List.of(premiumUser.getUserId()), premiumIds);
    }

    private User user(String userId, String email) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(email);
        user.setFullName(userId);
        return user;
    }

    private Role role(RoleName roleName) {
        Role role = new Role();
        role.setRoleName(roleName);
        role.setDisplayName(roleName.name());
        return role;
    }

    private UserRole globalRole(User user, Role role) {
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        return userRole;
    }

    private ServicePlan plan(ServicePlanType planType) {
        ServicePlan plan = new ServicePlan();
        plan.setPlanName(planType.name());
        plan.setPlanType(planType);
        plan.setMonthlyPrice(BigDecimal.ZERO);
        plan.setActive(true);
        return plan;
    }

    private Subscription subscription(User user, ServicePlan plan) {
        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setPlan(plan);
        subscription.setStartDate(LocalDate.now());
        subscription.setStartAt(Instant.now());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        return subscription;
    }
}
