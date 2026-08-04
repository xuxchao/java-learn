package com.example.ecommerce.cache;

/**
 * 延迟双删事件：写库后先删一次缓存，过一小段时间再删一次，
 * 兜底"删缓存与读请求并发导致旧值被回填"的极小概率窗口（缓存一致性）。
 */
public record DelayedEvictEvent(String key, long delayMs) {
}
