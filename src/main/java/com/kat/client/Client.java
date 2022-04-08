package com.kat.client;

import com.kat.server.AbstractServerComponent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.LinkedList;

import static java.nio.channels.SelectionKey.*;

public class Client extends AbstractServerComponent {

    private final LinkedList<String> messages;

    private final ChatResponseHandler handler;

    public Client(String id, String hostname, int port, ChatResponseHandler handler) throws IOException {
        super(id, port);
        this.handler = handler;
        this.messages = new LinkedList<>();
    }

    @Override
    protected AbstractSelectableChannel channel(InetSocketAddress address) throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.connect(address);
        return channel;
    }

    @Override
    protected Selector selector() throws IOException {
        Selector selector = Selector.open();
        abstractSelectableChannel.register(selector, OP_CONNECT);
        return selector;
    }

    @Override
    protected void handleIncomingData(SelectionKey sender, byte[] data) {
        for (String message : new String(data).split(MESSAGE_DELIMITER)) {
            handler.onMessage(message);
        }
    }

    @Override
    protected void write(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        while (!messages.isEmpty()) {
            String message = messages.poll();
            channel.write(ByteBuffer.wrap(message.getBytes()));
        }
        key.interestOps(OP_READ);
    }

    public void sendMessage(String message) {
        messages.add(message + MESSAGE_DELIMITER);
        SelectionKey key = abstractSelectableChannel.keyFor(selector);
        key.interestOps(OP_WRITE);
    }
}
