package com.eu.habbo.networking.gameserver.crypto;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.networking.gameserver.GameServerAttributes;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WsHandshakeHandlerCompatibilityTest {

    private static Field configField;
    private static Field runtimeField;
    private static Object originalConfig;
    private static Object originalRuntime;

    @BeforeAll
    static void configureUnsignedHandshake() throws Exception {
        configField = Emulator.class.getDeclaredField("config");
        runtimeField = Emulator.class.getDeclaredField("polarisRuntime");
        configField.setAccessible(true);
        runtimeField.setAccessible(true);
        originalConfig = configField.get(null);
        originalRuntime = runtimeField.get(null);

        ConfigurationManager configuration =
                mock(ConfigurationManager.class);
        when(configuration.getBoolean(
                "crypto.ws.signing.enabled",
                false)).thenReturn(false);
        runtimeField.set(null, null);
        configField.set(null, configuration);
    }

    @AfterAll
    static void restoreConfiguration() throws Exception {
        configField.set(null, originalConfig);
        runtimeField.set(null, originalRuntime);
    }

    @Test
    void serverHelloKeepsTheEstablishedUnsignedWireLayout()
            throws Exception {
        EmbeddedChannel channel = handshakeChannel();
        try {
            fireHandshakeComplete(channel);

            ByteBuf hello = channel.readOutbound();
            assertNotNull(hello);
            assertEquals(
                    WsSessionCrypto.HANDSHAKE_MAGIC,
                    hello.readInt());
            assertEquals(
                    WsSessionCrypto.TYPE_SERVER_HELLO,
                    hello.readByte());

            int publicKeyLength = hello.readUnsignedShort();
            assertTrue(publicKeyLength > 0);
            byte[] publicKey = new byte[publicKeyLength];
            hello.readBytes(publicKey);
            assertNotNull(
                    WsSessionCrypto.decodePublicKeySpki(publicKey));
            assertFalse(hello.isReadable());
            hello.release();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void validClientHelloInstallsTheSameSessionKeyAndAesHandlers()
            throws Exception {
        EmbeddedChannel channel = handshakeChannel();
        try {
            fireHandshakeComplete(channel);
            ByteBuf hello = channel.readOutbound();
            hello.skipBytes(5);
            int serverKeyLength = hello.readUnsignedShort();
            byte[] serverSpki = new byte[serverKeyLength];
            hello.readBytes(serverSpki);
            hello.release();

            PublicKey serverPublic =
                    WsSessionCrypto.decodePublicKeySpki(serverSpki);
            KeyPair clientKeyPair =
                    WsSessionCrypto.generateEphemeralKeyPair();
            byte[] clientSpki =
                    WsSessionCrypto.encodePublicKeySpki(
                            clientKeyPair.getPublic());
            byte[] expectedSessionKey =
                    WsSessionCrypto.deriveAesKey(
                            WsSessionCrypto.deriveSharedSecret(
                                    clientKeyPair.getPrivate(),
                                    serverPublic));

            ByteBuf clientHello =
                    Unpooled.buffer(7 + clientSpki.length);
            clientHello.writeInt(
                    WsSessionCrypto.HANDSHAKE_MAGIC);
            clientHello.writeByte(
                    WsSessionCrypto.TYPE_CLIENT_HELLO);
            clientHello.writeShort(clientSpki.length);
            clientHello.writeBytes(clientSpki);

            assertFalse(channel.writeInbound(clientHello));
            assertEquals(0, clientHello.refCnt());
            assertArrayEquals(
                    expectedSessionKey,
                    channel.attr(
                            GameServerAttributes.WS_AES_KEY).get());
            assertNull(
                    channel.pipeline().get(
                            WsHandshakeHandler.HANDLER_NAME));
            assertNotNull(channel.pipeline().get("wsAesDecoder"));
            assertNotNull(channel.pipeline().get("wsAesEncoder"));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void malformedClientHelloIsReleasedAndClosesTheChannel() {
        EmbeddedChannel channel = handshakeChannel();
        try {
            ByteBuf malformed =
                    Unpooled.buffer(7)
                            .writeInt(0x01020304)
                            .writeByte(
                                    WsSessionCrypto.TYPE_CLIENT_HELLO)
                            .writeShort(0);

            assertFalse(channel.writeInbound(malformed));
            assertEquals(0, malformed.refCnt());
            assertFalse(channel.isOpen());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static void fireHandshakeComplete(
            EmbeddedChannel channel) {
        channel.pipeline().fireUserEventTriggered(
                new WebSocketServerProtocolHandler.HandshakeComplete(
                        "/",
                        EmptyHttpHeaders.INSTANCE,
                        null));
    }

    private static EmbeddedChannel handshakeChannel() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(
                WsHandshakeHandler.HANDLER_NAME,
                new WsHandshakeHandler());
        return channel;
    }
}
