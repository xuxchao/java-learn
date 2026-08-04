package com.example.ecommerce.cache;

/**
 * 逻辑过期刷新事件：缓存值逻辑过期后，返回旧值的同时发此事件，
 * 由异步线程回源 DB 重写缓存（缓存击穿的"逻辑过期"方案）。
 */
public record RefreshCacheEvent(Long id) {
}
