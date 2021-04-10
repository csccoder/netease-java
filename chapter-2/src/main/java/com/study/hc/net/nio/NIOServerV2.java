package com.study.hc.net.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 基于非阻塞的写法 一个线程轮询处理所有请求
 *
 * @author chensicong
 * @date 2021-04-10 00:12
 **/
public class NIOServerV2 {

    public static void main(String[] args) throws Exception {
        // 1.创建网络服务端 ServerSocketChannel
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        // 将channel设置为非阻塞模式（channel默认为阻塞模式）
        serverSocketChannel.configureBlocking(false);
        // 监听端口
        serverSocketChannel.socket().bind(new InetSocketAddress(8089));

        // 2. 构建一个Selector选择器,并且将serverSocketChannel注册上去
        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT, serverSocketChannel);

        System.out.println("启动成功");
        while (true) {
            // 不再轮询通道，改为下面轮询事件的方式，select()方法有阻塞效果，直到channel有感兴趣的时间准备就绪时才会返回
            selector.select();
            // 获取事件
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            // 遍历查询结果
            while (iterator.hasNext()) {
                // 被封装的查询结果
                SelectionKey selectionKey = iterator.next();
                iterator.remove();
                //关注Accept 和 Read事件
                if (selectionKey.isAcceptable()) {
                    ServerSocketChannel server = (ServerSocketChannel) selectionKey.attachment();
                    // 获取新连接的客户端通道channel
                    SocketChannel clientSocketChannel = server.accept();
                    clientSocketChannel.configureBlocking(false);// 将客户端channel设置为非阻塞
                    // 将拿到的客户端连接通道客户端,注册到selector上面
                    clientSocketChannel.register(selector, SelectionKey.OP_READ, clientSocketChannel);
                    System.out.println("收到新连接 : " + clientSocketChannel.getRemoteAddress());
                }

                if (selectionKey.isReadable()) {
                    SocketChannel socketChannel = (SocketChannel) selectionKey.attachment();

                    try {
                        // 判断socketChannel是否有数据
                        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                        int i = -2;
                        while (socketChannel.isOpen() && socketChannel.read(byteBuffer) != -1) {
                            // 客户端有发来数据，正常情况下需要判断数据有没有读取结束
                            // 下面为了简化逻辑，只要取到数据就行了
                            if (byteBuffer.position() > 0) {
                                break;
                            }
                        }
                        // byteBuffer没有数据，则不继续后面的处理
                        if (byteBuffer.position() == 0) {
                            continue;
                        }
                        //打印客户端发来的数据
                        byteBuffer.flip();
                        byte[] content = new byte[byteBuffer.limit()];
                        byteBuffer.get(content);
                        System.out.println(new String(content));
                        System.out.println("收到数据,来自：" + socketChannel.getRemoteAddress());
                        // TODO 业务操作 数据库 接口调用等等

                        // 响应结果200
                        String response = "HTTP/1.1 200 OK\r\n" +
                                "Content-Length: 11\r\n\r\n" +
                                "Hello World";
                        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
                        while (buffer.hasRemaining()) {
                            socketChannel.write(buffer);// 非阻塞
                        }
                    } catch (IOException exception) {
                        // e.printStackTrace();
                        selectionKey.cancel(); // 取消事件订阅
                    }
                }
                selector.selectNow();
            }
        }
        // 问题: 此处一个selector监听所有事件,一个线程处理所有请求事件. 会成为瓶颈! 要有多线程的运用
    }
}
