package com.odysseygen.util;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class DeepSeekUtil {

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${deepseek.api.model:deepseek-v4-pro}")
    private String modelName;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public DeepSeekUtil() {
        // ✅ 配置超时：连接超时 5s，读取超时 35s
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
        log.info("DeepSeek API 客户端初始化完成，连接超时: 5s, 读取超时: 60s");
    }
    /**
     * 根据用户画像生成三条职业路径
     *
     * @param goalType    目标类型（1-就业/2-考研/3-考公）
     * @param profileJson 用户画像 JSON
     * @return AI 返回的原始 JSON 字符串（包含三条路径）
     */
    public String generatePaths(Integer goalType, String profileJson, Integer graduationYear, Map<String, Object> goalData) {
        String prompt = buildPrompt(goalType, profileJson, graduationYear, goalData);

        // 2. 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "你是一名资深职业规划专家，擅长根据用户画像生成三条差异化的职业发展路径。必须返回合法的 JSON 格式。特别注意：salaryExpectation 字段绝对不可为 null，必须根据用户的专业、GPA、学校层次给出合理数值。"),
                Map.of("role", "user", "content", prompt)
        ));
        requestBody.put("temperature", 0.7);
        requestBody.put("response_format", Map.of("type", "json_object"));

        // 3. 发送请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);
        log.info("DeepSeek 原始响应: {}", response);
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("DeepSeek API 调用失败: {}", response.getStatusCode());
            throw new RuntimeException("AI 服务调用失败");
        }

// 4. 解析响应，提取 choices[0].message.content
        String responseBodyStr = response.getBody();

// ✅ 先判空
        if (responseBodyStr == null || responseBodyStr.isEmpty()) {
            log.error("DeepSeek API 返回空响应");
            throw new RuntimeException("AI 服务返回空响应");
        }

// ✅ 用已保存的 responseBodyStr 解析，而不是再调一次 response.getBody()
        Map<String, Object> responseBody = objectMapper.readValue(responseBodyStr, Map.class);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("AI 返回结果为空");
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }

    private String buildPrompt(Integer goalType, String profileJson, Integer graduationYear, Map<String, Object> goalData) {
        String goalTypeName = switch (goalType) {
            case 1 -> "就业";
            case 2 -> "考研";
            case 3 -> "考公";
            default -> "未知";
        };

        int currentYear = java.time.LocalDate.now().getYear();
        int grade = graduationYear - currentYear;
        String gradeName;
        if (grade >= 4) {
            gradeName = "大一";
        } else if (grade == 3) {
            gradeName = "大二";
        } else if (grade == 2) {
            gradeName = "大三";
        } else if (grade == 1) {
            gradeName = "大四";
        } else if (grade == 0) {
            gradeName = "已毕业";
        } else {
            gradeName = "大三";  // 兜底
        }

        String salaryConstraint = "";
        if (goalType == 1 && goalData != null) {
            Integer minSalary = (Integer) goalData.get("expectedSalaryMin");
            Integer maxSalary = (Integer) goalData.get("expectedSalaryMax");
            if (minSalary != null || maxSalary != null) {
                salaryConstraint = String.format(
                        "该用户的期望薪资范围为 %d K - %d K/月。请优先推荐在该薪资范围内的岗位方向。",
                        minSalary != null ? minSalary : 0,
                        maxSalary != null ? maxSalary : 999
                );
            }
        }

        return """
            你是一名资深职业规划专家。请根据以下用户画像，生成三条差异化的职业发展路径。

            ## 用户画像（JSON）
            %s

            ## 目标类型
            %s

            ## 期望薪资约束
            %s

                ## ⚠️ 重要：时间线规则
                - 该用户是**本科生**，预计 %d 年毕业。
                - 当前年级是 **%s**。
                - timeline（时间线）必须从 **%s** 开始，一直规划到毕业后的第 2 年。
                - 例如：如果当前是大三，则时间线应为：大三 → 大四 → 毕业后第1年 → 毕业后第2年。
                - **绝对不要**使用“研一、研二、研三”。
                - ⚠️ **忽略下方示例中的大一/大二/大三/大四模板，以本规则为准！**  // ✅ 新增这句
                - 请根据当前年级合理安排每个阶段的具体行动。

            ## 字段要求
            每条路径必须包含以下字段：pathType, pathName, pathSummary, description, timeline, keyNodes, skillGap, salaryExpectation, riskFactors, recommendedActions，stopLossAdvice。
            ## 输出格式要求
            只输出合法 JSON，不要用 ```json 包裹，不要有任何多余文字。
            JSON 根对象为 {"paths": [...]}，paths 数组恰好包含 3 个元素。
            
                ## stopLossAdvice 格式要求
                {
                  "trigger": "触发条件（量化指标，如：大三暑假结束前未获得实习offer）",
                  "action": "建议切换的路径和具体行动",
                  "deadline": "截止时间节点",
                  "alternativePath": "建议切换到的路径名称"
                }

            ## 参考示例（请严格按照此格式生成）
            {
              "paths": [
                {
                  "pathType": 1,
                  "pathName": "Java后端开发",
                  "pathSummary": "务实路线，进入互联网公司",
                  "description": "该路径适合计算机专业学生，GPA较高...",
                  "timeline": [
                    {"year": "大一", "action": "学习Java基础"},
                    {"year": "大二", "action": "学习Spring Boot"},
                    {"year": "大三", "action": "寻找实习"},
                    {"year": "大四", "action": "参加校招"}
                  ],
                  "keyNodes": [
                    {"node": "完成第一个项目", "deadline": "大二暑假"},
                    {"node": "获得实习offer", "deadline": "大三暑假"},
                    {"node": "拿到校招offer", "deadline": "大四上学期"}
                  ],
                  "skillGap": ["Spring Cloud", "Redis", "分布式系统"],
                  "salaryExpectation": {"entry": 15, "mid": 30, "senior": 50},
                  "riskFactors": ["竞争激烈", "技术更新快"],
                  "recommendedActions": ["刷LeetCode", "参加开源项目"]
                },
                {
                  "pathType": 2,
                  "pathName": "测试开发",
                  "pathSummary": "备选路线，稳定发展",
                  "description": "测试开发岗位需求量大...",
                  "timeline": [
                    {"year": "大一", "action": "学习编程基础"},
                    {"year": "大二", "action": "学习测试工具"},
                    {"year": "大三", "action": "寻找测试实习"},
                    {"year": "大四", "action": "参加校招"}
                  ],
                  "keyNodes": [
                    {"node": "掌握自动化测试", "deadline": "大二暑假"},
                    {"node": "获得测试实习", "deadline": "大三暑假"},
                    {"node": "拿到测试offer", "deadline": "大四上学期"}
                  ],
                  "skillGap": ["自动化测试", "Python", "Linux"],
                  "salaryExpectation": {"entry": 10, "mid": 20, "senior": 35},
                  "riskFactors": ["薪资上限较低", "岗位较少"],
                  "recommendedActions": ["学习pytest", "考取ISTQB证书"]
                },
                {
                  "pathType": 3,
                  "pathName": "独立开发者",
                  "pathSummary": "理想路线，追求自由",
                  "description": "独立开发者可以自由选择技术栈...",
                  "timeline": [
                    {"year": "大一", "action": "学习前端基础"},
                    {"year": "大二", "action": "学习全栈开发"},
                    {"year": "大三", "action": "开发产品上线"},
                    {"year": "大四", "action": "运营推广"}
                  ],
                  "keyNodes": [
                    {"node": "上线第一个产品", "deadline": "大二暑假"},
                    {"node": "获得100+用户", "deadline": "大三暑假"},
                    {"node": "月收入过万", "deadline": "大四上学期"}
                  ],
                  "skillGap": ["产品设计", "运营推广", "项目管理"],
                  "salaryExpectation": {"entry": 5, "mid": 20, "senior": 50},
                  "riskFactors": ["收入不稳定", "需要自律"],
                  "recommendedActions": ["学习产品设计", "参加黑客松"]
                }
              ]
            }
            """.formatted(profileJson, goalTypeName, salaryConstraint, graduationYear, gradeName, gradeName);
    }
}
