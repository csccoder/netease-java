package com.study.hc.net.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于reactor模式实现网络服务端
 *
 * @author chensicong
 * @date 2021-04-10 00:12
 **/
public class NIOServerV3 {

    /**
     * 业务线程池，负责处理业务逻辑
     */
    private ExecutorService workerThreadPool = Executors.newFixedThreadPool(10);

    private ServerSocketChannel serverSocketChannel;

    // 1.创建多个线程 - acceptor处理Reactor线程  负责捕获Accept事件 （处理网络连接）
    private ReactorThread[] acceptorReactorThreads = new ReactorThread[1];

    //2.创建多个线程 - I/O处理Reactor线程   负责捕获READ、Writes事件 （处理IO操作，读取完数据后交由业务线程池处理)
    private ReactorThread[] ioReactorThreads = new ReactorThread[8];

    // reactor线程，封装了Selector轮询事件逻辑
    abstract class ReactorThread extends Thread {

        Selector selector;

        boolean running = false;

        private ArrayBlockingQueue<Runnable> taskQueue = new ArrayBlockingQueue(100);

        public ReactorThread() throws IOException {
            selector = Selector.open();
        }

        //业务处理方法
        public abstract void handle(SelectableChannel channel) throws Exception;

        @Override
        public void run() {
            while (true) {
                try {
                    // 执行队列中的任务
                    Runnable task;
                    while ((task = taskQueue.poll()) != null) {
                        task.run();
                    }
                    // 非阻塞的方式轮询channel上是否有感兴趣的事件就绪
                    int eventNums = selector.select(1000);
                    if (eventNums > 0) {
                        // 获取到就绪事件的集合
                        Set<SelectionKey> selectionKeys = selector.selectedKeys();
                        Iterator<SelectionKey> iterator = selectionKeys.iterator();
                        while (iterator.hasNext()) {
                            SelectionKey key = iterator.next();
                            iterator.remove();

                            try {
                                // 判断事件是否感兴趣
                                if (key.isAcceptable() || key.isReadable()) {
                                    // 获取SelectionKey中附加的channel
                                    SelectableChannel selectableChannel = (SelectableChannel) key.attachment();
                                    // 将channel设置成非阻塞模式
                                    selectableChannel.configureBlocking(false);
                                    // 将channel交给业务方法处理
                                    handle(selectableChannel);
                                    if (!selectableChannel.isOpen()) {
                                        key.cancel();
                                    }
                                }
                            } catch (Exception e) {
                                key.cancel();
                            }
                        }
                    }

                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
        }

        public void doStart() {
            if (!running) {
                running = true;
                start();
            }
        }

        public SelectionKey register(SelectableChannel selectableChannel) throws Exception {
            // 为什么register要以任务提交的形式，让reactor线程去处理？
            // 因为线程在执行channel注册到selector的过程中，会和调用selector.select()方法的线程争用同一把锁
            // 而select()方法实在eventLoop中通过while循环调用的，争抢的可能性很高，为了让register能更快的执行，就放到同一个线程来处理
            FutureTask<SelectionKey> futureTask = new FutureTask(() -> selectableChannel.register(selector, 0, selectableChannel));
            taskQueue.put(futureTask);
            return futureTask.get();
        }
    }

    private void bind() throws IOException {
        //  1、 正式绑定端口，对外服务
        this.serverSocketChannel.bind(new InetSocketAddress(8089));
        System.out.println("启动完成，端口8080");
    }

    private void initAndRegister() {
        try {
            //创建ServerSocketChannel服务端socket通道
            this.serverSocketChannel = ServerSocketChannel.open();
            // 设置为非阻塞模式
            this.serverSocketChannel.configureBlocking(false);

            //将ServerSocketChannel绑定到acceptor线程的selector上
            Random random = new Random();
            int i = random.nextInt(this.acceptorReactorThreads.length);
            this.acceptorReactorThreads[i].doStart();
            SelectionKey selectionKey = this.acceptorReactorThreads[i].register(serverSocketChannel);
            selectionKey.interestOps(SelectionKey.OP_ACCEPT);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void newGroup() throws Exception {
        //创建acceptor线程，负责处理客户端的连接
        for (int i = 0; i < acceptorReactorThreads.length; i++) {
            this.acceptorReactorThreads[i] = new ReactorThread() {
                AtomicInteger incr = new AtomicInteger();

                @Override
                public void handle(SelectableChannel channel) throws Exception {
                    // 只做socketChannel分发，不做具体的数据读取
                    ServerSocketChannel server = (ServerSocketChannel) channel;
                    SocketChannel socketChannel = server.accept();
                    // 将socketChannel设置为非阻塞模式
                    socketChannel.configureBlocking(false);
                    // 收到客户端连接建立通知后，分发给I/O线程继续去读取数据
                    ReactorThread workEventLoop = ioReactorThreads[incr.getAndIncrement()];
                    workEventLoop.doStart();
                    SelectionKey selectionKey = workEventLoop.register(socketChannel);
                    selectionKey.interestOps(SelectionKey.OP_READ);
                    System.out.println(String.format("%s 接收到新连接:%s", Thread.currentThread(), socketChannel.getRemoteAddress()));
                }
            };
        }

        // 创建IO线程，负责处理客户端连接以后socketchannel的读写
        for (int i = 0; i < ioReactorThreads.length; i++) {
            this.ioReactorThreads[i] = new ReactorThread() {
                @Override
                public void handle(SelectableChannel channel) {
                    try {
                        SocketChannel socketChannel = (SocketChannel) channel;
                        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                        //读取channel中的数据
                        while (channel.isOpen() && socketChannel.read(byteBuffer) != -1) {
                            // 长连接情况下,需要手动判断数据有没有读取结束 (此处做一个简单的判断: 超过0字节就认为请求结束了)
                            if (byteBuffer.position() > 0) {
                                break;
                            }
                        }
                        //如果缓冲区为空，则不执行后面的逻辑
                        if (byteBuffer.position() == 0) {
                            return;
                        }
                        //缓冲区切换为读模式
                        byteBuffer.flip();
                        byte[] content = new byte[byteBuffer.limit()];
                        byteBuffer.get(content);
                        System.out.println(String.format("%s 接收到数据：%s", Thread.currentThread(), new String(content)));

                        // TODO 业务操作 数据库、接口...
                        workerThreadPool.submit(() -> {
                        });

                        //响应客户端
                        String response = "HTTP/1.1 200 OK\r\n" +
                                "Content-Length: 11\r\n\r\n" +
                                "Hello World";
                        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
                        while (buffer.hasRemaining()) {
                            socketChannel.write(buffer);
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            };
        }
    }

    public static void main(String[] args) throws Exception {
        NIOServerV3 nioServerV3 = new NIOServerV3();
        // 创建main、sub线程组
        nioServerV3.newGroup();
        // 创建 ServerSocketChannel，并将它注册到mainAcceptor线程的selector上
        nioServerV3.initAndRegister();
        // 为ServerSocketChannel绑定端口
        nioServerV3.bind();
    }
}
