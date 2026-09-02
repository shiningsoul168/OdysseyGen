-- =====================================================================
-- OdysseyGen 数据库初始化脚本
-- 环境：MySQL 8.0+
-- 说明：全新安装时直接执行本脚本即可；已存在的库请勿重复执行（会建表冲突）。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS career_plan
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE career_plan;

-- =====================================================================
-- 1. 用户表
-- =====================================================================
CREATE TABLE `user_info` (
    `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID（主键）',
    `username` varchar(64) NOT NULL COMMENT '登录名（唯一）',
    `password` varchar(255) NOT NULL COMMENT '加密密码（BCrypt）',
    `email` varchar(128) DEFAULT NULL COMMENT '邮箱',
    `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
    `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名（可选）',
    `phone` varchar(20) DEFAULT NULL COMMENT '手机号（可选）',
    `status` tinyint(1) DEFAULT '1' COMMENT '1-正常 0-禁用',
    `role` varchar(20) DEFAULT 'USER' COMMENT '角色：USER / ADMIN',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `major` varchar(100) DEFAULT NULL COMMENT '专业名称',
    `gpa` decimal(3,2) DEFAULT NULL COMMENT 'GPA',
    `english_level` tinyint DEFAULT NULL COMMENT '英语水平',
    `school_level` tinyint DEFAULT NULL COMMENT '学校层次',
    `graduation_year` int DEFAULT NULL COMMENT '毕业年份',
    `personality_tags` json DEFAULT NULL COMMENT '性格标签',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- =====================================================================
-- 2. 用户画像表
-- =====================================================================
CREATE TABLE `user_profiles` (
    `profile_id` bigint NOT NULL AUTO_INCREMENT COMMENT '画像ID（主键）',
    `user_id` bigint NOT NULL COMMENT '所属用户ID',
    `goal_type` tinyint NOT NULL COMMENT '目标类型: 1-就业 2-考研 3-考公',
    `major` varchar(100) NOT NULL COMMENT '专业名称',
    `gpa` decimal(3,2) DEFAULT NULL COMMENT 'GPA',
    `school_level` tinyint DEFAULT NULL COMMENT '学校层次: 1-985/211 2-双一流 3-普通本科 4-专科',
    `english_level` tinyint DEFAULT NULL COMMENT '英语水平: 1-CET-4 2-CET-6 3-雅思/托福 4-无',
    `is_party_member` tinyint(1) DEFAULT '0' COMMENT '是否党员',
    `graduation_year` int DEFAULT NULL COMMENT '预计毕业年份',
    `goal_specific_data` json NOT NULL COMMENT '目标专属数据',
    `personality_tags` json DEFAULT NULL COMMENT '性格标签',
    `is_active` tinyint(1) DEFAULT '1' COMMENT '1-当前活跃 0-历史画像',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`profile_id`),
    KEY `idx_goal_type` (`goal_type`),
    KEY `idx_active` (`is_active`),
    KEY `idx_user_active` (`user_id`,`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户画像表';

-- =====================================================================
-- 3. 职业规划主记录表
-- =====================================================================
CREATE TABLE `plan_records` (
    `plan_id` bigint NOT NULL AUTO_INCREMENT COMMENT '规划ID（主键）',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `profile_id` bigint NOT NULL COMMENT '画像ID（快照）',
    `generation_prompt` text COMMENT '生成时使用的Prompt',
    `generation_cost` decimal(10,6) DEFAULT NULL COMMENT 'AI调用成本（元）',
    `response_time_ms` int DEFAULT NULL COMMENT 'AI响应耗时（毫秒）',
    `is_favorite` tinyint(1) DEFAULT '0' COMMENT '是否收藏',
    `is_deleted` tinyint(1) DEFAULT '0' COMMENT '0-正常 1-已删除（逻辑删除）',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `goal_type` tinyint DEFAULT NULL COMMENT '目标类型(冗余)',
    PRIMARY KEY (`plan_id`),
    KEY `idx_profile_id` (`profile_id`),
    KEY `idx_user_created` (`user_id`,`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='职业规划主记录表';

-- =====================================================================
-- 4. 路径详情表
-- =====================================================================
CREATE TABLE `path_details` (
    `path_id` bigint NOT NULL AUTO_INCREMENT COMMENT '路径详情ID（主键）',
    `plan_id` bigint NOT NULL COMMENT '所属规划ID',
    `path_type` tinyint NOT NULL COMMENT '1-主流 2-备用 3-理想',
    `path_name` varchar(100) NOT NULL COMMENT '路径标题',
    `path_summary` varchar(500) DEFAULT NULL COMMENT '一句话总结',
    `description` text COMMENT '路径详细描述',
    `timeline` json DEFAULT NULL COMMENT '时间线',
    `key_nodes` json DEFAULT NULL COMMENT '关键里程碑',
    `skill_gap` json DEFAULT NULL COMMENT '技能差距',
    `salary_expectation` json DEFAULT NULL COMMENT '薪资/学术预期',
    `risk_factors` json DEFAULT NULL COMMENT '风险因素',
    `recommended_actions` json DEFAULT NULL COMMENT '推荐行动',
    `sort_order` tinyint DEFAULT '1' COMMENT '显示顺序',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
    `stop_loss_advice` json DEFAULT NULL COMMENT '止损建议',
    PRIMARY KEY (`path_id`),
    KEY `idx_plan_name` (`plan_id`,`sort_order`,`path_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='路径详情表';

-- =====================================================================
-- 5. 用户路径跟踪表
-- =====================================================================
CREATE TABLE `user_path_tracking` (
    `tracking_id` bigint NOT NULL AUTO_INCREMENT COMMENT '跟踪记录ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `plan_id` bigint NOT NULL COMMENT '选定的规划ID',
    `path_type` tinyint NOT NULL COMMENT '选定的路径类型: 1-主流 2-备用 3-理想',
    `status` tinyint DEFAULT '1' COMMENT '跟踪状态: 1-进行中 2-已完成 3-已放弃',
    `started_at` datetime DEFAULT NULL COMMENT '开始跟踪时间',
    `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
    `notes` varchar(500) DEFAULT NULL COMMENT '用户备注',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`tracking_id`),
    KEY `idx_user_status` (`user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户路径跟踪表';

-- =====================================================================
-- 6. 用户里程碑跟踪表
-- =====================================================================
CREATE TABLE `user_milestone_tracking` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `tracking_id` bigint NOT NULL COMMENT '关联的路径跟踪ID',
    `plan_id` bigint NOT NULL COMMENT '规划ID',
    `path_type` tinyint NOT NULL COMMENT '路径类型',
    `node_index` tinyint NOT NULL COMMENT '里程碑在数组中的索引',
    `node_name` varchar(200) NOT NULL COMMENT '里程碑名称',
    `node_deadline` varchar(100) DEFAULT NULL COMMENT '里程碑截止时间',
    `status` tinyint DEFAULT '0' COMMENT '0-未开始 1-进行中 2-已完成',
    `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_tracking_node` (`user_id`,`tracking_id`,`node_index`),
    KEY `idx_tracking_node` (`tracking_id`,`node_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户里程碑跟踪表';

-- =====================================================================
-- 7. 规则配置表
-- =====================================================================
CREATE TABLE `rule_config` (
    `rule_id` int NOT NULL AUTO_INCREMENT COMMENT '规则ID（主键）',
    `rule_key` varchar(64) NOT NULL COMMENT '规则键（唯一）',
    `rule_name` varchar(100) NOT NULL COMMENT '规则名称',
    `rule_type` tinyint DEFAULT '1' COMMENT '规则类型: 1-硬性过滤 2-软性扣分 3-薪资加权(SALARY)',
    `applicable_goal_types` varchar(50) DEFAULT NULL COMMENT '适用目标类型：1,2,3',
    `condition_expression` json NOT NULL COMMENT '条件表达式',
    `action_expression` json NOT NULL COMMENT '动作表达式',
    `priority` int DEFAULT '0' COMMENT '优先级',
    `enabled` tinyint(1) DEFAULT '1' COMMENT '1-启用 0-禁用',
    `description` varchar(255) DEFAULT NULL COMMENT '规则描述',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`rule_id`),
    UNIQUE KEY `uk_rule_key` (`rule_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则配置表';

-- =====================================================================
-- 种子数据
-- =====================================================================

-- ⚠️ 安全说明：曾内置演示管理员账号（testuser/123456），因公开仓库可被直接登录获取
-- ADMIN 角色而移除。如需管理员账号，请通过注册接口创建普通用户后手动执行：
--   UPDATE user_info SET role='ADMIN' WHERE username='你的用户名';

-- 薪资规则（rule_type=3 表示 SALARY，供规则引擎计算就业方向薪资系数）
INSERT INTO `rule_config`
(`rule_key`, `rule_name`, `rule_type`, `applicable_goal_types`, `condition_expression`, `action_expression`, `priority`, `enabled`, `description`, `created_at`, `updated_at`)
VALUES
('salary_school_985', '985/211 院校加成', 3, '1', '{"field":"schoolLevel","operator":"==","value":1}', '{"multiplier":1.15}', 100, 1, '985/211 院校薪资加成 15%', NOW(), NOW()),
('salary_gpa_high', '高 GPA 加成', 3, '1', '{"field":"gpa","operator":">=","value":3.5}', '{"multiplier":1.1}', 90, 1, 'GPA >= 3.5 薪资加成 10%', NOW(), NOW()),
('salary_internship', '实习经历加成', 3, '1', '{"field":"internshipCount","operator":">=","value":2}', '{"multiplier":1.1}', 80, 1, '2 段以上实习薪资加成 10%', NOW(), NOW());

-- =====================================================================
-- 兜底策略模板（rule_type=4 = FALLBACK，规则引擎按画像匹配）
-- =====================================================================

INSERT INTO `rule_config`
(`rule_key`, `rule_name`, `rule_type`, `applicable_goal_types`, `condition_expression`, `action_expression`, `priority`, `enabled`, `description`, `created_at`, `updated_at`)
VALUES
-- ===== 就业（goalType=1） =====
('fallback_1_1', '就业-主流路径', 4, '1', '{"field":"pathType","operator":"==","value":1}',
 '{"pathName":"💼 快速就业路线（研发岗）","pathSummary":"以本科学历直接冲击中小企业研发岗，快速积累实战经验","description":"专注于构建可落地的项目经验，通过 GitHub 开源项目和实习经历弥补学历短板。适合 {major} 专业、动手能力强、不排斥编码、希望快速经济独立的人群。","timeline":[{"year":"当前","action":"梳理已学知识，搭建个人技术博客，用 Spring Boot + Vue 写一个完整项目并部署"},{"year":"3 个月后","action":"完成第一个项目并上传 GitHub，简历上能有 2-3 个可展示的项目链接"},{"year":"6 个月后","action":"投递中小厂研发岗位，针对性刷题和八股文，参加校园招聘和线上招聘会"}],"keyNodes":[{"node":"完成一个全栈项目并部署到公网","deadline":"3 个月内"},{"node":"整理简历并投递 50+ 家中小厂","deadline":"6 个月内"}],"skillGap":["Spring Boot / MyBatis","MySQL 索引优化","Linux 基础运维","Git 协作规范"],"riskFactors":["学历在简历筛选中处于劣势","无实习经历，需要靠项目弥补"],"recommendedActions":["去 B站/慕课网找完整项目教程，跟做完并自己改一遍","把项目部署到云服务器（学生优惠），简历里写访问链接","整理 GitHub，让自己的代码看起来规范（有 README、有注释）","刷 LeetCode Hot 100 前 50 题 + 背 Java 八股文"],"stopLossAdvice":{"trigger":"如果该方案在 6 个月内没有进展","action":"重新评估职业规划，尝试其他方向或降低预期","deadline":"6 个月后","alternativePath":"结合自己的实际情况，动态调整目标"}}',
 0, 1, '就业方向主流路径兜底模板', NOW(), NOW()),

('fallback_1_2', '就业-备用路径', 4, '1', '{"field":"pathType","operator":"==","value":2}',
 '{"pathName":"🛠️ 备选路线（运维/实施/技术支持）","pathSummary":"从运维或技术支持切入，积累行业经验后再转岗或跳槽","description":"研发岗竞争激烈时，可考虑运维工程师、实施工程师等岗位，技术要求相对低，更容易获得面试机会。适合 {major} 专业但偏工程实践的人群，积累 1-2 年经验后再规划转岗。","timeline":[{"year":"当前","action":"学习 Linux 基础命令、Shell 脚本、网络基础"},{"year":"3 个月后","action":"熟悉常见运维工具（Docker、Nginx、MySQL 运维），完成一个运维部署项目"},{"year":"6 个月后","action":"投递运维/技术支持岗位，重点投递传统行业数字化转型的企业"}],"keyNodes":[{"node":"掌握 Linux + Docker 基本操作","deadline":"3 个月内"},{"node":"拿到一个运维或技术支持 Offer","deadline":"6 个月内"}],"skillGap":["Linux 运维","Shell/Python 脚本","网络基础 (TCP/IP)","Docker 容器化"],"riskFactors":["运维岗位天花板较低，后期需转岗或深耕 DevOps","薪资涨幅有限"],"recommendedActions":["学习 Linux 常用命令（能完成文件操作、进程管理、日志查看）","安装 Docker，学会写 Dockerfile 和 docker-compose.yml","了解云服务（阿里云/腾讯云）的基础产品，能用 ECS + RDS 部署项目","考一个云厂商的基础认证（阿里云 ACA），简历上加分"],"stopLossAdvice":{"trigger":"如果该方案在 6 个月内没有进展","action":"重新评估职业规划，尝试其他方向或降低预期","deadline":"6 个月后","alternativePath":"结合自己的实际情况，动态调整目标"}}',
 0, 1, '就业方向备用路径兜底模板', NOW(), NOW()),

('fallback_1_3', '就业-冲刺路径', 4, '1', '{"field":"pathType","operator":"==","value":3}',
 '{"pathName":"🚀 冲刺路线（外包/自由职业 → 积累经验）","pathSummary":"通过外包、自由职业积累项目经验，以实战能力打开局面","description":"如果校招投递效果不理想，通过接外包项目、参与开源贡献等方式证明自己的实战能力，积累 1 年后以经验跳槽到更好的平台。","timeline":[{"year":"当前","action":"注册国内外包平台（程序员客栈、开源中国众包），接小需求练手"},{"year":"3 个月后","action":"完成 3-5 个小外包项目，积累客户好评和真实案例"},{"year":"6 个月后","action":"以外包项目经验包装简历，重新投递正式岗位，或继续深耕外包赛道，往独立开发者方向转型"}],"keyNodes":[{"node":"完成第一个外包项目并获得好评","deadline":"1 个月内"},{"node":"累计完成 5 个项目，形成案例集","deadline":"4 个月内"}],"skillGap":["项目沟通与需求理解","快速学习新框架的能力","独立解决问题"],"riskFactors":["外包收入不稳定","缺乏团队协作经验，后期入职需要适应"],"recommendedActions":["在程序员客栈注册，完善技能标签，主动报价接小需求","把每个外包项目都写成案例，附上交付物截图和客户评价","每天花 2 小时学新技术，保持竞争力","参与知名开源项目（如 Spring 生态、Vue 生态），哪怕只修一个小 Bug 也是亮点"],"stopLossAdvice":{"trigger":"如果该方案在 6 个月内没有进展","action":"重新评估职业规划，尝试其他方向或降低预期","deadline":"6 个月后","alternativePath":"结合自己的实际情况，动态调整目标"}}',
 0, 1, '就业方向冲刺路径兜底模板', NOW(), NOW()),

-- ===== 考研（goalType=2） =====
('fallback_2_1', '考研-主流路径', 4, '2', '{"field":"pathType","operator":"==","value":1}',
 '{"pathName":"📚 考研备选路线（冲刺名校）","pathSummary":"以本科学历报考 211/985 院校，目标学术深造","description":"适合有学术追求、愿意投入时间备考的用户。建议从现在开始制定复习计划，选择目标院校和专业，全力以赴准备初试。","timeline":[{"year":"当前","action":"确定目标院校和专业，购买教材和历年真题，制定阶段复习计划"},{"year":"3 个月后","action":"完成第一轮基础复习，英语词汇量达到 5000+，数学完成一轮刷题"},{"year":"6 个月后","action":"进入第二轮强化，专业课专项突破，参加目标院校的暑期营或联系导师"}],"keyNodes":[{"node":"确定目标院校并完成信息收集","deadline":"1 个月内"},{"node":"完成第一轮复习","deadline":"3 个月内"},{"node":"模拟考达到目标分数 80%","deadline":"考前 2 个月"}],"skillGap":["高等数学基础","英语长难句阅读","专业课知识体系","考研政治时政"],"riskFactors":["考研竞争激烈，分数线逐年上涨","心理压力大，容易产生畏难情绪"],"recommendedActions":["每天固定时间学习（早起 2 小时 + 晚上 3 小时）","加入考研群，与同专业考生交流，获取内部资料","每周末做一次模拟考，记录分数变化","提前联系目标院校的学长学姐获取信息"],"stopLossAdvice":{"trigger":"如果该方案在 6 个月内没有进展","action":"重新评估职业规划，尝试其他方向或降低预期","deadline":"6 个月后","alternativePath":"结合自己的实际情况，动态调整目标"}}',
 0, 1, '考研方向主流路径兜底模板', NOW(), NOW()),

('fallback_2_2', '考研-备用路径', 4, '2', '{"field":"pathType","operator":"==","value":2}',
 '{"pathName":"🏫 备选路线（保底院校）","pathSummary":"选择竞争较小的院校，稳妥上岸为首要目标","description":"如果名校竞争过于激烈，可以选择双非院校的强势专业，以稳妥上岸为首要目标，未来再通过读博或工作弥补学历差距。","timeline":[{"year":"当前","action":"筛选 3-5 个双非但专业实力不错的院校"},{"year":"3 个月后","action":"针对性复习目标院校的自命题专业课"},{"year":"考前","action":"优先确保过线，调剂时可作为保底选择"}],"keyNodes":[{"node":"确定 3 个保底目标院校","deadline":"1 个月内"},{"node":"获得院校自命题资料和往年真题","deadline":"2 个月内"}],"skillGap":["信息搜集能力","专业课重点把握","调剂技巧"],"riskFactors":["双非院校学历认可度较低","部分院校就业资源有限"],"recommendedActions":["关注研招网和目标院校官网，及时获取招生简章变化","准备调剂备选方案，提前了解调剂流程"],"stopLossAdvice":{"trigger":"如果该方案在 6 个月内没有进展","action":"重新评估职业规划，尝试其他方向或降低预期","deadline":"6 个月后","alternativePath":"结合自己的实际情况，动态调整目标"}}',
 0, 1, '考研方向备用路径兜底模板', NOW(), NOW()),

('fallback_2_3', '考研-冲刺路径', 4, '2', '{"field":"pathType","operator":"==","value":3}',
 '{"pathName":"💼 考研 + 就业双备选","pathSummary":"一边考研一边关注招聘，两条腿走路","description":"考研不是唯一出路，建议同步关注秋招和春招，如果考研结果不理想，依然有就业备选方案，避免毕业即失业。","timeline":[{"year":"当前","action":"复习考研的同时，花 20% 精力关注招聘信息，投递部分保底岗位"},{"year":"3 个月后","action":"参加秋招，拿一个保底 Offer（优先级：国企 > 私企）"},{"year":"考研结束后","action":"根据考研结果决定：上岸则入学，失败则入职保底岗位"}],"keyNodes":[{"node":"获得一个保底就业 Offer","deadline":"秋招结束前"},{"node":"考研初试过线","deadline":"次年 3 月"}],"skillGap":["时间管理能力","多任务并行处理能力"],"riskFactors":["精力分散可能导致考研和就业两头落空","需要极强的自律能力"],"recommendedActions":["周一到周五专注考研，周末投递简历和刷面试题","精准投递，只投递与自己专业匹配的岗位","不盲目追求大厂，以中小厂保底为主"],"stopLossAdvice":{"trigger":"如果该方案在 6 个月内没有进展","action":"重新评估职业规划，尝试其他方向或降低预期","deadline":"6 个月后","alternativePath":"结合自己的实际情况，动态调整目标"}}',
 0, 1, '考研方向冲刺路径兜底模板', NOW(), NOW()),

-- ===== 考公（goalType=3） =====
('fallback_3_1', '考公-主流路径', 4, '3', '{"field":"pathType","operator":"==","value":1}',
 '{"pathName":"🏛️ 考公路线（国考/省考）","pathSummary":"以国考和省考为目标，备考行测和申论","description":"适合追求稳定、愿意投入时间备考公务员的用户。建议根据自身专业情况选择岗位，制定系统的备考计划，关注每年考试时间节点。","timeline":[{"year":"当前","action":"了解国考/省考报名条件，确定意向岗位类型，购买教材和题库"},{"year":"3 个月后","action":"完成行测基础训练，各题型正确率达到 60% 以上，开始申论大作文练习"},{"year":"考前 1 个月","action":"进入冲刺阶段，每周至少做 3 套真题，参加模拟考试"}],"keyNodes":[{"node":"确定报考岗位并完成报名","deadline":"国考报名截止前"},{"node":"行测模考稳定 65 分以上","deadline":"考前 1 个月"}],"skillGap":["行测题型熟练度","申论写作规范","政策热点敏感度","面试表达能力"],"riskFactors":["竞争比例极高，部分岗位甚至达到 1000:1","需要至少 6 个月以上的持续备考"],"recommendedActions":["报名参加线下或线上考公培训班，跟随系统学习","每天刷题 100 道行测题 + 一篇申论作文","关注人民日报、半月谈等官方媒体，积累素材","参加粉笔等平台的模拟考试，了解自己的排名"],"stopLossAdvice":{"trigger":"如果该方案在 6 个月内没有进展","action":"重新评估职业规划，尝试其他方向或降低预期","deadline":"6 个月后","alternativePath":"结合自己的实际情况，动态调整目标"}}',
 0, 1, '考公方向主流路径兜底模板', NOW(), NOW()),

('fallback_3_2', '考公-备用路径', 4, '3', '{"field":"pathType","operator":"==","value":2}',
 '{"pathName":"📋 备选路线（事业单位/国企）","pathSummary":"关注事业单位招聘和国企招聘，多一条路","description":"如果国考/省考竞争过于激烈，事业单位和国企的招聘难度相对较低，且福利待遇同样稳定，可以同步关注。","timeline":[{"year":"当前","action":"关注事业单位招聘信息（省人社厅网站、事业单位招聘考试网）"},{"year":"考试前","action":"根据事业单位考试内容（公共基础知识/职测）进行专项备考"},{"year":"毕业后","action":"如果公务员未录取，优先入职事业单位/国企，再规划继续备考"}],"keyNodes":[{"node":"确定 3-5 个事业编/国企意向岗位","deadline":"招聘公告发布后"},{"node":"完成至少一个单位的笔面试","deadline":"毕业后 3 个月内"}],"skillGap":["事业单位考情了解","结构化面试技巧"],"riskFactors":["事业单位薪资涨幅有限","部分国企基层岗位工作强度不低"],"recommendedActions":["关注当地人才引进政策，部分城市对本科生有补贴","报名事业单位培训班，内容与考公重叠度高"],"stopLossAdvice":{"trigger":"如果该方案在 6 个月内没有进展","action":"重新评估职业规划，尝试其他方向或降低预期","deadline":"6 个月后","alternativePath":"结合自己的实际情况，动态调整目标"}}',
 0, 1, '考公方向备用路径兜底模板', NOW(), NOW()),

('fallback_3_3', '考公-冲刺路径', 4, '3', '{"field":"pathType","operator":"==","value":3}',
 '{"pathName":"🌱 基层项目 + 曲线入编","pathSummary":"通过三支一扶、西部计划等基层项目入编","description":"如果公务员和事业单位连续 2 年未成功，可以考虑三支一扶、西部计划等基层项目，服务期满后可通过定向招录进入公务员或事业单位编制。","timeline":[{"year":"当前","action":"了解三支一扶、西部计划、特岗教师等基层项目的报名条件和政策"},{"year":"毕业后","action":"报名参加基层项目，服务期通常为 2-3 年"},{"year":"服务期满后","action":"参加定向招录考试，或者利用基层经验参加考公/考编面试"}],"keyNodes":[{"node":"确定意向基层项目","deadline":"毕业前"},{"node":"完成基层项目报名并录用","deadline":"毕业后 1 年内"}],"skillGap":["基层工作能力","吃苦耐劳的意志力"],"riskFactors":["基层项目工作环境艰苦","部分地区待遇偏低","需要 2-3 年时间成本"],"recommendedActions":["提前了解各省基层项目的招募公告","加入基层项目交流群，了解真实的基层工作状态","做好吃苦和长期扎根的准备"],"stopLossAdvice":{"trigger":"如果该方案在 6 个月内没有进展","action":"重新评估职业规划，尝试其他方向或降低预期","deadline":"6 个月后","alternativePath":"结合自己的实际情况，动态调整目标"}}',
 0, 1, '考公方向冲刺路径兜底模板', NOW(), NOW());
