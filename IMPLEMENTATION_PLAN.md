# 小店模式改造——详细实现方案

> 仿 yshop-drink，去掉外卖/骑手，新增：商品估清、退款、加餐、收银台、桌台管理、扫码点餐、多人协同点餐
> 去掉：积分/优惠券/会员卡/小票打印、骑手派送功能

---

## 一、数据库变更

### 1.1 新增表：`dinner_table`（桌台管理表）

```sql
DROP TABLE IF EXISTS `dinner_table`;
CREATE TABLE `dinner_table` (
    `id` BIGINT NOT NULL COMMENT '桌台ID（雪花ID）',
    `table_no` VARCHAR(32) NOT NULL COMMENT '桌号，如 A01/B12',
    `capacity` INT DEFAULT 4 COMMENT '容纳人数',
    `area` VARCHAR(32) DEFAULT '大厅' COMMENT '区域：大厅/包间/露台',
    `status` INT DEFAULT 0 COMMENT '状态：0空闲 1使用中',
    `qr_code_path` VARCHAR(255) DEFAULT NULL COMMENT '二维码图片路径',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    `create_user` BIGINT DEFAULT NULL COMMENT '创建人',
    `update_user` BIGINT DEFAULT NULL COMMENT '修改人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_table_no` (`table_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='桌台管理表';
```

### 1.2 现有表字段语义变更（不改字段类型，只扩展枚举值）

| 表 | 字段 | 旧枚举 | 新枚举 | 说明 |
|----|------|--------|--------|------|
| `dish` | `status` | 0=停售, 1=启售 | **0=停售, 1=启售, 2=估清(售罄)** | 新增估清状态 |
| `orders` | `status` | 1=待付款, 2=待派送→制作中, 3=已派送→已完成, 4=已完成(废弃), 5=已取消 | **1=待付款, 2=制作中, 3=已完成, 5=已取消, 6=已退款** | 去掉骑手状态，新增退款 |

---

## 二、后端 Java 代码清单

### 2.1 新增文件（16个）

```
src/main/java/edu/ouc/
├── entity/
│   └── DinnerTable.java                          # 桌台实体
├── mapper/
│   └── DinnerTableMapper.java                    # 桌台 Mapper
├── service/
│   ├── IDinnerTableService.java                  # 桌台 Service 接口
│   └── impl/
│       └── DinnerTableServiceImpl.java           # 桌台 Service 实现
├── controller/
│   ├── DinnerTableController.java                # 桌台 CRUD + 二维码生成
│   ├── RefundController.java                     # 退款接口（或在 OrderController 里加方法）
│   └── CashierController.java                    # 收银台统计接口
├── dto/
│   └── CashierTodayDto.java                      # 收银台今日汇总 DTO
├── common/
│   └── QRCodeGenerator.java                      # ZXing 二维码生成工具
```

### 2.2 修改文件（6个）

| 文件 | 修改内容 |
|------|----------|
| `controller/OrderController.java` | 新增 `/refund` 退款接口、`/addItems` 加餐接口、`/{id}/void` 作废接口 |
| `service/IOrderService.java` | 接口新增 `refund()`, `addItems()`, `voidOrder()` 方法签名 |
| `service/impl/OrderServiceImpl.java` | 实现退款/加餐/作废逻辑 |
| `controller/DishController.java` | 新增 `PUT /soldOut/{id}` 估清、`PUT /resume/{id}` 恢复启售 |
| `service/impl/DishServiceImpl.java` | 新增单个菜品启售/估清方法 |
| `filter/LoginCheckFilter.java` | 白名单追加 `/dinnerTable/qrcode/**`（二维码图片无需登录） |

---

## 三、API 接口设计

### 3.1 商品估清

| 方法 | 路径 | 入参 | 返回 | 说明 |
|------|------|------|------|------|
| PUT | `/dish/soldOut/{id}` | 菜品ID | `R<String>` | 将该菜品状态改为2(估清)，同时从 KitchenDataContext 待加工队列移除该菜品的未加工任务 |
| PUT | `/dish/resume/{id}` | 菜品ID | `R<String>` | 恢复状态为1(启售) |

**规则**：
- 已估清的菜品，C端点餐页以灰色 + "已售罄"标签展示，不可加入购物车
- 已估清的菜品，后厨加工中的 Task 不受影响（不抢占中断）
- 套餐内包含估清菜品时，整个套餐不可选

### 3.2 退款

| 方法 | 路径 | 入参 | 返回 | 说明 |
|------|------|------|------|------|
| POST | `/order/refund` | `{orderId: Long, reason: String}` | `R<String>` | 仅 status=1(待付款) 或 status=2(制作中) 的订单可退款 |

**规则**（@Transactional）：
1. 校验订单状态：只允许 1 或 2
2. 更新 order.status → 6(已退款)
3. 遍历 order_detail 中所有 dishId，检查是否有估清的菜品 → 如有，恢复 status 2→1
4. 如果订单已在后厨加工中，从 KitchenDataContext 移除该订单所有待加工/加工中的 Task
5. 加工中的 Task 遵循 `kitchen.force-stop-on-cancel=false` 配置：默认不强制中断

### 3.3 加餐（追加菜品）

| 方法 | 路径 | 入参 | 返回 | 说明 |
|------|------|------|------|------|
| POST | `/order/addItems` | `{orderId: Long, items: [{dishId, setmealId, number, dishFlavor}]}` | `R<String>` | 仅 status=2(制作中) 的订单可加餐 |

**规则**（@Transactional）：
1. 校验订单 status=2
2. 为每个新增 item 生成雪花 ID，插入 order_detail
3. 更新 order.amount += 新增总金额
4. 将新增菜品生成 KitchenTask 加入待加工队列

### 3.4 收银台

| 方法 | 路径 | 入参 | 返回 | 说明 |
|------|------|------|------|------|
| GET | `/order/cashier/today` | 无 | `R<CashierTodayDto>` | 返回当日汇总 |

**CashierTodayDto 结构**：
```java
{
    totalCount: 120,       // 今日总订单数
    totalAmount: 5680.50,  // 今日总金额
    paidCount: 110,        // 已付款数（status!=1）
    unpaidCount: 10,       // 待付款数（status=1）
    refundCount: 3,        // 退款数（status=6）
    avgAmount: 47.34,      // 客单价
    hourStats: [{hour: "08", count: 5, amount: 230}, ...]  // 按小时统计
}
```

### 3.5 桌台管理

| 方法 | 路径 | 入参 | 返回 | 说明 |
|------|------|------|------|------|
| GET | `/dinnerTable/page` | page, pageSize, area | `R<Page<DinnerTable>>` | 分页查询桌台 |
| GET | `/dinnerTable/list` | area | `R<List<DinnerTable>>` | 列表查询（下拉框用） |
| POST | `/dinnerTable` | DinnerTable JSON | `R<String>` | 新增桌台 |
| PUT | `/dinnerTable` | DinnerTable JSON | `R<String>` | 编辑桌台 |
| DELETE | `/dinnerTable` | ids(数组) | `R<String>` | 删除桌台（仅空闲桌台可删） |
| GET | `/dinnerTable/qrcode/{id}` | 桌台ID | 图片流 | 生成并返回二维码图片 |

### 3.6 扫码点餐（C端）

| 方法 | 路径 | 说明 |
|------|------|------|
| 无需新增后端接口 | 前端 URL: `/front/index.html?tableId={id}` | 前端读取 tableId 参数 |

**规则**：
1. C端入口页检测 URL 参数 `tableId`
2. 如果有 tableId：自动标记为桌台点餐模式，在页面顶部显示桌号
3. 购物车数据绑定 tableId（存在 Redis key=`table_cart:{tableId}:{userId}` 中）
4. 下单时 orders 的 remark 字段存 JSON: `{"tableId": "xxx", "tableNo": "A01", "orderType": "dineIn"}`

### 3.7 多人协同点餐

| 实现方式 | Redis 缓存 |
|----------|------------|
| Key | `table_cart:{tableId}` (Hash 结构：field=userId, value=cart JSON) |
| 合并时机 | 任一用户点击"下单"时，合并所有用户的购物车为一个订单 |
| 清空时机 | 下单成功后清空整个 tableId 对应的 Hash |

**规则**：
1. 同一桌台的多个用户各自加购
2. 任一用户点击"去结算"时，展示本桌所有成员的购物车汇总
3. 确认下单后，生成一个共享订单，清空 Redis 中该桌台购物车
4. 结算界面显示：桌号 A01 · 共 X 件商品 · ￥XX.XX

---

## 四、前端页面清单

### 4.1 新增页面（3个）

| 文件路径 | 功能 |
|----------|------|
| `backend/page/table/list.html` | 桌台管理列表（CRUD + 二维码预览） |
| `backend/page/cashier/index.html` | 收银台大屏（实时统计 + 今日订单流水） |
| 心智模型设计文件 | 供桌台管理页引用 |

### 4.2 修改页面（6个）

| 文件路径 | 修改内容 |
|----------|----------|
| `backend/index.html` | 菜单新增"桌台管理"（id=7）+ "收银台"（id=8） |
| `backend/page/food/list.html` | 菜品列表新增"设为估清"/"恢复启售"按钮；status 列显示估清状态 |
| `backend/page/order/list.html` | 操作列：status=2 订单增加"退款"按钮 |
| `front/index.html` | 支持 `?tableId=xxx` 参数；顶部显示桌号；估清菜品灰色展示 |
| `front/page/order.html` | "再来一单"条件：status=3→status=3(已完成)；制作中订单显示"加餐"按钮 |
| `front/page/add-order.html` | 桌台模式跳过地址选择，改为显示桌号信息 |

### 4.3 修改功能按钮逻辑

| 订单状态 | 后台操作列按钮 | 说明 |
|----------|---------------|------|
| 1 待付款 | [查看] | |
| 2 制作中 | [查看] [完成] [退款] | 新增退款按钮 |
| 3 已完成 | [查看] | |
| 5 已取消 | [查看] | |
| 6 已退款 | [查看] | 新增状态 |

---

## 五、pom.xml 新增依赖

```xml
<!-- ZXing：生成桌台二维码 -->
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>core</artifactId>
    <version>3.5.3</version>
</dependency>
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>javase</artifactId>
    <version>3.5.3</version>
</dependency>
```

---

## 六、实施步骤（严格顺序）

### Step 1：数据库 + 实体层
- [ ] 执行 `dinner_table` 建表 SQL
- [ ] 创建 `DinnerTable.java` 实体（参考 Dish.java 风格）
- [ ] 创建 `DinnerTableMapper.java`

### Step 2：商品估清
- [ ] `DishController` 新增 `/soldOut/{id}` / `/resume/{id}`
- [ ] `DishServiceImpl` 新增单个状态切换方法
- [ ] `front/index.html`：估清菜品灰色 + "已售罄"
- [ ] `backend/page/food/list.html`：估清按钮

### Step 3：退款
- [ ] `OrderController` 新增 `/refund` 接口
- [ ] `OrderServiceImpl` 实现退款逻辑（status→6）
- [ ] `backend/page/order/list.html`：[退款] 按钮

### Step 4：加餐
- [ ] `OrderController` 新增 `/addItems` 接口
- [ ] `OrderServiceImpl` 实现加餐逻辑
- [ ] `front/page/order.html`：[加餐] 按钮

### Step 5：收银台
- [ ] `CashierTodayDto.java`
- [ ] `CashierController.java` + `/order/cashier/today`
- [ ] `backend/page/cashier/index.html`
- [ ] `backend/index.html` 菜单加"收银台"

### Step 6：桌台管理
- [ ] `IDinnerTableService` + `DinnerTableServiceImpl`（CRUD）
- [ ] `DinnerTableController`（含二维码生成）
- [ ] `QRCodeGenerator.java`
- [ ] `backend/page/table/list.html`
- [ ] `backend/index.html` 菜单加"桌台管理"

### Step 7：扫码点餐 + 多人协同
- [ ] 注入 `RedisTemplate` 到购物车/下单逻辑
- [ ] `front/index.html`：解析 `?tableId=` 参数
- [ ] `ShoppingCart`：绑定 tableId（remark 字段存或扩展 Redis key）
- [ ] `OrderServiceImpl.submit()`：合并桌台购物车 + 清空 Redis
- [ ] `front/page/add-order.html`：桌台模式跳过地址选择

---

## 七、checklist（核对 GATE RULE 锚点）

| 锚点 | 遵守情况 |
|------|----------|
| 包结构 edu.ouc | ✅ 新文件全部放在对应子包 |
| 三层架构 | ✅ Entity → Mapper → Service → ServiceImpl → Controller |
| R<T> 统一返回 | ✅ 所有 Controller 返回 R<T> |
| @Transactional 多表写 | ✅ 退款/加餐均标注 |
| 雪花 ID + @JsonSerialize | ✅ DinnerTable.id 使用 ToStringSerializer |
| 公共字段自动填充 | ✅ DinnerTable 的 createTime/updateTime/createUser/updateUser 用 @TableField |
| 分页查询三板斧 | ✅ 桌台分页：Page + LambdaQueryWrapper + this.page() |
| 登录过滤器放行 | ✅ /dinnerTable/qrcode/** 加入白名单 |
| 表结构一致 | ✅ dinner_table 字段类型与现有表一致 |
| 禁止新增表（放宽后） | ⚠️ 确认放宽，新增 dinner_table |