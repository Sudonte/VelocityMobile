package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class ConnectionProbeTest {

    private ServerSocket server;

    @After
    public void stopServer() throws IOException {
        if (server != null) server.close();
    }

    /** A one-request-at-a-time HTTP server on localhost: waits delayMs, then answers with the given status and a tiny body. */
    private String serve(int status, long delayMs) throws IOException {
        server = new ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"));
        final ServerSocket listening = server;
        Thread t = new Thread(() -> {
            while (!listening.isClosed()) {
                try (Socket client = listening.accept()) {
                    InputStream in = client.getInputStream();
                    byte[] buf = new byte[2048];
                    in.read(buf); // the request line + headers
                    if (delayMs > 0) Thread.sleep(delayMs);
                    byte[] body = "ok".getBytes();
                    String head = "HTTP/1.1 " + status + " X\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
                    OutputStream out = client.getOutputStream();
                    out.write(head.getBytes());
                    out.write(body);
                    out.flush();
                } catch (Exception ignored) {
                    // client gave up (timeout test) or the server was closed
                }
            }
        });
        t.setDaemon(true);
        t.start();
        return "http://127.0.0.1:" + listening.getLocalPort() + "/up";
    }

    // ---- classification: the thresholds ----

    @Test
    public void unreachableIsOffline() {
        assertEquals(ConnectionProbe.Quality.OFFLINE, ConnectionProbe.classify(false, 50, 80, 50_000));
    }

    @Test
    public void quickAnswerIsFast() {
        assertEquals(ConnectionProbe.Quality.FAST, ConnectionProbe.classify(true, 120, 300, 20_000));
        assertEquals(ConnectionProbe.Quality.FAST, ConnectionProbe.classify(true, 1_200, 2_500, 0));
    }

    @Test
    public void highLatencyOrLongDownloadIsSlow() {
        assertEquals(ConnectionProbe.Quality.SLOW, ConnectionProbe.classify(true, 1_201, 1_300, 20_000));
        assertEquals(ConnectionProbe.Quality.SLOW, ConnectionProbe.classify(true, 300, 2_501, 20_000));
    }

    @Test
    public void theOsEstimateOnlyTipsAMiddlingTimingNeverDecidesAlone() {
        assertEquals("a bad estimate with a good timing stays fast", ConnectionProbe.Quality.FAST, ConnectionProbe.classify(true, 100, 400, 100));
        assertEquals("a bad estimate plus a mediocre timing is slow", ConnectionProbe.Quality.SLOW, ConnectionProbe.classify(true, 300, 1_500, 100));
        assertEquals("an unknown estimate (0) is ignored", ConnectionProbe.Quality.FAST, ConnectionProbe.classify(true, 300, 1_500, 0));
    }

    // ---- a real request ----

    @Test
    public void aHealthyServerIsFast() throws IOException {
        ConnectionProbe.Result r = ConnectionProbe.run(serve(200, 0), 0, true);
        assertEquals(ConnectionProbe.Quality.FAST, r.quality);
        assertTrue(r.totalMs >= r.latencyMs);
    }

    @Test
    public void aServerThatTakesLongToAnswerIsSlow() throws IOException {
        ConnectionProbe.Result r = ConnectionProbe.run(serve(200, 1_400), 0, true);
        assertEquals(ConnectionProbe.Quality.SLOW, r.quality);
        assertTrue(r.latencyMs >= 1_400);
    }

    @Test
    public void anErrorStatusIsNotAWorkingBackend() throws IOException {
        assertEquals(ConnectionProbe.Quality.OFFLINE, ConnectionProbe.run(serve(503, 0), 0, true).quality);
    }

    @Test
    public void nothingListeningIsOffline() throws IOException {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        assertEquals(ConnectionProbe.Quality.OFFLINE,
                ConnectionProbe.run("http://127.0.0.1:" + closedPort + "/up", 0, true).quality);
    }

    @Test
    public void aTimeoutWithoutAValidatedConnectionIsOfflineWithoutTheLongRetry() throws IOException {
        long start = System.currentTimeMillis();
        ConnectionProbe.Result r = ConnectionProbe.run(serve(200, 6_000), 0, false);
        assertEquals(ConnectionProbe.Quality.OFFLINE, r.quality);
        assertTrue("only the quick 4 s try ran", System.currentTimeMillis() - start < 8_000);
    }
}
