## 用户相关

### 管理员表

```sql
CREATE TABLE IF NOT EXISTS `admin` (
    `id` bigint NOT NULL PRIMARY KEY COMMENT '用户ID，全局唯一ID，不可修改',
    `username` varchar(30) NOT NULL COMMENT '用户名，30，可重复',
    `password` varchar(255) NOT NULL DEFAULT '' COMMENT '密码，密文',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '帐号状态（0正常 1异常 2封禁 3注销）',
    `email` varchar(50) DEFAULT '' COMMENT '用户邮箱',
    `mobile` varchar(11) DEFAULT '' COMMENT '手机号码',
    `ban_time` datetime DEFAULT NULL COMMENT '封禁开始时间',
    `ban_end_time` datetime DEFAULT NULL COMMENT '封禁结束时间，表示到这时进行解封',
    `ban_reason` varchar(200) DEFAULT '' COMMENT '封禁理由',
    `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `updated_by` bigint DEFAULT 0 COMMENT '更新者',
    `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除，0-否，1-是'
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员信息表';
```

### 用户表

```sql
CREATE TABLE IF NOT EXISTS `user` (
    `id` bigint NOT NULL PRIMARY KEY COMMENT '用户ID，全局唯一ID，不可修改',
    `username` varchar(30) NOT NULL COMMENT '用户名，30，可重复',
    `password` varchar(255) NOT NULL DEFAULT '' COMMENT '密码，密文',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '帐号状态（0正常 1异常 2封禁 3注销）',
    `introduction` varchar(255) DEFAULT '' COMMENT '用户简介',
    `email` varchar(50) DEFAULT '' COMMENT '用户邮箱',
    `mobile` varchar(11) DEFAULT '' COMMENT '手机号码',
    `gender` tinyint DEFAULT 2 COMMENT '性别，0：女，1：男，2：未知',
    `avatar` varchar(512) DEFAULT '' COMMENT '用户头像路径',
    `ban_time` datetime DEFAULT NULL COMMENT '封禁开始时间',
    `ban_end_time` datetime DEFAULT NULL COMMENT '封禁结束时间，表示到这时进行解封，如果是封禁状态但这里为NULL则是永久封禁',
    `ban_reason` varchar(200) DEFAULT '' COMMENT '封禁原因',
    `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `updated_by` bigint DEFAULT 0 COMMENT '更新者',
    `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除，0-否，1-是'
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户信息表';
```



## 角色与权限相关（RBAC模型）

### 角色表

```sql
CREATE TABLE IF NOT EXISTS `role` (
    `id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID，自增主键',
    `name` varchar(50) NOT NULL UNIQUE COMMENT '角色名（如admin/user/VIP），唯一且非空',
    `status` tinyint DEFAULT 0 COMMENT '角色状态（0正常 1停用 2删除）',
    `remark` varchar(100) DEFAULT NULL COMMENT '备注信息',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
    `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `updated_by` bigint DEFAULT NULL COMMENT '最后更新人用户ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';
```

### 用户角色关联表

```sql
CREATE TABLE IF NOT EXISTS `person_role` (
    `person_id` bigint NOT NULL COMMENT '用户ID',
    `role_id` bigint NOT NULL COMMENT '角色ID，关联role表的id',
    PRIMARY KEY (`person_id`, `role_id`),
    INDEX `idx_person_id` (`person_id`),
    INDEX `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';
```

### 权限表

```sql
CREATE TABLE IF NOT EXISTS `permission` (
    `id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '权限唯一标识，自增主键',
    `perms` varchar(100) NOT NULL COMMENT '权限标识符（如：video:delete），用于权限验证',
    `status` tinyint DEFAULT 0 COMMENT '状态标识：0-正常，1-停用，2-删除',
    `remark` varchar(100) DEFAULT NULL COMMENT '备注信息，用于描述权限的用途或特殊说明',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
    `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `updated_by` bigint DEFAULT NULL COMMENT '最后更新人用户ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表：存储菜单项及其对应权限标识';
```

### 角色-权限关联表

```sql
CREATE TABLE IF NOT EXISTS `role_permission` (
    `role_id` bigint NOT NULL COMMENT '角色ID，逻辑关联role表中的id',
    `permission_id` bigint NOT NULL COMMENT '菜单权限ID，逻辑关联permission表中的id',
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    INDEX `idx_role_id` (`role_id`),
    INDEX `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限关联表';
```



## 会话相关

### 会话表

```sql
CREATE TABLE IF NOT EXISTS `conversation` (
    `id` bigint NOT NULL PRIMARY KEY COMMENT '会话的唯一标识符',
    `user_id` bigint NOT NULL COMMENT '用户ID，关联用户表',
    `name` varchar(80) DEFAULT NULL COMMENT '该会话的名称',
    `context_summary` varchar(200) DEFAULT NULL COMMENT '上下文概括',
    `analysis_context_summary` varchar(500) DEFAULT NULL COMMENT '情绪分析上下文语义压缩',
    `context_summary_round` int DEFAULT NULL COMMENT '语义压缩时轮次',
    `last_active_time` datetime DEFAULT NULL COMMENT '最后活跃时间，用于业务展示、排序、统计',
    `chat_mode` varchar(30) DEFAULT NULL COMMENT '会话模式',
    `session_mapping` json DEFAULT NULL COMMENT '会话映射表',
    `current_round` int NOT NULL DEFAULT 1 COMMENT '当前轮次，执行中是当前轮次，执行后是下一轮次',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除，0-否，1-是',
    INDEX `idx_user_id_updated_time` (`user_id`, `updated_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';
```

### 会话聊天信息表

```sql
CREATE TABLE IF NOT EXISTS `conversation_memory` (
    `id` bigint NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '自增id',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `conversation_id` bigint NOT NULL COMMENT '会话的唯一标识符',
    `content` varchar(1000) NOT NULL COMMENT '消息的具体文本内容',
    `audio_emotion_label` varchar(30) DEFAULT NULL COMMENT '单段语音SER识别的情绪标签（如焦虑、低落、平静、愤怒）',
    `audio_emotion_confidence` double DEFAULT NULL COMMENT '语音情绪识别置信度 0-1',
    `audio_feature` varchar(1024) DEFAULT NULL COMMENT '语音声学特征（语速、音量波动、抖动程度、哽咽感等，JSON格式存储）',
    `type` varchar(20) NOT NULL COMMENT '消息的类型（USER, ASSISTANT, SYSTEM, TOOL, ASSISTANT_TOOL, THINKING）',
    `state` tinyint DEFAULT 0 COMMENT '消息状态，0-未处理，1-已处理',
    `round_num` int NOT NULL DEFAULT 1 COMMENT '轮次',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除，0-否，1-是',
    INDEX `idx_conversation_id_round_num` (`conversation_id`, `round_num`),
    INDEX `idx_conversation_id_created_time` (`conversation_id`, `created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话聊天信息表';
```

### 检查点数据表

```sql
CREATE TABLE IF NOT EXISTS `graph_checkpoint` (
    `id` varchar(36) NOT NULL PRIMARY KEY COMMENT '主键ID',
    `conversation_id` varchar(36) NOT NULL COMMENT '会话ID',
    `node_id` varchar(255) DEFAULT NULL COMMENT '存储当前Node的标记',
    `next_node_id` varchar(255) DEFAULT NULL COMMENT '存储下一个Node的标记',
    `state_data` json NOT NULL COMMENT '存储state中的数据信息',
    `round_num` int NOT NULL DEFAULT 1 COMMENT '轮次',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除，0-否，1-是',
    INDEX `idx_conversation_id_round_num` (`conversation_id`, `round_num`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检查点数据表';
```



## 情绪分析相关

### 情绪记录表

```sql
CREATE TABLE IF NOT EXISTS `emotion_analysis` (
    `id` bigint NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '自增id',
    `conversation_id` bigint NOT NULL COMMENT '会话ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `round_num` int NOT NULL COMMENT '对应的轮次，与会话ID结合查询对应的对话信息',
    `analysis_content` text DEFAULT NULL COMMENT '情感分析详情',
    `emotion_label` varchar(30) DEFAULT NULL COMMENT '情感标签（如anger/开心/neutral/不满等）',
    `emotion_sub_label` varchar(30) DEFAULT NULL COMMENT '情感细分标签（如愤怒可细分"不满/暴怒/抱怨"）',
    `emotion_confidence` double DEFAULT NULL COMMENT '情感识别置信度（0-1，如0.9200）',
    `emotion_intensity` double DEFAULT NULL COMMENT '情绪本身的强烈程度（0-1，如0.9200）',
    `emotion_trend` varchar(20) DEFAULT NULL COMMENT '较上一轮的情绪变化趋势',
    `p_score` double DEFAULT NULL COMMENT 'PAD愉悦度P，取值范围[-1,1]',
    `a_score` double DEFAULT NULL COMMENT 'PAD唤醒度A，取值范围[-1,1]',
    `d_score` double DEFAULT NULL COMMENT 'PAD支配度D，取值范围[-1,1]',
    `negative_emotion_ratio` double DEFAULT NULL COMMENT '负向情绪占比（0-1）',
    `neutral_emotion_ratio` double DEFAULT NULL COMMENT '中性情绪占比（0-1）',
    `positive_emotion_ratio` double DEFAULT NULL COMMENT '正向情绪占比（0-1）',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除，0-否，1-是',
    INDEX `idx_conversation_id_round_num` (`conversation_id`, `round_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情绪记录表';
```

### 情感诊断书表（多轮会话汇总）

```sql
CREATE TABLE IF NOT EXISTS `emotion_diagnosis` (
    `id` bigint NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '诊断书主键ID',
    `conversation_id` bigint NOT NULL COMMENT '会话ID',
    `user_id` bigint NOT NULL COMMENT '用户ID（冗余存储，便于单独查询）',
    `round_num` int DEFAULT NULL COMMENT '该诊断数据创建或更新时的轮次',
    `diagnosis_content` text DEFAULT NULL COMMENT '诊断书核心内容（自然语言总结）',
    `core_emotion_label` varchar(30) DEFAULT NULL COMMENT '核心情绪标签（如焦虑/抑郁/开心/中性）',
    `core_emotion_conf_avg` decimal(10,4) DEFAULT NULL COMMENT '核心情绪平均置信度（0-1，精度提升）',
    `core_emotion_intensity_score` decimal(10,4) DEFAULT NULL COMMENT '核心情绪强度分值（0-1）',
    `core_emotion` json DEFAULT NULL COMMENT '核心情绪，key:核心情绪标签，value:核心情绪置信度',
    `secondary_emotion` json DEFAULT NULL COMMENT '次要情绪，key:次要情绪标签，value:次要情绪置信度',
    `negative_emotion_ratio` decimal(10,4) DEFAULT NULL COMMENT '负向情绪占比（0-1）',
    `positive_emotion_ratio` decimal(10,4) DEFAULT NULL COMMENT '正向情绪占比（0-1）',
    `neutral_emotion_ratio` decimal(10,4) DEFAULT NULL COMMENT '中性情绪占比（0-1，三者和为1）',
    `negative_emotion_detail` json DEFAULT NULL COMMENT '负向情绪细分占比，如{"焦虑":0.45,"愤怒":0.25,"悲伤":0.10}',
    `positive_emotion_detail` json DEFAULT NULL COMMENT '正向情绪细分占比，如{"开心":0.30,"欣慰":0.15,"放松":0.05}',
    `emotion_trend` tinyint DEFAULT NULL COMMENT '整体情绪趋势（0-上升/1-下降/2-平稳/3-无法判断）',
    `emotion_peak_round` int DEFAULT NULL COMMENT '情绪峰值轮次（核心情绪强度最高的轮次）',
    `emotion_valley_round` int DEFAULT NULL COMMENT '情绪低谷轮次（核心情绪强度最低的轮次）',
    `emotion_fluctuation_amplitude` decimal(10,4) DEFAULT NULL COMMENT '情绪波动幅度（峰值-谷值的置信度差）',
    `emotion_stable_rounds` int DEFAULT NULL COMMENT '情绪平稳的轮次数量',
    `emotion_stability_score` decimal(10,4) DEFAULT NULL COMMENT '情绪稳定性得分（0-1，越高越稳定）',
    `avg_p` decimal(10,4) DEFAULT NULL COMMENT '整体PAD愉悦度均值，值域[-1,1]',
    `avg_a` decimal(10,4) DEFAULT NULL COMMENT '整体PAD唤醒度均值，值域[-1,1]',
    `avg_d` decimal(10,4) DEFAULT NULL COMMENT '整体PAD支配度均值，值域[-1,1]',
    `std_p` decimal(10,4) DEFAULT NULL COMMENT 'P维度标准差，数值越大情绪愉悦度波动越强',
    `std_a` decimal(10,4) DEFAULT NULL COMMENT 'A维度标准差，数值越大唤醒起伏剧烈',
    `std_d` decimal(10,4) DEFAULT NULL COMMENT 'D维度标准差，数值越大掌控感反复变化',
    `core_trigger_scene` varchar(100) DEFAULT NULL COMMENT '核心触发场景（如工作压力/人际关系/家庭矛盾）',
    `core_trigger_keywords` varchar(200) DEFAULT NULL COMMENT '核心触发关键词（多个用逗号分隔）',
    `trigger_round_num` int DEFAULT NULL COMMENT '首次出现核心触发因素的轮次',
    `psychological_state` varchar(100) DEFAULT NULL COMMENT '整体心理状态评估',
    `symptom_summary` varchar(500) DEFAULT NULL COMMENT '核心症状总结（自然语言）',
    `symptom_tags` varchar(200) DEFAULT NULL COMMENT '症状标签集合，逗号分隔',
    `social_function_impact` varchar(30) DEFAULT NULL COMMENT '社会功能受损程度：无影响/轻度受损/中度受损/重度受损',
    `impact_domains` varchar(200) DEFAULT NULL COMMENT '受影响的具体领域，逗号分隔',
    `daily_life_influence` varchar(500) DEFAULT NULL COMMENT '对日常生活影响的自然语言描述',
    `symptom_duration` varchar(30) DEFAULT NULL COMMENT '症状持续时长',
    `onset_pattern` varchar(30) DEFAULT NULL COMMENT '发作模式：持续性/阵发性/偶发/逐渐加重/反复波动',
    `first_trigger_desc` varchar(500) DEFAULT NULL COMMENT '用户提及的首次触发事件/原因',
    `social_support_level` tinyint DEFAULT NULL COMMENT '社会支持水平：0-良好/1-一般/2-较差/3-匮乏/4-无法判断',
    `protective_factors` varchar(200) DEFAULT NULL COMMENT '保护性因素/心理资源，逗号分隔',
    `coping_style` varchar(100) DEFAULT NULL COMMENT '用户的应对方式',
    `emotion_risk_level` tinyint DEFAULT NULL COMMENT '情绪风险等级（0-低/1-中/2-高/3-危急/4-无法判断）',
    `emotion_adjust_suggestion` varchar(500) DEFAULT NULL COMMENT '情绪调节建议（自然语言）',
    `need_manual_intervene` tinyint DEFAULT NULL COMMENT '是否需要人工干预（0-否，1-是）',
    `self_harm_risk_level` tinyint DEFAULT NULL COMMENT '自伤风险等级：0-无/1-低/2-中/3-高/4-极高/5-无法判断',
    `suicide_risk_level` tinyint DEFAULT NULL COMMENT '自杀风险等级：0-无/1-低/2-中/3-高/4-极高/5-无法判断',
    `risk_detail` varchar(500) DEFAULT NULL COMMENT '风险细节描述',
    `crisis_warning` tinyint DEFAULT NULL COMMENT '是否触发危机预警：0-否 1-是',
    `self_help_suggestion` varchar(500) DEFAULT NULL COMMENT '自助调节建议',
    `social_support_suggestion` varchar(500) DEFAULT NULL COMMENT '社会支持建议',
    `professional_intervene_suggestion` varchar(500) DEFAULT NULL COMMENT '专业干预建议',
    `suggestion_priority` tinyint DEFAULT NULL COMMENT '建议优先级：1-自助为主 2-建议寻求支持 3-强烈建议专业干预 4-无法判断',
    `diagnosis_score` tinyint DEFAULT NULL COMMENT '用户对本次诊断打分 1~5分，NULL代表未评分',
    `feedback_content` varchar(500) DEFAULT NULL COMMENT '用户文字反馈、吐槽、补充意见',
    `agree_risk_judge` tinyint DEFAULT NULL COMMENT '是否认同风险评估：0-不认同 1-认同 NULL未反馈',
    `agree_suggestion_self` tinyint DEFAULT NULL COMMENT '是否认同自助调节建议：0-不认同 1-认同 NULL未反馈',
    `agree_suggestion_social` tinyint DEFAULT NULL COMMENT '是否认同社会支持建议：0-不认同 1-认同 NULL未反馈',
    `agree_suggestion_professional` tinyint DEFAULT NULL COMMENT '是否认同专业干预建议：0-不认同 1-认同 NULL未反馈',
    `use_suggestion` tinyint DEFAULT NULL COMMENT '是否尝试采纳建议：0-没有 1-尝试部分 2-全部尝试 NULL未反馈',
    `feedback_time` datetime DEFAULT NULL COMMENT '用户提交反馈时间',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除，0-否，1-是',
    INDEX `idx_conversation_id` (`conversation_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情感诊断书表（多轮会话汇总）';
```



## 知识库相关

### 知识库切片元数据表

```sql
CREATE TABLE IF NOT EXISTS `knowledge_document` (
    `id` bigint NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键ID',
    `file_id` bigint NOT NULL COMMENT '关联文件表infra_file.id',
    `slice_id` bigint NOT NULL COMMENT '切片唯一ID（与Milvus主键一一对应）',
    `content` text NOT NULL COMMENT '切片原文内容',
    `chunk_level1_idx` int DEFAULT NULL COMMENT '第一次初始分片的位置索引',
    `chunk_level2_idx` int DEFAULT NULL COMMENT '第二次分片的位置索引',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库切片元数据表';
```

### 知识侧检索过程记录表

```sql
CREATE TABLE IF NOT EXISTS `rag_retrieve_log` (
    `id` bigint NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `diagnosis_id` bigint DEFAULT NULL COMMENT '关联的诊断记录ID',
    `request_params` json DEFAULT NULL COMMENT '检索入参（结构化特征JSON）',
    `retry_count` int DEFAULT NULL COMMENT '检索重试次数',
    `symptom_doc_count` int DEFAULT NULL COMMENT '症状知识库召回文档数量',
    `diagnosis_doc_count` int DEFAULT NULL COMMENT '诊断标准库召回文档数量',
    `intervention_doc_count` int DEFAULT NULL COMMENT '干预方案库召回文档数量',
    `retrieve_cost_time` int DEFAULT NULL COMMENT '检索总耗时（毫秒）',
    `rerule_result` json DEFAULT NULL COMMENT '重排结果：分片ID+得分列表JSON',
    `fallback_status` tinyint DEFAULT 0 COMMENT '是否触发兜底：0=否 1=是',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识侧检索过程记录表';
```

### 症状标准术语字典

```sql
CREATE TABLE IF NOT EXISTS `symptom_dict` (
    `id` bigint NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '标准术语ID',
    `symptom_term` varchar(100) NOT NULL COMMENT '标准症状名称',
    `symptom_category` varchar(30) DEFAULT NULL COMMENT '症状大类：情绪症状/躯体症状/认知症状/行为症状',
    `synonym_words` json DEFAULT NULL COMMENT '同义口语词数组，例：["睡不着","躺床上翻来覆去睡不着"]',
    `severity_default` varchar(10) DEFAULT '1' COMMENT '默认严重程度：1=轻度 2=中度 3=重度',
    `status` tinyint DEFAULT 1 COMMENT '状态 0：禁用 1：启用',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='症状标准术语字典';
```



## 文件存储相关

### 文件元数据表

```sql
CREATE TABLE IF NOT EXISTS `infra_file` (
    `id` bigint NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键ID',
    `person_id` bigint DEFAULT NULL COMMENT '人员ID，关联用户/管理员表',
    `category_id` bigint DEFAULT NULL COMMENT '分类ID',
    `config_id` bigint DEFAULT 0 COMMENT '配置编号，默认0-本地存储',
    `original_name` varchar(256) DEFAULT NULL COMMENT '文件原始名称（含后缀）',
    `file_url` varchar(512) NOT NULL COMMENT '文件存储路径（本地相对/绝对路径）',
    `file_suffix` varchar(128) DEFAULT NULL COMMENT '文件后缀（例：doc、pdf）',
    `file_size` bigint NOT NULL COMMENT '文件大小，单位：字节',
    `file_md5` varchar(32) DEFAULT NULL COMMENT '文件MD5哈希值，用于去重/校验',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '文件状态：0-待解析，1-解析中，2-解析失败，3-解析完成',
    `fail_reason` varchar(500) DEFAULT NULL COMMENT '解析失败原因',
    `vector_status` tinyint NOT NULL DEFAULT 0 COMMENT '是否启用向量检索，0-否，1-是',
    `knowledge_type` tinyint NOT NULL DEFAULT 0 COMMENT '向量库知识类型，0-无，1=症状库 2=诊断标准库 3=干预方案库',
    `source` varchar(50) DEFAULT NULL COMMENT '文件来源',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
    `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除，0-否，1-是'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件元数据表';
```

### 文件分类表

```sql
CREATE TABLE IF NOT EXISTS `infra_file_category` (
    `id` bigint NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键ID',
    `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父级类目ID，0=一级分类',
    `person_id` bigint DEFAULT NULL COMMENT '人员ID，关联用户/管理员表',
    `category_name` varchar(100) NOT NULL COMMENT '类目名称',
    `default_type` tinyint NOT NULL DEFAULT 0 COMMENT '是否默认分类：0-否，1-是',
    `file_count` int DEFAULT 0 COMMENT '该分类下的文件数量',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
    `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `updated_by` bigint DEFAULT NULL COMMENT '更新人ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件分类表';
```



## 量表测评相关

### 量表主表

```sql
CREATE TABLE IF NOT EXISTS `scale` (
    `id` bigint NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键ID',
    `scale_name` varchar(100) NOT NULL COMMENT '量表名称',
    `description` varchar(500) DEFAULT NULL COMMENT '量表说明、指导语、开头介绍',
    `question_count` int DEFAULT NULL COMMENT '题目总数',
    `scale_category_id` bigint DEFAULT NULL COMMENT '分类ID',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
    `score_rule` varchar(200) DEFAULT NULL COMMENT '统一选项模板的通用评分规则',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
    `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `updated_by` bigint DEFAULT NULL COMMENT '最后更新人用户ID',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除，0-否，1-是'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='量表主表';
```

### 量表类别表

```sql
CREATE TABLE IF NOT EXISTS `scale_category` (
    `id` bigint NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键ID',
    `category_name` varchar(100) NOT NULL COMMENT '类别名称',
    `use_count` int DEFAULT 0 COMMENT '类别使用数量',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
    `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `updated_by` bigint DEFAULT NULL COMMENT '最后更新人用户ID',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除，0-否，1-是'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='量表类别表';
```

### 量表题目表

```sql
CREATE TABLE IF NOT EXISTS `scale_question` (
    `id` bigint NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键ID',
    `scale_id` bigint NOT NULL COMMENT '量表ID',
    `title` varchar(500) NOT NULL COMMENT '题目内容',
    `sort` int DEFAULT NULL COMMENT '题目显示顺序',
    `score_type` tinyint DEFAULT 1 COMMENT '计分类型：1=正向计分 2=反向计分',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
    `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `updated_by` bigint DEFAULT NULL COMMENT '最后更新人用户ID',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除，0-否，1-是',
    INDEX `idx_scale_id` (`scale_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='量表题目表';
```

### 题目选项表

```sql
CREATE TABLE IF NOT EXISTS `scale_option` (
    `id` bigint NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键ID',
    `question_id` bigint NOT NULL COMMENT '所属题目ID',
    `option_text` varchar(200) NOT NULL COMMENT '选项描述',
    `score` int DEFAULT NULL COMMENT '该选项对应的原始分数',
    `sort` int DEFAULT NULL COMMENT '选项显示顺序',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
    `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `updated_by` bigint DEFAULT NULL COMMENT '最后更新人用户ID',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除，0-否，1-是',
    INDEX `idx_question_id` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目选项表';
```

### 题目选项模板表

```sql
CREATE TABLE IF NOT EXISTS `scale_option_template` (
    `id` bigint NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键ID',
    `scale_id` bigint NOT NULL COMMENT '量表ID',
    `option_text` varchar(200) NOT NULL COMMENT '选项描述',
    `score` int DEFAULT NULL COMMENT '该选项对应的原始分数',
    `sort` int DEFAULT NULL COMMENT '选项显示顺序',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
    `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `updated_by` bigint DEFAULT NULL COMMENT '最后更新人用户ID',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除，0-否，1-是',
    INDEX `idx_scale_id` (`scale_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目选项模板表';
```

### 量表结果规则表

```sql
CREATE TABLE IF NOT EXISTS `scale_result_rule` (
    `id` bigint NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键ID',
    `scale_id` bigint NOT NULL COMMENT '量表ID',
    `min_score` int DEFAULT NULL COMMENT '区间最低分（包含）',
    `max_score` int DEFAULT NULL COMMENT '区间最高分（包含）',
    `result_text` varchar(500) DEFAULT NULL COMMENT '测评结果描述文本',
    `sort` int DEFAULT NULL COMMENT '排序序号',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
    `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `updated_by` bigint DEFAULT NULL COMMENT '最后更新人用户ID',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除，0-否，1-是',
    INDEX `idx_scale_id` (`scale_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='量表结果规则表';
```

### 用户答题明细表

```sql
CREATE TABLE IF NOT EXISTS `scale_user_answer` (
    `id` bigint NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键ID',
    `record_id` bigint NOT NULL COMMENT '关联的测评记录ID',
    `question_id` bigint NOT NULL COMMENT '题目ID',
    `option_id` bigint NOT NULL COMMENT '用户选择的选项ID',
    `sort` int DEFAULT NULL COMMENT '题目显示顺序（冗余存储）',
    `question_title` varchar(500) DEFAULT NULL COMMENT '答题时的题目内容（冗余存储）',
    `option_text` varchar(200) DEFAULT NULL COMMENT '答题时的选项内容（冗余存储）',
    `original_score` int DEFAULT NULL COMMENT '选项得分（冗余存储）',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除，0-否，1-是',
    INDEX `idx_record_id` (`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户答题明细表';
```

### 用户测评记录表

```sql
CREATE TABLE IF NOT EXISTS `scale_user_record` (
    `id` bigint NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `scale_id` bigint NOT NULL COMMENT '量表ID',
    `scale_name` varchar(100) DEFAULT NULL COMMENT '量表名称（冗余存储）',
    `total_score` int DEFAULT NULL COMMENT '最终计算总分',
    `result_text` varchar(500) DEFAULT NULL COMMENT '本次测评结果描述',
    `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除，0-否，1-是',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_scale_id` (`scale_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户测评记录表';
```



## 向量数据相关（Milvus向量库元数据映射）

### 向量数据元数据表

```sql
CREATE TABLE IF NOT EXISTS `vector_data` (
    `id` bigint NOT NULL PRIMARY KEY COMMENT '自增主键ID，与Milvus主键一一对应',
    `file_id` bigint NOT NULL COMMENT '文件ID',
    `knowledge_type` tinyint NOT NULL COMMENT '知识类型，1=症状库 2=诊断标准库 3=干预方案库',
    `chunk_level1_idx` int DEFAULT NULL COMMENT '一级分块索引',
    `chunk_level2_idx` int DEFAULT NULL COMMENT '二级分块索引',
    `content` text NOT NULL COMMENT '文本内容',
    `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除，0-否，1-是'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='向量数据元数据表（Milvus向量库的MySQL侧映射，向量本身存储在Milvus中）';
```