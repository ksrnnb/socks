import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class SocksServer {
    private final int port = 1080;

    public void run() throws IOException {
        System.out.println("socks server is running on localhost:" + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                Thread.ofVirtual().start(() -> {
                    try {
                        handleAccept(socket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        }
    }

    private void handleAccept(Socket socket) throws IOException {
        String targetHost = "example.com";
        int targetPort = 80;

        try (socket; Socket targetSocket = new Socket(targetHost, targetPort)) {

            InputStream targetInput = targetSocket.getInputStream();
            OutputStream targetOutput = targetSocket.getOutputStream();

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            Thread t = Thread.ofVirtual().start(() -> {
                try {
                    pump(targetInput, out);
                    socket.shutdownOutput();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            pump(in, targetOutput);
            targetSocket.shutdownOutput();
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void pump(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
    }
}
