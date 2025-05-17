package com.ghostchu.peerbanhelper.downloader.impl.bitcomet.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Base64;

public final class BCAESTool {

    public static void init() {
        //Security.addProvider((Provider) Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider").getDeclaredConstructor().newInstance());
        Security.addProvider(new BouncyCastleProvider());
    }

    public static String credential(String json, String cid) throws Exception {
        // Ensure Bouncy Castle is added to your project for PBKDF2
        byte[] t = new byte[8];
        byte[] r = new byte[8];
        SecureRandom random = new SecureRandom();
        random.nextBytes(t);
        random.nextBytes(r);

        byte[] n = PBKDF2(cid.getBytes(StandardCharsets.UTF_8), t, 10000, 32, "SHA1");
        byte[] i = PBKDF2(cid.getBytes(StandardCharsets.UTF_8), r, 10000, 32, "SHA1");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        SecretKeySpec keySpec = new SecretKeySpec(n, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] s = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
        byte[] o = cipher.getIV();

        ByteArrayOutputStream msg = new ByteArrayOutputStream();
        msg.write(3); // m[0]
        msg.write(1); // m[1]
        msg.write(t);
        msg.write(r);
        msg.write(o);
        msg.write(s);

        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(i, "HmacSHA256"));
        hmac.update(msg.toByteArray());
        byte[] sig = hmac.doFinal();

        msg.write(sig);

        // Use an HTTP client like OkHttp or Apache HttpClient for the POST request
        return Base64.getEncoder().encodeToString(msg.toByteArray());
    }

    private static byte[] PBKDF2(byte[] password, byte[] salt, int iterations, int keyLength, String hashAlgorithm) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(new String(password, StandardCharsets.UTF_8).toCharArray(), salt, iterations, keyLength * 8);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmac" + hashAlgorithm);
        return factory.generateSecret(spec).getEncoded();
    }

}