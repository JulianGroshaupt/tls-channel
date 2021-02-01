/**
 * 
 */
package tlschannel.new_example;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;

import tlschannel.ClientTlsChannel;
import tlschannel.TlsChannel;

public class SampleClient {

  private static final Charset utf8 = StandardCharsets.UTF_8;

  public static void main(String[] args) throws IOException, InterruptedException, GeneralSecurityException {
    System.setProperty("jdk.tls.client.enableStatusRequestExtension", "true");
    // System.setProperty("com.sun.net.ssl.checkRevocation", "true");
    // java.security.Security.setProperty("ocsp.enable", "true");
    SSLContext sslContext = SSLContextClass.authenticatedContext("TLSv1.2");

    // connect raw socket channel normally
    try (SocketChannel rawChannel = SocketChannel.open()) {
      rawChannel.connect(new InetSocketAddress("server.localhost", 10000));

      // create TlsChannel builder, combining the raw channel and the SSLEngine, using minimal
      // options
      ClientTlsChannel.Builder builder = ClientTlsChannel.newBuilder(rawChannel, sslContext);

      // instantiate TlsChannel
      try (TlsChannel tlsChannel = builder.build()) {

        // do HTTP interaction and print result
        tlsChannel.write(ByteBuffer.wrap("Hallo".getBytes(StandardCharsets.US_ASCII)));
        ByteBuffer res = ByteBuffer.allocate(10000);

        // being HTTP 1.0, the server will just close the connection at the end
        while (tlsChannel.read(res) != -1) ;
        res.flip();
        System.out.println(utf8.decode(res).toString());
      }
    }
  }
}