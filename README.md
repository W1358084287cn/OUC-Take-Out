# OUC 智慧食堂 —— 校园堂食扫码点餐系统

## 项目简介
面向高校食堂的**堂食扫码点餐系统**：学生到店落座 → 微信扫码桌台二维码 → 同桌多人共享购物车拼单 → 提交订单 → 后厨并行调度出餐 → 叫号取餐。覆盖 C 端点餐、B 端管理、后厨调度、退款售后、收银统计。

## 技术栈
| 层级 | 技术选型 |
|------|----------|
| 后端框架 | Spring Boot 2.7 + MyBatis-Plus 3.5 |
| 数据存储 | MySQL 8.0 + Druid 连接池 + Redis（缓存 / 共享购物车 / Session） |
| 即时通讯 | Netty 自建 WebSocket 服务（端口 9090），多窗口客服模式 |
| 后台前端 | Vue.js + Element UI + Axios |
| 移动端（C端） | Vant UI 移动端组件库，适配手机浏览器 |
| 工具库 | Lombok、Jackson、Hutool、ZXing（桌台二维码生成）、JavaMail（邮箱验证码登录） |
| 构建工具 | Maven |

## 核心功能模块

### C 端（学生手机扫码点餐）
- **桌台扫码**：每张餐桌绑定唯一二维码，扫码自动识别桌号进入点餐页
- **同桌共享购物车**：同一桌多人扫码后商品汇入共享购物车（Redis List 按桌号存储），同桌拼单一起下单
- **菜品浏览与搜索**：按分类筛选菜品/套餐、关键词搜索、口味选择
- **下单支付**：下单后订单进入后厨调度队列，支持订单进度实时查看
- **退款申请**：制作中/已完成订单可发起退款，实时查询退款进度

### B 端（食堂管理后台）
- **桌台管理**：A/B/C 三区（大厅/包间/露台）共 18 张桌台，空闲/使用中状态管理，自动生成二维码
- **菜品与套餐管理**：多规格口味、分类展示、启售停售、批量操作
- **订单管理**：实时接单、状态流转、时间范围筛选
- **退款审核**：商家审核退款申请（同意/拒绝/部分退款），语音通知提醒，红点未读提示
- **收银台**：今日营业额/退款/净收入实时统计 + 历史记录分页，纯内存计算无数据库依赖
- **后厨调度**：4 槽位并行加工模型，订单自动排队、状态追踪（已下单→加工中→已完成）
- **实时客服**：Netty WebSocket 自建聊天，多窗口客服面板、输入状态感知、未读消息红点

### 安全与体验
- **邮箱验证码登录**：替代短信，QQ 邮箱 SMTP 发送 6 位验证码，60 秒有效期
- **30 天记住我**：Cookie 签名 Token（HMAC-SHA256），HttpOnly 防 XSS，无需数据库改表
- **登录拦截**：Filter + AntPathMatcher 白名单，Session/Token 双通道认证
- **CVE 安全修复**：识别并修复 FastJSON、Logback、Tomcat、Netty、Spring Framework 等 5+ 高危漏洞

## 解决的实际问题
1. **食堂排队点餐效率低** → 扫码即点，同桌拼单，减少排队时间
2. **多人同桌各自下单麻烦** → 共享购物车，一人提交全桌订单
3. **后厨出餐混乱** → 槽位调度模型，订单排队、并行加工、状态可视化
4. **退款处理无流程** → 申请→审核→处理全链路，支持部分退款
5. **营收统计靠手工** → 收银台自动汇总日营业额/退款/净收入
6. **顾客与后厨沟通断层** → 自建 WebSocket 实时聊天，替代第三方 IM

## 项目规模
- 后端：14 个 Controller、12 个 Service、10 个 Mapper、15 个 Entity
- 前端：C 端 8 个页面 + B 端 12 个页面
- 子系统：退款系统、收银台、后厨调度、实时聊天 4 个独立子系统
- 代码量：Java 约 8000+ 行，前端约 6000+ 行

---

## 项目目录结构

```
OUC-Take-Out/
├── pom.xml                              # Maven 构建配置
├── src/main/resources/
│   ├── application.yml                  # 核心配置（数据库/Redis/端口/文件路径）
│   ├── logback-spring.xml               # 日志配置
│   ├── db/
│   │   └── init.sql                     # 数据库初始化 DDL（一键建库建表）
│   └── static/                          # 前端静态资源
│       ├── backend/                     # B端后台管理（Vue + Element UI）
│       │   ├── api/          → 后端接口 JS 封装（axios 请求层）
│       │   ├── page/         → 12 个管理页面（登录/菜品/套餐/订单/桌台/收银台等）
│       │   ├── plugins/      → 第三方库（Vue / Element UI / Axios）
│       │   └── js/           → 公共 JS（请求拦截/校验）
│       └── front/                       # C端移动端点餐（Vant UI）
│           ├── api/          → 接口封装（登录/下单/退款）
│           ├── page/         → 8 个页面（登录/首页/点餐/订单/支付等）
│           ├── js/           → 公共 JS + Vant 组件库
│           └── styles/       → 独立 CSS 样式
│
└── src/main/java/edu/ouc/
    ├── ReggieTakeOutApplication.java    # Spring Boot 启动类
    │
    ├── common/                          # 通用模块（横切关注点）
    │   ├── R.java                       → 统一响应格式（code+data+msg）
    │   ├── BaseContext.java             → ThreadLocal 用户上下文
    │   ├── CustomException.java         → 自定义业务异常
    │   ├── GlobalExceptionHandler.java  → 全局异常拦截（@ControllerAdvice）
    │   ├── MyMetaObjectHandler.java     → 公共字段自动填充（createTime/updateUser等）
    │   ├── CommonController.java        → 文件上传/下载
    │   ├── AnnouncementContext.java     → 公告内存上下文
    │   ├── BusinessHoursContext.java    → 营业时间内存上下文
    │   ├── CashRegisterContext.java     → 收银台内存上下文（日统计）
    │   ├── DinnerTableContext.java      → 桌台内存上下文（18 张桌台）
    │   ├── RefundContext.java           → 退款内存上下文（申请/审核）
    │   └── ViewedContext.java           → 已读状态内存上下文
    │
    ├── config/                          # 配置类
    │   ├── MPConfig.java                → MyBatis-Plus 分页拦截器
    │   ├── RedisConfig.java             → Redis 序列化 + Spring Cache 配置
    │   ├── CashRegisterConfig.java      → 收银台配置（刷新时间/保留天数）
    │   └── DbSchemaConfig.java          → 启动时数据库字段兼容
    │
    ├── controller/                      # 控制器层（REST API，12 个）
    │   ├── EmployeeController.java      → 员工登录/退出/CRUD
    │   ├── UserController.java          → 顾客邮箱验证码登录/退出
    │   ├── CategoryController.java      → 菜品/套餐分类管理
    │   ├── DishController.java          → 菜品 CRUD + 批量启售停售
    │   ├── SetmealController.java       → 套餐 CRUD + 批量操作
    │   ├── ShoppingCartController.java  → 购物车（加/减/查/清空）
    │   ├── OrderController.java         → 下单/退款申请/商家审核
    │   ├── OrderDetailController.java   → 订单明细查询
    │   ├── DinnerTableController.java   → 桌台管理 + 二维码生成
    │   ├── CashierController.java       → 收银台今日概览/历史分页
    │   ├── AnnouncementController.java  → 公告管理
    │   └── BusinessHoursController.java → 营业时间开关
    │
    ├── service/                         # 业务接口层（12 个）
    │   ├── impl/                        → 业务实现层
    │   │   ├── EmployeeServiceImpl      → 员工登录（MD5 验证）
    │   │   ├── UserServiceImpl          → 邮箱验证码校验 + 新用户注册
    │   │   ├── DishServiceImpl          → 菜品多表操作（@Transactional）
    │   │   ├── SetmealServiceImpl       → 套餐启售校验 + 多表操作
    │   │   ├── OrderServiceImpl         → 下单/退款/收银台联动
    │   │   ├── ShoppingCartServiceImpl  → 购物车增删改查
    │   │   ├── SharedCartServiceImpl    → 同桌共享购物车（Redis List）
    │   │   └── ...                      → 其余 CRUD 实现
    │   └── ISharedCartService           → 共享购物车接口（Redis 存储）
    │
    ├── mapper/                          # 数据访问层（MyBatis-Plus BaseMapper）
    │   ├── EmployeeMapper.java
    │   ├── UserMapper.java
    │   ├── CategoryMapper.java
    │   ├── DishMapper.java + DishFlavorMapper.java
    │   ├── SetmealMapper.java + SetmealDishMapper.java
    │   ├── OrderMapper.java + OrderDetailMapper.java
    │   └── ShoppingCartMapper.java
    │
    ├── entity/                          # 数据库实体（15 个）
    │   ├── Employee / User              → 员工 & 顾客
    │   ├── Category                     → 分类
    │   ├── Dish / DishFlavor            → 菜品 & 口味
    │   ├── Setmeal / SetmealDish        → 套餐 & 关联菜品
    │   ├── Orders / OrderDetail         → 订单 & 明细
    │   ├── ShoppingCart                 → 购物车
    │   ├── DinnerTable                  → 桌台
    │   ├── Announcement / BusinessHours → 公告/营业时间（纯内存）
    │   ├── RefundRequest / CashRegisterDay → 退款/收银台（纯内存）
    │
    ├── dto/                             # 数据传输对象（多表联查返回）
    │   ├── DishDto.java                 → 菜品 + 口味列表 + 分类名
    │   ├── SetmealDto.java              → 套餐 + 关联菜品列表 + 分类名
    │   ├── OrderDto.java                → 订单 + 订单明细列表
    │   └── CashierTodayDto.java         → 收银台今日概览
    │
    ├── filter/                          # 过滤器
    │   └── LoginCheckFilter.java        → 登录拦截（Session + Cookie Token 双通道）
    │
    ├── utils/                           # 工具类
    │   ├── MailUtils.java               → QQ 邮箱 SMTP 发送验证码
    │   ├── QRCodeGenerator.java         → ZXing 桌台二维码生成
    │   └── TokenUtils.java              → HMAC-SHA256 Cookie 签名 Token（记住我）
    │
    ├── scheduler/                       # 定时任务
    │   └── OrderCleanScheduler.java     → 每天凌晨 2:00 清理过期订单
    │
    └── kitchen/                         # 后厨调度子系统（独立模块）
        ├── config/
        │   └── KitchenConfig.java       → 槽位数/强制终止配置
        ├── common/
        │   └── KitchenDataContext.java   → 优先级队列 + 槽位信号量（纯内存）
        ├── model/
        │   ├── KitchenTask.java         → 加工任务模型
        │   ├── KitchenSlot.java         → 加工槽位模型
        │   ├── OrderKitchenStatus.java  → 订单后厨状态
        │   └── enums/                   → 阶段/状态枚举
        ├── controller/
        │   └── KitchenController.java   → 后厨看板 API
        └── service/
            ├── IKitchenOrderService     → 订单入队/取消接口
            ├── IKitchenQueryService     → 状态查询接口
            ├── IKitchenSchedulerService → 调度算法接口
            └── impl/                    → 实现类（优先级调度 + Semaphore 并发控制）
```

## 日志体系

### 日志框架
采用 **SLF4J + Logback**（Spring Boot 默认日志方案），所有模块统一使用 Lombok `@Slf4j` 注解输出日志。

### 日志配置 (`logback-spring.xml`)
| 配置项 | 说明 |
|--------|------|
| 输出目标 | 控制台（ConsoleAppender），开发阶段不落盘 |
| 编码格式 | UTF-8 |
| 日志格式 | `时间 + 高亮级别 + 线程名 + 类名 + 消息` |
| 业务日志级别 | `edu.ouc` 包 → **INFO**（记录关键业务节点） |
| SQL 日志级别 | `edu.ouc.mapper` 包 → **DEBUG**（MyBatis 执行的每条 SQL 及参数） |
| 框架日志级别 | Spring / Druid → **WARN**（屏蔽框架噪音，只输出警告以上） |

### 日志输出示例
```
2026-09-23 14:30:01.234 INFO  [http-nio-8080-exec-1] edu.ouc.service.impl.OrderServiceImpl - 用户 1582150528872198145 提交订单，订单号 20260923143001001
2026-09-23 14:30:01.456 DEBUG [http-nio-8080-exec-1] edu.ouc.mapper.OrderMapper - ==>  Preparing: INSERT INTO orders (id, number, status, user_id, ...) VALUES (?, ?, ?, ?, ...)
2026-09-23 14:30:01.678 INFO  [http-nio-8080-exec-1] edu.ouc.common.CashRegisterContext - 收银台入账: 订单 20260923143001001, 金额 ¥38.50
2026-09-23 14:30:02.001 INFO  [kitchen-scheduler-1] edu.ouc.kitchen.service.impl.KitchenSchedulerServiceImpl - 订单 20260923143001001 进入后厨加工队列，优先级=5，预计耗时 12min
```

### Controller 层日志规范
每个 Controller 使用 `@Slf4j`，关键操作记录日志：
- **新增** → `log.info("新增{}：{}", 实体名, 实体对象)`
- **删除** → `log.info("删除{}，ID：{}", 实体名, ids)`
- **异常** → `log.error("操作失败：{}", e.getMessage())`

### Service 层日志规范
- **下单**：记录用户 ID + 订单号 + 金额
- **退款**：记录订单号 + 退款金额 + 处理结果
- **后厨调度**：记录订单入队/开始加工/加工完成 + 耗时
- **收银台**：记录每次入账/退款金额变化
- **定时任务**：记录清理开始/结束 + 清理数量

### 中文日志
所有日志、错误提示、异常信息统一使用**简体中文**，便于运维排查。例如：
```
log.warn("桌台{}正在使用中，无法删除", tableNo);
throw new CustomException("当前分类下关联了菜品，无法删除");
```

---

## 本地环境搭建教程（别人电脑如何跑起来）

### 前置环境要求
| 软件 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 1.8+ | 推荐 Amazon Corretto 8 或 Oracle JDK 8 |
| Maven | 3.6+ | 用于依赖管理和项目构建 |
| MySQL | 8.0+ | 数据库服务，需本地运行 |
| Redis | 6.0+ | 缓存 / Session / 共享购物车，需本地运行（Windows 用 Memurai 或 WSL） |

### 第一步：克隆项目
```bash
git clone https://github.com/W1358084287cn/OUC-Take-Out.git
cd OUC-Take-Out
```

### 第二步：创建数据库
登录 MySQL，执行以下 SQL 创建数据库：
```sql
CREATE DATABASE IF NOT EXISTS reggie
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;
```

### 第三步：建表（复制执行即可）
```sql
USE reggie;

-- 1. 员工表
CREATE TABLE employee (
    id BIGINT NOT NULL COMMENT '主键',
    name VARCHAR(32) NOT NULL COMMENT '姓名',
    username VARCHAR(32) NOT NULL COMMENT '用户名',
    password VARCHAR(64) NOT NULL COMMENT '密码',
    phone VARCHAR(11) NOT NULL COMMENT '手机号',
    sex VARCHAR(2) NOT NULL COMMENT '性别',
    id_number VARCHAR(18) NOT NULL COMMENT '身份证号',
    status INT DEFAULT 1 COMMENT '状态 0禁用 1启用',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_user BIGINT COMMENT '创建人',
    update_user BIGINT COMMENT '修改人',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工表';

-- 2. 用户表（C端顾客）
CREATE TABLE user (
    id BIGINT NOT NULL COMMENT '主键',
    name VARCHAR(50) COMMENT '用户名',
    email VARCHAR(100) COMMENT '邮箱',
    sex VARCHAR(2) COMMENT '性别',
    id_number VARCHAR(18) COMMENT '身份证号',
    avatar VARCHAR(500) COMMENT '头像',
    status INT DEFAULT 1 COMMENT '状态 0禁用 1启用',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 3. 分类表（菜品分类/套餐分类）
CREATE TABLE category (
    id BIGINT NOT NULL COMMENT '主键',
    type INT COMMENT '类型 1菜品分类 2套餐分类',
    name VARCHAR(64) NOT NULL COMMENT '分类名称',
    sort INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_user BIGINT COMMENT '创建人',
    update_user BIGINT COMMENT '修改人',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品及套餐分类';

-- 4. 菜品表
CREATE TABLE dish (
    id BIGINT NOT NULL COMMENT '主键',
    name VARCHAR(64) NOT NULL COMMENT '菜品名称',
    category_id BIGINT NOT NULL COMMENT '分类ID',
    price DECIMAL(10,2) DEFAULT 0 COMMENT '价格',
    code VARCHAR(64) COMMENT '商品码',
    image VARCHAR(200) COMMENT '图片',
    description VARCHAR(400) COMMENT '描述',
    status INT DEFAULT 1 COMMENT '状态 0停售 1启售',
    sort INT DEFAULT 0 COMMENT '排序',
    is_deleted INT DEFAULT 0 COMMENT '是否删除 0否 1是',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_user BIGINT COMMENT '创建人',
    update_user BIGINT COMMENT '修改人',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品表';

-- 5. 菜品口味表
CREATE TABLE dish_flavor (
    id BIGINT NOT NULL COMMENT '主键',
    dish_id BIGINT NOT NULL COMMENT '菜品ID',
    name VARCHAR(64) COMMENT '口味名称',
    value VARCHAR(500) COMMENT '口味数据list',
    is_deleted INT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_user BIGINT COMMENT '创建人',
    update_user BIGINT COMMENT '修改人',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品口味关系表';

-- 6. 套餐表
CREATE TABLE setmeal (
    id BIGINT NOT NULL COMMENT '主键',
    category_id BIGINT NOT NULL COMMENT '分类ID',
    name VARCHAR(64) NOT NULL COMMENT '套餐名称',
    price DECIMAL(10,2) DEFAULT 0 COMMENT '价格',
    status INT DEFAULT 1 COMMENT '状态 0停售 1启售',
    code VARCHAR(64) COMMENT '套餐码',
    description VARCHAR(400) COMMENT '描述',
    image VARCHAR(200) COMMENT '图片',
    is_deleted INT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_user BIGINT COMMENT '创建人',
    update_user BIGINT COMMENT '修改人',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐表';

-- 7. 套餐菜品关联表
CREATE TABLE setmeal_dish (
    id BIGINT NOT NULL COMMENT '主键',
    setmeal_id BIGINT NOT NULL COMMENT '套餐ID',
    dish_id BIGINT NOT NULL COMMENT '菜品ID',
    name VARCHAR(64) COMMENT '菜品名称（冗余）',
    price DECIMAL(10,2) COMMENT '菜品原价',
    copies INT DEFAULT 1 COMMENT '份数',
    sort INT DEFAULT 0 COMMENT '排序',
    is_deleted INT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_user BIGINT COMMENT '创建人',
    update_user BIGINT COMMENT '修改人',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐菜品关系表';

-- 8. 购物车表
CREATE TABLE shopping_cart (
    id BIGINT NOT NULL COMMENT '主键',
    name VARCHAR(64) COMMENT '商品名称',
    image VARCHAR(200) COMMENT '图片',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    dish_id BIGINT COMMENT '菜品ID',
    setmeal_id BIGINT COMMENT '套餐ID',
    dish_flavor VARCHAR(100) COMMENT '口味',
    number INT DEFAULT 1 COMMENT '数量',
    amount DECIMAL(10,2) DEFAULT 0 COMMENT '金额',
    create_time DATETIME COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

-- 9. 订单表
CREATE TABLE orders (
    id BIGINT NOT NULL COMMENT '主键',
    number VARCHAR(50) COMMENT '订单号',
    status INT DEFAULT 1 COMMENT '状态 1待付款 2制作中 3已完成 5已取消 6已退款 7退款申请中 8部分退款',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    address_book_id BIGINT DEFAULT NULL COMMENT '地址簿ID',
    order_time DATETIME COMMENT '下单时间',
    checkout_time DATETIME COMMENT '支付时间',
    pay_method INT DEFAULT 1 COMMENT '支付方式',
    amount DECIMAL(10,2) DEFAULT 0 COMMENT '实收金额',
    remark VARCHAR(200) COMMENT '备注',
    phone VARCHAR(50) COMMENT '电话',
    address VARCHAR(200) COMMENT '地址',
    user_name VARCHAR(50) COMMENT '用户名',
    consignee VARCHAR(50) COMMENT '收货人',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 10. 订单明细表
CREATE TABLE order_detail (
    id BIGINT NOT NULL COMMENT '主键',
    name VARCHAR(64) COMMENT '商品名称',
    image VARCHAR(200) COMMENT '图片',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    dish_id BIGINT COMMENT '菜品ID',
    setmeal_id BIGINT COMMENT '套餐ID',
    dish_flavor VARCHAR(100) COMMENT '口味',
    number INT DEFAULT 1 COMMENT '数量',
    amount DECIMAL(10,2) DEFAULT 0 COMMENT '金额',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';
```

### 第四步：修改配置文件
打开 `src/main/resources/application.yml`，改 3 个地方：

```yaml
# 1. 数据库连接 — 改成你的 MySQL 账号密码
spring:
  datasource:
    druid:
      username: root        # ← 改成你的 MySQL 用户名
      password: 123456      # ← 改成你的 MySQL 密码

# 2. 图片上传路径 — 改成你电脑上的目录（需手动创建 D:\img\ 文件夹）
reggie:
  path: D:\img\             # ← Windows 不改也行（先创建 D:\img\ 目录）
                             # ← Mac/Linux 改成如 /Users/xxx/img/

# 3. Cookie 签名密钥 — 改成你自己的随机字符串（不少于32字符）
  remember-key: 改成你自己的随机字符串不少于32字符
```

### 第五步：配置 QQ 邮箱 SMTP（邮箱验证码登录用）
1. 登录 QQ 邮箱 → 设置 → 账户 → 开启 **POP3/SMTP 服务**
2. 会生成一个 **授权码**（不是 QQ 密码），复制保存
3. 打开 `src/main/java/edu/ouc/utils/MailUtils.java`，修改：

```java
// 发件人邮箱
private static final String FROM = "你的QQ号@qq.com";        // ← 改这里
// SMTP 授权码
private static final String PASSWORD = "你的QQ邮箱授权码";    // ← 改这里
```

### 第六步：启动 Redis
- **Windows**：下载 [Memurai](https://www.memurai.com/) 或 Redis for Windows，默认端口 6379
- **Mac**：`brew install redis && brew services start redis`
- **Linux**：`sudo apt install redis-server && sudo systemctl start redis`

### 第七步：启动项目
```bash
# 在项目根目录执行
mvn clean package -DskipTests
mvn spring-boot:run

# 或者用 IDE（IDEA）直接运行 ReggieTakeOutApplication.java
```

### 第八步：访问系统

| 端 | 地址 | 说明 |
|----|------|------|
| 后台管理 | http://localhost:8080/backend/page/login/login.html | 默认账号 admin / 密码 123456 |
| 移动端点餐 | http://localhost:8080/front/page/login.html | 邮箱验证码登录 |

### 常见问题
| 问题 | 解决 |
|------|------|
| 数据库连接失败 | 确认 MySQL 已启动，reggie 库已创建，yml 中账号密码正确 |
| Redis 连接失败 | 确认 Redis 已启动在 6379 端口 |
| 图片上传 404 | 确认 `D:\img\` 目录已手动创建 |
| 验证码收不到 | 检查 QQ 邮箱 SMTP 是否开启、授权码是否正确 |
| WebSocket 聊天不通 | Netty 聊天端口 9090，确认未被防火墙拦截 |
