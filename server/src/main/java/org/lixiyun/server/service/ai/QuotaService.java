package org.lixiyun.server.service.ai;

/**
 * AI对话配额消费引擎服务接口
 * <p>承载配额扣减、回滚与剩余提示文案等**用户侧消费**能力，
 * 供 {@code QuotaLimitAspect}（@QuotaLimit 注解切面）在文本对话等入口接入。</p>
 * <p>全局配置与用户级配额的管理能力见 {@link org.lixiyun.server.service.admin.AdminQuotaConfigService}
 * / {@link org.lixiyun.server.service.admin.SysUserQuotaService}。</p>
 *
 * @author lixiyun
 * @since 2026-10-15
 */
public interface QuotaService {

    /**
     * 原子扣减一次配额
     * <p>执行顺序：配置缺失→放行兜底；available=0→抛业务异常（资源停用）；enabled=0→放行；unlimited=1→放行不计数；
     * 否则条件 UPDATE（行锁串行）扣减，超限返回 false；行不存在时懒创建（唯一键去重）后重试。
     * 扣减成功/超限 best-effort 落审计明细。</p>
     *
     * @param userId       用户ID {@link org.lixiyun.pojo.entity.quota.QuotaUser}
     * @param resourceType 资源类型：1=TEXT 2=ASR 3=TTS 4=AVATAR_2D {@link org.lixiyun.pojo.entity.quota.QuotaConfig}
     * @return true=放行/扣减成功；false=超限被拒（调用方抛 QUOTA_EXCEEDED）
     */
    boolean tryConsume(Long userId, Integer resourceType);

    /**
     * 回滚一次扣减（失败补偿，best-effort 减 1，下限 0）
     *
     * @param userId       用户ID
     * @param resourceType 资源类型
     */
    void rollbackConsume(Long userId, Integer resourceType);

    /**
     * 拼接「已达上限」提示文案（按资源类型）
     *
     * @param resourceType 资源类型
     * @return 如「AI文本对话次数已达上限，如需继续使用请联系管理员」
     */
    String getRemainingDesc(Integer resourceType);

    /**
     * 只读预检：用户某资源当前是否仍有可用额度
     * <p>与 {@link #tryConsume} 前置判定逐条同口径：配置缺失→放行；available=0→无额度；enabled=0→放行；
     * unlimited=1→放行；否则 {@code used &lt; 生效上限} 才为 true。<b>只读不扣减、不写审计明细</b>，
     * 供会话初始化等场景做「无额度早拒绝」，实际用量仍由消费点 {@link #tryConsume} 精确控制。</p>
     *
     * @param userId       用户ID
     * @param resourceType 资源类型
     * @return true=有额度/放行；false=无额度（超限或资源停用）
     */
    boolean hasQuota(Long userId, Integer resourceType);
}