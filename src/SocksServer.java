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

    private final int CMD_CONNECT = 0x01;

    private final int REPLY_SUCCEEDED = 0x00;
    private final int REPLY_CONNECTION_REFUSED = 0x05;
    private final int REPLY_COMMAND_NOT_SUPPORTED = 0x07;
    private final int REPLY_ADDRESS_TYPE_NOT_SUPPORTED = 0x08;

    private final int RESERVED = 0x00;

    private final int ADDRESS_TYPE_IPV4 = 0x01;

    private record TargetAddress(String host, int port, int addressType, byte[] addressBytes, byte[] portBytes) {
    }

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

            TargetAddress address = parseTargetAddress(requestId, in, out);

            Socket targetSocket;
            try {
                targetSocket = new Socket(address.host, address.port);
            } catch (IOException e) {
                out.write(bytes(SOCKS_VERSION, REPLY_CONNECTION_REFUSED, RESERVED, ADDRESS_TYPE_IPV4,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00));
                return;
            }

            try (targetSocket) {
                respondSocksRequest(requestId, out, address);

                InputStream targetInput = targetSocket.getInputStream();
                OutputStream targetOutput = targetSocket.getOutputStream();

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
            throw new IllegalArgumentException("method number is invalid");
        }

        byte[] authMethods = in.readNBytes(methodsNum);
        if (authMethods.length != methodsNum) {
            throw new IllegalArgumentException("invalid auth methods");
        }

        dump(requestId, "authMethods", authMethods, methodsNum);
        for (int i = 0; i < authMethods.length; i++) {
            if ((authMethods[i] & 0xFF) == NO_AUTH_REQUIRED) {
                out.write(bytes(SOCKS_VERSION, NO_AUTH_REQUIRED));
                return;
            }
        }

        out.write(bytes(SOCKS_VERSION, NO_ACCEPTABLE_METHODS));
        throw new IllegalArgumentException("no acceptable methods");
    }

    private TargetAddress parseTargetAddress(long requestId, InputStream in, OutputStream out) throws IOException {
        byte[] socksRequestHeader = in.readNBytes(4);
        if (socksRequestHeader.length < 4) {
            throw new IllegalArgumentException("socks request size is too small");
        }

        dump(requestId, "SocksRequestHeader", socksRequestHeader, 4);

        if (socksRequestHeader[0] != SOCKS_VERSION) {
            throw new IllegalArgumentException("socks version is not suppoerted");
        }

        if (socksRequestHeader[1] != CMD_CONNECT) {
            out.write(
                    bytes(SOCKS_VERSION, REPLY_COMMAND_NOT_SUPPORTED, ADDRESS_TYPE_IPV4,
                            0x00, 0x00, 0x00, 0x00, 0x00, 0x00));
            throw new IllegalArgumentException("command is not suppoerted");

        }

        return switch (socksRequestHeader[3]) {
            case ADDRESS_TYPE_IPV4 -> parseIPv4TargetAddress(requestId, in, out);
            default -> {
                out.write(
                        bytes(SOCKS_VERSION, REPLY_ADDRESS_TYPE_NOT_SUPPORTED, ADDRESS_TYPE_IPV4,
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00));
                throw new IllegalArgumentException("address type is not suppoerted");
            }
        };
    }

    private TargetAddress parseIPv4TargetAddress(long requestId, InputStream in, OutputStream out) throws IOException {
        byte[] hostBytes = in.readNBytes(4);
        if (hostBytes.length < 4) {
            throw new IllegalArgumentException("host size is too small");
        }
        byte[] portBytes = in.readNBytes(2);
        if (portBytes.length < 2) {
            throw new IllegalArgumentException("port size is too small");
        }
        dump(requestId, "ipv4 host", hostBytes, 4);
        dump(requestId, "ipv4 port", portBytes, 2);

        String host = String.format("%d.%d.%d.%d", hostBytes[0] & 0xFF, hostBytes[1] & 0xFF,
                hostBytes[2] & 0xFF, hostBytes[3] & 0xFF);
        int port = ((portBytes[0] & 0xFF) << 8) | (portBytes[1] & 0xFF);

        return new TargetAddress(host, port, ADDRESS_TYPE_IPV4, hostBytes, portBytes);
    }

    private void respondSocksRequest(long requestId, OutputStream out, TargetAddress address) throws IOException {
        print(requestId, "CONNECT %s:%d", address.host, address.port);

        out.write(bytes(SOCKS_VERSION, REPLY_SUCCEEDED, RESERVED, address.addressType));
        out.write(address.addressBytes);
        out.write(address.portBytes);
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
