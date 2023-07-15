package com.hmdp.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AES {
    //TIP Java中的AES加密算法可以使用JCE（Java Cryptography Extension）库来实现。
    //TIP 指定算法为AES加密算法
    private static final String ALGORITHM = "AES";
    //TIP 使用PKCS5Padding方式进行填充,在明文的末尾进行填充,填充的数据是当前和16个字节相差的数量
    //TIP 加密模式使用ECB模式,在解密的时候也需要使用ECB模式来解密
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    //TIP 加密用的密钥,只保存在服务端 这个密钥的长度只能为128位或192位或256位
    private static final String KEY = "hmdpsecretkey626";
    public static String encrypt(String plainText) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
    public static String decrypt(String encryptedText) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
}