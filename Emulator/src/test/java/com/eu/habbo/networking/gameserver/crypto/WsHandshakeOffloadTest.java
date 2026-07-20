package com.eu.habbo.networking.gameserver.crypto;

import com.eu.habbo.networking.gameserver.GameServerAttributes;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WsHandshakeOffloadTest {

    @Test
    void keyGenerationLeavesTheCallingIoThread()
            throws Exception {
        LinkedBlockingQueue<Runnable> cryptoTasks =
                new LinkedBlockingQueue<>();
        WsHandshakeHandler handler =
                new WsHandshakeHandler(
                        cryptoTasks::add,
                        false);
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(
                WsHandshakeHandler.HANDLER_NAME,
                handler);
        try {
            channel.pipeline().fireUserEventTriggered(
                    new WebSocketServerProtocolHandler
                            .HandshakeComplete(
                            "/",
                            EmptyHttpHeaders.INSTANCE,
                            null));

            assertNull(channel.readOutbound());
            assertEquals(1, cryptoTasks.size());

            runCryptoTask(cryptoTasks);
            channel.runPendingTasks();

            ByteBuf serverHello = channel.readOutbound();
            assertNotNull(serverHello);
            serverHello.release();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void keyAgreementLeavesTheCallingIoThread()
            throws Exception {
        LinkedBlockingQueue<Runnable> cryptoTasks =
                new LinkedBlockingQueue<>();
        WsHandshakeHandler handler =
                new WsHandshakeHandler(
                        cryptoTasks::add,
                        false);
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(
                WsHandshakeHandler.HANDLER_NAME,
                handler);
        try {
            channel.pipeline().fireUserEventTriggered(
                    new WebSocketServerProtocolHandler
                            .HandshakeComplete(
                            "/",
                            EmptyHttpHeaders.INSTANCE,
                            null));
            runCryptoTask(cryptoTasks);
            channel.runPendingTasks();
            ByteBuf serverHello = channel.readOutbound();
            assertNotNull(serverHello);
            serverHello.release();

            KeyPair clientKeyPair =
                    WsSessionCrypto.generateEphemeralKeyPair();
            byte[] clientSpki =
                    WsSessionCrypto.encodePublicKeySpki(
                            clientKeyPair.getPublic());
            ByteBuf clientHello =
                    Unpooled.buffer(7 + clientSpki.length)
                            .writeInt(
                                    WsSessionCrypto
                                            .HANDSHAKE_MAGIC)
                            .writeByte(
                                    WsSessionCrypto
                                            .TYPE_CLIENT_HELLO)
                            .writeShort(clientSpki.length)
                            .writeBytes(clientSpki);

            assertFalse(channel.writeInbound(clientHello));
            assertNull(channel.attr(
                    GameServerAttributes.WS_AES_KEY).get());
            assertEquals(1, cryptoTasks.size());

            runCryptoTask(cryptoTasks);
            channel.runPendingTasks();

            assertNotNull(channel.attr(
                    GameServerAttributes.WS_AES_KEY).get());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void framesArrivingDuringKeyAgreementDoNotCloseTheConnection()
            throws Exception {
        LinkedBlockingQueue<Runnable> cryptoTasks =
                new LinkedBlockingQueue<>();
        WsHandshakeHandler handler =
                new WsHandshakeHandler(
                        cryptoTasks::add,
                        false);
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(
                WsHandshakeHandler.HANDLER_NAME,
                handler);
        try {
            channel.pipeline().fireUserEventTriggered(
                    new WebSocketServerProtocolHandler
                            .HandshakeComplete(
                            "/",
                            EmptyHttpHeaders.INSTANCE,
                            null));
            runCryptoTask(cryptoTasks);
            channel.runPendingTasks();
            channel.readOutbound();

            KeyPair clientKeyPair =
                    WsSessionCrypto.generateEphemeralKeyPair();
            byte[] clientSpki =
                    WsSessionCrypto.encodePublicKeySpki(
                            clientKeyPair.getPublic());
            ByteBuf clientHello =
                    Unpooled.buffer(7 + clientSpki.length)
                            .writeInt(
                                    WsSessionCrypto
                                            .HANDSHAKE_MAGIC)
                            .writeByte(
                                    WsSessionCrypto
                                            .TYPE_CLIENT_HELLO)
                            .writeShort(clientSpki.length)
                            .writeBytes(clientSpki);
            assertFalse(channel.writeInbound(clientHello));
            assertEquals(1, cryptoTasks.size());

            // The client derives the key locally and can send an AES frame before
            // the decoder is installed. It must be queued, not parsed as handshake.
            ByteBuf earlyFrame = Unpooled.wrappedBuffer(
                    new byte[]{0, 1, 2, 3, 4, 5, 6, 7});
            channel.writeInbound(earlyFrame);

            assertTrue(channel.isActive());
            assertEquals(1, cryptoTasks.size());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static void runCryptoTask(
            LinkedBlockingQueue<Runnable> cryptoTasks)
            throws InterruptedException {
        Thread worker = new Thread(
                cryptoTasks.remove(),
                "test-ws-crypto-worker");
        worker.start();
        worker.join(TimeUnit.SECONDS.toMillis(1));
        assertFalse(worker.isAlive());
    }
}
