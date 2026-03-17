-- KEYS[1]: 限流KEY（如 rate_limit:user123:/api/order）
-- ARGV[1]: 当前时间戳（毫秒）
-- ARGV[2]: 窗口总大小（毫秒）
-- ARGV[3]: 窗口内允许的最大请求数
-- ARGV[4]: 请求的唯一标识（用于确保ZSET成员唯一，避免覆盖）

-- 1. 移除当前时间窗口之前的所有记录（滑动窗口的核心）
local windowStart = tonumber(ARGV[1]) - tonumber(ARGV[2])
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, windowStart)

-- 2. 获取当前窗口内的总请求数
local currentCount = redis.call('ZCARD', KEYS[1])

-- 3. 判断是否允许本次请求
if currentCount < tonumber(ARGV[3]) then
    -- 4. 未超限：将当前请求添加到ZSET中（分数=时间戳，成员=唯一标识）
    redis.call('ZADD', KEYS[1], ARGV[1], ARGV[4])
    -- 5. 为KEY设置过期时间（避免冷数据永久占用内存）。过期时间应略大于窗口大小。
    redis.call('EXPIRE', KEYS[1], (tonumber(ARGV[2]) / 1000) + 10) -- 窗口秒数 + 10秒缓冲
    return 1 -- 允许请求
else
    return 0 -- 拒绝请求
end