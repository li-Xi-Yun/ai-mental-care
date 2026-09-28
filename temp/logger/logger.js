/**
 * 前端日志核心
 * 仿照 SLF4J + Logback 的分发机制：
 *   Logger 负责接收日志调用 → 级别过滤 → 分发给所有 Appender
 *
 * 使用方式：
 *   const logger = window.__logger.getLogger('ChatWebSocket');
 *   logger.info('WebSocket连接成功');
 *   logger.warn('心跳超时', { latency: 5000 });
 *   logger.error('请求失败', error);
 */

class Logger {
    /**
     * @param {string} module  模块名，对应后端 %logger{36}
     * @param {Object} config  含 level 阈值
     * @param {Array}  appenders  输出目标列表
     */
    constructor(module, config, appenders) {
        this.module = module;
        this.config = config;
        this.appenders = appenders || [];
    }

    _timestamp() {
        const now = new Date();
        const pad = (n) => String(n).padStart(2, '0');
        const ms = String(now.getMilliseconds()).padStart(3, '0');
        return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} `
            + `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}.${ms}`;
    }

    _buildEntry(level, message, extra) {
        const entry = {
            timestamp: this._timestamp(),
            level: LogLevel.getName(level),
            module: this.module,
            message: String(message),
            extra: null,
            stack: null
        };

        if (extra instanceof Error) {
            entry.stack = extra.stack || extra.message;
        } else if (extra !== undefined && extra !== null) {
            entry.extra = extra;
        }

        return entry;
    }

    log(level, message, extra) {
        if (level < this.config.level) return;

        const entry = this._buildEntry(level, message, extra);
        for (const appender of this.appenders) {
            try {
                appender.write(entry);
            } catch (e) {
                // 写日志本身不能再抛异常
            }
        }
    }

    debug(message, extra) { this.log(LogLevel.DEBUG, message, extra); }
    info(message, extra)  { this.log(LogLevel.INFO, message, extra); }
    warn(message, extra)  { this.log(LogLevel.WARN, message, extra); }
    error(message, extra) { this.log(LogLevel.ERROR, message, extra); }
}


/**
 * Logger 工厂
 * 管理所有模块的 Logger 实例，确保同一模块共用同一个 Logger
 */
const LoggerFactory = {
    _config: { level: LogLevel.INFO },
    _appenders: [],
    _loggers: {},

    init(config, appenders) {
        this._config = config || { level: LogLevel.INFO };
        this._appenders = appenders || [];
    },

    getLogger(module) {
        if (!this._loggers[module]) {
            this._loggers[module] = new Logger(module, this._config, this._appenders);
        }
        return this._loggers[module];
    },

    setLevel(level) {
        this._config.level = level;
    }
};