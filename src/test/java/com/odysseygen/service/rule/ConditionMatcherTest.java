package com.odysseygen.service.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionMatcherTest {

    private ConditionMatcher conditionMatcher;

    @BeforeEach
    void setUp() {
        conditionMatcher = new ConditionMatcher(new ObjectMapper());
    }

    private boolean matches(String conditionJson, Map<String, Object> context) {
        return conditionMatcher.matches(conditionJson, context);
    }

    @Test
    void testNumericOperators() {
        Map<String, Object> ctx = Map.of("gpa", 3.5, "internshipCount", 2);

        assertTrue(matches("{\"field\":\"gpa\",\"operator\":\"==\",\"value\":3.5}", ctx));
        assertTrue(matches("{\"field\":\"gpa\",\"operator\":\"=\",\"value\":3.5}", ctx));
        assertFalse(matches("{\"field\":\"gpa\",\"operator\":\"==\",\"value\":3.0}", ctx));
        assertTrue(matches("{\"field\":\"gpa\",\"operator\":\"!=\",\"value\":3.0}", ctx));
        assertTrue(matches("{\"field\":\"gpa\",\"operator\":\">\",\"value\":3.0}", ctx));
        assertTrue(matches("{\"field\":\"gpa\",\"operator\":\">=\",\"value\":3.5}", ctx));
        assertFalse(matches("{\"field\":\"gpa\",\"operator\":\">\",\"value\":4.0}", ctx));
        assertTrue(matches("{\"field\":\"gpa\",\"operator\":\"<\",\"value\":4.0}", ctx));
        assertTrue(matches("{\"field\":\"gpa\",\"operator\":\"<=\",\"value\":3.5}", ctx));
        assertTrue(matches("{\"field\":\"internshipCount\",\"operator\":\">=\",\"value\":2}", ctx));
        assertTrue(matches("{\"field\":\"gpa\",\"operator\":\"between\",\"value\":3.0,\"value2\":4.0}", ctx));
        assertFalse(matches("{\"field\":\"gpa\",\"operator\":\"between\",\"value\":4.0,\"value2\":5.0}", ctx));
    }

    @Test
    void testStringAndInOperators() {
        Map<String, Object> ctx = Map.of("schoolLevel", "985", "major", "软件工程");

        assertTrue(matches("{\"field\":\"schoolLevel\",\"operator\":\"==\",\"value\":\"985\"}", ctx));
        assertFalse(matches("{\"field\":\"schoolLevel\",\"operator\":\"==\",\"value\":\"211\"}", ctx));
        assertTrue(matches("{\"field\":\"schoolLevel\",\"operator\":\"!=\",\"value\":\"211\"}", ctx));
        assertTrue(matches("{\"field\":\"major\",\"operator\":\"in\",\"value\":[\"软件工程\",\"计算机\"]}", ctx));
        assertFalse(matches("{\"field\":\"major\",\"operator\":\"in\",\"value\":[\"金融\",\"法律\"]}", ctx));
    }

    @Test
    void testBooleanOperators() {
        Map<String, Object> ctx = Map.of("isPartyMember", true);
        assertTrue(matches("{\"field\":\"isPartyMember\",\"operator\":\"==\",\"value\":true}", ctx));
        assertFalse(matches("{\"field\":\"isPartyMember\",\"operator\":\"==\",\"value\":false}", ctx));
        assertTrue(matches("{\"field\":\"isPartyMember\",\"operator\":\"!=\",\"value\":false}", ctx));
    }

    @Test
    void testEdgeCases() {
        Map<String, Object> ctx = Map.of("gpa", 3.0);
        // 字段不存在 → false
        assertFalse(matches("{\"field\":\"notExist\",\"operator\":\"==\",\"value\":1}", ctx));
        // 空条件 / null → true（无条件规则命中所有）
        assertTrue(matches(null, ctx));
        assertTrue(matches("", ctx));
        // 非法 JSON → false（不抛异常）
        assertFalse(matches("not-json", ctx));
        // 未知操作符 → false
        assertFalse(matches("{\"field\":\"gpa\",\"operator\":\"like\",\"value\":3.0}", ctx));
    }

    @Test
    void testApplicableGoalTypes() {
        Map<String, Object> ctx = Map.of("goalType", 1);
        // 适用类型匹配
        assertTrue(conditionMatcher.applicable("1", ctx));
        assertTrue(conditionMatcher.applicable("1,2,3", ctx));
        assertTrue(conditionMatcher.applicable("2,1", ctx));
        // 不适用
        assertFalse(conditionMatcher.applicable("2", ctx));
        assertFalse(conditionMatcher.applicable("2,3", ctx));
        // 空 → 所有都适用
        assertTrue(conditionMatcher.applicable(null, ctx));
        assertTrue(conditionMatcher.applicable("", ctx));
    }
}
