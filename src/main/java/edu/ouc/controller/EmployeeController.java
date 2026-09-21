package edu.ouc.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.ouc.common.R;
import edu.ouc.entity.Employee;
import edu.ouc.filter.LoginCheckFilter;
import edu.ouc.service.IEmployeeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Author: Sihang Xie
 * Description: 员工Employee表现层
 * Date: 2022/9/29 13:48
 * Version: 0.0.1
 * Modified By:
 */
@Slf4j
@RestController
@RequestMapping("/employee")
@RequiredArgsConstructor
public class EmployeeController {

    // 注入业务层
    private final IEmployeeService empService;
    private final StringRedisTemplate stringRedisTemplate;

    // 后台员工登录方法
    @PostMapping("/login")
    public R<Employee> login(HttpServletRequest request, @RequestBody Employee employee) {
        String username = employee.getUsername();
        String password = employee.getPassword();
        log.info("员工登录请求: username={}", username);
        // 1.对前端传入的密码进行md5加密
        password = md5Hex(password);
        // 2.根据前端传入的账号去数据库中查询
        // 2.1 创建查询条件对象
        LambdaQueryWrapper<Employee> lqw = new LambdaQueryWrapper<>();
        // 2.2 使用MP的等值查询eq
        lqw.eq(StringUtils.isNotEmpty(username), Employee::getUsername, username);
        // 2.3 根据lqw的条件进行等值查询
        Employee emp = empService.getOne(lqw);

        // 3.判断查询到的员工是否为空
        if (emp == null) {
            log.warn("员工登录失败: 用户不存在, username={}", username);
            return R.error("用户不存在，登录失败");
        }
        // 4.密码比对
        if (!password.equals(emp.getPassword())) {
            log.warn("员工登录失败: 密码错误, username={}", username);
            return R.error("密码错误，登录失败");
        }
        // 5.查看员工状态是否禁用
        if (emp.getStatus() != 1) {
            log.warn("员工登录失败: 账号已禁用, username={}", username);
            return R.error("该账号已禁用");
        }
        // 6.将员工ID存放在Session保存作用域中
        request.getSession().setAttribute("employee", emp.getId());
        // 注册Redis在线会话：新登录自动踢掉该员工在其他浏览器的旧会话
        LoginCheckFilter.trackOnlineSession(stringRedisTemplate, request.getSession().getId(), "employee", emp.getId());
        log.info("员工登录成功: id={}, username={}", emp.getId(), username);
        return R.success(emp);
    }

    // 后台员工退出登录方法
    @PostMapping("/logout")
    public R<String> logout(HttpServletRequest request) {
        Object empId = request.getSession().getAttribute("employee");
        log.info("员工退出登录: id={}", empId);
        // 1.清除Session保存作用域中保存的数据
        request.getSession().removeAttribute("employee");
        // 2.移除Redis在线会话记录
        if (empId instanceof Long) {
            LoginCheckFilter.removeOnlineSession(stringRedisTemplate, "employee", (Long) empId);
        }
        // 3.返回结果
        return R.success("退出成功");
    }

    // 新增员工功能
    @PostMapping
    public R<String> save(@RequestBody Employee employee) {
        log.info("新增员工: name={}, username={}, phone={}", employee.getName(), employee.getUsername(), employee.getPhone());

        // 1.设置创建人ID
//        employee.setCreateUser((Long) request.getSession().getAttribute("employee"));

        // 2.设置最后修改人ID
//        employee.setUpdateUser((Long) request.getSession().getAttribute("employee"));

        // 3.设置初始密码为身份证后6位，并经过MD5加密
        String idNumber = employee.getIdNumber();
        String password = md5Hex(idNumber.substring(idNumber.length() - 6));
        employee.setPassword(password);

        // 4.设置创建时间
//        employee.setCreateTime(LocalDateTime.now());

        // 5.设置修改时间
//        employee.setUpdateTime(LocalDateTime.now());

        // 6.调用业务层保存到数据库中
        empService.save(employee);
        return R.success("添加成功");
    }

    // 分页查询+根据员工姓名查询功能
    @GetMapping("/page")
    public R<Page<Employee>> getPage(Long page, Long pageSize, String name) {
        return R.success(empService.getPage(page, pageSize, name));
    }

    // 修改员工信息
    @PutMapping
    public R<String> update(@RequestBody Employee employee) {
        log.info("修改员工信息: id={}, name={}, status={}", employee.getId(), employee.getName(), employee.getStatus());
        if (empService.updateById(employee)) {

            // 查看当前线程的ID
            long id = Thread.currentThread().getId();
            log.info("线程ID为：{}", id);

            return R.success("修改成功");
        }
        return R.error("修改失败");
    }

    // 根据ID查询员工信息
    @GetMapping("/{id}")
    public R<Employee> getById(@PathVariable Long id) {
        Employee employee = empService.getById(id);
        // 当查询结果不为空时才返回employee
        if (employee != null) {
            return R.success(employee);
        }
        return R.error("查询员工不存在");
    }

    // MD5加密工具方法，替代已废弃的DigestUtils.md5DigestAsHex(byte[])
    private String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5算法不可用", e);
        }
    }
}