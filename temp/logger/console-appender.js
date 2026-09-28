/**
 * 控制台日志输出器
 * 仿照 logback ConsoleAppender，仅输出到浏览器控制台
 */
class ConsoleAppender {
    /**
     * @param {Object} logEntry  { timestamp, level, module, message, stack, extra }
     */
    write(logEntry) {
        const time = logEntry.timestamp ? logEntry.timestamp.substring(11, 19) : '';
        const prefix = `[${time}] [${logEntry.module}]`;
        const msg = logEntry.message;

        switch (logEntry.level) {
            case 'TRACE':
            case 'DEBUG':
                console.debug(prefix, msg, logEntry.extra || '');
                break;
            case 'INFO':
                console.log(prefix, msg, logEntry.extra || '');
                break;
            case 'WARN':
                console.warn(prefix, msg, logEntry.extra || '');
                break;
            case 'ERROR':
                console.error(prefix, msg, logEntry.stack || logEntry.extra || '');
                break;
            default:
                console.log(prefix, msg, logEntry.extra || '');
        }
    }
}