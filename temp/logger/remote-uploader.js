/**
 * 远程日志上报器
 * 仿照后端 SkyWalking GRPC 上报（被注释掉的 sky_log appender）
 *
 * 定时从 IndexedDB 读取未上报的日志，批量 POST 到后端 /common/logs/frontend/upload
 * 上传成功后标记为已发送，避免重复上报
 */
class RemoteUploader {
    /**
     * @param {Object} options
     * @param {string} options.uploadUrl     上报 URL
     * @param {IndexedDBAppender} options.dbAppender  IndexedDB Appender 实例
     * @param {number} options.uploadInterval 上报间隔（ms），默认 30 秒
     * @param {number} options.maxBatchSize  单次最大上报条数，默认 100
     */
    constructor(options = {}) {
        this.uploadUrl = options.uploadUrl || '/common/logs/frontend/upload';
        this.dbAppender = options.dbAppender;
        this.uploadInterval = options.uploadInterval || 30000;
        this.maxBatchSize = options.maxBatchSize || 100;
        this.timer = null;
        this._uploading = false;
    }

    start() {
        if (this.timer) return;
        this.timer = setInterval(() => this._upload(), this.uploadInterval);
        this._upload();
    }

    stop() {
        if (this.timer) {
            clearInterval(this.timer);
            this.timer = null;
        }
    }

    async _upload() {
        if (this._uploading || !this.dbAppender) return;
        this._uploading = true;
        try {
            const logs = await this.dbAppender.getUnsentLogs(this.maxBatchSize);
            if (!logs || logs.length === 0) return;

            const payload = {
                logs: logs.map((entry) => ({
                    timestamp: entry.timestamp,
                    level: entry.level,
                    module: entry.module,
                    message: entry.message,
                    stack: entry.stack || null
                }))
            };

            const response = await fetch(this.uploadUrl, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (!response.ok) return;

            const ids = logs.map((entry) => entry.id);
            await this.dbAppender.markAsSent(ids);
        } catch (e) {
            console.warn('[RemoteUploader] 日志上报失败:', e.message);
        } finally {
            this._uploading = false;
        }
    }
}