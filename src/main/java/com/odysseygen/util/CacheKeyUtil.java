package com.odysseygen.util;

import com.odysseygen.constant.CacheConstants;
import com.odysseygen.dto.request.ProfileRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
@Slf4j
public class CacheKeyUtil {

    public String generateCacheKey(ProfileRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getGoalType()).append("|");
        sb.append(request.getMajor()).append("|");
        sb.append(request.getGpa()).append("|");
        sb.append(request.getSchoolLevel()).append("|");
        sb.append(request.getEnglishLevel()).append("|");
        sb.append(request.getIsPartyMember()).append("|");
        sb.append(request.getGraduationYear()).append("|");

        // 处理 goalData，确保数组有序
        if (request.getGoalData() != null) {
            TreeMap<String, Object> sorted = new TreeMap<>(request.getGoalData());
            sb.append(stringifySortedMap(sorted));
        }
        sb.append("|");

        // 处理 personalityTags，排序后拼接
        if (request.getPersonalityTags() != null && request.getPersonalityTags().length > 0) {
            String[] sortedTags = request.getPersonalityTags().clone();
            Arrays.sort(sortedTags);
            sb.append(String.join(",", sortedTags));
        }

        String raw = sb.toString();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }

            String key = CacheConstants.CACHE_PREFIX + hex;
            log.debug("生成缓存 Key: {} (画像摘要: {})", key, raw.substring(0, Math.min(raw.length(), 30)) + "...");
            return key;

        } catch (Exception e) {
            log.error("生成缓存 Key 失败", e);
            return CacheConstants.CACHE_PREFIX + System.currentTimeMillis() + "-" + request.hashCode();
        }
    }

    /**
     * 将 Map 转换为有序字符串，并对数组进行排序
     */
    private String stringifySortedMap(TreeMap<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append(entry.getKey()).append("=");
            sb.append(stringifyValue(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 递归处理值，对数组排序
     */
    private String stringifyValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Collection) {
            // 对集合排序
            List<Object> list = new ArrayList<>((Collection<?>) value);
            // 转字符串排序（保证稳定）
            list.sort(Comparator.comparing(Object::toString));
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(stringifyValue(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        } else if (value instanceof Map) {
            // 递归处理嵌套 Map
            TreeMap<String, Object> sortedMap = new TreeMap<>((Map<String, Object>) value);
            return stringifySortedMap(sortedMap);
        } else {
            return value.toString();
        }
    }
}
