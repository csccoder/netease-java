package com.study.hc.net.nio;

import java.nio.ByteBuffer;

/**
 * {@link java.nio.ByteBuffer#allocateDirect(int)} 堆外缓存
 *
 * @author chensicong
 * @date 2021-04-09 23:24
 **/
public class DirectBufferDemo {

    public static void main(String[] args) {
        // 构建一个byte字节缓冲区 容量为4
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4);
        // 默认为写模式，查看3个重要指标
        System.out.println(String.format("初始化：capacity容量：%s，position位置：%s，limit限制：%s", byteBuffer.capacity(),
                byteBuffer.position(), byteBuffer.limit()));
        // 写入3字节数据
        byteBuffer.put((byte) 1);
        byteBuffer.put((byte) 2);
        byteBuffer.put((byte) 3);
        // 再次查看指标
        System.out.println(String.format("写入3字节数据后：capacity容量：%s，position位置：%s，limit限制：%s", byteBuffer.capacity(),
                byteBuffer.position(), byteBuffer.limit()));

        //转化为读模式(不调用flip方法，也可以读取数据，但是position记录读取的位置不对)
        System.out.println("######开始读取");
        byteBuffer.flip();

        byte a = byteBuffer.get();
        System.out.println(a);
        byte b = byteBuffer.get();
        System.out.println(b);
        // 再次查看指标
        System.out.println(String.format("读取2字节数据后：capacity容量：%s，position位置：%s，limit限制：%s", byteBuffer.capacity(),
                byteBuffer.position(), byteBuffer.limit()));

        // 继续写入3字节，此时读模式下position位置：2，limit限制：3，继续写入只能覆盖写入2条数据
        // clear()方法清除整个缓冲区，compact()方法清除已读数据，把未读数据依次从缓存区首位开始排。转化为写入模式
        byteBuffer.compact();
        byteBuffer.put((byte) 4);
        byteBuffer.put((byte) 5);
        byteBuffer.put((byte) 6);
        // 再次查看指标
        System.out.println(String.format("最终的情况：capacity容量：%s，position位置：%s，limit限制：%s", byteBuffer.capacity(),
                byteBuffer.position(), byteBuffer.limit()));
        //rewind() 重置position=0，并且清除mark标记
        //byteBuffer.rewind();
        //mark() 标记position的位置  mark = position
        //reset() 将position重置为mark()标记的位置
        //byteBuffer.reset();
    }
}
