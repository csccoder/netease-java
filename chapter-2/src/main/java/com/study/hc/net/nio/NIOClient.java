package com.study.hc.net.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * 基于NIO 实现 tcp 客户端
 *
 * @author chensicong
 * @date 2021-04-10 00:43
 **/
public class NIOClient {

    public static void main(String[] args) throws IOException {
        // 创建网络客户端
        SocketChannel socketChannel = SocketChannel.open();
        // 设置为非阻塞模式
        socketChannel.configureBlocking(false);
        // 请求与服务端简建立连接
        socketChannel.connect(new InetSocketAddress("localhost", 8089));
        while (!socketChannel.finishConnect()) {
            //没连接上，则一直等待
            Thread.yield();
        }

        Scanner scanner = new Scanner(System.in);
        System.out.println("请输入：");
        // 发送内容
        String msg = scanner.nextLine();
        ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
        while (buffer.hasRemaining()) {
            socketChannel.write(buffer);
        }

        // 读取响应
        System.out.println("收到服务端响应:");
        ByteBuffer requestBuffer = ByteBuffer.allocate(1024);
        while (socketChannel.isOpen() && socketChannel.read(requestBuffer) != -1) {
            // 长连接情况下,需要手动判断数据有没有读取结束 (此处做一个简单的判断: 超过0字节就认为请求结束了)
            if (requestBuffer.position() > 0) break;
        }
        requestBuffer.flip();
        byte[] content = new byte[requestBuffer.limit()];
        requestBuffer.get(content);
        System.out.println(new String(content));

        scanner.close();
        socketChannel.close();
    }
}
