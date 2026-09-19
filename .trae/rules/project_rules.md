﻿﻿﻿# 项目规则索引（README.md 全量抽取）

## GATE RULE（最高优先级总规则）
**【最高优先级 · 守门总规则】**
1. 所有代码改造、配置修改、新增功能，**必须先匹配索引锚点**，没有匹配锚点的需求，直接拒绝执行，禁止自行新增README文档以外的业务逻辑、接口、表字段。
2. 任何修改前，先核对对应锚点的子规则；代码实现必须严格遵守子规则，**不允许简化、跳过子规则要求**。
3. 涉及数据库修改：只能使用我提供的本地MySQL连接信息，建表、改字段必须和原项目文档表结构保持一致，**禁止私自新增数据表、私自修改字段类型**。
4. 改动代码之后，输出检查清单：列出本次改动用到哪个锚点、核对对应的子规则是否全部遵守。
5. 如果需求和project_rules.md内规则冲突，以本规则文件为准，不允许擅自修改规则文件，如需改规则，必须先询问我。
6. 不要主动优化重构现有代码，除非我明确提出重构指令；只做我指定的任务。

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

---

> 使用说明：后续所有开发任务必须先在本索引中按关键词匹配锚点，找到对应子规则后严格按子规则执行。无锚点匹配的操作一律拒绝。