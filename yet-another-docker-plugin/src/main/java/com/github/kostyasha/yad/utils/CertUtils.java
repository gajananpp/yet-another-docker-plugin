package com.github.kostyasha.yad.utils;

import com.github.kostyasha.yad.docker_java.org.bouncycastle.asn1.ASN1ObjectIdentifier;
import com.github.kostyasha.yad.docker_java.org.bouncycastle.cert.X509CertificateHolder;
import com.github.kostyasha.yad.docker_java.org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import com.github.kostyasha.yad.docker_java.org.bouncycastle.openssl.PEMKeyPair;
import com.github.kostyasha.yad.docker_java.org.bouncycastle.openssl.PEMParser;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * TODO migrate to docker-java
 *
 * @author Kanstantsin Shautsou
 */
public class CertUtils {
    private static final Logger LOG = LoggerFactory.getLogger(CertUtils.class);

    private CertUtils() {
    }

    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public static KeyStore createKeyStore(final String keypem, final String certpem) throws NoSuchAlgorithmException,
            InvalidKeySpecException, IOException, CertificateException, KeyStoreException {
        KeyPair keyPair = loadPrivateKey(keypem);
        requireNonNull(keyPair);
        List<Certificate> privateCertificates = loadCertificates(certpem);

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null);

        keyStore.setKeyEntry("docker",
                keyPair.getPrivate(),
                "docker".toCharArray(),
                privateCertificates.toArray(new Certificate[privateCertificates.size()])
        );

        return keyStore;
    }

    /**
     * from "cert.pem" String
     */
    private static List<Certificate> loadCertificates(final String certpem) throws IOException,
            CertificateException {
        final StringReader certReader = new StringReader(certpem);
        try (BufferedReader reader = new BufferedReader(certReader)) {
            return loadCertificates(reader);
        }
    }

    /**
     * "cert.pem" from reader
     */
    private static List<Certificate> loadCertificates(final Reader reader) throws IOException,
            CertificateException {
        try (PEMParser pemParser = new PEMParser(reader)) {
            List<Certificate> certificates = new ArrayList<>();

            JcaX509CertificateConverter certificateConverter = new JcaX509CertificateConverter().setProvider("BC");
            Object certObj = pemParser.readObject();

            if (certObj instanceof X509CertificateHolder) {
                X509CertificateHolder certificateHolder = (X509CertificateHolder) certObj;
                certificates.add(certificateConverter.getCertificate(certificateHolder));
            }

            return certificates;
        }
    }


    /**
     * Return KeyPair from "key.pem" from Reader
     */
    @CheckForNull
    private static KeyPair loadPrivateKey(final Reader reader) throws IOException, NoSuchAlgorithmException,
            InvalidKeySpecException {
        try (PEMParser pemParser = new PEMParser(reader)) {
            Object readObject = pemParser.readObject();
            while (readObject != null) {
                if (readObject instanceof PEMKeyPair) {
                    PEMKeyPair pemKeyPair = (PEMKeyPair) readObject;

                    byte[] pemPrivateKeyEncoded = pemKeyPair.getPrivateKeyInfo().getEncoded();
                    byte[] pemPublicKeyEncoded = pemKeyPair.getPublicKeyInfo().getEncoded();

                    for (String guessFactory : new String[]{"RSA", "ECDSA"}) {
                        try {
                            KeyFactory factory = KeyFactory.getInstance(guessFactory);
//                            KeyFactory factory = KeyFactory.getInstance("ECDSA");

                            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(pemPublicKeyEncoded);
                            PublicKey publicKey = factory.generatePublic(publicKeySpec);

                            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(pemPrivateKeyEncoded);
                            PrivateKey privateKey = factory.generatePrivate(privateKeySpec);

                            return new KeyPair(publicKey, privateKey);
                        } catch (InvalidKeySpecException ex) {
                        }
                    }
                } else if (readObject instanceof ASN1ObjectIdentifier) {
                    // no idea how it can be used
                    final ASN1ObjectIdentifier asn1ObjectIdentifier = (ASN1ObjectIdentifier) readObject;
                    LOG.trace("Ignoring asn1ObjectIdentifier {}", asn1ObjectIdentifier);
                } else {
                    LOG.warn("Unknown object '{}' from PEMParser", readObject);
                }

                readObject = pemParser.readObject();
            }
        }

        return null;
    }

    /**
     * Return KeyPair from "key.pem"
     */
    @CheckForNull
    private static KeyPair loadPrivateKey(final String keypem) throws IOException, NoSuchAlgorithmException,
            InvalidKeySpecException {
        try (StringReader certReader = new StringReader(keypem);
             BufferedReader reader = new BufferedReader(certReader)) {
            return loadPrivateKey(reader);
        }
    }

    /**
     * "ca.pem" from String
     */
    public static KeyStore createTrustStore(String capem) throws IOException, CertificateException,
            KeyStoreException, NoSuchAlgorithmException {
        try (Reader certReader = new StringReader(capem)) {
            return createTrustStore(certReader);
        }
    }

    /**
     * "ca.pem" from Reader
     */
    public static KeyStore createTrustStore(final Reader certReader) throws IOException, CertificateException,
            KeyStoreException, NoSuchAlgorithmException {
        try (PEMParser pemParser = new PEMParser(certReader)) {
            X509CertificateHolder certificateHolder = (X509CertificateHolder) pemParser.readObject();
            Certificate caCertificate = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certificateHolder);

            KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(null);
            trustStore.setCertificateEntry("ca", caCertificate);

            return trustStore;
        }
    }

}
