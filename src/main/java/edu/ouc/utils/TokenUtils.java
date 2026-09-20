package edu.ouc.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Description: C端30天保持登录 Token 工具类（HMAC-SHA256 签名，自包含验证）
 * Date: 2026/09/19
 */
public class TokenUtils {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    // 生成记住我 Token
    // 格式：Base64(userIdHex:expiryMillis:hmacHex)
    public static String generateRememberToken(Long userId, String secret, int days) {
        long expiry = System.currentTimeMillis() + days * 86400000L;
        String userIdHex = Long.toHexString(userId);
        String payload = userIdHex + ":" + expiry;
        String signature = hmacSha256Hex(payload, secret);
        String token = payload + ":" + signature;
        return Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    // 验证记住我 Token，成功返回 userId，失败返回 null
    public static Long validateRememberToken(String token, String secret) {
        try {
            String decoded = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
            int lastColon = decoded.lastIndexOf(':');
            if (lastColon < 1) return null;

            String payload = decoded.substring(0, lastColon);
            String signature = decoded.substring(lastColon + 1);

            // 解析 payload
            String[] parts = payload.split(":", 2);
            if (parts.length != 2) return null;

            String userIdHex = parts[0];
            long expiry = Long.parseLong(parts[1]);

            // 校验过期时间
            if (System.currentTimeMillis() > expiry) return null;

            // 校验 HMAC 签名
            String expected = hmacSha256Hex(payload, secret);
            if (!expected.equals(signature)) return null;

            // 解析 userId
            return Long.parseLong(userIdHex, 16);
        } catch (Exception e) {
            return null;
        }
    }

    // HMAC-SHA256 签名并返回十六进制字符串
    private static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(keySpec);
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : result) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 签名失败", e);
        }
    }
}