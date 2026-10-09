package org.lixiyun.pojo.entity.conversation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话待处理人工交互表(ConversationPendingAction)实体类
 *
 * @author lixiyun
 * @since 2026-10-06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationPendingAction implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 交互类型：量表测评 */
    public static final int ACTION_TYPE_SCALE = 1;

    /** 等待用户操作 */
    public static final int STATUS_WAITING = 0;
    /** 待对话注入 */
    public static final int STATUS_PENDING_INJECT = 1;
    /** 已响应 */
    public static final int STATUS_RESPONDED = 2;
    /** 已取消 */
    public static final int STATUS_CANCELLED = 3;
    /** 已过期 */
    public static final int STATUS_EXPIRED = 4;

    /**
     * 自增id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 用户ID（冗余，便于按用户查询）
     */
    private Long userId;

    /**
     * 创建时的对话轮次，前端据此定位卡片插入位置
     */
    private Integer roundNum;

    /**
     * 交互类型：1=量表测评 2=转介咨询师 3=紧急确认
     */
    private Integer actionType;

    /**
     * 模型调用该工具的原因/推荐理由，用于审计追溯
     */
    private String callReason;

    /**
     * 前端渲染卡片所需的上下文数据（JSON格式，结构因action_type而异）
     */
    private String actionData;

    /**
     * 工具执行完的结果内容（JSON格式）
     */
    private String toolResult;

    /**
     * 状态：0=等待用户操作 1=待对话注入 2=已响应 3=已取消 4=已过期
     */
    private Integer status;

    /**
     * 过期时间；NULL=永不过期
     */
    private LocalDateTime expireTime;

    /**
     * 完成/取消时间
     */
    private LocalDateTime completedTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 最后更新时间
     */
    private LocalDateTime updatedTime;

    /**
     * 是否删除，0-否，1-是
     */
    @TableLogic
    private Integer deleted;

    /**
     * 判断当前状态是否为"等待用户操作"
     *
     * @return true 表示状态为等待用户操作，false 表示不是
     */
    public boolean isWaiting() {
        return this.status != null && this.status == STATUS_WAITING;
    }

    /**
     * 判断当前状态是否为"待对话注入"
     *
     * @return true 表示状态为待对话注入，false 表示不是
     */
    public boolean isPendingInject() {
        return this.status != null && this.status == STATUS_PENDING_INJECT;
    }

    /**
     * 判断当前状态是否为"已响应"
     *
     * @return true 表示状态为已响应，false 表示不是
     */
    public boolean isResponded() {
        return this.status != null && this.status == STATUS_RESPONDED;
    }

    /**
     * 判断当前状态是否为"已取消"
     *
     * @return true 表示状态为已取消，false 表示不是
     */
    public boolean isCancelled() {
        return this.status != null && this.status == STATUS_CANCELLED;
    }

    /**
     * 判断当前状态是否为"已过期"
     *
     * @return true 表示状态为已过期，false 表示不是
     */
    public boolean isExpired() {
        return this.status != null && this.status == STATUS_EXPIRED;
    }

    /**
     * 判断当前交互类型是否为"量表测评"
     *
     * @return true 表示交互类型为量表测评，false 表示不是
     */
    public boolean isActionTypeScale() {
        return this.actionType != null && this.actionType == ACTION_TYPE_SCALE;
    }

    /**
     * 获取当前交互类型的文本描述
     *
     * @return 交互类型的文本描述
     */
    public String getActionTypeText() {
        return switch (actionType) {
            case ACTION_TYPE_SCALE -> "量表测评";
            default -> "未知交互类型(" + actionType + ")";
        };
    }

    public String getStatusText() {
        return switch (status) {
            case STATUS_WAITING -> "等待处理中";
            case STATUS_RESPONDED -> "已完成";
            case STATUS_CANCELLED -> "已取消";
            case STATUS_EXPIRED -> "已过期";
            default -> "未知状态(" + status + ")";
        };
    }

}