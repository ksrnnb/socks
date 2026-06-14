import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        SocksServer server = new SocksServer();

        server.run();
    }
}
