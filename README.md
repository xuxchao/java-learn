# 启动命令
1. docker-compose up -d
2. mvn install -DskipTests
3. cd java-learn-app
4. mvn spring-boot:run

# 执行测试文件

1. mvn test
2. mvn test -Dtest=InfraConnectionTest // 只执行 InfraConnectionTest 这一个测试文件

# 01-scaffold-and-local-infra.md

这个任务跑完了解了 Spring boot 的入口文件(EcommerceApplication.java)，代码组织方式，扫描方式，以及 resources 的作用。

## 代码组织方式
1. Controller：配置 http 的路由，注解主要有：RequestMapping, GetMapping
2. Service：写业务逻辑 @Service
3. Mapper：代码有点少，还没分析出来
4. resources/application.yml：定义的参数可以直接被第三方的包(mysql, redis) 等使用
5. config 文件夹：通过 @RestControllerAdvice 注解来做了一些控制切面的方式

## 扫描方式
不跟 nestjs 一样有 moudle 来构建地图而是直接扫描 EcommerceApplication.java 下面的文件。包含 @RestController 注解的文件就会被扫描进去。看起来比 module 文件更方便一些