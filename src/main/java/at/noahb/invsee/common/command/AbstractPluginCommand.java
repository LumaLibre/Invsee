package at.noahb.invsee.common.command;

import at.noahb.invsee.Constants;
import at.noahb.invsee.InvseePlugin;
import at.noahb.invsee.common.session.SessionManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

public abstract class AbstractPluginCommand extends Command {

    private final InvseePlugin instance;

    public AbstractPluginCommand(InvseePlugin instance, String name, String description, String usage, String permission, List<String> aliases) {
        super(name, description, usage, aliases);
        this.instance = instance;

        setPermission(permission);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (args.length != 1) {
            sender.sendMessage(text(getUsage()));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(text("Command can only be executed by a player.", RED));
            return true;
        }

        if (!player.hasPermission(Objects.requireNonNull(getPermission(), this::getCommandPermission))) {
            sender.sendMessage(text("You don't have permissions to use that command", RED));
            return true;
        }

        String playerName = args[0];
        OfflinePlayer other = this.instance.getServer().getOfflinePlayerIfCached(playerName);

        if (other == null || (!other.isOnline() && !other.hasPlayedBefore())) {
            resolvePlayer(player, playerName);
            return true;
        }

        openSession(player, other);
        return true;
    }

    private void resolvePlayer(Player player, String playerName) {
        try {
            this.instance.getServer().createProfileExact(null, playerName).update().whenComplete((profile, throwable) ->
                    player.getScheduler().run(this.instance, scheduledTask -> {
                        UUID uniqueId = profile == null ? null : profile.getId();

                        if (throwable != null || uniqueId == null) {
                            player.sendMessage(text("Could not look up player " + playerName + ".", RED));
                            return;
                        }

                        openSession(player, this.instance.getServer().getOfflinePlayer(uniqueId));
                    }, null));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(text("Invalid player name " + playerName + ".", RED));
        }
    }

    private void openSession(Player player, OfflinePlayer other) {
        if (player.getUniqueId().equals(other.getUniqueId())) {
            player.sendMessage(text("You cannot view your own inventory.", RED));
            return;
        }

        if (!other.isOnline() && !other.hasPlayedBefore()) {
            if (!InvseePlugin.getInstance().getConfig().getBoolean(Constants.LOOKUP_UNSEEN_CONFIG)) {
                player.sendMessage(text("Player ", RED)
                        .append(text(Objects.requireNonNullElse(other.getName(), other.getUniqueId().toString())))
                        .append(text(" has never played on this server.")));
                return;
            }

            if (!player.hasPermission(Constants.LOOKUP_UNSEEN_PERMISSION)) {
                player.sendMessage(text("Player ", RED)
                        .append(text(Objects.requireNonNullElse(other.getName(), other.getUniqueId().toString())))
                        .append(text(" has never played on this server.")));
                return;
            }
        }

        getSessionManager().addSubscriberToSession(other, player.getUniqueId());
    }

    protected InvseePlugin getInstance() {
        return this.instance;
    }

    protected abstract String getCommandPermission();

    protected abstract SessionManager getSessionManager();
}
