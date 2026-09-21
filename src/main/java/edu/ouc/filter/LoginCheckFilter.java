package edu.ouc.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ouc.common.BaseContext;
import edu.ouc.common.R;
import edu.ouc.entity.User;
import edu.ouc.service.IUserService;
import edu.ouc.utils.TokenUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

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

        // 5.判断本次请求是否为C端业务接口（订单、购物车、地址簿、C端用户）
        // C端接口优先使用user身份，避免与B端employee登录态冲突
        boolean isClientApi = requestURI.startsWith("/order/submit")
                || requestURI.startsWith("/order/userPage")
                || requestURI.startsWith("/shoppingCart")
                || requestURI.startsWith("/addressBook")
                || requestURI.startsWith("/user/loginout");

        if (isClientApi) {
            // C端业务接口：优先检查user登录态
            if (request.getSession().getAttribute("user") != null) {
                Long userId = (Long) request.getSession().getAttribute("user");
                log.info("C端邮箱用户{}已登录", userId);
                BaseContext.setCurrentUserId(userId);
                filterChain.doFilter(request, response);
                return;
            }
        } else {
            // B端业务接口：优先检查employee登录态
            if (request.getSession().getAttribute("employee") != null) {
                Long id = (Long) request.getSession().getAttribute("employee");
                log.info("B端员工{}已登录", id);
                BaseContext.setCurrentUserId(id);
                filterChain.doFilter(request, response);
                return;
            }

            // B端请求也允许user登录态访问（如后台查看C端数据）
            if (request.getSession().getAttribute("user") != null) {
                Long userId = (Long) request.getSession().getAttribute("user");
                log.info("B端请求使用C端用户{}身份", userId);
                BaseContext.setCurrentUserId(userId);
                filterChain.doFilter(request, response);
                return;
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
            // 从Spring容器获取服务（过滤器不是Spring Bean，通过WebApplicationContextUtils获取）
            WebApplicationContext context = WebApplicationContextUtils
                    .getWebApplicationContext(request.getServletContext());
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
                        // 自动登录成功，补设Session
                        request.getSession().setAttribute("user", userId);
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