/**
 * 前端日志系统初始化
 * 在 config.js 之后、所有业务脚本之前加载
 *
 * 架构对应关系（前端 → 后端 logback）：
 *   ConsoleAppender     → <appender name="console" class="ConsoleAppender">
 *   IndexedDBAppender   → <appender name="file_info/warn/error" class="RollingFileAppender">
 *   RemoteUploader      → <appender name="sky_log" class="GRPCLogClientAppender"> (被注释的)
 *
 * 日志流向：
 *   业务代码 logger.info(msg)
 *     → Logger（级别过滤）
 *       → ConsoleAppender.write()  → 浏览器控制台（始终启用，开发友好）
 *       → IndexedDBAppender.write() → IndexedDB 本地暂存
 *         → RemoteUploader._upload() → POST /api/logs/upload → 后端 logback name="frontend"
 *           → ./logs/frontend/sys-info|warn|error|debug.log
 */
(function () {

// ==================== 1. 创建组件实例 ====================

    const consoleAppender = new ConsoleAppender();

    const dbAppender = new IndexedDBAppender({
        maxAge: 60,         // 保留 60 天（对应 logback maxHistory=60）
        maxSize: 50,        // 最大 50MB
        batchSize: 32,      // 攒 32 条批量写入 IndexedDB
        flushInterval: 5000 // 5 秒定时刷盘（对应 AsyncAppender）
    });

    const remoteUploader = new RemoteUploader({
        uploadUrl: '/api/logs/upload',
        dbAppender: dbAppender,
        uploadInterval: 30000,  // 30 秒上传一次
        maxBatchSize: 100       // 单次最多 100 条
    });

// ==================== 2. 初始化 LoggerFactory ====================

    LoggerFactory.init(
        { level: LogLevel.DEBUG },   // 默认 DEBUG，生产可通过配置覆盖
        [consoleAppender, dbAppender]
    );

    window.__logger = LoggerFactory;

// ==================== 3. 异步初始化 IndexedDB，启动上报 ====================

    dbAppender.init()
        .then(() => {
            remoteUploader.start();
            console.debug('[Logger] IndexedDB 就绪，远程上报已启动');
        })
        .catch((e) => {
            console.warn('[Logger] IndexedDB 不可用，仅控制台输出:', e.message);
        });

})();