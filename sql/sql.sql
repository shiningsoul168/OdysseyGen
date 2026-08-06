CREATE DATABASE IF NOT EXISTS career_plan
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE career_plan;



CREATE TABLE `user_info` (
                             `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID（主键）',
                             `username` varchar(64) NOT NULL COMMENT '登录名（唯一）',
                             `password` varchar(255) NOT NULL COMMENT '加密密码',
                             `email` varchar(128) DEFAULT NULL COMMENT '邮箱',
                             `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
                             `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名（可选）',
                             `phone` varchar(20) DEFAULT NULL COMMENT '手机号（可选）',
                             `status` tinyint(1) DEFAULT '1' COMMENT '1-正常 0-禁用',
                             `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
                             `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             `major` varchar(100) DEFAULT NULL COMMENT '专业名称',
                             `gpa` decimal(3,2) DEFAULT NULL COMMENT 'GPA',
                             `english_level` tinyint DEFAULT NULL COMMENT '英语水平',
                             `school_level` tinyint DEFAULT NULL COMMENT '学校层次',
                             `graduation_year` int DEFAULT NULL COMMENT '毕业年份',
                             `personality_tags` json DEFAULT NULL,
                             PRIMARY KEY (`user_id`),
                             UNIQUE KEY `uk_username` (`username`),
                             UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

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
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户画像表';

CREATE TABLE `plan_records` (
                                `plan_id` bigint NOT NULL AUTO_INCREMENT COMMENT '规划ID（主键）',
                                `user_id` bigint NOT NULL COMMENT '用户ID',
                                `profile_id` bigint NOT NULL COMMENT '画像ID（快照）',
                                `generation_prompt` text COMMENT '生成时使用的Prompt',
                                `generation_cost` decimal(10,6) DEFAULT NULL COMMENT 'AI调用成本（元）',
                                `response_time_ms` int DEFAULT NULL COMMENT 'AI响应耗时（毫秒）',
                                `is_favorite` tinyint(1) DEFAULT '0' COMMENT '是否收藏',
                                `is_deleted` tinyint(1) DEFAULT '0' COMMENT '0-正常 1-已删除',
                                `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                                `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                `goal_type` tinyint DEFAULT NULL COMMENT '目标类型(冗余)',
                                PRIMARY KEY (`plan_id`),
                                KEY `idx_profile_id` (`profile_id`),
                                KEY `idx_user_created` (`user_id`,`created_at` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=42 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='职业规划主记录表';

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
) ENGINE=InnoDB AUTO_INCREMENT=124 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='路径详情表';

CREATE TABLE `user_path_tracking` (
                                      `tracking_id` bigint NOT NULL AUTO_INCREMENT COMMENT '跟踪记录ID',
                                      `user_id` bigint NOT NULL COMMENT '用户ID',
                                      `plan_id` bigint NOT NULL COMMENT '选定的规划ID',
                                      `path_type` tinyint NOT NULL COMMENT '选定的路径类型: 1-主流 2-备用 3-理想',
                                      `status` tinyint DEFAULT '1' COMMENT '跟踪状态: 1-进行中 2-已完成 3-已放弃',
                                      `started_at` datetime DEFAULT NULL COMMENT '开始跟踪时间',
                                      `completed_at` datetime DEFAULT NULL COMMENT '完成时间（状态为已完成时记录）',
                                      `notes` varchar(500) DEFAULT NULL COMMENT '用户备注',
                                      `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                                      `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                      PRIMARY KEY (`tracking_id`),
                                      KEY `idx_user_status` (`user_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户路径跟踪表';

CREATE TABLE `user_milestone_tracking` (
                                           `id` bigint NOT NULL AUTO_INCREMENT,
                                           `user_id` bigint NOT NULL COMMENT '用户ID',
                                           `tracking_id` bigint NOT NULL COMMENT '关联的路径跟踪ID（user_path_tracking）',
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
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户里程碑跟踪表';

CREATE TABLE `rule_config` (
                               `rule_id` int NOT NULL AUTO_INCREMENT COMMENT '规则ID（主键）',
                               `rule_key` varchar(64) NOT NULL COMMENT '规则键（唯一）',
                               `rule_name` varchar(100) NOT NULL COMMENT '规则名称',
                               `rule_type` tinyint DEFAULT '1' COMMENT '1-硬性过滤 2-软性扣分 3-推荐加权',
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
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则配置表'