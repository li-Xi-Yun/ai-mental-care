package org.lixiyun.common.redis.utils;

import cn.hutool.extra.spring.SpringUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.redisson.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * redis 工具类
 *
 * @author lixiyun
 * @version 3.1.0 新增
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings(value = {"unchecked", "rawtypes"})
public class RedisUtils {

    private static final RedissonClient CLIENT = SpringUtil.getBean(RedissonClient.class);

    /**
     * 限流
     *
     * @param key          限流key
     * @param rateType     限流类型
     * @param rate         速率
     * @param rateInterval 速率间隔
     * @return -1 表示失败
     */
    public static long rateLimiter(String key, RateType rateType, int rate, int rateInterval) {
        RRateLimiter rateLimiter = CLIENT.getRateLimiter(key);
        rateLimiter.trySetRate(rateType, rate, rateInterval, RateIntervalUnit.SECONDS);
        if (rateLimiter.tryAcquire()) {
            return rateLimiter.availablePermits();
        } else {
            return -1L;
        }
    }

    /**
     * 获取客户端实例
     */
    public static RedissonClient getClient() {
        return CLIENT;
    }

    /**
     * 发布通道消息
     *
     * @param channelKey 通道key
     * @param msg        发送数据
     * @param consumer   自定义处理
     */
    public static <T> void publish(String channelKey, T msg, Consumer<T> consumer) {
        RTopic topic = CLIENT.getTopic(channelKey);
        topic.publish(msg);
        consumer.accept(msg);
    }

    public static <T> void publish(String channelKey, T msg) {
        RTopic topic = CLIENT.getTopic(channelKey);
        topic.publish(msg);
    }

    /**
     * 订阅通道接收消息
     *
     * @param channelKey 通道key
     * @param clazz      消息类型
     * @param consumer   自定义处理
     */
    public static <T> void subscribe(String channelKey, Class<T> clazz, Consumer<T> consumer) {
        RTopic topic = CLIENT.getTopic(channelKey);
        topic.addListener(clazz, (channel, msg) -> consumer.accept(msg));
    }

    /**
     * 缓存基本的对象，Integer、String、实体类等，不设置过期时间
     *
     * @param key   缓存的键值
     * @param value 缓存的值
     */
    public static <T> void setCacheObject(final String key, final T value) {
        setCacheObject(key, value, false);
    }

    /**
     * 缓存基本的对象，Integer、String、实体类等
     *
     * @param key      缓存的键值
     * @param value    缓存的值
     * @param timeout  超时时间
     * @param timeUnit 时间单位
     */
    public static <T> void setCacheObject(final String key, final T value, final long timeout, final TimeUnit timeUnit) {
        setCacheObject(key, value, Duration.of(timeout, timeUnit.toChronoUnit()));
    }

    /**
     * 缓存基本的对象，保留当前对象 TTL 有效期
     *
     * @param key       缓存的键值
     * @param value     缓存的值
     * @param isSaveTtl 是否保留TTL有效期(例如: set之前ttl剩余90 set之后还是为90)
     * @since Redis 6.X 以上使用 setAndKeepTTL 兼容 5.X 方案
     */
    public static <T> void setCacheObject(final String key, final T value, final boolean isSaveTtl) {
        RBucket<T> bucket = CLIENT.getBucket(key);
        if (isSaveTtl) {
            try {
                bucket.setAndKeepTTL(value);
            } catch (Exception e) {
                long timeToLive = bucket.remainTimeToLive();
                setCacheObject(key, value, Duration.ofMillis(timeToLive));
            }
        } else {
            bucket.set(value);
        }
    }

    /**
     * 缓存基本的对象，Integer、String、实体类等
     *
     * @param key      缓存的键值
     * @param value    缓存的值
     * @param duration 时间
     */
    public static <T> void setCacheObject(final String key, final T value, final Duration duration) {
        RBatch batch = CLIENT.createBatch();
        RBucketAsync<T> bucket = batch.getBucket(key);
        bucket.setAsync(value);
        bucket.expireAsync(duration);
        batch.execute();
    }

    /**
     * 注册对象监听器
     * <p>
     * key 监听器需开启 `notify-keyspace-events` 等 redis 相关配置
     *
     * @param key      缓存的键值
     * @param listener 监听器配置
     */
    public static <T> void addObjectListener(final String key, final ObjectListener listener) {
        RBucket<T> result = CLIENT.getBucket(key);
        result.addListener(listener);
    }

    /**
     * 设置有效时间
     *
     * @param key     Redis键
     * @param timeout 超时时间
     * @return true=设置成功；false=设置失败
     */
    public static boolean expire(final String key, final long timeout) {
        return expire(key, Duration.ofSeconds(timeout));
    }

    /**
     * 设置过期时间
     *
     * @param redisKey  Redis键
     * @param extendMillis 过期时间
     * @param timeUnit 时间单位
     */
    public static void expire(String redisKey, long extendMillis, TimeUnit timeUnit) {
        expire(redisKey, Duration.of(extendMillis, timeUnit.toChronoUnit()));
    }

    /**
     * 设置有效时间
     *
     * @param key      Redis键
     * @param duration 超时时间
     * @return true=设置成功；false=设置失败
     */
    public static boolean expire(final String key, final Duration duration) {
        RBucket rBucket = CLIENT.getBucket(key);
        return rBucket.expire(duration);
    }

    /**
     * 获得缓存的基本对象。
     *
     * @param key 缓存键值
     * @return 缓存键值对应的数据
     */
    public static <T> T getCacheObject(final String key) {
        RBucket<T> rBucket = CLIENT.getBucket(key);
        return rBucket.get();
    }

    /**
     * 获得key剩余存活时间
     *
     * @param key 缓存键值
     * @return 剩余存活时间
     */
    public static <T> long getTimeToLive(final String key) {
        RBucket<T> rBucket = CLIENT.getBucket(key);
        return rBucket.remainTimeToLive();
    }

    /**
     * 获取指定键的过期时间（秒）
     *
     * @param key Redis键
     * @return 过期时间(秒)，-1表示永不过期，-2表示键不存在
     */
    public static long getExpire(final String key) {
        RBucket<Object> bucket = CLIENT.getBucket(key);
        if (!bucket.isExists()) {
            return -2L;
        }
        long ttl = bucket.remainTimeToLive();
        return ttl == -1 ? -1 : ttl / 1000;
    }

    /**
     * 删除单个对象
     *
     * @param key 缓存的键值
     */
    public static boolean deleteObject(final String key) {
        return CLIENT.getBucket(key).delete();
    }

    /**
     * 删除集合对象
     *
     * @param collection 多个对象
     */
    public static void deleteObject(final Collection collection) {
        RBatch batch = CLIENT.createBatch();
        collection.forEach(t -> {
            batch.getBucket(t.toString()).deleteAsync();
        });
        batch.execute();
    }

    /**
     * 检查缓存对象是否存在
     *
     * @param key 缓存的键值
     */
    public static boolean isExistsObject(final String key) {
        return CLIENT.getBucket(key).isExists();
    }

    /**
     * 缓存List数据
     *
     * @param key      缓存的键值
     * @param dataList 待缓存的List数据
     * @return 缓存的对象
     */
    public static <T> boolean setCacheList(final String key, final List<T> dataList) {
        RList<T> rList = CLIENT.getList(key);
        return rList.addAll(dataList);
    }

    /**
     * 注册List监听器
     * <p>
     * key 监听器需开启 `notify-keyspace-events` 等 redis 相关配置
     *
     * @param key      缓存的键值
     * @param listener 监听器配置
     */
    public static <T> void addListListener(final String key, final ObjectListener listener) {
        RList<T> rList = CLIENT.getList(key);
        rList.addListener(listener);
    }

    /**
     * 获得缓存的list对象
     *
     * @param key 缓存的键值
     * @return 缓存键值对应的数据
     */
    public static <T> List<T> getCacheList(final String key) {
        RList<T> rList = CLIENT.getList(key);
        return rList.readAll();
    }

    /**
     * 缓存Set
     *
     * @param key     缓存键值
     * @param dataSet 缓存的数据
     * @return 缓存数据的对象
     */
    public static <T> boolean setCacheSet(final String key, final Set<T> dataSet) {
        RSet<T> rSet = CLIENT.getSet(key);
        return rSet.addAll(dataSet);
    }

    /**
     * 注册Set监听器
     * <p>
     * key 监听器需开启 `notify-keyspace-events` 等 redis 相关配置
     *
     * @param key      缓存的键值
     * @param listener 监听器配置
     */
    public static <T> void addSetListener(final String key, final ObjectListener listener) {
        RSet<T> rSet = CLIENT.getSet(key);
        rSet.addListener(listener);
    }

    /**
     * 获得缓存的set
     *
     * @param key 缓存的key
     * @return set对象
     */
    public static <T> Set<T> getCacheSet(final String key) {
        RSet<T> rSet = CLIENT.getSet(key);
        return rSet.readAll();
    }

    /**
     * 缓存Map
     *
     * @param key     缓存的键值
     * @param dataMap 缓存的数据
     */
    public static <T> void setCacheMap(final String key, final Map<String, T> dataMap) {
        if (dataMap != null) {
            RMap<String, T> rMap = CLIENT.getMap(key);
            rMap.putAll(dataMap);
        }
    }

    /**
     * 注册Map监听器
     * <p>
     * key 监听器需开启 `notify-keyspace-events` 等 redis 相关配置
     *
     * @param key      缓存的键值
     * @param listener 监听器配置
     */
    public static <T> void addMapListener(final String key, final ObjectListener listener) {
        RMap<String, T> rMap = CLIENT.getMap(key);
        rMap.addListener(listener);
    }

    /**
     * 获得缓存的Map
     *
     * @param key 缓存的键值
     * @return map对象
     */
    public static <T> Map<String, T> getCacheMap(final String key) {
        RMap<String, T> rMap = CLIENT.getMap(key);
        return rMap.getAll(rMap.keySet());
    }

    /**
     * 获得缓存Map的key列表
     *
     * @param key 缓存的键值
     * @return key列表
     */
    public static <T> Set<String> getCacheMapKeySet(final String key) {
        RMap<String, T> rMap = CLIENT.getMap(key);
        return rMap.keySet();
    }

    /**
     * 往Hash中存入数据
     *
     * @param key   Redis键
     * @param hKey  Hash键
     * @param value 值
     * @return 原来的值
     */
    public static <T> T setCacheMapValue(final String key, final String hKey, final T value) {
        RMap<String, T> rMap = CLIENT.getMap(key);
        return rMap.put(hKey, value);
    }

    /**
     * 获取Hash中的数据
     *
     * @param key  Redis键
     * @param hKey Hash键
     * @return Hash中的对象
     */
    public static <T> T getCacheMapValue(final String key, final String hKey) {
        RMap<String, T> rMap = CLIENT.getMap(key);
        return rMap.get(hKey);
    }

    /**
     * 删除Hash中的数据
     *
     * @param key  Redis键
     * @param hKey Hash键
     * @return Hash中的对象
     */
    public static <T> T delCacheMapValue(final String key, final String hKey) {
        RMap<String, T> rMap = CLIENT.getMap(key);
        return rMap.remove(hKey);
    }

    /**
     * 获取多个Hash中的数据
     *
     * @param key   Redis键
     * @param hKeys Hash键集合
     * @return Hash对象集合
     */
    public static <K, V> Map<K, V> getMultiCacheMapValue(final String key, final Set<K> hKeys) {
        RMap<K, V> rMap = CLIENT.getMap(key);
        return rMap.getAll(hKeys);
    }

    /**
     * 设置原子值
     *
     * @param key   Redis键
     * @param value 值
     */
    public static void setAtomicValue(String key, long value) {
        RAtomicLong atomic = CLIENT.getAtomicLong(key);
        atomic.set(value);
    }

    /**
     * 获取原子值
     *
     * @param key Redis键
     * @return 当前值
     */
    public static long getAtomicValue(String key) {
        RAtomicLong atomic = CLIENT.getAtomicLong(key);
        return atomic.get();
    }

    /**
     * 递增原子值
     *
     * @param key Redis键
     * @return 当前值
     */
    public static long incrAtomicValue(String key) {
        RAtomicLong atomic = CLIENT.getAtomicLong(key);
        return atomic.incrementAndGet();
    }

    /**
     * 递减原子值
     *
     * @param key Redis键
     * @return 当前值
     */
    public static long decrAtomicValue(String key) {
        RAtomicLong atomic = CLIENT.getAtomicLong(key);
        return atomic.decrementAndGet();
    }

    /**
     * 获得缓存的基本对象列表
     *
     * @param pattern 字符串前缀
     * @return 对象列表
     */
    public static Collection<String> keys(final String pattern) {
        Stream<String> stream = CLIENT.getKeys().getKeysStreamByPattern(pattern);
        return stream.collect(Collectors.toList());
    }

    /**
     * 删除缓存的基本对象列表
     *
     * @param pattern 字符串前缀
     */
    public static void deleteKeys(final String pattern) {
        CLIENT.getKeys().deleteByPattern(pattern);
    }

    /**
     * 检查redis中是否存在key
     *
     * @param key 键
     */
    public static Boolean hasKey(String key) {
        RKeys rKeys = CLIENT.getKeys();
        return rKeys.countExists(key) > 0;
    }

    /**
     * 执行Lua脚本
     *
     * @param redisScript Lua脚本
     * @param keys 键列表
     * @param args 参数列表
     * @return 脚本执行结果
     */
    public static <T> T execute(DefaultRedisScript<T> redisScript, List<String> keys, Object... args) {
        StringRedisTemplate stringRedisTemplate = SpringUtil.getBean(StringRedisTemplate.class);
        return stringRedisTemplate.execute(redisScript, keys, args);
    }

    /**
     * 执行Lua脚本
     *
     * @param scriptContent Lua脚本内容
     * @param returnType 返回类型
     * @param keys 键列表
     * @param args 参数列表
     * @return 脚本执行结果
     */
    public static <T> T executeLuaScript(String scriptContent, RScript.ReturnType returnType, List<String> keys, Object... args) {
        RScript rScript = CLIENT.getScript();

        return (T) rScript.eval(
                RScript.Mode.READ_WRITE,
                scriptContent,
                returnType,
                Collections.singletonList(keys),
                args
        );
    }

    /**
     * 尝试获取分布式锁
     *
     * @param lockKey      锁的Key
     * @param waitTimeSec  等待时间（秒）
     * @param leaseTimeSec 锁持有时间（秒）
     * @return boolean true-成功获取锁，false-获取失败
     */
    public static boolean tryAcquireLock(String lockKey, long waitTimeSec, long leaseTimeSec) {
        try {
            RLock lock = CLIENT.getLock(lockKey);
            return lock.tryLock(waitTimeSec, leaseTimeSec, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 释放分布式锁
     * <p>
     * 只有锁的持有者才能释放锁，防止误释放。
     * </p>
     *
     * @param lockKey 锁的Key
     */
    public static void releaseLock(String lockKey) {
        RLock lock = CLIENT.getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * 向有序集合（ZSet/ScoredSortedSet）添加元素
     * <p>用于会话消息队列等场景，按时间戳排序存储数据</p>
     *
     * @param key   Redis键（ZSet的key）
     * @param score 分数（通常为时间戳）
     * @param value 值（通常为会话ID等标识）
     */
    public static void addToScoredSortedSet(String key, double score, String value) {
        RScoredSortedSet<String> scoredSortedSet = CLIENT.getScoredSortedSet(key);
        scoredSortedSet.add(score, value);
    }

    /**
     * 从有序集合（ZSet/ScoredSortedSet）中移除元素
     *
     * @param key   Redis键
     * @param value 要移除的值
     * @return 是否成功移除
     */
    public static boolean removeFromScoredSortedSet(String key, String value) {
        RScoredSortedSet<String> scoredSortedSet = CLIENT.getScoredSortedSet(key);
        return scoredSortedSet.remove(value);
    }

    /**
     * 获取有序集合（ZSet/ScoredSortedSet）中的所有元素
     *
     * @param key Redis键
     * @return 有序集合的所有值（按分数升序排列）
     */
    public static Collection<String> getScoredSortedSetValues(String key) {
        RScoredSortedSet<String> scoredSortedSet = CLIENT.getScoredSortedSet(key);
        return scoredSortedSet.readAll();
    }

    /**
     * 获取有序集合（ZSet/ScoredSortedSet）中指定分数范围内的元素
     *
     * @param key       Redis键
     * @param startScore 起始分数（包含）
     * @param endScore   结束分数（包含）
     * @return 符合条件的值集合
     */
    public static Collection<String> getScoredSortedSetByScoreRange(String key, double startScore, double endScore) {
        RScoredSortedSet<String> scoredSortedSet = CLIENT.getScoredSortedSet(key);
        return scoredSortedSet.valueRange(startScore, true, endScore, true);
    }

    /**
     * 原子性Compare-And-Swap (CAS) 操作 - Hash字段值替换
     * <p>
     * 使用Redisson RMap的CAS语义，保证并发安全：
     * <ul>
     *     <li>只有当Hash Field的当前值等于期望的旧值时，才执行替换</li>
     *     <li>整个操作是原子的，不会被其他线程打断</li>
     *     <li>适用于分布式锁、状态机转换、令牌获取等场景</li>
     * </ul>
     * </p>
     *
     * @param key         Redis键（Hash的主Key）
     * @param field       Hash字段名
     * @param expectValue 期望的旧值（null表示期望字段不存在）
     * @param newValue    要设置的新值
     * @return 是否成功替换（true=成功，false=当前值与期望值不匹配或已被其他线程修改）
     */
    public static boolean compareAndSwapMapValue(String key, String field, Object expectValue, Object newValue) {
        try {
            RMap<String, Object> rMap = CLIENT.getMap(key);

            Object currentValue = rMap.get(field);

            if (expectValue == null && currentValue == null) {
                return rMap.fastPutIfAbsent(field, newValue);
            }

            if (currentValue != null && currentValue.equals(expectValue)) {
                return rMap.replace(field, currentValue, newValue);
            }

            return false;
        } catch (Exception e) {
            throw new RuntimeException("CAS操作失败，key: " + key + ", field: " + field, e);
        }
    }

}