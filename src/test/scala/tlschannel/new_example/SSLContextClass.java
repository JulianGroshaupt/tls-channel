package tlschannel.new_example;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

public class SSLContextClass {

  public static SSLContext authenticatedContext(String protocol) throws GeneralSecurityException, IOException {
    SSLContext sslContext = SSLContext.getInstance(protocol);
    KeyStore ks = KeyStore.getInstance("JKS");

    try (InputStream keystoreFile = SSLContextClass.class.getClassLoader().getResourceAsStream("keystore.jks")) {
      ks.load(keystoreFile, "changeme".toCharArray());

      KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

      tmf.init(ks);
      kmf.init(ks, "changeme".toCharArray());

      sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

      return sslContext;
    }
  }

  public static SSLEngine authenticatedEngine(String protocol) throws GeneralSecurityException, IOException {
    SSLContext sslContext = authenticatedContext(protocol);

    SSLEngine sslEngine = sslContext.createSSLEngine();
    String[] suites = { "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384" };
    sslEngine.setEnabledCipherSuites(suites);

    return sslEngine;
  }

}
