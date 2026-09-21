package edu.ouc.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ouc.common.BaseContext;
import edu.ouc.common.R;
import edu.ouc.entity.User;
import edu.ouc.service.IUserService;
import edu.ouc.utils.TokenUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Author: Sihang Xie
 * Description: 用户登录检查过滤器
 * Date: 2022/9/30 9:47
 * Version: 0.0.1
 * Modified By:
 */
// 过滤器注解
// filterName：过滤器名称，可以随便起
// urlPatterns：想要拦截的URL地址，/*表示拦截所有请求
@WebFilter(filterName = "LoginCheckFilter", urlPatterns = "/*")
@Slf4j
public class LoginCheckFilter implements Filter {

    // Spring框架提供的路径匹配器，支持通配符
    public static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    // Redis在线会话前缀
    private static final String ONLINE_EMPLOYEE_PREFIX = "online:employee:";
    private static final String ONLINE_USER_PREFIX = "online:user:";
    // 在线会话 Redis TTL（与 Session 超时一致，30分钟）
    private static final long ONLINE_TTL_MINUTES = 30;

    // 缓存 StringRedisTemplate，避免每次请求都从 Spring 容器获取
    private StringRedisTemplate stringRedisTemplate;
    private WebApplicationContext cachedContext;

    private StringRedisTemplate getRedisTemplate(HttpServletRequest request) {
        if (stringRedisTemplate == null) {
            WebApplicationContext context = WebApplicationContextUtils
                    .getWebApplicationContext(request.getServletContext());
            if (context != null) {
                stringRedisTemplate = context.getBean(StringRedisTemplate.class);
                cachedContext = context;
            }
        }
        return stringRedisTemplate;
    }

    private WebApplicationContext getSpringContext(HttpServletRequest request) {
        if (cachedContext == null) {
            cachedContext = WebApplicationContextUtils
                    .getWebApplicationContext(request.getServletContext());
        }
        return cachedContext;
    }

    // 验证当前 Session 是否仍为有效在线会话（多人同号登录互踢）
    private boolean validateOnlineSession(HttpServletRequest request, String type, Long userId) {
        StringRedisTemplate redis = getRedisTemplate(request);
        if (redis == null) {
            // Redis不可用，降级：只依赖本地Session（不阻止登录）
            return true;
        }
        try {
            String key = ("employee".equals(type) ? ONLINE_EMPLOYEE_PREFIX : ONLINE_USER_PREFIX) + userId;
            String expectedSessionId = redis.opsForValue().get(key);
            // Redis 中没有记录 → 会话已过期或被踢
            if (expectedSessionId == null) {
                log.info("{}用户{}的Redis在线记录不存在，会话已过期", type, userId);
                return false;
            }
            String currentSessionId = request.getSession().getId();
            // Session ID 不匹配 → 被新登录踢掉
            if (!currentSessionId.equals(expectedSessionId)) {
                log.info("{}用户{}的Session ID不匹配(当前={}, 期望={})，已被新登录踢下线", type, userId, currentSessionId, expectedSessionId);
                return false;
            }
            // 验证通过，刷新 TTL
            redis.expire(key, ONLINE_TTL_MINUTES, TimeUnit.MINUTES);
            return true;
        } catch (Exception e) {
            log.error("Redis会话校验异常，降级放行: {}", e.getMessage());
            return true;
        }
    }

    // 在Redis中注册在线会话（登录成功时调用）
    public static void trackOnlineSession(StringRedisTemplate redis, String sessionId, String type, Long userId) {
        if (redis == null) return;
        try {
            String key = ("employee".equals(type) ? ONLINE_EMPLOYEE_PREFIX : ONLINE_USER_PREFIX) + userId;
            redis.opsForValue().set(key, sessionId, ONLINE_TTL_MINUTES, TimeUnit.MINUTES);
            log.info("在线会话已注册: {}用户ID={}, sessionId={}", type, userId, sessionId);
        } catch (Exception e) {
            log.error("注册在线会话失败: {}", e.getMessage());
        }
    }

    // 从Redis中移除在线会话（退出登录时调用）
    public static void removeOnlineSession(StringRedisTemplate redis, String type, Long userId) {
        if (redis == null) return;
        try {
            String key = ("employee".equals(type) ? ONLINE_EMPLOYEE_PREFIX : ONLINE_USER_PREFIX) + userId;
            redis.delete(key);
            log.info("在线会话已移除: {}用户ID={}", type, userId);
        } catch (Exception e) {
            log.error("移除在线会话失败: {}", e.getMessage());
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        // 强转
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // 1.获取本次请求的URL
        String requestURI = request.getRequestURI();    // /backend/index.html

        log.info("拦截到请求URL：{}", requestURI);

        // 2.定义不需要拦截的URL地址数组
        String[] urls = new String[]{
                "/employee/login",  // 登录页面
                "/employee/logout", // 退出登录
                "/backend/**",      // 后台页面的页面的静态资源
                "/front/**",        // 移动端页面的静态资源
                "/user/login",      // 用户登录
                "/user/sendMsg",    // 发送登录验证码
                "/common/**",       // 文件上传下载（图片等静态资源，无需登录）
                "/kitchen/**",
			"/cashier/**",      // 收银台接口
			"/dinnerTable/**"   // 桌台管理接口
        };

        // 3.判断本次请求URL是否需要拦截
        Boolean check = check(urls, requestURI);

        // 4.如果check为true则不需要处理，直接放行
        if (check) {
            log.info("本次请求{}不需要处理", requestURI);
            filterChain.doFilter(request, response);
            return;
        }

        // 5.判断本次请求是否为C端业务接口（订单、购物车、C端用户）
        // C端接口优先使用user身份，避免与B端employee登录态冲突
        boolean isClientApi = requestURI.startsWith("/order/submit")
                || requestURI.startsWith("/order/userPage")
                || requestURI.startsWith("/order/requestRefund")
                || requestURI.startsWith("/order/refundStatus")
                || requestURI.startsWith("/shoppingCart")
                || requestURI.startsWith("/user/loginout");

        if (isClientApi) {
            // C端业务接口：优先检查user登录态（含Redis多端互踢校验）
            if (request.getSession().getAttribute("user") != null) {
                Long userId = (Long) request.getSession().getAttribute("user");
                if (!validateOnlineSession(request, "user", userId)) {
                    request.getSession().removeAttribute("user");
                    log.info("C端用户{}的会话已被新登录踢下线", userId);
                } else {
                    log.info("C端邮箱用户{}已登录", userId);
                    BaseContext.setCurrentUserId(userId);
                    filterChain.doFilter(request, response);
                    return;
                }
            }
        } else {
            // B端业务接口：优先检查employee登录态（含Redis多端互踢校验）
            if (request.getSession().getAttribute("employee") != null) {
                Long id = (Long) request.getSession().getAttribute("employee");
                if (!validateOnlineSession(request, "employee", id)) {
                    request.getSession().removeAttribute("employee");
                    log.info("B端员工{}的会话已被新登录踢下线", id);
                } else {
                    log.info("B端员工{}已登录", id);
                    BaseContext.setCurrentUserId(id);
                    filterChain.doFilter(request, response);
                    return;
                }
            }

            // B端请求也允许user登录态访问（如后台查看C端数据）
            if (request.getSession().getAttribute("user") != null) {
                Long userId = (Long) request.getSession().getAttribute("user");
                if (!validateOnlineSession(request, "user", userId)) {
                    request.getSession().removeAttribute("user");
                    log.info("B端请求中C端用户{}的会话已被新登录踢下线", userId);
                } else {
                    log.info("B端请求使用C端用户{}身份", userId);
                    BaseContext.setCurrentUserId(userId);
                    filterChain.doFilter(request, response);
                    return;
                }
            }
        }

        log.info("B端员工和C端用户Session均未命中，尝试Cookie记住我自动登录");

        // 7.检查Cookie中的记住我Token，实现30天自动登录
        Cookie[] cookies = request.getCookies();
        String rememberToken = null;
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("remember".equals(cookie.getName())) {
                    rememberToken = cookie.getValue();
                    break;
                }
            }
        }
        if (rememberToken != null) {
            // 从Spring容器获取服务（使用缓存的上下文）
            WebApplicationContext context = getSpringContext(request);
            if (context != null) {
                // 读取配置中的签名密钥
                String rememberKey = context.getEnvironment()
                        .getProperty("reggie.remember-key");
                IUserService userService = context.getBean(IUserService.class);

                // 校验Token有效性
                Long userId = TokenUtils.validateRememberToken(rememberToken, rememberKey);
                if (userId != null) {
                    // 反查用户是否存在且未被禁用
                    User user = userService.getById(userId);
                    if (user != null && user.getStatus() == 1) {
                        // 自动登录成功，补设Session并注册Redis在线会话
                        request.getSession().setAttribute("user", userId);
                        // 注册在线会话（踢掉该用户在其他浏览器的旧会话）
                        StringRedisTemplate redis = getRedisTemplate(request);
                        trackOnlineSession(redis, request.getSession().getId(), "user", userId);
                        BaseContext.setCurrentUserId(userId);
                        log.info("Cookie记住我自动登录成功，用户ID：{}", userId);
                        filterChain.doFilter(request, response);
                        return;
                    }
                    log.info("Cookie记住我Token有效，但用户不存在或已被禁用，用户ID：{}", userId);
                }
            }
        }

        log.info("用户未登录");

        // 8.走到这里就是没登录
        // 向浏览器响应一个流，让前端读到R里面的数据
        response.getWriter().write(new ObjectMapper().writeValueAsString(R.error("NOTLOGIN")));
    }

    // 核查请求URL是否在放行URL数组中，检查本次请求是否需要放行
    private Boolean check(String[] urls, String requestURI) {
        for (String url : urls) {
            boolean match = PATH_MATCHER.match(url, requestURI);
            if (match) {
                return true;
            }
        }
        // 循环完了都匹配不上就返回false
        return false;
    }
}