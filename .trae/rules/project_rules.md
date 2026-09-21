﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿# 项目规则索引（README.md 全量抽取）

## GATE RULE（最高优先级总规则）
**【最高优先级 · 守门总规则】**
1. 所有代码改造、配置修改、新增功能，**必须先匹配索引锚点**，没有匹配锚点的需求，直接拒绝执行，禁止自行新增README文档以外的业务逻辑、接口、表字段。
2. 任何修改前，先核对对应锚点的子规则；代码实现必须严格遵守子规则，**不允许简化、跳过子规则要求**。
3. 涉及数据库修改：只能使用我提供的本地MySQL连接信息，建表、改字段必须和原项目文档表结构保持一致，**禁止私自新增数据表、私自修改字段类型**。
4. 改动代码之后，输出检查清单：列出本次改动用到哪个锚点、核对对应的子规则是否全部遵守。
5. 如果需求和project_rules.md内规则冲突，以本规则文件为准，不允许擅自修改规则文件，如需改规则，必须先询问我。
6. 不要主动优化重构现有代码，除非我明确提出重构指令；只做我指定的任务。
7，一切按照高聚合低耦合实现，使用容器控制反转来解耦组件之间的依赖关系。
---

## 索引目录

### 项目搭建与目录结构
- 包结构：edu.ouc 根包，分 controller / service / service.impl / mapper / entity / dto / common / config / filter / utils
- pom.xml 依赖：Spring Boot + MyBatis-Plus + Druid + Lombok + Jackson
- 启动类：ReggieTakeOutApplication.java，@SpringBootApplication + @ServletComponentScan

### 数据库配置
- 数据库 reggie：utf8 字符集，单库 11 张表
- 11 表清单：address_book / category / dish / dish_flavor / employee / order_detail / orders / setmeal / setmeal_dish / shopping_cart / user
- application.yml 核心配置：spring.datasource Druid 连接池（localhost:3306/reggie） + mybatis-plus.configuration 驼峰映射/日志/雪花 ID + server.port=8080
- 文件存储路径配置：reggie.path: D:\img\
- MP 分页拦截器 MPConfig：PaginationInnerInterceptor 注册为 Spring Bean，@Configuration

### 接口改造——统一响应格式
- R<T> 通用返回类：code=1 成功 / code=0 失败，含 data + msg + map，静态方法 R.success(T) / R.error(String)
- 所有 Controller 方法强制返回 R<T>：查询返回 R.success(data)，写入返回 R.success("成功") / R.error("失败")
- Controller 注解规范：@RestController + @RequestMapping("/xxx") + @Slf4j

### 登录拦截
- 后台登录流程：POST /employee/login，密码 MD5 加密（DigestUtils.md5DigestAsHex）比对，成功写 session.setAttribute("employee", id)，返回 R.success(employee)
- 后台退出登录：POST /employee/logout，session.removeAttribute("employee")，返回 R.success("退出成功")
- 登录过滤器 LoginCheckFilter：@WebFilter("/*") 实现 Filter，AntPathMatcher 匹配路径白名单，未登录返回 R.error("NOTLOGIN") 并 response.getWriter().write(JSON.toJSONString(...))
- 前端响应拦截：request.js 中 res.code === 0 && res.msg === 'NOTLOGIN' 时 window.top.location.href = '/backend/page/login/login.html'
- 邮箱登录时代拦截器额外放行：白名单追加 /user/login、/user/sendMsg；额外判断 session.getAttribute("user") 放行 C 端用户

### ThreadLocal 用户上下文
- BaseContext 工具类：ThreadLocal<Long> 封装 setCurrentUserId(Long) / getCurrentUserId()
- 写入时机：LoginCheckFilter.doFilter() 中登录校验通过后调用 BaseContext.setCurrentUserId(id)
- 读取时机：MyMetaObjectHandler 自动填充 createUser/updateUser + 各业务层通过 BaseContext.getCurrentUserId() 获取当前用户 ID

### 公共字段自动填充
- @TableField 注解策略：FieldFill.INSERT 标注 createTime/createUser，FieldFill.INSERT_UPDATE 标注 updateTime/updateUser
- 元数据对象处理器 MyMetaObjectHandler：@Component 实现 MetaObjectHandler，insertFill() 赋 createTime=now() + createUser=BaseContext.getCurrentUserId()，updateFill() 赋 updateTime=now() + updateUser=BaseContext.getCurrentUserId()
- Token 兼容保护：metaObject.hasSetter("字段名") 判断字段是否存在再赋值，适配不同实体
- 订单自动填充扩展：orderTime / checkoutTime 字段也需要在 insertFill() 中 hasSetter 判断后赋 LocalDateTime.now()

### 全局异常处理
- GlobalExceptionHandler：@ControllerAdvice + @ResponseBody，拦截所有 Controller
- SQLIntegrityConstraintViolationException：判断 contains("Duplicate entry") -> 提取重复字段返回 R.error("xxx已存在")
- CustomException：自定义业务异常，返回 R.error(message)

### 自定义业务异常
- CustomException：extends RuntimeException，构造 super(message)
- 抛异常场景：删除有关联数据时（"正在售卖"/"已绑定套餐"）、购物车为空时、地址为空时、验证码错误时、邮箱/验证码为空时

### ID 精度丢失修复
- 雪花 ID Long -> String：所有实体中 Long 类型 ID 标注 @JsonSerialize(using = ToStringSerializer.class)
- 适用字段：id / categoryId / dishId / setmealId / userId / addressBookId / orderId 等所有雪花 Long ID

### 三层架构标准范式
- Entity：@Data + @TableField(fill=...) 标注公共字段 + @JsonSerialize 处理 Long->String + implements Serializable + serialVersionUID
- Mapper：@Mapper + extends BaseMapper<Entity>，无方法体
- Service 接口：extends IService<Entity>
- ServiceImpl：@Service + extends ServiceImpl<Mapper, Entity> + implements Service接口
- Controller：@RestController + @RequestMapping("/xxx") + @Slf4j，方法返回 R<T> 或 R<String>

### 分页查询模式
- MP 分页拦截器注册：MPConfig 中 PaginationInnerInterceptor Bean
- 分页查询三板斧：1.Page<T> page = new Page<>(页码, 每页大小) -> 2.LambdaQueryWrapper<T> 构建条件 -> 3.this.page(page, wrapper)
- 分页 + 条件查询合并：同一个方法接收 page/pageSize + 搜索关键字，lqw.like(Strings.isNotEmpty(name), ...) 动态 SQL
- Page<DTO> 转换模式：BeanUtils.copyProperties(entityPage, dtoPage, "records") + entityPage.getRecords().stream().map(...) 转 DTO

### DTO 对象封装模式
- DishDto：extends Dish，扩展 List<DishFlavor> flavors + String categoryName + Integer copies
- SetmealDto：extends Setmeal，扩展 List<SetmealDish> setmealDishes + String categoryName
- OrderDto：extends Orders，扩展 List<OrderDetail> orderDetails
- DTO 转换范式：BeanUtils.copyProperties(source, target) + Stream map() + collect(Collectors.toList())
- 多表联查心得：根据 SELECT 字段不同创建不同 DTO；多表写操作必须 @Transactional

### 员工管理
- 新增员工：POST /employee，@RequestBody Employee，密码 MD5 加密后 save()
- 员工分页查询：GET /employee/page，page/pageSize/name 参数，lqw.like(name!=null, Employee::getName, name)
- 启用/禁用员工：PUT /employee，@RequestBody Employee（只传 id + status），this.updateById(employee)
- 编辑员工：GET /employee/{id} 回显 -> PUT /employee 更新，@RequestBody Employee

### 分类管理
- 新增分类：POST /category，@RequestBody Category，save()
- 分类分页查询：GET /category/page，page/pageSize，lqw.orderByAsc(Category::getSort)
- 删除分类：DELETE /category，参数 Long ids，删除前查 dish 表和 setmeal 表是否有该分类下的数据，有则抛 CustomException("当前分类下关联了菜品/套餐，无法删除")
- 修改分类：PUT /category，@RequestBody Category，updateById()

### 文件上传下载
- 文件上传：POST /common/upload，入参 MultipartFile file（参数名必须 file），UUID 重命名防覆盖，file.transferTo(new File(basePath + fileName))
- 文件下载/回显：GET /common/download，入参 String name（参数名必须 name），FileInputStream 读 + ServletOutputStream 写回浏览器
- @Value 注入路径：@Value("${reggie.path}") private String basePath
- 前端表单要求：method="post" + enctype="multipart/form-data" + type="file"

### 菜品管理
- 新增菜品：POST /dish，@RequestBody DishDto，涉及 dish + dish_flavor 两张表，@Transactional，先 save(dishDto) -> 获取 dishId -> Stream 给每个 flavor 设 dishId -> saveBatch(flavors)
- 菜品分页查询：GET /dish/page，page/pageSize/name，Page<DishDto> 返回，Stream map() 中按 categoryId 查 categoryService.getById() 获取分类名
- 修改菜品：GET /dish/{id} 回显（DishDto） -> PUT /dish，@RequestBody DishDto，更新 dish 基本信息 + 先清 dish_flavor 该 dishId 记录再批量插新口味
- 菜品启售/停售：POST /dish/status/{status}，@RequestParam List<Long> ids，Stream peek(dish -> dish.setStatus(status)) + this.updateBatchById()
- 删除菜品：DELETE /dish，@RequestParam List<Long> ids，三关校验：1.查在售（status=1）抛异常->2.查是否关联在售套餐（setmealDish -> setmeals 在售）抛异常->3.removeByIds(ids) + 删 dish_flavor

### 依赖循环解决方案
- 场景：CategoryServiceImpl <-> DishServiceImpl 互相依赖
- 治标方案：互相依赖的 @Autowired 字段均加 @Lazy 注解
- 治本方案：重新设计依赖，避免循环

### 套餐管理
- 新增套餐：POST /setmeal，@RequestBody SetmealDto，涉及 setmeal + setmeal_dish，@Transactional，save(setmealDto) -> 获取 setmealId -> Stream peek 设 setmealId -> saveBatch(setmealDishes)
- 菜品列表展示（供套餐选择）：GET /dish/list，入参 Dish dish（可仅带 categoryId），lqw.eq(Dish::getStatus, 1) 只查启售菜品
- 套餐分页查询：GET /setmeal/page，page/pageSize/name，Page<SetmealDto> 返回，Stream map 中查 categoryName
- 修改套餐：GET /setmeal/{id} 回显 SetmealDto -> PUT /setmeal，@RequestBody SetmealDto，@Transactional，先 updateById(setmealDto) -> 清 setmeal_dish 该 setmealId 记录 -> 重新批量插入
- 套餐启售/停售：POST /setmeal/status/{status}，@RequestParam List<Long> ids；启售前校验：查关联菜品是否在售（status=0 抛异常）+ 查关联菜品是否被删除（dishService.listByIds() 数量不一致抛异常）；停售时不校验
- 删除套餐：DELETE /setmeal，@RequestParam List<Long> ids，@Transactional，先查停售（Stream anyMatch status==1 抛异常）-> 先删 setmeal_dish -> 再删 setmeal
- 前端 Bug 修复：add.html#L335：{{ item.dishName }} -> {{ item.name }}

### 邮箱验证码登录
- 替代短信：QQ 邮箱 + SMTP/POP3，MailUtils 工具类封装
- MailUtils.sendMail(email, code)：Session.getInstance(props, authenticator) + MimeMessage + Transport.send()
- MailUtils.getCode()：打乱 36 字符数组后 Collections.shuffle() -> sb.substring(10, 16) 取 6 位验证码
- 发送验证码：POST /user/sendMsg，@RequestBody User（含 email），生成 code -> 发邮件 -> session.setAttribute(email, code)；验证码 60 秒有效期：新线程 sleep(60000L) 后覆盖 session 中 code
- 邮箱登录：POST /user/login，@RequestBody Map<String, String>（email + code），校验非空 -> session.getAttribute(email) 比对验证码 -> 新用户 save() -> session.setAttribute("user", user.getId())
- C 端退出登录：POST /user/loginout，session.removeAttribute("user") + session.removeAttribute(email)
- user 表改造：phone 字段重命名为 email

### 前端改造——邮箱登录
- login.js：sendMsgApi(data) 发 POST 到 /user/sendMsg，data 是 {email: ...}
- login.html 修改：placeholder 改 "电子邮箱"，正则改邮箱正则，中文 "手机号"->"电子邮箱"
- 验证码倒计时：Vue 数据模型 show/count/timer，60 秒倒计时，<span v-show="!show"> 显示剩余秒数，浅灰色 CSS
- 前端登录逻辑优化：btnLogin() 校验 email+coded 非空 + localStorage.setItem('userInfo', ...) + sessionStorage.setItem('userEmail', ...)
- 请求超时：request.js timeout: 1000000

### 用户地址簿
- 数据表：address_book，含 userId/consignee/phone/sex/provinceCode~districtName/detail/label/isDefault
- 新增地址：POST /addressBook，@RequestBody AddressBook，save()
- 地址列表：GET /addressBook/list，按 userId=BaseContext.getCurrentUserId() 查当前用户所有地址，orderByDesc(updateTime)
- 设置默认地址：PUT /addressBook/default，@RequestBody AddressBook（含 id），推荐方案：LambdaUpdateWrapper 先设当前用户所有 isDefault=0 -> 再设目标 isDefault=1 -> updateById()
- 编辑地址：GET /addressBook/{id} 回显 -> PUT /addressBook 更新
- 删除地址：DELETE /addressBook，?ids=xxx
- 前端 Bug：address-edit.html#L111：this.activeIndex = this.labelList.indexOf(this.form.label) 修复回显

### 菜品展示（C端）
- 分类列表：GET /category/list，lqw.eq(Category::getType, 1) 查菜品分类，orderByAsc(sort) -> orderByDesc(updateTime)，返回 List<Category>
- 菜品/套餐按分类展示：GET /dish/list（按 categoryId 过滤，lqw.eq(Dish::getStatus, 1) 只查启售）+ GET /setmeal/list（按 categoryId 过滤，lqw.eq(Setmeal::getStatus, 1)）
- 口味查询：菜品展示时需根据 dishId 查 dish_flavor 表展示口味

### C端菜品搜索
- 搜索入口：点餐页 index.html 顶部新增 van-search 搜索框，支持输入关键词搜索菜品和套餐
- 后端搜索接口：复用 GET /dish/list 和 GET /setmeal/list，新增可选参数 name（String），传 name 时按名称模糊匹配（lqw.like(Strings.isNotEmpty(name), Dish::getName, name)），不传 name 时保持原有按分类过滤行为
- 套餐搜索：GET /setmeal/list 新增 name 参数，Service 层手写 lqw.like(Strings.isNotEmpty(name), Setmeal::getName, name)，只查启售套餐（status=1）
- 搜索结果合并：前端同时调用菜品和套餐搜索接口，Promise.all 并行请求，合并结果统一渲染，套餐条目标记 itemType='setmeal' 用于区分点击行为（菜品走口味弹窗/套餐走套餐详情弹窗）
- 搜索清空恢复：点击搜索框清除按钮或清空关键词后，恢复分类浏览模式，重新加载当前分类数据
- 搜索模式标记：Vue data 中 isSearching 布尔值标记当前是否处于搜索模式，用于控制 UI 渲染逻辑
- 前端样式：搜索框宽度与页面主体一致（345rem），圆角搜索框（shape="round"），白色背景，位于分类列表和菜品列表上方

### 购物车
- 数据模型：shopping_cart 表，userId/dishId/setmealId/name/image/dishFlavor/number/amount
- 添加到购物车：POST /shoppingCart/add，@RequestBody ShoppingCart，先查当前用户是否已有该商品（userId+dishId/setmealId+dishFlavor）：无则 number=1 新增；有则 number+1 更新
- 查看购物车：GET /shoppingCart/list，按 userId=BaseContext.getCurrentUserId() 查询，orderByAsc(createTime)
- 清空购物车：DELETE /shoppingCart/clean，按 userId 删除当前用户所有记录
- 商品减一/删除：POST /shoppingCart/sub，@RequestBody ShoppingCart（含 dishId 或 setmealId），按 userId 查询：number>1 减一 -> number=1 直接 remove(lqw) 删除

### 下单
- 数据模型：orders 表 + order_detail 表，雪花 ID 生成订单号
- 获取默认地址：GET /addressBook/default
- 提交订单：POST /order/submit，@RequestBody Orders（含 remark/payMethod/addressBookId），@Transactional：1.获取 userId 2.查购物车（空抛异常）3.查地址簿（空抛异常）4.查用户 5.IdWorker.getId() 生成 orderId，AtomicInteger 累加总金额 6.Stream map 构建 List<OrderDetail> 7.设 orders 各属性（number/status=2/amount/phone/consignee/address）8.save + saveBatch 9.清空购物车
- C 端订单分页：GET /order/userPage，page/pageSize，按 userId + orderByDesc(orderTime)，Page<OrderDto> 含订单明细
- 后台订单分页查询：GET /order/page，page/pageSize/number/beginTime/endTime，lqw.like(number, ...) + lqw.gt(beginTime, ...) + lqw.lt(endTime, ...) 动态 SQL
- 订单状态修改：PUT /order，@RequestBody Orders（含 number + status），this.updateById(order)

### 多表操作铁律
- 写操作（INSERT/UPDATE/DELETE）涉及多表 -> 必须加 @Transactional
- 多表联查返回 -> 必须使用 DTO 封装
- 先删子表（外键表）-> 再删主表（套餐删除：先删 setmeal_dish -> 再删 setmeal；菜品删除：先删 dish_flavor -> 再删 dish）
- 修改多表 -> 先清后加（修改套餐时：先删 setmeal_dish 旧记录 -> 后批量 saveBatch 新记录）

### Stream 流惯用范式
- 批量设值：list.stream().peek(item -> item.setXxx(value)).collect(Collectors.toList())
- 实体 -> DTO 转换：records.stream().map(entity -> { Dto dto = new Dto(); BeanUtils.copyProperties(entity, dto); return dto; }).collect(Collectors.toList())
- 提取 ID 集合去重：list.stream().map(Entity::getId).collect(Collectors.toSet())
- 提取名称集合：list.stream().map(Entity::getName).collect(Collectors.toList())
- 计算总和：AtomicInteger amount = new AtomicInteger(0); + Stream 中 amount.addAndGet(...)

### 文件路径规约
- 后端 Java 路径：src/main/java/edu/ouc/ + {controller|service|service/impl|mapper|entity|dto|common|config|filter|utils}
- 前端静态资源：src/main/resources/static/backend/（后台） / src/main/resources/static/front/（用户端）
- 静态资源子目录：api/、js/、page/、styles/、plugins/、images/、fonts/
- 配置文件：src/main/resources/application.yml

### 退款系统——客户发起退款申请
- 订单状态码扩展 / RefundRequest 内存数据模型 / RefundContext 内存上下文 / 客户发起退款 POST /order/requestRefund / 商家处理 POST /order/handleRefund / 部分退款

### 退款系统——前端改造
- C端 order.html 退款按钮 / 退款申请弹窗 / 商家后台退款审核 / 退款处理弹窗

### 退款语音通知
- 音频文件：static/backend/audio/您有新的退款，_br....mp3（预加载，新退款时播放）
- 商家后台轮询：order/list.html 每 30 秒调用 GET /order/refundUnreadCount 检查未读数
- 未读红点：el-badge 脉冲动画，打开退款审核弹窗后调用 POST /order/markRefundViewed 清除
- 已读数存储：RefundContext.viewedRefundIds（ConcurrentHashMap.newKeySet()）纯内存
- 未读数 API：GET /order/refundUnreadCount → RefundContext.getUnreadCount()
- 已读标记 API：POST /order/markRefundViewed → RefundContext.markAllViewed()

### 收银台系统
- 内存数据模型：CashRegisterDay（date / totalAmount / refundAmount / netAmount / orderCount）纯内存存储，不建表
- 内存上下文：CashRegisterContext（@Component 单例），ConcurrentSkipListMap<LocalDate, CashRegisterDay> history
- 配置类：CashRegisterConfig（@ConfigurationProperties prefix=cash-register），refreshHour 刷新小时 / retentionDays 保留天数（1-3650，最高10年）
- 刷新逻辑：cashRegisterContext.addIncome(amount) 和 addRefund(amount) 内部调用 checkAndRefresh()，每日凌晨 resetHour 点后首次访问时自动将今日记录存入 history，新建空白今日记录
- 定时兜底：@Scheduled(fixedDelay=60000) 每分钟检查跨天刷新 + @Scheduled(cron="0 5 0 * * ?") 每天凌晨清理过期历史
- 订单联动：OrderServiceImpl.update() 中 status==3 时调用 cashRegisterContext.addIncome()；handleRefund() 中同意(status=1)/部分退款(status=3)时调用 cashRegisterContext.addRefund()
- 收银台 API：GET /cashier/today（今日概览）/ GET /cashier/history?page&pageSize（历史分页）/ GET /cashier/config（查看配置）/ PUT /cashier/config（更新配置，校验 resetHour 0-23，retentionDays 1-3650）
- 收银台页面：frontend/backend/page/cashier/index.html，含今日统计卡片 / 历史记录表格（分页）/ 设置弹窗（刷新时间下拉 + 保留天数数字输入，最高3650天）
- 前端 API：backend/api/cashRegister.js，含 getCashierTodayApi / getCashierHistoryApi / getCashierConfigApi / updateCashierConfigApi
- 配置项 application.yml：cash-register.reset-hour=4 / cash-register.retention-days=90

### C端30天保持登录（记住我）
- 技术方案：Cookie 签名 Token（自包含验证），零数据库改动，无需写表/改表/增字段
- Token 格式：Base64( userIdInHex + ":" + expiryTimestamp + ":" + HMAC-SHA256(userIdInHex:expiry, secret) )，userId 按十六进制编码避免 Base64 中出现特殊字符
- 签名密钥：application.yml 中配置 reggie.remember-key，不少于 32 字符随机字符串
- Token 生成工具类：TokenUtils.java（包路径 edu.ouc.utils），静态方法 generateRememberToken(Long userId, String secret, int days) / validateRememberToken(String token, String secret)
- Cookie 设置：登录时若前端传 rememberMe=true，生成 Token 写入 Cookie（name=remember, value=token, maxAge=2592000 即 30 天, httpOnly=true, path=/）
- Cookie 清除：退出登录时删除 Cookie（maxAge=0）
- 过滤器兼容：LoginCheckFilter 在 C 端 session 检查失败后，额外检查 Cookie remember 字段，解析 Token 获取 userId，反查 userService.getById(userId) 确认用户存在且 status=1，验证通过后补设 session.setAttribute("user", userId) + BaseContext.setCurrentUserId(userId)
- 前端登录 UI：login.html 追加"30天内保持登录"复选框，默认不勾选，勾选时 loginApi 多传 rememberMe: true
- 前端退出登录：loginoutApi 正常调用 /user/loginout，后端清 Cookie
- 安全性：HttpOnly Cookie 防 XSS 窃取、HMAC 签名防篡改、内置过期时间服务端二次校验、用户 status=0 禁用即时失效、退出登录即时清除

### 退款系统——客户发起退款申请
- 订单状态码扩展：1=待付款、2=制作中、3=已完成、5=已取消、6=已退款、**7=退款申请中、8=部分退款（已处理）**
- 退款数据模型 RefundRequest（内存存储，不建数据库表）：refundId(雪花ID) / orderId / userId / refundAmount(申请金额) / refundReason / status(0=待审核/1=已同意/2=已拒绝/3=部分退款) / actualRefund(实际退款金额) / merchantReply(商家回复) / createTime / updateTime
- 退款内存上下文 RefundContext：@Component 单例，ConcurrentHashMap<Long, RefundRequest> refundStore + ConcurrentHashMap<Long, Long> orderRefundIndex 按订单ID快速索引
- 客户发起退款：POST /order/requestRefund，@RequestBody RefundRequest（含 orderId + refundAmount + refundReason），校验：1.订单必须存在且属于当前用户 2.订单状态只能是 2(制作中)或 3(已完成) 3.退款金额不能超过订单金额 4.同一订单不能重复申请 → 生成 refundId → 存入 RefundContext → 更新订单 status 为 7 → 返回 R.success("退款申请已提交")
- 客户查询退款进度：GET /order/refundStatus/{orderId}，按 orderId 查 orderRefundIndex 获取 refundId → 返回 RefundRequest
- 商家查看退款申请列表：GET /order/refundRequests，从 RefundContext 查所有 status=0 的记录，返回 List<RefundRequest>
- 商家处理退款：POST /order/handleRefund，@RequestBody RefundRequest（含 refundId + status + actualRefund + merchantReply），三种处理：1.同意（status→1，更新订单 status 为 6 已退款）2.拒绝（status→2，订单 status 恢复为原状态 2 或 3）3.部分退款（status→3，actualRefund 必填，订单 status→8）→ 更新 RefundContext 中的 RefundRequest
- 恢复已估清菜品：同意退款或部分退款时，遍历 orderDetail 中的 dishId，若菜品 status==2（已估清）则恢复为 status==1（启售）
- 异常处理：订单不存在抛 CustomException("订单不存在")、订单状态不允许退款抛 CustomException("当前订单状态不可退款")、退款金额超过订单金额抛 CustomException("退款金额不能超过订单金额")、重复申请抛 CustomException("该订单已有退款申请在处理中")

### 退款系统——前端改造
- C 端 order.html 按钮区改造：status==2（制作中）时新增「申请退款」按钮（与「加餐」并列）；status==7（退款申请中）时显示「退款审核中...」不可点击按钮；status==8（部分退款已处理）时显示结果摘要
- 退款申请弹窗：Vant Dialog 弹出，含退款金额（默认全额，可修改 ≤ 原金额）、退款原因（textarea 必填）、确认提交按钮
- 前端 API（order.js 新增）：requestRefundApi(data) → POST /order/requestRefund、getRefundStatusApi(orderId) → GET /order/refundStatus/{orderId}
- 商家后台 order/list.html：工具栏新增「退款审核」按钮（el-button type="warning"），点击筛选 status==7 的订单；status==7 的订单操作列显示「处理退款」按钮（el-button type="primary"）；点击弹出 el-dialog 处理弹窗，含退款金额输入、同意/拒绝/部分退款单选、商家回复 textarea
- 商家后台 API（backend/api/order.js 新增）：getRefundRequestsApi() → GET /order/refundRequests、handleRefundApi(data) → POST /order/handleRefund

### 即时通讯——TCP/WebSocket 服务端
- 技术选型：Netty 内嵌 TCP Server（port 9090）+ WebSocket 协议升级，浏览器通过 ws://localhost:9090/chat 连接，本质是 TCP 长连接
- pom.xml 新增依赖：netty-all（版本与 Spring Boot 内置 netty 版本保持一致）
- 包结构：edu.ouc.chat.server（ChatServer / ChatServerInitializer / ChatMessageHandler）+ edu.ouc.chat.model（ChatMessage / ChatSession / ChatConversation）+ edu.ouc.chat.context（ChatSessionContext）
- ChatServer：@Component，@PostConstruct 启动 Netty（new Thread 避免阻塞 Spring 主线程），bossGroup(1) + workerGroup(N) + ServerBootstrap + NioServerSocketChannel，绑定 port 9090，@PreDestroy 优雅关闭 EventLoopGroup
- ChatServerInitializer：extends ChannelInitializer<SocketChannel>，Pipeline 顺序：HttpServerCodec → ChunkedWriteHandler → HttpObjectAggregator(65536) → WebSocketServerProtocolHandler("/chat") → ChatMessageHandler
- ChatMessageHandler：extends SimpleChannelInboundHandler<TextWebSocketFrame>，channelRead0() 解析 JSON → ChatMessage，根据 senderRole 路由：CUSTOMER→推送给所有在线商家、MERCHANT→按 orderId 查到客户 userId 推送给客户
- ChatSessionContext：@Component 单例，ConcurrentHashMap<String, ChatSession> sessions(channelId→session) + ConcurrentHashMap<Long, String> userChannelMap(userId→channelId，一个用户一个连接) + ConcurrentHashMap<String, LinkedList<ChatMessage>> conversationMessages(conversationId→消息列表) + ConcurrentHashMap<String, ChatConversation> activeConversations(conversationId→会话元信息，含未读数/最后消息/输入状态)
- 生命周期：channelActive() 时暂存 Channel→channelId 映射不绑定用户；客户端发 LOGIN 消息（含 userId+role）后绑定到 userChannelMap；channelInactive() 时清理 sessions + userChannelMap + 广播离线通知
- 消息类型枚举：CHAT(聊天消息) / TYPING(正在输入) / TYPING_STOP(停止输入) / SYSTEM(系统通知:上线/下线) / LOGIN(登录绑定) / CONVERSATION_LIST(推送会话列表更新)
- 消息历史查询 HTTP 接口：GET /chat/history?orderId=xxx&userId=xxx，从 ChatSessionContext 查 conversationMessages，返回 List<ChatMessage>，最多 100 条
- 配置项（application.yml 新增）：chat.port=9090、chat.path=/chat、chat.max-recent-messages=100

### 即时通讯——多窗口聊天（客服模式）
- 商家端布局：左侧会话列表 + 右侧聊天窗口，经典客服面板模式，每个客户+订单组合为独立会话窗口
- 会话标识 conversationId = orderId + "_" + userId，按此键分会话存储消息和元数据
- 会话列表排序：按 lastMessageTime 降序排列，最新消息的会话排最上面
- 未读消息：商家当前未打开的会话收到新消息时 unreadCount++；商家切换到该会话时 unreadCount 清零；总未读数显示在消息中心入口红点
- 消息路由规则：客户发消息 → 推送给所有在线商家（每个商家更新对应 conversationId 的消息列表 + 会话列表）；商家发消息 → 按 conversationId 解析出客户 userId → 推送给该客户（不广播给其他客户）
- 会话列表更新推送：商家收到新消息时，服务端推送 CONVERSATION_LIST 类型消息，含所有会话的最新摘要信息（lastMessage + lastTime + unreadCount）
- 聊天面板 chat-panel.html：独立 HTML 页面（backend/page/chat/chat-panel.html），可作为弹窗 iframe 嵌入 order/list.html 或独立打开；Vue 数据模型: conversations[] / activeConversationId / messages[] / inputText / customerTyping / totalUnread / wsConnection
- 连接认证：WebSocket 连接建立后立即发送 LOGIN 消息 {"type":"LOGIN","userId":xxx,"role":"MERCHANT"}，服务端绑定会话；未认证的连接 10 秒内不发 LOGIN 则断开

### 即时通讯——实时输入状态
- 协议定义：TYPING 消息 {"type":"TYPING","senderId":xxx,"senderRole":"CUSTOMER/MERCHANT","orderId":xxx,"receiverId":xxx}；停止输入 TYPING_STOP 消息同理，只改 type 字段
- 前端防抖逻辑：输入框 @input 事件触发 → 首次输入发 TYPING → 设 2 秒定时器 → 2 秒内无新输入则发 TYPING_STOP；每次输入重置定时器
- 显示位置：客户侧聊天窗口底部（输入框上方）显示「商家正在输入...」；商家侧当前聊天窗口底部显示「客户正在输入...」；同时左侧会话列表中该客户条目显示「⏳ 正在输入...」动画标记
- 输入状态超时保护：服务端在 ChatSession 中记录 lastTypingTime，若超过 5 秒未收到 TYPING_STOP 且无新 TYPING，服务端自动广播 TYPING_STOP 清理状态
- 输入状态不能广播给无关方：客户 TYPING 只推送给商家、商家 TYPING 只推送给对应客户，不允许跨会话泄露

---

> 使用说明：后续所有开发任务必须先在本索引中按关键词匹配锚点，找到对应子规则后严格按子规则执行。无锚点匹配的操作一律拒绝。