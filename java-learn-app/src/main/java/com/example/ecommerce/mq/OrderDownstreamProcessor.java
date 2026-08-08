package com.example.ecommerce.mq;

/**
 * 订单下游处理器（M6 消费者业务逻辑抽象）。
 *
 * <p>用接口隔离"异步副作用"本身，便于单元测试里用 lambda 替身验证"重复投递只跑一次"。
 * 真实实现见 {@link DefaultOrderDownstreamProcessor}：标记订单为已处理 + 记录日志（如通知 / 扣减下游库存）。
 *
 * <p>关键认知：库存扣减已经在下单事务（M3）里完成，<b>这里绝不能重复扣减</b>。
 * 这里的副作用（通知 / 状态推进）必须是可幂等重入的，而"只跑一次"由
 * {@link OrderEventConsumer} 借助 M5 的 {@code IdempotencyService} 保证。
 */
public interface OrderDownstreamProcessor {

    /**
     * 处理一条下单事件。幂等键（orderNo）相同的情况下，本方法只会被真正调用一次。
     *
     * @param event 下单成功事件
     * @return 处理结果（会被幂等服务缓存，供重放时直接返回）
     */
    String process(OrderCreatedEvent event);
}
