package at.noahb.invsee.common.session;

import at.noahb.invsee.InvseePlugin;
import com.mojang.authlib.GameProfile;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static net.kyori.adventure.text.Component.text;

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
            cachedPlayer.saveData();
        }
    }

    default void update(Runnable runnable) {
        try {
            getLock().lock();
            runnable.run();
            if (isOffline()) {
                save();
            }
        } finally {
            if (getLock().isHeldByCurrentThread()) getLock().unlock();
        }
    }

    default boolean isOffline() {
        return !InvseePlugin.getInstance().getServer().getOfflinePlayer(getUniqueIdOfObservedPlayer()).isOnline();
    }

    default Optional<Player> getPlayerOffline(OfflinePlayer offlinePlayer) {
        Player cached = getCachedPlayer();
        if (cached != null) {
            return Optional.of(cached);
        }

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        Location location = offlinePlayer.getLocation();
        ServerLevel world;

        if (location == null) {
            world = server.overworld();
        } else {
            world = ((CraftWorld) location.getWorld()).getHandle();
        }

        GameProfile profile = new GameProfile(offlinePlayer.getUniqueId(),
                offlinePlayer.getName() != null ? offlinePlayer.getName() : offlinePlayer.getUniqueId().toString());

        ServerPlayer serverPlayer = new ServerPlayer(server, world, profile, ClientInformation.createDefault());
        Player target = serverPlayer.getBukkitEntity();
        target.loadData();
        cache(target);
        return Optional.of(target);
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
