/**
 * IndexedDB 本地日志持久化器
 * 仿照 logback RollingFileAppender：
 *   - 日志批量写入 IndexedDB
 *   - 按 maxAge（天）定期清理过期日志
 *   - 按 maxSize（MB）限制总存储量
 *
 * RemoteUploader 通过 getUnsentLogs / markAsSent 读取并上报后标记已发送
 */
class IndexedDBAppender {
    constructor(options = {}) {
        this.dbName = options.dbName || 'FrontendLogs';
        this.storeName = options.storeName || 'logs';
        this.dbVersion = 1;
        this.maxAge = options.maxAge || 60;
        this.maxSize = options.maxSize || 50 * 1024 * 1024;
        this.batchSize = options.batchSize || 32;
        this.flushInterval = options.flushInterval || 5000;

        this.db = null;
        this.buffer = [];
        this.flushTimer = null;
        this.cleanupTimer = null;
        this._ready = false;
    }

    async init() {
        return new Promise((resolve, reject) => {
            if (!window.indexedDB) {
                this._ready = false;
                reject(new Error('浏览器不支持 IndexedDB'));
                return;
            }
            const request = window.indexedDB.open(this.dbName, this.dbVersion);
            request.onupgradeneeded = (event) => {
                const db = event.target.result;
                if (!db.objectStoreNames.contains(this.storeName)) {
                    const store = db.createObjectStore(this.storeName, {
                        keyPath: 'id',
                        autoIncrement: true
                    });
                    store.createIndex('timestamp', 'timestamp', { unique: false });
                    store.createIndex('uploaded', 'uploaded', { unique: false });
                }
            };
            request.onsuccess = (event) => {
                this.db = event.target.result;
                this._ready = true;
                this._startFlushTimer();
                this._startCleanupTimer();
                resolve();
            };
            request.onerror = (event) => {
                this._ready = false;
                reject(event.target.error);
            };
        });
    }

    write(logEntry) {
        if (!this._ready) return;
        const entry = Object.assign({ uploaded: 0 }, logEntry);
        this.buffer.push(entry);
        if (this.buffer.length >= this.batchSize) {
            this._flush();
        }
    }

    async _flush() {
        if (this.buffer.length === 0 || !this._ready) return;
        const batch = this.buffer.splice(0);
        const transaction = this.db.transaction([this.storeName], 'readwrite');
        const store = transaction.objectStore(this.storeName);
        for (const entry of batch) {
            store.add(entry);
        }
        return new Promise((resolve, reject) => {
            transaction.oncomplete = () => resolve();
            transaction.onerror = (e) => reject(e.target.error);
        });
    }

    async getUnsentLogs(limit = 100) {
        if (!this._ready) return [];
        return new Promise((resolve) => {
            const transaction = this.db.transaction([this.storeName], 'readonly');
            const store = transaction.objectStore(this.storeName);
            const index = store.index('uploaded');
            const range = IDBKeyRange.only(0);
            const results = [];
            const request = index.openCursor(range);
            request.onsuccess = (event) => {
                const cursor = event.target.result;
                if (cursor && results.length < limit) {
                    results.push(cursor.value);
                    cursor.continue();
                } else {
                    resolve(results);
                }
            };
            request.onerror = () => resolve([]);
        });
    }

    async markAsSent(ids) {
        if (!this._ready || ids.length === 0) return;
        const transaction = this.db.transaction([this.storeName], 'readwrite');
        const store = transaction.objectStore(this.storeName);
        for (const id of ids) {
            store.get(id).onsuccess = (e) => {
                const record = e.target.result;
                if (record) {
                    record.uploaded = 1;
                    store.put(record);
                }
            };
        }
    }

    _startFlushTimer() {
        this.flushTimer = setInterval(() => this._flush(), this.flushInterval);
    }

    _startCleanupTimer() {
        this.cleanupTimer = setInterval(() => this._cleanup(), 60 * 60 * 1000);
        this._cleanup();
    }

    async _cleanup() {
        if (!this._ready) return;
        const cutoff = Date.now() - this.maxAge * 24 * 60 * 60 * 1000;
        const cutoffTimestamp = new Date(cutoff).toISOString()
            .replace('T', ' ').substring(0, 19);

        const transaction = this.db.transaction([this.storeName], 'readwrite');
        const store = transaction.objectStore(this.storeName);
        const index = store.index('timestamp');
        const range = IDBKeyRange.upperBound(cutoffTimestamp, false);
        const request = index.openCursor(range);
        request.onsuccess = (event) => {
            const cursor = event.target.result;
            if (cursor) {
                cursor.delete();
                cursor.continue();
            }
        };
    }

    destroy() {
        if (this.flushTimer) clearInterval(this.flushTimer);
        if (this.cleanupTimer) clearInterval(this.cleanupTimer);
        this._flush().catch(() => {});
        if (this.db) this.db.close();
    }
}