package at.noahb.invsee.common.session;

import at.noahb.invsee.InvseePlugin;
import at.noahb.invsee.common.player.OfflinePlayerDataAccess;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public interface Session extends SessionInventory {

    default void addSubscriber(UUID subscriber) {
        if (subscriber == null) return;
        if (hasSubscriber(subscriber)) return;
        Player player = InvseePlugin.getInstance().getServer().getPlayer(subscriber);
        if (player == null) return;

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(getUniqueIdOfObservedPlayer());

        Optional<Player> other = getPlayerOffline(offlinePlayer);
        if (other.isEmpty()) {
            return;
        }

        getSubscribers().add(subscriber);
        player.getScheduler().run(InvseePlugin.getInstance(), scheduledTask -> player.openInventory(getInventory()), null);
    }


    default void save() {
        Player cachedPlayer = getCachedPlayer();
        if (cachedPlayer != null) {
            OfflinePlayerDataAccess.save(cachedPlayer);
        }
    }

    default void update(Runnable runnable) {
        Runnable guardedUpdate = () -> {
            try {
                getLock().lock();
                runnable.run();
                if (isOffline()) {
                    save();
                }
            } finally {
                if (getLock().isHeldByCurrentThread()) getLock().unlock();
            }
        };

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(getUniqueIdOfObservedPlayer());
        if (offlinePlayer instanceof Player onlinePlayer) {
            if (Bukkit.isOwnedByCurrentRegion(onlinePlayer)) {
                guardedUpdate.run();
            } else {
                onlinePlayer.getScheduler().run(InvseePlugin.getInstance(), scheduledTask -> guardedUpdate.run(), null);
            }
            return;
        }

        Location location = getSchedulingLocation(offlinePlayer);
        if (Bukkit.isOwnedByCurrentRegion(location)) {
            guardedUpdate.run();
        } else {
            Bukkit.getRegionScheduler().execute(InvseePlugin.getInstance(), location, guardedUpdate);
        }
    }

    default boolean isOffline() {
        return !InvseePlugin.getInstance().getServer().getOfflinePlayer(getUniqueIdOfObservedPlayer()).isOnline();
    }

    default Optional<Player> getPlayerOffline(OfflinePlayer offlinePlayer) {
        if (offlinePlayer instanceof Player onlinePlayer) {
            return Optional.of(onlinePlayer);
        }

        Player cached = getCachedPlayer();
        if (cached != null) {
            return Optional.of(cached);
        }

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        Location location = getSchedulingLocation(offlinePlayer);
        ServerLevel world = ((CraftWorld) location.getWorld()).getHandle();

        GameProfile profile = new GameProfile(offlinePlayer.getUniqueId(),
                offlinePlayer.getName() != null ? offlinePlayer.getName() : offlinePlayer.getUniqueId().toString());

        ServerPlayer serverPlayer = new ServerPlayer(server, world, profile, ClientInformation.createDefault());
        serverPlayer.setPos(location.getX(), location.getY(), location.getZ());
        OfflinePlayerDataAccess.load(server, serverPlayer);
        Player target = serverPlayer.getBukkitEntity();
        cache(target);
        return Optional.of(target);
    }

    static Location getSchedulingLocation(OfflinePlayer offlinePlayer) {
        Location location = offlinePlayer.getLocation();
        if (location != null) {
            return location;
        }

        return Bukkit.getWorlds().getFirst().getSpawnLocation();
    }

    UUID getUniqueIdOfObservedPlayer();

    void updateObservedInventory();

    void updateSubscriberInventory();

    Set<UUID> getSubscribers();

    void removeSubscriber(UUID subscriber);

    boolean hasSubscriber(UUID subscriber);

    ReentrantLock getLock();

    void cache(Player player);

    Player getCachedPlayer();

    boolean isSubscriber(@NotNull UUID whoClicked);
}
