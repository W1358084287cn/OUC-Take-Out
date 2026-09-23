package edu.ouc.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Author: Sihang Xie
 * Description: 公共的Controller组件
 * Date: 2022/10/3 14:18
 * Version: 0.0.1
 * Modified By:
 */
@RestController
@RequestMapping("/common")
@Slf4j
public class CommonController {

    // 获取配置文件中的存储路径
    @Value("${reggie.path}")
    private String basePath;

    // 允许上传的文件扩展名白名单
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".ico", // 图片
            ".mp3", ".wav", ".ogg"                                    // 音频
    ));

    // 文件上传
    @PostMapping("/upload")
    public R<String> upload(MultipartFile file) {
        // 获取原始文件名
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            return R.error("文件名不能为空");
        }

        // 校验文件扩展名白名单
        int dotIndex = originalFilename.lastIndexOf(".");
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            return R.error("文件缺少有效扩展名，仅支持常见图片和音频格式");
        }
        String suffix = originalFilename.substring(dotIndex).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(suffix)) {
            return R.error("不支持的文件类型：" + suffix + "，仅支持图片和音频格式");
        }

        // 使用UUID重新生成文件名，防止文件名称重复造成文件覆盖，同时防路径穿越
        String fileName = UUID.randomUUID() + suffix;

        // 确保文件写入basePath目录内（防路径穿越）
        Path targetPath = Paths.get(basePath, fileName).normalize();
        if (!targetPath.startsWith(Paths.get(basePath).normalize())) {
            log.error("非法文件路径: {}", targetPath);
            return R.error("文件存储路径非法");
        }

        // 创建一个目录对象
        File dir = new File(basePath);
        // 判断当前目录是否存在
        if (!dir.exists() && !dir.mkdirs()) {
            log.error("创建目录失败: {}", basePath);
            return R.error("文件存储目录创建失败");
        }

        try {
            // 转存
            file.transferTo(targetPath.toFile());
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return R.error("文件上传失败，请重试");
        }
        // 返回文件名称即可
        return R.success(fileName);
    }

    // 文件下载
    @GetMapping("/download")
    public void download(String name, HttpServletResponse response) {
        if (name == null || name.trim().isEmpty()) {
            response.setStatus(400);
            return;
        }

        // 防御路径穿越：规范化路径后校验是否在basePath内
        Path targetPath = Paths.get(basePath, name).normalize();
        Path baseDir = Paths.get(basePath).normalize();
        if (!targetPath.startsWith(baseDir)) {
            log.warn("拒绝非法文件访问: name={}, resolved={}", name, targetPath);
            response.setStatus(403);
            return;
        }

        File file = targetPath.toFile();
        if (!file.exists() || !file.isFile()) {
            log.warn("文件不存在: {}", targetPath);
            response.setStatus(404);
            return;
        }

        FileInputStream fis = null;
        ServletOutputStream os = null;

        try {
            // 输入流，通过输入流读取文件内容
            fis = new FileInputStream(file);

            // 输出流，通过输出流将文件写回浏览器，在浏览器展示图片
            os = response.getOutputStream();

            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                os.write(buffer, 0, len);
                os.flush();
            }

        } catch (IOException e) {
            log.error("文件下载失败: name={}", name, e);
            response.setStatus(500);
        } finally {
            // 关闭流资源
            try {
                if (fis != null)
                    fis.close();
            } catch (IOException e) {
                log.error("关闭文件输入流失败", e);
            }
            try {
                if (os != null)
                    os.close();
            } catch (IOException e) {
                log.error("关闭输出流失败", e);
            }
        }
    }
}