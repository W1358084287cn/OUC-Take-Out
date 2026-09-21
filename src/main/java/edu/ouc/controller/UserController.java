package edu.ouc.controller;

import edu.ouc.common.R;
import edu.ouc.entity.User;
import edu.ouc.service.impl.UserServiceImpl;
import edu.ouc.utils.TokenUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.mail.MessagingException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;

/**
 * Author: Sihang Xie
 * Description: 用户控制层
 * Date: 2022/10/22 10:23
 * Version: 0.0.1
 * Modified By:
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserServiceImpl userService;

    // 注入配置文件中的记住我签名密钥
    @Value("${reggie.remember-key}")
    private String rememberKey;

    public UserController(UserServiceImpl userService) {
        this.userService = userService;
    }

    // 发送邮箱验证码
    @PostMapping("/sendMsg")
    public R<String> sendMsg(@RequestBody User user, HttpSession session) throws MessagingException {
        log.info("发送验证码请求: email={}", user.getEmail());
        if (userService.sendMsg(user, session)) {
            return R.success("验证码发送成功");
        }
        return R.error("验证码发送失败");
    }

    // 移动端用户登录登录
    @PostMapping("/login")
    public R<User> login(@RequestBody Map<String, String> map, HttpSession session, HttpServletResponse response) {
        log.info("用户登录请求: email={}", map.get("email"));
        User user = userService.login(map, session);

        // 如果前端勾选了"30天内保持登录"，生成Token写入Cookie
        String rememberMe = map.get("rememberMe");
        if ("true".equals(rememberMe)) {
            String token = TokenUtils.generateRememberToken(user.getId(), rememberKey, 30);
            Cookie cookie = new Cookie("remember", token);
            cookie.setMaxAge(2592000); // 30天（秒）
            cookie.setHttpOnly(true);  // 防XSS窃取
            cookie.setPath("/");       // 全站可用
            response.addCookie(cookie);
            log.info("用户{}已勾选记住我，Cookie已设置", user.getId());
        }

        return R.success(user);
    }

    // 移动端用户退出登录
    @PostMapping("/loginout")
    public R<String> logout(HttpSession session, HttpServletResponse response) {
        if (userService.logout(session)) {
            // 清除记住我 Cookie
            Cookie cookie = new Cookie("remember", "");
            cookie.setMaxAge(0);        // 立即过期
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            response.addCookie(cookie);
            log.info("用户退出登录，记住我Cookie已清除");
            return R.success("退出成功");
        }
        return R.error("退出失败");
    }
}