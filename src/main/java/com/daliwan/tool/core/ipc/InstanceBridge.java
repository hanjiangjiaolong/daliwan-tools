package com.daliwan.tool.core.ipc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 主窗口与右键菜单之间的本地通道。
 *
 * <p>资源管理器对多选文件是按「每个文件一条命令」调用的，如果每条命令都自己
 * 转一份，会同时冒出好几个进程（每个都要几百 MB）和好几个结果窗口。
 * 这里让运行中的主窗口在 {@code 127.0.0.1} 上监听一个随机端口，
 * 端口号写在用户目录的 {@code instance.port} 里；后来的调用先试着把文件
 * 递进去，递不进去（程序没开）才自己转。
 *
 * <p>没有任何网络暴露：只绑回环地址，且带一句握手暗号，认不出来就当没人在。
 *
 * <p>回调只用来「借个进程」：收到了就在后台线程里转，不切页面、不激活窗口，
 * 用户看到的仍是程序没开时那张独立结果卡片。
 */
public final class InstanceBridge implements AutoCloseable {

    /**
     * 一次右键菜单请求：要做什么（命令行开关）+ 对哪些文件做。
     *
     * <p>光有文件列表不够：右键菜单现在既有「转 PDF」也有「转 Word / Excel / PPT / 文本」，
     * 转发过去之后必须知道当初点的是哪一项，否则主窗口只能猜。
     */
    public record Request(String flag, List<Path> files) {
    }

    private static final String MAGIC = "DALIWAN-CONV-1";
    private static final Path PORT_FILE = Path.of(
            System.getProperty("user.home"), ".daliwan", "instance.port");

    private final ServerSocket server;
    private final Consumer<Request> sink;
    private final Thread acceptor;
    private volatile boolean closed;

    private InstanceBridge(ServerSocket server, Consumer<Request> sink) {
        this.server = server;
        this.sink = sink;
        this.acceptor = new Thread(this::acceptLoop, "daliwan-instance-bridge");
        this.acceptor.setDaemon(true);
    }

    /** 启动监听；失败时返回 null（右键菜单会退回「自己转」的单进程模式）。 */
    public static InstanceBridge start(Consumer<Request> sink) {
        try {
            ServerSocket server = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
            server.setSoTimeout(800);
            InstanceBridge bridge = new InstanceBridge(server, sink);
            bridge.writePortFile();
            bridge.acceptor.start();
            return bridge;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            server.close();
        } catch (IOException ignored) {
            // 关不上也不影响进程退出
        }
        removePortFile();
    }

    private void acceptLoop() {
        while (!closed) {
            try (Socket socket = server.accept()) {
                handle(socket);
            } catch (java.net.SocketTimeoutException e) {
                // 正常：靠超时轮询 closed 标记
            } catch (IOException e) {
                if (closed) {
                    return;
                }
            }
        }
    }

    private void handle(Socket socket) {
        try {
            socket.setSoTimeout(2000);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String magic = reader.readLine();
            if (!MAGIC.equals(magic)) {
                respond(socket, "ERR");
                return;
            }
            // 第二行是命令行开关（--to-pdf / --to-docx …），决定这批文件要怎么转
            String flag = reader.readLine();
            if (flag == null || flag.isBlank()) {
                respond(socket, "ERR");
                return;
            }
            List<Path> files = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                files.add(Path.of(line));
            }
            respond(socket, "OK");
            if (!files.isEmpty()) {
                // 回调本身可能在任意线程；切到界面线程的事由调用方负责，
                // 这样 core 包不用引用 JavaFX
                sink.accept(new Request(flag, files));
            }
        } catch (Exception ignored) {
            // 单个连接出错不能拖垮监听线程
        }
    }

    private static void respond(Socket socket, String text) {
        try {
            Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            writer.write(text);
            writer.write('\n');
            writer.flush();
        } catch (IOException ignored) {
            // 对方已经断开就算了
        }
    }

    /**
     * 把请求递给已经在运行的窗口。
     *
     * @param flag  命令行开关，如 {@code --to-pdf}、{@code --to-docx}
     * @param files 要处理的文件
     * @return true 表示递到了，调用方应当直接退出
     */
    public static boolean forward(String flag, List<Path> files) {
        if (files == null || files.isEmpty() || flag == null || flag.isBlank()) {
            return false;
        }
        int port = readPort();
        if (port <= 0) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 600);
            socket.setSoTimeout(1500);
            Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            writer.write(MAGIC);
            writer.write('\n');
            writer.write(flag);
            writer.write('\n');
            for (Path file : files) {
                writer.write(file.toAbsolutePath().toString());
                writer.write('\n');
            }
            writer.flush();
            socket.shutdownOutput();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            return "OK".equals(reader.readLine());
        } catch (Exception e) {
            return false;
        }
    }

    private void writePortFile() {
        try {
            Path parent = PORT_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(PORT_FILE,
                    server.getLocalPort() + System.lineSeparator()
                            + ProcessHandle.current().pid() + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // 写不了端口文件只是没法合并请求，主功能照常
        }
    }

    private static int readPort() {
        if (!Files.isRegularFile(PORT_FILE)) {
            return -1;
        }
        try {
            String first = Files.readAllLines(PORT_FILE, StandardCharsets.UTF_8).get(0).trim();
            return Integer.parseInt(first);
        } catch (Exception e) {
            return -1;
        }
    }

    private static void removePortFile() {
        try {
            if (!Files.isRegularFile(PORT_FILE)) {
                return;
            }
            List<String> lines = Files.readAllLines(PORT_FILE, StandardCharsets.UTF_8);
            long pid = ProcessHandle.current().pid();
            if (lines.size() > 1 && String.valueOf(pid).equals(lines.get(1).trim())) {
                Files.deleteIfExists(PORT_FILE);
            }
        } catch (IOException ignored) {
            // 删不掉就留着，下次启动会覆盖
        }
    }
}
