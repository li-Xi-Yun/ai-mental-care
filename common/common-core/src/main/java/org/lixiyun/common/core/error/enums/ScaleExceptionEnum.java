package org.lixiyun.common.core.error.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * 量表模块异常枚举
 *
 * @author lixiyun
 * @since 2026-04-15
 */
public enum ScaleExceptionEnum implements ErrorCode {

    /* ==================== 量表类别 ==================== */
    SCALE_CATEGORY_NOT_FOUND("量表类别不存在", 1600),
    SCALE_CATEGORY_NAME_EXISTS("量表类别名称已存在", 1601),
    SCALE_CATEGORY_IN_USE("量表类别下存在量表，无法删除", 1602),
    SCALE_CATEGORY_ADD_FAIL("量表类别新增失败", 1610),
    SCALE_CATEGORY_UPDATE_FAIL("量表类别修改失败", 1611),
    SCALE_CATEGORY_DELETE_FAIL("量表类别删除失败", 1612),

    /* ==================== 量表主表 ==================== */
    SCALE_NOT_FOUND("量表不存在", 1620),
    SCALE_NAME_EXISTS("量表名称已存在", 1621),
    SCALE_DISABLED("量表已禁用，无法作答", 1622),
    SCALE_ADD_FAIL("量表新增失败", 1623),
    SCALE_UPDATE_FAIL("量表修改失败", 1624),
    SCALE_DELETE_FAIL("量表删除失败", 1625),
    SCALE_RESTORE_FAIL("量表恢复失败", 1626),
    SCALE_NO_CURRENT_VERSION("量表尚无当前生效版本", 1627),

    /* ==================== 量表版本 ==================== */
    SCALE_VERSION_NOT_FOUND("量表版本不存在", 1630),
    SCALE_VERSION_NO_EXISTS("该量表下版本号已存在", 1631),
    SCALE_VERSION_CURRENT_DELETE_FORBIDDEN("当前生效版本不允许删除", 1632),
    SCALE_VERSION_PUBLISH_EMPTY("版本下无题目，暂不能发布", 1633),
    SCALE_VERSION_COPY_FAIL("版本复制失败", 1634),
    SCALE_VERSION_DELETE_FAIL("量表版本删除失败", 1635),
    SCALE_VERSION_ADD_FAIL("量表版本新增失败", 1636),
    SCALE_VERSION_UPDATE_FAIL("量表版本修改失败", 1637),

    /* ==================== 量表维度 ==================== */
    SCALE_DIMENSION_NOT_FOUND("量表维度不存在", 1640),
    SCALE_DIMENSION_CODE_EXISTS("维度编码已存在", 1641),
    SCALE_DIMENSION_IN_USE("维度下存在题目，无法删除", 1642),
    SCALE_DIMENSION_ADD_FAIL("量表维度新增失败", 1643),
    SCALE_DIMENSION_UPDATE_FAIL("量表维度修改失败", 1644),
    SCALE_DIMENSION_DELETE_FAIL("量表维度删除失败", 1645),

    /* ==================== 题目与选项 ==================== */
    SCALE_QUESTION_NOT_FOUND("量表题目不存在", 1650),
    SCALE_QUESTION_ADD_FAIL("量表题目新增失败", 1651),
    SCALE_QUESTION_OPTION_MISMATCH("题目与选项不匹配", 1652),
    SCALE_OPTION_NOT_FOUND("量表选项不存在", 1653),
    SCALE_OPTION_ADD_FAIL("选项新增失败", 1654),
    SCALE_OPTION_SORT_EXISTS("同一题目下选项顺序重复", 1655),
    SCALE_QUESTION_UPDATE_FAIL("量表题目修改失败", 1656),
    SCALE_OPTION_UPDATE_FAIL("选项修改失败", 1657),
    SCALE_QUESTION_COPY_FAIL("题目复制失败", 1658),

    /* ==================== 选项模板 ==================== */
    SCALE_OPTION_TEMPLATE_GROUP_NOT_FOUND("选项模板组不存在", 1660),
    SCALE_OPTION_TEMPLATE_APPLY_FAIL("模板应用到题目失败", 1661),
    SCALE_OPTION_TEMPLATE_ADD_FAIL("选项模板组新增失败", 1662),

    /* ==================== 跳题规则 ==================== */
    SCALE_BRANCH_RULE_NOT_FOUND("跳题规则不存在", 1670),
    SCALE_BRANCH_RULE_SOURCE_INVALID("跳题触发题不属于当前版本", 1671),
    SCALE_BRANCH_RULE_TARGET_INVALID("跳转目标题不属于当前版本", 1672),
    SCALE_BRANCH_RULE_CYCLE("跳题规则不允许形成循环跳转", 1673),
    SCALE_BRANCH_RULE_ADD_FAIL("跳题规则新增失败", 1674),

    /* ==================== 结果规则 ==================== */
    SCALE_RESULT_RULE_NOT_FOUND("结果规则不存在", 1680),
    SCALE_RESULT_RULE_RANGE_INVALID("结果规则区间不合法", 1681),
    SCALE_RESULT_RULE_OVERLAP("同一维度下的结果区间不允许重叠", 1682),
    SCALE_RESULT_RULE_ADD_FAIL("结果规则新增失败", 1683),

    /* ==================== 常模 ==================== */
    SCALE_NORM_GROUP_NOT_FOUND("常模组不存在", 1690),
    SCALE_NORM_GROUP_FORBIDDEN_DELETE("常模组已关联测评记录，不允许删除", 1691),
    SCALE_NORM_GROUP_INCOMPLETE("查表法常模必须有完整的常模明细", 1692),
    SCALE_NORM_GROUP_ADD_FAIL("常模组新增失败", 1693),
    SCALE_NORM_DETAIL_NOT_FOUND("常模明细不存在", 1694),
    SCALE_NORM_RAW_DUPLICATE("常模明细原始分已存在", 1695),
    SCALE_NORM_RAW_ORDER_INVALID("常模明细原始分必须单调递增有序", 1696),
    SCALE_NORM_ADD_FAIL("常模明细新增失败", 1697),
    SCALE_NORM_UPDATE_FAIL("常模明细修改失败", 1698),
    SCALE_NORM_DELETE_FAIL("常模明细删除失败", 1699),
    SCALE_NORM_GROUP_DELETE_FAIL("常模组删除失败", 1712),

    /* ==================== 测评记录与作答 ==================== */
    SCALE_RECORD_NOT_FOUND("测评记录不存在", 1700),
    SCALE_RECORD_NOT_OWNED("无权访问该测评记录", 1701),
    SCALE_RECORD_FINISHED("该测评已完成提交，请查看结果", 1702),
    SCALE_RECORD_TERMINATED("该测评已终止", 1703),
    SCALE_COOLING("量表作答冷却中，暂不能开始", 1704),
    SCALE_ANSWER_EMPTY("提交的答案不能为空", 1705),
    SCALE_ANSWER_OPTION_INVALID("提交的选项不属于该题目", 1706),
    SCALE_ANSWER_PATH_INVALID("作答路径不合法", 1707),
    SCALE_TIME_LIMIT_EXCEEDED("作答已超时", 1708),
    SCALE_START_FAIL("开始测评失败", 1709),
    SCALE_REPEAT_LIMITED("该量表不允许重复作答", 1710),
    SCALE_REQUIRED_UNANSWERED("存在必答题未作答，请完成全部必答题后提交", 1711),

    /* ==================== 量表工具（AI对话） ==================== */
    SCALE_TOOL_PENDING_ACTION_INVALID("量表工具待处理记录不存在或无权访问", 1713),
    SCALE_TOOL_RECORD_UNFINISHED("该量表尚未完成作答提交，无法生成分析结果", 1714),

    ;

    @Getter
    private String msg;
    @Getter
    private int code;

    ScaleExceptionEnum(String msg, int code) {
        this.msg = msg;
        this.code = code;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public void setCode(int code) {
        this.code = code;
    }
}