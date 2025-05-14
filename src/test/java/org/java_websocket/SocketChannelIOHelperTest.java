package org.java_websocket;

import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.Framedata;
import org.java_websocket.framing.PingFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SocketChannelIOHelperTest {

    private WebSocketImpl socket;
    private RecorderChannel channel;

    @BeforeEach
    void setUp() {
        socket = new WebSocketImpl(getListener(), null, 10);
    }

    @Test
    void coalescingWriteSingleMessageFully() throws IOException {
        socket.outQueue.add(ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6}));
        channel = new RecorderChannel(10);

        boolean finished = SocketChannelIOHelper.coalescingWrite(socket, channel);

        assertTrue(finished);
        assertEquals(1, channel.buffers.size());
        assertEquals(4, channel.capacity);
        assertFalse(socket.writeCoalescingBufferFlipped);
    }

    @Test
    void coalescingWriteSingleMessagePartially() throws IOException {
        socket.outQueue.add(ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6}));
        channel = new RecorderChannel(5);

        boolean finished = SocketChannelIOHelper.coalescingWrite(socket, channel);

        assertFalse(finished);
        assertEquals(1, channel.buffers.size());
        assertEquals(0, channel.capacity);
        assertTrue(socket.writeCoalescingBufferFlipped);
    }

    @Test
    void coalescingWriteMultipleMessagesInBufferFully() throws IOException {
        socket.outQueue.add(ByteBuffer.wrap(new byte[] {1, 2, 3}));
        socket.outQueue.add(ByteBuffer.wrap(new byte[] {4, 5, 6}));
        socket.outQueue.add(ByteBuffer.wrap(new byte[] {7, 8, 9}));
        channel = new RecorderChannel(10);

        boolean finished = SocketChannelIOHelper.coalescingWrite(socket, channel);

        assertTrue(finished);
        assertEquals(1, channel.buffers.size());
        assertEquals(1, channel.capacity);
        assertFalse(socket.writeCoalescingBufferFlipped);
    }

    @Test
    void coalescingWriteMultipleMessagesInBufferPartially() throws IOException {
        socket.outQueue.add(ByteBuffer.wrap(new byte[] {1, 2, 3}));
        socket.outQueue.add(ByteBuffer.wrap(new byte[] {4, 5, 6}));
        socket.outQueue.add(ByteBuffer.wrap(new byte[] {7, 8, 9}));
        channel = new RecorderChannel(8);

        boolean finished = SocketChannelIOHelper.coalescingWrite(socket, channel);

        assertFalse(finished);
        assertEquals(1, channel.buffers.size());
        assertEquals(0, channel.capacity);
        assertTrue(socket.writeCoalescingBufferFlipped);
    }

    @Test
    void coalescingWriteMultipleMessagesExceedingBuffer() throws IOException {
        socket.outQueue.add(ByteBuffer.wrap(new byte[] {1, 2, 3}));
        socket.outQueue.add(ByteBuffer.wrap(new byte[] {4, 5, 6}));
        socket.outQueue.add(ByteBuffer.wrap(new byte[] {7, 8, 9}));
        ByteBuffer lastBuffer = ByteBuffer.wrap(new byte[] {10, 11, 12});
        socket.outQueue.add(lastBuffer);
        channel = new RecorderChannel(10);

        boolean finished = SocketChannelIOHelper.coalescingWrite(socket, channel);

        assertFalse(finished);
        assertEquals(1, channel.buffers.size());
        assertEquals(1, channel.capacity);
        assertFalse(socket.writeCoalescingBufferFlipped);
        assertEquals(1, socket.outQueue.size());
        assertSame(lastBuffer, socket.outQueue.poll());
    }

    @Test
    void coalescingWriteSingleMessageExceedingBufferFully() throws IOException {
        socket.outQueue.add(ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}));
        channel = new RecorderChannel(15);

        boolean finished = SocketChannelIOHelper.coalescingWrite(socket, channel);

        assertTrue(finished);
        assertEquals(1, channel.buffers.size());
        assertEquals(3, channel.capacity);
        assertFalse(socket.writeCoalescingBufferFlipped);
    }

    @Test
    void coalescingWriteSingleMessageExceedingBufferPartially() throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14});
        socket.outQueue.add(buffer);
        channel = new RecorderChannel(11);

        boolean finished = SocketChannelIOHelper.coalescingWrite(socket, channel);

        assertFalse(finished);
        assertEquals(1, channel.buffers.size());
        assertEquals(0, channel.capacity);
        assertEquals(3, buffer.remaining());
        assertEquals(1, socket.outQueue.size());
        assertFalse(socket.writeCoalescingBufferFlipped);
    }

    @Test
    void coalescingWriteFirstMessageExceedingBufferFully() throws IOException {
        socket.outQueue.add(ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}));
        socket.outQueue.add(ByteBuffer.wrap(new byte[] {13, 14, 15}));
        channel = new RecorderChannel(15);

        boolean finished = SocketChannelIOHelper.coalescingWrite(socket, channel);

        assertFalse(finished);
        assertEquals(1, channel.buffers.size());
        assertEquals(3, channel.capacity);
        assertFalse(socket.writeCoalescingBufferFlipped);
    }

    @Test
    void coalescingWriteFirstMessageExceedingBufferPartially() throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14});
        socket.outQueue.add(buffer);
        socket.outQueue.add(ByteBuffer.wrap(new byte[] {15, 16, 17}));
        channel = new RecorderChannel(11);

        boolean finished = SocketChannelIOHelper.coalescingWrite(socket, channel);

        assertFalse(finished);
        assertEquals(1, channel.buffers.size());
        assertEquals(0, channel.capacity);
        assertEquals(3, buffer.remaining());
        assertEquals(2, socket.outQueue.size());
        assertFalse(socket.writeCoalescingBufferFlipped);
    }

    @Test
    void coalescingWriteSecondMessageExceedingBuffer() throws IOException {
        socket.outQueue.add(ByteBuffer.wrap(new byte[] {1, 2, 3, 4}));
        socket.outQueue.add(ByteBuffer.wrap(new byte[] {5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15}));
        channel = new RecorderChannel(20);

        boolean finished = SocketChannelIOHelper.coalescingWrite(socket, channel);

        assertFalse(finished);
        assertEquals(1, channel.buffers.size());
        assertEquals(16, channel.capacity);
        assertFalse(socket.writeCoalescingBufferFlipped);
    }

    @Test
    void coalescingWriteRemainingDataCanBeSentAndNoNew() throws IOException {
        socket.writeCoalescingBuffer.put(new byte[] {1, 2, 3, 4});
        socket.writeCoalescingBuffer.flip();
        socket.writeCoalescingBufferFlipped = true;
        channel = new RecorderChannel(10);

        boolean finished = SocketChannelIOHelper.coalescingWrite(socket, channel);

        assertTrue(finished);
        assertEquals(1, channel.buffers.size());
        assertEquals(6, channel.capacity);
        assertFalse(socket.writeCoalescingBufferFlipped);
    }

    @Test
    void coalescingWriteRemainingDataCanBeSentAndNewFully() throws IOException {
        socket.writeCoalescingBuffer.put(new byte[] {1, 2, 3, 4});
        socket.writeCoalescingBuffer.flip();
        socket.writeCoalescingBufferFlipped = true;
        socket.outQueue.add(ByteBuffer.wrap(new byte[] {5, 6, 7, 8}));
        channel = new RecorderChannel(20);

        boolean finished = SocketChannelIOHelper.coalescingWrite(socket, channel);

        assertTrue(finished);
        assertEquals(2, channel.buffers.size());
        assertEquals(12, channel.capacity);
        assertFalse(socket.writeCoalescingBufferFlipped);
    }

    @Test
    void coalescingWriteRemainingDataCanBeSentAndNewPartially() throws IOException {
        socket.writeCoalescingBuffer.put(new byte[] {1, 2, 3, 4});
        socket.writeCoalescingBuffer.flip();
        socket.writeCoalescingBufferFlipped = true;
        ByteBuffer buffer = ByteBuffer.wrap(new byte[]{5, 6, 7, 8, 9, 10, 11, 12});
        socket.outQueue.add(buffer);
        channel = new RecorderChannel(10);

        boolean finished = SocketChannelIOHelper.coalescingWrite(socket, channel);

        assertFalse(finished);
        assertEquals(2, channel.buffers.size());
        assertEquals(0, channel.capacity);
        assertEquals(6, socket.writeCoalescingBuffer.position());
        assertTrue(socket.writeCoalescingBufferFlipped);
    }

    @Test
    void coalescingWriteRemainingDataCannotBeSent() throws IOException {
        socket.writeCoalescingBuffer.put(new byte[] {1, 2, 3, 4, 5, 6});
        socket.writeCoalescingBuffer.flip();
        socket.writeCoalescingBufferFlipped = true;
        channel = new RecorderChannel(5);

        boolean finished = SocketChannelIOHelper.coalescingWrite(socket, channel);

        assertFalse(finished);
        assertEquals(1, channel.buffers.size());
        assertEquals(0, channel.capacity);
        assertEquals(5, socket.writeCoalescingBuffer.position());
        assertTrue(socket.writeCoalescingBufferFlipped);
    }

    class RecorderChannel implements WritableByteChannel {

        final List<ByteBuffer> buffers = new ArrayList<>();
        int capacity;

        RecorderChannel(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public int write(ByteBuffer buffer) {
            buffers.add(buffer);
            if (capacity >= buffer.remaining()) {
                int written = buffer.remaining();
                buffer.position(buffer.position() + written);
                capacity -= written;
                return written;
            } else {
                int written = capacity;
                buffer.position(buffer.position() + written);
                capacity = 0;
                return written;
            }
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void close() throws IOException {
        }
    }


    private static WebSocketListener getListener() {
        return new WebSocketListener() {
            @Override
            public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket conn, Draft draft, ClientHandshake request) throws InvalidDataException {
                return null;
            }

            @Override
            public void onWebsocketHandshakeReceivedAsClient(WebSocket conn, ClientHandshake request, ServerHandshake response) throws InvalidDataException {

            }

            @Override
            public void onWebsocketHandshakeSentAsClient(WebSocket conn, ClientHandshake request) throws InvalidDataException {

            }

            @Override
            public void onWebsocketMessage(WebSocket conn, String message) {

            }

            @Override
            public void onWebsocketMessage(WebSocket conn, ByteBuffer blob) {

            }

            @Override
            public void onWebsocketOpen(WebSocket conn, Handshakedata d) {

            }

            @Override
            public void onWebsocketClose(WebSocket ws, int code, String reason, boolean remote) {

            }

            @Override
            public void onWebsocketClosing(WebSocket ws, int code, String reason, boolean remote) {

            }

            @Override
            public void onWebsocketCloseInitiated(WebSocket ws, int code, String reason) {

            }

            @Override
            public void onWebsocketError(WebSocket conn, Exception ex) {

            }

            @Override
            public void onWebsocketPing(WebSocket conn, Framedata f) {

            }

            @Override
            public PingFrame onPreparePing(WebSocket conn) {
                return null;
            }

            @Override
            public void onWebsocketPong(WebSocket conn, Framedata f) {

            }

            @Override
            public void onWriteDemand(WebSocket conn) {

            }

            @Override
            public InetSocketAddress getLocalSocketAddress(WebSocket conn) {
                return null;
            }

            @Override
            public InetSocketAddress getRemoteSocketAddress(WebSocket conn) {
                return null;
            }
        };
    }
}