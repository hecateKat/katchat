package com.kat.server;

import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Iterator;

import static com.kat.utils.Closer.close;
import static java.lang.System.arraycopy;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

@Getter
@Setter
public abstract class AbstractServerComponent implements Runnable, ChatConfig {

    private final String id;
    private final ByteBuffer byteBuffer;
    private final Selector selector;
    private final AbstractSelectableChannel abstractSelectableChannel;
    private final InetSocketAddress inetSocketAddress;
    private volatile boolean running;

    public AbstractServerComponent(String id, int port) throws IOException {
        this(id, new InetSocketAddress(port));
    }

    public AbstractServerComponent(String id, InetSocketAddress address) throws IOException {
        this.id = id;
        this.inetSocketAddress = address;
        this.byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        this.abstractSelectableChannel = channel(address);
        this.selector = selector();
    }


    public synchronized void start(){
        Thread thread = new Thread(this, id);
        running = true;
        thread.start();
    }

    public void stop(){
        running = false;
    }


    @Override
    public void run() {
        while (running) {
            runMainLoop();
        }
        cleanUp();
    }


    protected abstract AbstractSelectableChannel channel(InetSocketAddress address) throws IOException;

    protected abstract Selector selector() throws IOException;

    protected abstract void handleIncomingData(SelectionKey sender, byte[] data) throws IOException;

    protected abstract void write(SelectionKey key) throws IOException;

    private void runMainLoop() {
        try {
            selector.select(1000);

            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();

                if (!key.isValid()) {
                    continue;
                }

                if (key.isConnectable()) {
                    connect(key);
                } else if (key.isAcceptable()) {
                    accept(key);
                } else if (key.isReadable()) {
                    read(key);
                } else if (key.isWritable()) {
                    write(key);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void cleanUp() {
        for (SelectionKey key : selector.keys()) {
            close(key.channel());
        }
        close(selector);
    }

    protected void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, OP_READ);
    }

    protected void connect(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        try {
            channel.finishConnect();
            channel.configureBlocking(false);
            channel.register(selector, OP_WRITE);
        } catch (IOException e) {
            e.printStackTrace();
            key.channel().close();
            key.cancel();
        }
    }

    protected void read(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        byteBuffer.clear();
        int readCount;

        try {
            readCount = channel.read(byteBuffer);
        } catch (IOException e) {
            key.cancel();
            channel.close();
            return;
        }

        if (readCount == -1) {
            // Channel is no longer active - clean up
            key.channel().close();
            key.cancel();
            return;
        }

        byte [] data = new byte[byteBuffer.position()];

        arraycopy(byteBuffer.array(), 0, data, 0, byteBuffer.position());

        handleIncomingData(key, data);
    }
}