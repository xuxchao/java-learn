package com.example.ecommerce.service;

/**
 * 下单扣库存的并发控制策略（M3）。
 *
 * <ul>
 *   <li>{@code OPTIMISTIC}：CAS 乐观锁。下单时在单条 SQL 中"比较 available &gt;= 数量 并原子扣减"
 *       （Compare-And-Swap），不阻塞读取、冲突概率低时吞吐高；冲突时 UPDATE 命中 0 行需重试。</li>
 *   <li>{@code PESSIMISTIC}：基于 {@code SELECT ... FOR UPDATE} 行锁，
 *       读取即锁住该行直到事务结束，绝对不会冲突但并发度低、易锁等待。</li>
 * </ul>
 */
public enum LockType {
    OPTIMISTIC,
    PESSIMISTIC
}
