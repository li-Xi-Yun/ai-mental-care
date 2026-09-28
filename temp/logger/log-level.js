/**
 * 日志级别枚举
 * 仿照 logback Level，数值越大级别越高
 */
const LogLevel = {
    DEBUG: 0,
    INFO: 1,
    WARN: 2,
    ERROR: 3,
    OFF: 99,

    _names: { 0: 'DEBUG', 1: 'INFO', 2: 'WARN', 3: 'ERROR', 99: 'OFF' },

    getName(level) {
        return this._names[level] || 'UNKNOWN';
    },

    fromName(name) {
        const upper = (name || '').toUpperCase();
        for (const [key, value] of Object.entries(this._names)) {
            if (value === upper) return parseInt(key);
        }
        return this.INFO;
    }
};