package com.odysseygen.service;

import com.odysseygen.dto.request.ProfileRequest;
import com.odysseygen.service.rule.impl.SalaryRuleEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalaryCalculatorTest {

    @Mock
    private SalaryRuleEngine salaryRuleEngine;

    @InjectMocks
    private SalaryCalculator salaryCalculator;

    @Test
    void testEmploymentSalaryUsesRuleFactor() {
        when(salaryRuleEngine.evaluate(any(), anyDouble())).thenReturn(1.0);

        ProfileRequest request = new ProfileRequest();
        request.setGoalType(1);
        request.setGpa(3.0);
        request.setSchoolLevel(3);
        request.setEnglishLevel(1);

        Map<String, Integer> result = salaryCalculator.calculate(1, 1, request);

        // 就业 pathType=1 基础薪资 12/22/40，factor=1.0，无城市 → cityFactor=1.0
        assertEquals(12, result.get("entry"));
        assertEquals(22, result.get("mid"));
        assertEquals(40, result.get("senior"));
    }

    @Test
    void testPostgraduateScore() {
        ProfileRequest request = new ProfileRequest();
        request.setGoalType(2);
        request.setGpa(4.0);
        request.setSchoolLevel(1); // 985/211

        Map<String, Integer> result = salaryCalculator.calculate(2, 1, request);

        // gpaFactor=1.0, schoolFactor=0.70 → score=0.70
        assertEquals(35, result.get("entry"));   // round(50*0.7)
        assertEquals(18, result.get("mid"));     // round(25*0.7)=17.5→18
        assertEquals(7, result.get("senior"));   // round(10*0.7)
    }

    @Test
    void testCivilServiceRank() {
        ProfileRequest request = new ProfileRequest();
        request.setGoalType(3);
        request.setIsPartyMember(true);
        request.setSchoolLevel(1);

        Map<String, Integer> result = salaryCalculator.calculate(3, 1, request);

        assertEquals(1, result.get("entry"));
        assertEquals(4, result.get("mid"));     // 3 + 1(党员)
        assertEquals(8, result.get("senior")); // 6 + 2(985/211)
    }
}
