package com.odysseygen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odysseygen.dto.request.ProfileRequest;
import com.odysseygen.dto.response.PathResponse;
import com.odysseygen.entity.PlanRecord;
import com.odysseygen.entity.PathDetail;
import com.odysseygen.entity.UserProfile;
import com.odysseygen.mapper.PathDetailMapper;
import com.odysseygen.mapper.PlanRecordMapper;
import com.odysseygen.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 规划落库服务。
 * 单独拆成一个 Bean，是为了让 @Transactional 通过 Spring 代理生效，
 * 并且事务只包裹数据库写入，不包裹 AI 调用和 JSON 序列化/反序列化。
 */
@Service
@RequiredArgsConstructor
public class PlanPersistenceService {

    private final UserProfileMapper userProfileMapper;
    private final PlanRecordMapper planRecordMapper;
    private final PathDetailMapper pathDetailMapper;
    private final ObjectMapper objectMapper;
    private final SalaryCalculator salaryCalculator;

    /** 只负责落库（事务只包 DB 写入），返回 planId */
    @Transactional(rollbackFor = Exception.class)
    public Long savePlan(Long userId, ProfileRequest request, List<Map<String, Object>> paths) throws Exception {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setGoalType(request.getGoalType());
        profile.setMajor(request.getMajor());
        profile.setGpa(request.getGpa());
        profile.setSchoolLevel(request.getSchoolLevel());
        profile.setEnglishLevel(request.getEnglishLevel());
        profile.setIsPartyMember(request.getIsPartyMember() != null && request.getIsPartyMember());
        profile.setGraduationYear(request.getGraduationYear());
        profile.setGoalSpecificData(objectMapper.writeValueAsString(request.getGoalData()));
        profile.setPersonalityTags(objectMapper.writeValueAsString(request.getPersonalityTags()));
        profile.setIsActive(true);
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        userProfileMapper.insert(profile);

        PlanRecord plan = new PlanRecord();
        plan.setUserId(userId);
        plan.setProfileId(profile.getProfileId());
        plan.setIsFavorite(false);
        plan.setIsDeleted(false);
        plan.setGoalType(request.getGoalType());
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planRecordMapper.insert(plan);

        for (int i = 0; i < paths.size(); i++) {
            Map<String, Object> p = paths.get(i);
            PathDetail detail = new PathDetail();
            detail.setPlanId(plan.getPlanId());
            detail.setPathType((Integer) p.getOrDefault("pathType", i + 1));
            detail.setPathName((String) p.get("pathName"));
            detail.setPathSummary((String) p.get("pathSummary"));
            detail.setDescription((String) p.get("description"));
            detail.setTimeline(objectMapper.writeValueAsString(p.get("timeline")));
            detail.setKeyNodes(objectMapper.writeValueAsString(p.get("keyNodes")));
            detail.setSkillGap(objectMapper.writeValueAsString(p.get("skillGap")));
            detail.setSalaryExpectation(objectMapper.writeValueAsString(resolveSalary(p, request, detail.getPathType())));

            Object stopLossObj = p.get("stopLossAdvice");
            if (stopLossObj != null) {
                detail.setStopLossAdvice(objectMapper.writeValueAsString(stopLossObj));
            }

            detail.setRiskFactors(objectMapper.writeValueAsString(p.get("riskFactors")));
            detail.setRecommendedActions(objectMapper.writeValueAsString(p.get("recommendedActions")));
            detail.setSortOrder(i + 1);
            detail.setCreatedAt(LocalDateTime.now());
            pathDetailMapper.insert(detail);
        }

        return plan.getPlanId();
    }

    /** 构建返回对象（纯内存 + JSON 转换，不在事务里） */
    @SuppressWarnings("unchecked")
    public PathResponse buildResponse(Long planId, List<Map<String, Object>> paths, ProfileRequest request) {
        List<PathResponse.PathItem> items = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            Map<String, Object> p = paths.get(i);
            Integer pathType = (Integer) p.getOrDefault("pathType", i + 1);

            PathResponse.PathItem item = new PathResponse.PathItem();
            item.setPathType(pathType);
            item.setPathName((String) p.get("pathName"));
            item.setPathSummary((String) p.get("pathSummary"));
            item.setDescription((String) p.get("description"));
            item.setTimeline((List<Map<String, String>>) p.get("timeline"));
            item.setKeyNodes((List<Map<String, String>>) p.get("keyNodes"));
            item.setSkillGap((List<String>) p.get("skillGap"));
            item.setSalaryExpectation(objectMapper.convertValue(
                    resolveSalary(p, request, pathType), PathResponse.SalaryExpectation.class));

            Object stopLossObj = p.get("stopLossAdvice");
            if (stopLossObj != null) {
                item.setStopLossAdvice(objectMapper.convertValue(stopLossObj, PathResponse.StopLossAdvice.class));
            }

            item.setRiskFactors((List<String>) p.get("riskFactors"));
            item.setRecommendedActions((List<String>) p.get("recommendedActions"));
            items.add(item);
        }

        PathResponse response = new PathResponse();
        response.setPlanId(planId);
        response.setPaths(items);
        return response;
    }

    /** AI 未返回 salaryExpectation 时，用画像 + 规则引擎兜底计算 */
    private Object resolveSalary(Map<String, Object> p, ProfileRequest request, Integer pathType) {
        Object salaryObj = p.get("salaryExpectation");
        if (salaryObj == null) {
            return salaryCalculator.calculate(request.getGoalType(), pathType, request);
        }
        return salaryObj;
    }
}
