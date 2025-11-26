package yield.util;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

/**
 * Utility class for SSL configuration
 */
public class SslUtils {

    private static boolean configured = false;

    /**
     * Configures the SSL context to trust all certificates
     * This is necessary for some servers with certificate issues
     * This method is idempotent - calling it multiple times has no additional effect
     */
    public static synchronized void configureTrustAllCertificates() {
        if (configured) {
            return; // Already configured
        }

        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            SSLContext.setDefault(sc);

            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

            configured = true;
            System.out.println("SSL certificate validation disabled for all HTTPS connections");
        } catch (Exception e) {
            System.err.println("Warning: Could not configure SSL trust manager: " + e.getMessage());
        }
    }
}

