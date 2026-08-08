package com.example.middleware;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * 各库例子 runner 的基类：统一负责"整库跑全部 / 按名字单跑"的分发逻辑。
 *
 * 子类（RedisExampleRunner / RabbitExampleRunner）只需声明自己的 {@link #moduleName()} 与
 * {@link #examples()}（名字 -> 可运行例子的映射）。运行时读取 {@code example} 属性：
 * <ul>
 *   <li>未指定 -> 顺序跑完该库全部已注册例子，每个打印分段标题；</li>
 *   <li>指定了名字 -> 只跑名字匹配的那一个（便于单点调试）。</li>
 * </ul>
 * {@code example} 通过应用参数 {@code --example=<名字>} 传入（Spring Boot 会把它暴露成同名环境属性），
 * 这样 {@code spring-boot:run} fork 出的应用进程也能读到。
 *
 * 本类本身不绑定任何 profile，由各子类用 {@code @Profile} 控制何时生效。
 */
public abstract class BaseExampleRunner implements CommandLineRunner {

    @Autowired
    protected Environment env;

    /** 模块展示名，用于打印分段标题。 */
    protected abstract String moduleName();

    /** 已注册的例子：名字 -> 执行体。子类按需填充。 */
    protected abstract Map<String, Runnable> examples();

    @Override
    public void run(String... args) {
        Map<String, Runnable> ex = examples();
        System.out.println("========================================");
        System.out.println("  " + moduleName() + " examples  (registered: " + ex.size() + ")");
        System.out.println("========================================");

        String example = env.getProperty("example");
        if (example != null && !example.isBlank()) {
            String name = example.trim();
            Runnable r = ex.get(name);
            if (r == null) {
                System.out.println("! no example named '" + name + "'");
                System.out.println("  available: " + String.join(", ", ex.keySet()));
                return;
            }
            System.out.println("--- example: " + name + " ---");
            r.run();
            return;
        }

        if (ex.isEmpty()) {
            System.out.println("(scaffold: no examples registered yet)");
            return;
        }
        for (Map.Entry<String, Runnable> e : ex.entrySet()) {
            System.out.println("--- example: " + e.getKey() + " ---");
            e.getValue().run();
        }
    }
}
