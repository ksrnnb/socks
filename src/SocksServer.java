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
                try (Socket socket = serverSocket.accept()) {

                    InputStream in = socket.getInputStream();
                    OutputStream out = socket.getOutputStream();

                    int b;
                    while ((b = in.read()) != -1) {
                        out.write(b);
                    }
                }
            }
        }
    }
}
