import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class SocksServer {
    private final int port = 1080;

    private final int SOCKS_VERSION = 0x05;

    private final int NO_AUTH_REQUIRED = 0x00;

    private final int NO_ACCEPTABLE_METHODS = 0xFF;

    public void run() throws IOException {
        System.out.println("socks server is running on localhost:" + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            long id = 1;
            while (true) {
                final long requestId = id;
                Socket socket = serverSocket.accept();
                Thread.ofVirtual().start(() -> {
                    try {
                        handleRequest(requestId, socket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                id++;
            }
        }
    }

    private void handleRequest(long requestId, Socket socket) throws IOException {
        try (socket) {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            handleAuthRequest(requestId, in, out);
        }

        // String targetHost = "example.com";
        // int targetPort = 80;

        // try (socket; Socket targetSocket = new Socket(targetHost, targetPort)) {

        // InputStream targetInput = targetSocket.getInputStream();
        // OutputStream targetOutput = targetSocket.getOutputStream();

        // InputStream in = socket.getInputStream();
        // OutputStream out = socket.getOutputStream();

        // Thread t = Thread.ofVirtual().start(() -> {
        // try {
        // pump(targetInput, out);
        // socket.shutdownOutput();
        // } catch (IOException e) {
        // e.printStackTrace();
        // }
        // });

        // pump(in, targetOutput);
        // targetSocket.shutdownOutput();
        // try {
        // t.join();
        // } catch (InterruptedException e) {
        // e.printStackTrace();
        // }
        // }
    }

    private void handleAuthRequest(long requestId, InputStream in, OutputStream out) throws IOException {
        byte[] authMethodRequestHeader = in.readNBytes(2);
        if (authMethodRequestHeader.length < 2) {
            throw new IllegalArgumentException("authentication request size must be greater than or equal to 2");
        }

        dump(requestId, "authMethodHeader", authMethodRequestHeader, 2);

        if (authMethodRequestHeader[0] != SOCKS_VERSION) {
            throw new IllegalArgumentException("socks version is not suppoerted");
        }

        int methodsNum = authMethodRequestHeader[1] & 0xFF;
        if (methodsNum == 0) {
            out.write(bytes(SOCKS_VERSION, NO_ACCEPTABLE_METHODS));
            return;
        }

        byte[] authMethods = new byte[methodsNum];
        in.readNBytes(authMethods, 0, methodsNum);

        dump(requestId, "authMethods", authMethods, methodsNum);
        for (int i = 0; i < authMethods.length; i++) {
            if ((authMethods[i] & 0xFF) == NO_AUTH_REQUIRED) {
                out.write(bytes(SOCKS_VERSION, NO_AUTH_REQUIRED));
                return;
            }
        }

        out.write(bytes(SOCKS_VERSION, NO_ACCEPTABLE_METHODS));
        return;
    }

    private void pump(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
    }

    private void dump(long id, String label, byte[] buf, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02x ", buf[i] & 0xFF));
        }

        print(id, "%s: %s", label, sb);
    }

    private void print(long id, String format, Object... args) {
        String body = String.format(format, args);
        System.out.println("[#" + id + "] " + body);
    }

    private byte[] bytes(int... values) {
        byte[] buf = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            buf[i] = (byte) values[i];
        }

        return buf;
    }
}
