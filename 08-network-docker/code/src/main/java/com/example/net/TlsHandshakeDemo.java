package com.example.net;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;

/**
 * TLS 为什么安全：非对称加密只用来传一把对称密钥，真正的数据走对称加密；数字证书用来防中间人。
 * 直接运行 main。用的是 JDK 自带的 RSA、AES-GCM、SHA256withRSA，不发起真实的 TLS 连接。
 */
public class TlsHandshakeDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("1. 握手在协商什么（TLS 1.2 的主线，1.3 把往返压成 1-RTT，角色不变）");
        System.out.println("   客户端 → 支持的 TLS 版本和密码套件");
        System.out.println("   服务端 → 选定套件，并交出证书（里面是服务端的公钥，由 CA 签名）");
        System.out.println("   客户端 → 用信任库里的 CA 公钥验证证书；生成一把 AES 密钥，用服务端公钥加密后送过去");
        System.out.println("   之后 → 两边只用这把 AES 密钥加密业务数据");

        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        KeyPair ca = rsa.generateKeyPair();
        KeyPair server = rsa.generateKeyPair();
        KeyPair attacker = rsa.generateKeyPair();

        // ===== 实验 1：CA 给服务端公钥签名。客户端只信任 CA，不需要提前认识每一台服务器 =====
        byte[] certificate = sign(ca, server.getPublic().getEncoded());
        boolean serverOk = verify(ca, server.getPublic().getEncoded(), certificate);
        boolean attackerOk = verify(ca, attacker.getPublic().getEncoded(), certificate);
        System.out.println("\n2. 证书");
        System.out.println("   用 CA 公钥验证“服务端公钥 + 这份签名”：" + serverOk);
        System.out.println("   中间人换成自己的公钥，签名对不上：" + attackerOk);
        System.out.println("   证书还要核对域名和有效期。只加密、不验证证书，中间人可以自己充当服务端。");

        // ===== 实验 2：非对称加密只搬得动一把短密钥 =====
        KeyGenerator aesGen = KeyGenerator.getInstance("AES");
        aesGen.init(128);
        SecretKey aesKey = aesGen.generateKey();
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsaCipher.init(Cipher.ENCRYPT_MODE, server.getPublic());
        byte[] wrappedKey = rsaCipher.doFinal(aesKey.getEncoded());
        System.out.println("\n3. 密钥交换");
        System.out.println("   AES 密钥 " + aesKey.getEncoded().length + " 字节，RSA 加密后 " + wrappedKey.length + " 字节");
        byte[] big = new byte[1024];
        boolean rsaRejected = false;
        try {
            rsaCipher.doFinal(big);
        } catch (Exception e) {
            rsaRejected = true;
        }
        System.out.println("   用同一把 RSA 公钥直接加密 1KB 数据：" + (rsaRejected ? "被拒绝（一次装不下）" : "居然成功了"));

        // ===== 实验 3：业务数据走 AES-GCM。GCM 同时提供加密和完整性，改一个字节就解不开 =====
        byte[] plain = "订单 1001 已支付".getBytes();
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
        byte[] cipherText = gcm.doFinal(plain);
        System.out.println("\n4. 对称加密传数据");
        System.out.println("   明文 " + plain.length + " 字节，密文 " + cipherText.length + " 字节（多出来的 16 字节是 GCM 认证标签）");
        gcm.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
        byte[] back = gcm.doFinal(cipherText);
        cipherText[0] ^= 1;
        boolean tamperDetected = false;
        try {
            gcm.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
            gcm.doFinal(cipherText);
        } catch (Exception e) {
            tamperDetected = true;
        }
        System.out.println("   解密结果：" + new String(back));
        System.out.println("   密文被改了 1 个比特，GCM 拒绝解密：" + tamperDetected);

        long aesStart = System.nanoTime();
        byte[] payload = new byte[1024 * 1024];
        gcm.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
        gcm.doFinal(payload);
        long aesMs = (System.nanoTime() - aesStart) / 1_000_000;
        System.out.println("   AES-GCM 加密 1MB 用了 " + aesMs + "ms。这是 HTTPS 把大数据交给对称加密的原因");
    }

    private static byte[] sign(KeyPair ca, byte[] data) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(ca.getPrivate());
        signature.update(data);
        return signature.sign();
    }

    private static boolean verify(KeyPair ca, byte[] data, byte[] certificate) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(ca.getPublic());
        signature.update(data);
        return signature.verify(certificate);
    }
}
