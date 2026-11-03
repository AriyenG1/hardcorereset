import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.client.multiplayer.RegistryDataCollector;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.*;
import net.minecraft.network.protocol.login.*;
import net.minecraft.network.protocol.configuration.*;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.server.packs.resources.ResourceProvider;

/** Disposable offline-mode protocol client. Uses Minecraft's own 26.3 codecs, no reconnect logic. */
public final class ProtocolSmokeClient implements InvocationHandler {
    private final String name;
    private Connection connection;
    private final RegistryDataCollector registryData = new RegistryDataCollector();
    private volatile boolean failed;
    private ProtocolSmokeClient(String name) { this.name = name; }
    private void log(String text) { System.out.println("SMOKE " + name + " " + text); System.out.flush(); }
    @SuppressWarnings("unchecked")
    private <T> T listener(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, this);
    }
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        Object packet = args == null || args.length == 0 ? null : args[0];
        switch (methodName) {
            case "isAcceptingMessages": return true;
            case "onDisconnect": failed = true; log("DISCONNECTED " + packet); return null;
            case "handleDisconnect": case "handleLoginDisconnect":
                failed = true; log("DISCONNECT_PACKET " + packet); return null;
            case "handleCompression":
                connection.setupCompression(((ClientboundLoginCompressionPacket) packet).getCompressionThreshold(), false); return null;
            case "handleCustomQuery":
                connection.send(new ServerboundCustomQueryAnswerPacket(((ClientboundCustomQueryPacket) packet).transactionId(), null)); return null;
            case "handleLoginFinished":
                connection.setupInboundProtocol(ConfigurationProtocols.CLIENTBOUND, listener(ClientConfigurationPacketListener.class));
                connection.send(ServerboundLoginAcknowledgedPacket.INSTANCE);
                connection.setupOutboundProtocol(ConfigurationProtocols.SERVERBOUND);
                connection.send(new ServerboundClientInformationPacket(ClientInformation.createDefault()));
                log("LOGIN_AUTHENTICATED"); return null;
            case "handleSelectKnownPacks":
                connection.send(new ServerboundSelectKnownPacks(List.of())); return null;
            case "handleRegistryData":
                var data = (ClientboundRegistryDataPacket) packet;
                registryData.appendContents(data.registry(), data.entries()); return null;
            case "handleUpdateTags":
                registryData.appendTags(((ClientboundUpdateTagsPacket) packet).tags()); return null;
            case "handleConfigurationFinished":
                RegistryAccess.Frozen registries = registryData.collectGameRegistries(ResourceProvider.EMPTY,
                        ClientRegistryLayer.createRegistryAccess().compositeAccess(), false);
                connection.setupInboundProtocol(GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries)),
                        listener(ClientGamePacketListener.class));
                connection.send(ServerboundFinishConfigurationPacket.INSTANCE);
                connection.setupOutboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries),
                        new GameProtocols.Context() {
                            public boolean hasInfiniteMaterials() { return true; }
                            public boolean canUseCommandBlocks() { return true; }
                        }));
                log("CONFIGURED"); return null;
            case "handleKeepAlive":
                connection.send(new ServerboundKeepAlivePacket(((ClientboundKeepAlivePacket) packet).getId())); return null;
            case "handlePing":
                connection.send(new ServerboundPongPacket(((ClientboundPingPacket) packet).getId())); return null;
            case "handleLogin":
                var login = (ClientboundLoginPacket) packet;
                log("PLAY_LOGIN " + login.commonPlayerSpawnInfo().dimension().identifier()); return null;
            case "handleRespawn":
                var respawn = (ClientboundRespawnPacket) packet;
                log("RESPAWN " + respawn.commonPlayerSpawnInfo().dimension().identifier()
                        + " MODE=" + respawn.commonPlayerSpawnInfo().gameType()); return null;
            case "handleMovePlayer":
                var position = (ClientboundPlayerPositionPacket) packet;
                var change = position.change();
                connection.send(new ServerboundAcceptTeleportationPacket(position.id(), change.position().x, change.position().y, change.position().z, change.yRot(), change.xRot()));
                connection.send(new ServerboundPlayerLoadedPacket());
                log("TELEPORT " + change.position()); return null;
            case "handleChunkBatchFinished":
                connection.send(new ServerboundChunkBatchReceivedPacket(32)); return null;
            case "handleSystemChat":
                log("CHAT " + ((ClientboundSystemChatPacket) packet).content().getString()); return null;
            case "handleGameEvent":
                var event = (ClientboundGameEventPacket) packet;
                if (event.getEvent() == ClientboundGameEventPacket.CHANGE_GAME_MODE) log("MODE " + event.getParam());
                return null;
            case "handleSetExperience":
                log("XP " + packet); return null;
            case "toString": return "SmokeListener[" + name + "]";
            case "hashCode": return System.identityHashCode(proxy);
            case "equals": return proxy == packet;
        }
        if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args == null ? new Object[0] : args);
        if (method.getReturnType() == boolean.class) return false;
        if (method.getReturnType() == int.class) return 0;
        return null;
    }
    private void run(int port, int duration) throws Exception {
        connection = new Connection(net.minecraft.network.protocol.PacketFlow.CLIENTBOUND);
        Connection.connect(new InetSocketAddress("127.0.0.1", port), EventLoopGroupHolder.remote(false), connection).syncUninterruptibly();
        connection.initiateServerboundPlayConnection("127.0.0.1", port, listener(ClientLoginPacketListener.class));
        connection.send(new ServerboundHelloPacket(name, UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8))));
        long deadline = System.nanoTime() + duration * 1_000_000_000L;
        while (System.nanoTime() < deadline && !failed) {
            connection.tick();
            if (!connection.isConnected()) { failed = true; break; }
            Thread.sleep(50);
        }
        log(failed ? "FAILED" : "FINISHED_CONNECTED");
        System.exit(failed ? 1 : 0);
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        new ProtocolSmokeClient(args[0]).run(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
    }
}
