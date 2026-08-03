package com.example.ecommerce.service;

/**
 * 下单扣库存的并发控制策略（M3）。
 *
 * <ul>
 *   <li>{@code OPTIMISTIC}：基于 Stock.version 乐观锁（MyBatis-Plus @Version），
 *       不阻塞读取、冲突概率低时吞吐高，冲突时 update 命中 0 行需重试。</li>
 *   <li>{@code PESSIMISTIC}：基于 {@code SELECT ... FOR UPDATE} 行锁，
 *       读取即锁住该行直到事务结束，绝对不会冲突但并发度低、易锁等待。</li>
 * </ul>
 */
public enum LockType {
    OPTIMISTIC,
    PESSIMISTIC
}
