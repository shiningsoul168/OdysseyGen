
export function generateIdempotentKey() {
    try {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') {
            return window.crypto.randomUUID();
        }
    } catch (e) {
        console.warn('crypto.randomUUID 不可用，使用 fallback');
    }
    // 改用 performance.now() + 更强的随机性 + 计数器（可选）
    return performance.now().toString(36) + '-' + Math.random().toString(36).substring(2, 15) + '-' + Date.now().toString(36);
}