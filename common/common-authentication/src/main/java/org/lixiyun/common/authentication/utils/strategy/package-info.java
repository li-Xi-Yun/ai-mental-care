/**
 * JWT认证策略包
 * 
 * 使用策略模式管理不同用户类型的JWT认证逻辑
 * 
 * 包含：
 * - JwtAuthenticationStrategy: 策略接口
 * - AbstractJwtAuthenticationStrategy: 抽象基类，提供通用实现
 * - UserJwtAuthenticationStrategy: 用户认证策略
 * - AdminJwtAuthenticationStrategy: 管理员认证策略
 * - GuestJwtAuthenticationStrategy: 游客认证策略
 * - JwtAuthenticationStrategyFactory: 策略工厂
 * 
 * 使用示例：
 * <pre>
 * // 1. 通过JwtUtil使用（推荐）
 * String token = JwtUtil.createJwtWithRedis(loginUser, true, JwtType.USER);
 * Long userId = JwtUtil.parseJwtWithType(token, JwtType.USER);
 * JwtUtil.deleteJwtWithRedis(userId, JwtType.USER);
 * 
 * // 2. 直接使用策略（高级用法）
 * JwtAuthenticationStrategy strategy = JwtAuthenticationStrategyFactory.getStrategy(JwtType.USER);
 * String tokenName = strategy.getTokenName();
 * String secretKey = strategy.getSecretKey();
 * 
 * // 3. 注册自定义策略
 * JwtAuthenticationStrategy customStrategy = new CustomJwtAuthenticationStrategy();
 * JwtAuthenticationStrategyFactory.registerStrategy(JwtType.CUSTOM, customStrategy);
 * </pre>
 * 
 * 扩展新的认证类型：
 * 1. 在 JwtType 枚举中添加新类型
 * 2. 创建新的策略类继承 AbstractJwtAuthenticationStrategy
 * 3. 在 JwtAuthenticationStrategyFactory 中注册新策略
 * 
 * @see org.lixiyun.common.authentication.enums.JwtType
 * @see org.lixiyun.common.authentication.utils.JwtUtil
 */
package org.lixiyun.common.authentication.utils.strategy;