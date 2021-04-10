package com.study.hc.net.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 基于非阻塞的写法 一个线程轮询处理所有请求
 *
 * @author chensicong
 * @date 2021-04-10 00:12
 **/
public class NIOServerV1 {

    /**
     * 已经建立连接的集合
     */
    private static List<SocketChannel> channelList = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        // 创建网络服务端
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        // 将channel设置为非阻塞模式（channel默认为阻塞模式）
        serverSocketChannel.configureBlocking(false);
        // 监听端口
        serverSocketChannel.socket().bind(new InetSocketAddress(8089));
        System.out.println("启动成功");

        while (true) {
            SocketChannel socketChannel = serverSocketChannel.accept();// 获取新tcp连接通道
            // 判断是否有客户端请求连接，socketChannel不为空则表示有
            if (socketChannel != null) {
                System.out.println("接收到新连接：" + socketChannel.getRemoteAddress());
                socketChannel.configureBlocking(false); // 默认是阻塞的,一定要设置为非阻塞
                channelList.add(socketChannel);
            } else {
                Iterator<SocketChannel> iterator = channelList.iterator();
                while (iterator.hasNext()) {
                    socketChannel = iterator.next();

                    // 判断socketChannel是否有数据
                    ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                    if (socketChannel.read(byteBuffer) == 0) {
                        continue;
                    }
                    while (socketChannel.isOpen() && socketChannel.read(byteBuffer) != -1) {
                        // 客户端有发来数据，正常情况下需要判断数据是否全部读取完
                        // 下面为了简化逻辑，只要取到数据就行了
                        if (byteBuffer.position() > 0) {
                            break;
                        }
                    }
                    // byteBuffer没有数据，则不继续后面的处理
                    if (!byteBuffer.hasRemaining()) {
                        continue;
                    }
                    //打印客户端发来的数据
                    byteBuffer.flip();
                    byte[] content = new byte[byteBuffer.limit()];
                    byteBuffer.get(content);
                    System.out.println(new String(content));
                    System.out.println("收到数据,来自：" + socketChannel.getRemoteAddress());

                    // 响应结果200
                    String response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Length: 11\r\n\r\n" +
                            "Hello World";
                    ByteBuffer buffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
                    while (buffer.hasRemaining()) {
                        socketChannel.write(buffer);// 非阻塞
                    }
                    iterator.remove();
                }
            }
        }
        // 用到了非阻塞的API, 在设计上,和BIO可以有很大的不同.继续改进
    }
}
