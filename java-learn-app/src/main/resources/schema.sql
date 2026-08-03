-- M2 用户表。使用 IF NOT EXISTS 保证每次启动幂等（已存在则不重建）。
CREATE TABLE IF NOT EXISTS users (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    username   VARCHAR(64)  NOT NULL,
    password   VARCHAR(100) NOT NULL,
    role       VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- M3 商品表。演示 InnoDB 索引设计：主键 id、普通索引 name（用于 EXPLAIN 验证索引命中/失效）。
CREATE TABLE IF NOT EXISTS products (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(128)  NOT NULL,
    price       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    description VARCHAR(512)  DEFAULT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_products_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- M3 库存表。与商品一对一并通过 product_id 唯一关联；version 字段用于 MyBatis-Plus @Version 乐观锁。
CREATE TABLE IF NOT EXISTS stock (
    id          BIGINT    NOT NULL AUTO_INCREMENT,
    product_id  BIGINT    NOT NULL,
    total       INT       NOT NULL DEFAULT 0,
    available   INT       NOT NULL DEFAULT 0,
    version     INT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- M3 订单表。"下单即扣库存"会把一条订单与库存扣减放在同一个数据库事务里完成。
CREATE TABLE IF NOT EXISTS orders (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    order_no   VARCHAR(64)  NOT NULL,
    user_id    BIGINT       NOT NULL,
    product_id BIGINT       NOT NULL,
    quantity   INT          NOT NULL,
    amount     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status     VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_no (order_no),
    KEY idx_orders_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
