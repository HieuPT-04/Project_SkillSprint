package com.skillsprint.service.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.dto.request.admin.CreateServicePlanRequest;
import com.skillsprint.dto.request.admin.UpdatePlanFeaturesRequest;
import com.skillsprint.dto.request.admin.UpdateServicePlanRequest;
import com.skillsprint.dto.request.admin.UpdateServicePlanStatusRequest;
import com.skillsprint.dto.response.admin.AdminAuditLogResponse;
import com.skillsprint.dto.response.subscription.FeatureCatalogResponse;
import com.skillsprint.dto.response.subscription.ServicePlanResponse;
import com.skillsprint.entity.BusinessActivityLog;
import com.skillsprint.entity.Feature;
import com.skillsprint.entity.PlanFeature;
import com.skillsprint.entity.ServicePlan;
import com.skillsprint.entity.User;
import com.skillsprint.enums.log.BusinessActionType;
import com.skillsprint.enums.log.BusinessEntityType;
import com.skillsprint.enums.plan.ServicePlanType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.SubscriptionMapper;
import com.skillsprint.repository.BusinessActivityLogRepository;
import com.skillsprint.repository.FeatureRepository;
import com.skillsprint.repository.PlanFeatureRepository;
import com.skillsprint.repository.ServicePlanRepository;
import com.skillsprint.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminServicePlanService {

    ServicePlanRepository servicePlanRepository;
    FeatureRepository featureRepository;
    PlanFeatureRepository planFeatureRepository;
    BusinessActivityLogRepository activityLogRepository;
    UserRepository userRepository;
    SubscriptionMapper subscriptionMapper;
    ObjectMapper objectMapper;

    @Transactional
    public List<ServicePlanResponse> getPlans() {
        return servicePlanRepository.findAllByOrderBySortOrderAscMonthlyPriceAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ServicePlanResponse getPlan(UUID planId) {
        return toResponse(findPlan(planId));
    }

    @Transactional(readOnly = true)
    public List<FeatureCatalogResponse> getFeatures() {
        return featureRepository.findAllByOrderByFeatureNameAsc()
                .stream()
                .map(feature -> FeatureCatalogResponse.builder()
                        .featureId(feature.getFeatureId())
                        .featureKey(feature.getFeatureKey())
                        .featureName(feature.getFeatureName())
                        .description(feature.getDescription())
                        .active(feature.isActive())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminAuditLogResponse> getAuditLogs() {
        return activityLogRepository.findTop100ByEntityTypeOrderByCreatedAtDesc(BusinessEntityType.SERVICE_PLAN)
                .stream()
                .map(this::toAuditLogResponse)
                .toList();
    }

    @Transactional
    public ServicePlanResponse createPlan(String adminUserId, CreateServicePlanRequest request) {
        ServicePlan plan = new ServicePlan();
        plan.setPlanName(normalizeRequiredText(request.getPlanName(), "Tên gói không được để trống"));
        plan.setDescription(request.getDescription() == null ? null : normalizeNullableText(request.getDescription()));
        plan.setBenefits(normalizeBenefits(request.getBenefits()));
        assertPlanTypeAvailable(request.getPlanType(), null);
        plan.setPlanType(request.getPlanType());
        plan.setBadgeColor(request.getBadgeColor());
        plan.setBadgeIcon(request.getBadgeIcon());
        plan.setAnimationType(request.getAnimationType());
        validateNonNegative(request.getMonthlyPrice(), "Giá gói không được âm");
        plan.setMonthlyPrice(request.getMonthlyPrice());
        plan.setCurrency(normalizeCurrency(request.getCurrency()));
        plan.setMaxWorkspaces(validateNonNegative(defaultValue(request.getMaxWorkspaces(), 1), "Số workspace không được âm"));
        plan.setMaxUploads(validateNonNegative(defaultValue(request.getMaxUploads(), 5), "Số upload không được âm"));
        plan.setMaxCommunityRooms(validateNonNegative(defaultValue(request.getMaxCommunityRooms(), 0), "Số phòng cộng đồng không được âm"));
        plan.setAiParsingLimit(validateNonNegative(defaultValue(request.getAiGenerateLimit(), 5), "Giới hạn AI generate không được âm"));
        plan.setMaxFileMb(validatePositive(defaultValue(request.getMaxFileMb(), 20), "Dung lượng file phải lớn hơn 0"));
        plan.setMaxWorkspaceMb(validatePositive(defaultValue(request.getMaxWorkspaceMb(), 100), "Dung lượng workspace phải lớn hơn 0"));
        plan.setActive(defaultBoolean(request.getActive(), false));
        plan.setPublicVisible(defaultBoolean(request.getPublicVisible(), false));
        plan.setSortOrder(validateNonNegative(defaultValue(request.getSortOrder(), 0), "Thứ tự hiển thị không được âm"));

        ServicePlan savedPlan = servicePlanRepository.save(plan);
        ensureAllFeatureRows(savedPlan);
        if (request.getFeatures() != null && !request.getFeatures().isEmpty()) {
            applyFeatureToggles(savedPlan, request.getFeatures());
        }

        logActivity(
                adminUserId,
                savedPlan,
                BusinessActionType.SERVICE_PLAN_CREATED,
                null,
                snapshotPlan(savedPlan),
                "Tạo gói dịch vụ",
                "Admin tạo gói " + savedPlan.getPlanName()
        );

        return toResponse(savedPlan);
    }

    @Transactional
    public ServicePlanResponse updatePlan(String adminUserId, UUID planId, UpdateServicePlanRequest request) {
        ServicePlan plan = findPlan(planId);
        Map<String, Object> oldValue = snapshotPlan(plan);

        if (request.getPlanName() != null) {
            plan.setPlanName(normalizeRequiredText(request.getPlanName(), "Tên gói không được để trống"));
        }

        if (request.getDescription() != null) {
            plan.setDescription(normalizeNullableText(request.getDescription()));
        }

        if (request.getBenefits() != null) {
            plan.setBenefits(normalizeBenefits(request.getBenefits()));
        }

        if (request.getPlanType() != null) {
            assertPlanTypeAvailable(request.getPlanType(), plan.getPlanId());
            plan.setPlanType(request.getPlanType());
        }

        if (request.getBadgeColor() != null) {
            plan.setBadgeColor(request.getBadgeColor());
        }

        if (request.getBadgeIcon() != null) {
            plan.setBadgeIcon(request.getBadgeIcon());
        }

        if (request.getAnimationType() != null) {
            plan.setAnimationType(request.getAnimationType());
        }

        if (request.getMonthlyPrice() != null) {
            validateNonNegative(request.getMonthlyPrice(), "Giá gói không được âm");
            plan.setMonthlyPrice(request.getMonthlyPrice());
        }

        if (request.getCurrency() != null) {
            plan.setCurrency(normalizeCurrency(request.getCurrency()));
        }

        if (request.getMaxWorkspaces() != null) {
            plan.setMaxWorkspaces(validateNonNegative(request.getMaxWorkspaces(), "Số workspace không được âm"));
        }

        if (request.getMaxUploads() != null) {
            plan.setMaxUploads(validateNonNegative(request.getMaxUploads(), "Số upload không được âm"));
        }

        if (request.getMaxCommunityRooms() != null) {
            plan.setMaxCommunityRooms(validateNonNegative(request.getMaxCommunityRooms(), "Số phòng cộng đồng không được âm"));
        }

        if (request.getAiGenerateLimit() != null) {
            plan.setAiParsingLimit(validateNonNegative(request.getAiGenerateLimit(), "Giới hạn AI generate không được âm"));
        }

        if (request.getMaxFileMb() != null) {
            plan.setMaxFileMb(validatePositive(request.getMaxFileMb(), "Dung lượng file phải lớn hơn 0"));
        }

        if (request.getMaxWorkspaceMb() != null) {
            plan.setMaxWorkspaceMb(validatePositive(request.getMaxWorkspaceMb(), "Dung lượng workspace phải lớn hơn 0"));
        }

        if (request.getPublicVisible() != null) {
            plan.setPublicVisible(request.getPublicVisible());
        }

        if (request.getSortOrder() != null) {
            plan.setSortOrder(validateNonNegative(request.getSortOrder(), "Thứ tự hiển thị không được âm"));
        }

        ServicePlan savedPlan = servicePlanRepository.save(plan);
        logActivity(
                adminUserId,
                savedPlan,
                BusinessActionType.SERVICE_PLAN_UPDATED,
                oldValue,
                snapshotPlan(savedPlan),
                "Cập nhật gói dịch vụ",
                "Admin cập nhật thông tin gói " + savedPlan.getPlanName()
        );

        return toResponse(savedPlan);
    }

    @Transactional
    public ServicePlanResponse updateStatus(String adminUserId, UUID planId, UpdateServicePlanStatusRequest request) {
        ServicePlan plan = findPlan(planId);
        Map<String, Object> oldValue = snapshotPlanStatus(plan);

        if (request.getActive() != null) {
            plan.setActive(request.getActive());
        }

        if (request.getPublicVisible() != null) {
            plan.setPublicVisible(request.getPublicVisible());
        }

        ServicePlan savedPlan = servicePlanRepository.save(plan);
        logActivity(
                adminUserId,
                savedPlan,
                BusinessActionType.SERVICE_PLAN_STATUS_UPDATED,
                oldValue,
                snapshotPlanStatus(savedPlan),
                "Cập nhật trạng thái gói",
                "Admin cập nhật trạng thái gói " + savedPlan.getPlanName()
        );

        return toResponse(savedPlan);
    }

    @Transactional
    public ServicePlanResponse updateFeatures(String adminUserId, UUID planId, UpdatePlanFeaturesRequest request) {
        ServicePlan plan = findPlan(planId);
        ensureAllFeatureRows(plan);
        Map<String, Object> oldValue = snapshotFeatures(plan);
        applyFeatureToggles(plan, request.getFeatures());
        Map<String, Object> newValue = snapshotFeatures(plan);
        logActivity(
                adminUserId,
                plan,
                BusinessActionType.SERVICE_PLAN_FEATURES_UPDATED,
                oldValue,
                newValue,
                "Cập nhật tính năng gói",
                "Admin cập nhật tính năng của gói " + plan.getPlanName()
        );
        return toResponse(plan);
    }

    private ServicePlan findPlan(UUID planId) {
        return servicePlanRepository.findById(planId)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_PLAN_NOT_FOUND));
    }

    // plan_type is unique per plan; reject collisions with a clean 400 instead of a raw DB error.
    private void assertPlanTypeAvailable(ServicePlanType planType, UUID currentPlanId) {
        if (planType == null) {
            return;
        }
        servicePlanRepository.findByPlanType(planType).ifPresent(existing -> {
            if (!existing.getPlanId().equals(currentPlanId)) {
                throw new AppException(
                        ErrorCode.VALIDATION_ERROR,
                        "Loại gói '" + planType + "' đã được sử dụng bởi một gói khác");
            }
        });
    }

    /**
     * Seeds the internal ADMIN_DEFAULT plan once at startup if it does not exist yet.
     * Runs on bean init; uses the repository directly (its save() opens its own transaction).
     * Fail-soft: a seeding error must never block application startup.
     */
    @PostConstruct
    void seedAdminDefaultPlan() {
        try {
            if (servicePlanRepository.existsByPlanType(ServicePlanType.ADMIN_DEFAULT)) {
                return;
            }
            ServicePlan plan = new ServicePlan();
            plan.setPlanType(ServicePlanType.ADMIN_DEFAULT);
            plan.setPlanName("Admin Default");
            plan.setDescription("Gói nội bộ dành cho quản trị viên — mở khóa toàn bộ quota và tính năng.");
            plan.setBenefits(List.of(
                    "Toàn quyền truy cập hệ thống",
                    "Quota tối đa cho mọi tài nguyên",
                    "Không giới hạn lượt AI generate"
            ));
            plan.setMonthlyPrice(BigDecimal.ZERO);
            plan.setCurrency("VND");
            plan.setMaxWorkspaces(999999);
            plan.setMaxUploads(999999);
            plan.setMaxCommunityRooms(999999);
            plan.setAiParsingLimit(999999);
            plan.setMaxFileMb(999999);
            plan.setMaxWorkspaceMb(999999);
            plan.setActive(true);
            plan.setPublicVisible(false); // internal: never shown on the public pricing page
            plan.setSortOrder(99);
            plan.setBadgeColor("from-pink-500 via-purple-500 to-indigo-600 text-white shadow-purple-500/30");
            plan.setBadgeIcon("ShieldAlert");
            plan.setAnimationType("pulse");
            servicePlanRepository.save(plan);
            log.info("Seeded ADMIN_DEFAULT service plan");
        } catch (Exception ex) {
            log.warn("Could not seed ADMIN_DEFAULT plan at startup", ex);
        }
    }

    private ServicePlanResponse toResponse(ServicePlan plan) {
        ensureAllFeatureRows(plan);
        return subscriptionMapper.toServicePlanResponse(plan, planFeatureRepository.findByPlanPlanId(plan.getPlanId()));
    }

    private void ensureAllFeatureRows(ServicePlan plan) {
        List<PlanFeature> existing = planFeatureRepository.findByPlanPlanId(plan.getPlanId());
        List<String> existingKeys = existing.stream()
                .map(planFeature -> planFeature.getFeature().getFeatureKey())
                .toList();

        List<PlanFeature> missing = featureRepository.findAllByOrderByFeatureNameAsc()
                .stream()
                .filter(feature -> !existingKeys.contains(feature.getFeatureKey()))
                .map(feature -> {
                    PlanFeature planFeature = new PlanFeature();
                    planFeature.setPlan(plan);
                    planFeature.setFeature(feature);
                    planFeature.setEnabled(false);
                    return planFeature;
                })
                .toList();

        if (!missing.isEmpty()) {
            planFeatureRepository.saveAll(missing);
        }
    }

    private void applyFeatureToggles(ServicePlan plan, List<UpdatePlanFeaturesRequest.FeatureToggle> toggles) {
        for (UpdatePlanFeaturesRequest.FeatureToggle toggle : toggles) {
            String featureKey = normalizeRequiredText(toggle.getFeatureKey(), "Mã tính năng không được để trống");
            Feature feature = featureRepository.findByFeatureKey(featureKey)
                    .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR, "Tính năng không tồn tại: " + featureKey));

            PlanFeature planFeature = planFeatureRepository.findByPlanPlanIdAndFeatureFeatureKey(
                            plan.getPlanId(),
                            feature.getFeatureKey()
                    )
                    .orElseGet(() -> {
                        PlanFeature created = new PlanFeature();
                        created.setPlan(plan);
                        created.setFeature(feature);
                        return created;
                    });
            planFeature.setEnabled(Boolean.TRUE.equals(toggle.getEnabled()));
            planFeatureRepository.save(planFeature);
        }
    }

    private Map<String, Object> snapshotPlan(ServicePlan plan) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("planId", plan.getPlanId());
        values.put("planName", plan.getPlanName());
        values.put("description", plan.getDescription());
        values.put("benefits", plan.getBenefits());
        values.put("planType", plan.getPlanType());
        values.put("monthlyPrice", plan.getMonthlyPrice());
        values.put("currency", plan.getCurrency());
        values.put("maxWorkspaces", plan.getMaxWorkspaces());
        values.put("maxUploads", plan.getMaxUploads());
        values.put("maxCommunityRooms", plan.getMaxCommunityRooms());
        values.put("aiGenerateLimit", plan.getAiParsingLimit());
        values.put("maxFileMb", plan.getMaxFileMb());
        values.put("maxWorkspaceMb", plan.getMaxWorkspaceMb());
        values.put("active", plan.isActive());
        values.put("publicVisible", plan.getPublicVisible());
        values.put("sortOrder", plan.getSortOrder());
        return values;
    }

    private Map<String, Object> snapshotPlanStatus(ServicePlan plan) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("active", plan.isActive());
        values.put("publicVisible", plan.getPublicVisible());
        return values;
    }

    private Map<String, Object> snapshotFeatures(ServicePlan plan) {
        Map<String, Object> values = new LinkedHashMap<>();
        planFeatureRepository.findByPlanPlanId(plan.getPlanId())
                .forEach(planFeature -> values.put(
                        planFeature.getFeature().getFeatureKey(),
                        planFeature.isEnabled()
                ));
        return values;
    }

    private void logActivity(
            String adminUserId,
            ServicePlan plan,
            BusinessActionType actionType,
            Map<String, Object> oldValue,
            Map<String, Object> newValue,
            String title,
            String description
    ) {
        BusinessActivityLog log = new BusinessActivityLog();
        if (adminUserId != null && !adminUserId.isBlank()) {
            User admin = userRepository.findById(adminUserId).orElse(null);
            log.setUser(admin);
        }
        log.setEntityType(BusinessEntityType.SERVICE_PLAN);
        log.setEntityId(plan.getPlanId());
        log.setActionType(actionType);
        log.setTitle(title);
        log.setDescription(description);
        log.setOldValue(toJson(oldValue));
        log.setNewValue(toJson(newValue));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("planName", plan.getPlanName());
        metadata.put("planType", plan.getPlanType());
        metadata.put("adminUserId", adminUserId);
        log.setMetadata(toJson(metadata));

        activityLogRepository.save(log);
    }

    private AdminAuditLogResponse toAuditLogResponse(BusinessActivityLog log) {
        User admin = log.getUser();
        return AdminAuditLogResponse.builder()
                .logId(log.getLogId())
                .adminUserId(admin == null ? null : admin.getUserId())
                .adminEmail(admin == null ? null : admin.getEmail())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .actionType(log.getActionType())
                .title(log.getTitle())
                .description(log.getDescription())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .metadata(log.getMetadata())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private String toJson(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String normalizeNullableText(String value) {
        if (value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private List<String> normalizeBenefits(List<String> benefits) {
        if (benefits == null) {
            return List.of();
        }

        return benefits.stream()
                .filter(benefit -> benefit != null && !benefit.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String normalizeRequiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, message);
        }
        return value.trim();
    }

    private String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            return "VND";
        }
        return value.trim().toUpperCase();
    }

    private void validateNonNegative(BigDecimal value, String message) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private int validateNonNegative(int value, String message) {
        if (value < 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, message);
        }
        return value;
    }

    private int validatePositive(int value, String message) {
        if (value <= 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, message);
        }
        return value;
    }

    private int defaultValue(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private boolean defaultBoolean(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }
}
