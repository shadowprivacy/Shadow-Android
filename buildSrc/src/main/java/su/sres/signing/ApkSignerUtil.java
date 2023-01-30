package su.sres.signing;

import com.android.apksig.ApkSigner;
import com.android.apksig.apk.ApkFormatException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

public class ApkSignerUtil {
    private final String keyStoreType;

    private final String keyStoreFile;

    private final String keyStorePassword;


    public ApkSignerUtil(String keyStoreType, String keyStoreFile, String keyStorePassword) {
        this.keyStoreType     = keyStoreType;
        this.keyStoreFile     = keyStoreFile;
        this.keyStorePassword = keyStorePassword;
    }

    public void calculateSignature(String inputApkFile, String outputApkFile)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException, ApkFormatException, InvalidKeyException, SignatureException
    {
        System.out.println("Running calculateSignature()...");

        ApkSigner apkSigner = new ApkSigner.Builder(Collections.singletonList(loadKeyStore(keyStoreType, keyStoreFile, keyStorePassword)))
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setInputApk(new File(inputApkFile))
                .setOutputApk(new File(outputApkFile))
                .setOtherSignersSignaturesPreserved(false)
                .build();

        apkSigner.sign();
    }

    private ApkSigner.SignerConfig loadKeyStore(String keyStoreType, String keyStoreFile, String keyStorePassword) throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
        KeyStore keyStoreEntity = KeyStore.getInstance(keyStoreType == null ? KeyStore.getDefaultType() : keyStoreType);
        char[]   password       = getPassword(keyStorePassword);
        keyStoreEntity.load(Files.newInputStream(Paths.get(keyStoreFile)), password);

        Enumeration<String> aliases  = keyStoreEntity.aliases();
        String              keyAlias = null;

        while (aliases != null && aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStoreEntity.isKeyEntry(alias)) {
                keyAlias = alias;
                break;
            }
        }

        if (keyAlias == null) {
            throw new IllegalArgumentException("Keystore has no key entries!");
        }

        PrivateKey    privateKey   = (PrivateKey) keyStoreEntity.getKey(keyAlias, password);
        Certificate[] certificates = keyStoreEntity.getCertificateChain(keyAlias);

        if (certificates == null || certificates.length == 0) {
            throw new IllegalArgumentException("Unable to load certificates!");
        }

        List<X509Certificate> results = new LinkedList<>();

        for (Certificate certificate : certificates) {
            results.add((X509Certificate)certificate);
        }


        return new ApkSigner.SignerConfig.Builder("Shadow Signer", privateKey, results).build();
    }

    private char[] getPassword(String encoded) throws IOException {
        if (encoded.startsWith("file:")) {
            String         name     = encoded.substring("file:".length());
            BufferedReader reader   = new BufferedReader(new FileReader(new File(name)));
            String         password = reader.readLine();

            if (password.length() == 0) {
                throw new IOException("Failed to read password from file: " + name);
            }

            return password.toCharArray();
        } else {
            return encoded.toCharArray();
        }
    }

}