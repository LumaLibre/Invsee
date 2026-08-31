package at.noahb.invsee.common.player;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

public final class OfflinePlayerDataAccess {

    private OfflinePlayerDataAccess() {
    }

    public static void load(MinecraftServer server, ServerPlayer player) {
        server.getPlayerList().playerIo.load(player.nameAndId())
                .map(tag -> TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), tag))
                .ifPresent(player::load);
    }

    public static void save(Player player) {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
        server.getPlayerList().playerIo.save(serverPlayer);
    }
}
