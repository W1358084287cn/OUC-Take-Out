package edu.ouc.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * 二维码生成工具类
 */
@Slf4j
public class QRCodeGenerator {

    private QRCodeGenerator() {}

    public static String generateBase64(String text, int width, int height) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bos);
            byte[] bytes = bos.toByteArray();
            String base64 = Base64.getEncoder().encodeToString(bytes);
            log.info("二维码生成成功，内容长度={}", text.length());
            return "data:image/png;base64," + base64;
        } catch (Exception e) {
            log.error("二维码生成失败: {}", e.getMessage());
            return null;
        }
    }
}