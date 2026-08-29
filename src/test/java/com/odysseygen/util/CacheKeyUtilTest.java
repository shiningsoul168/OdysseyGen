package com.odysseygen.util;

import com.odysseygen.dto.request.ProfileRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CacheKeyUtilTest {

    private final CacheKeyUtil cacheKeyUtil = new CacheKeyUtil();

    @Test
    void testSameProfileGeneratesSameKey() {
        assertEquals(cacheKeyUtil.generateCacheKey(buildProfile()), cacheKeyUtil.generateCacheKey(buildProfile()));
    }

    @Test
    void testPersonalityTagsOrderDoesNotMatter() {
        ProfileRequest r1 = buildProfile();
        r1.setPersonalityTags(new String[]{"逻辑强", "外向"});
        ProfileRequest r2 = buildProfile();
        r2.setPersonalityTags(new String[]{"外向", "逻辑强"});

        assertEquals(cacheKeyUtil.generateCacheKey(r1), cacheKeyUtil.generateCacheKey(r2));
    }

    @Test
    void testDifferentMajorGeneratesDifferentKey() {
        ProfileRequest r1 = buildProfile();
        r1.setMajor("软件工程");
        ProfileRequest r2 = buildProfile();
        r2.setMajor("计算机科学");

        assertNotEquals(cacheKeyUtil.generateCacheKey(r1), cacheKeyUtil.generateCacheKey(r2));
    }

    private ProfileRequest buildProfile() {
        ProfileRequest request = new ProfileRequest();
        request.setGoalType(1);
        request.setMajor("软件工程");
        request.setGpa(3.5);
        request.setSchoolLevel(3);
        request.setEnglishLevel(1);
        request.setIsPartyMember(false);
        request.setGraduationYear(2027);
        request.setPersonalityTags(new String[]{"逻辑强", "外向"});
        return request;
    }
}
