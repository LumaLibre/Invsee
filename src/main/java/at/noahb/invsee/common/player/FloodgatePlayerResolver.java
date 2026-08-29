package at.noahb.invsee.common.player;

import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FloodgatePlayerResolver {

    private FloodgatePlayerResolver() {
    }

    public static boolean hasPrefix(String playerName) {
        String prefix = FloodgateApi.getInstance().getPlayerPrefix();
        return !prefix.isEmpty() && playerName.startsWith(prefix);
    }

    public static boolean hasNoPrefix() {
        return FloodgateApi.getInstance().getPlayerPrefix().isEmpty();
    }

    public static CompletableFuture<UUID> resolve(String playerName) {
        FloodgateApi api = FloodgateApi.getInstance();
        String prefix = api.getPlayerPrefix();
        String gamertag = !prefix.isEmpty() && playerName.startsWith(prefix)
                ? playerName.substring(prefix.length())
                : playerName;

        return api.getUuidFor(gamertag).thenCompose(uniqueId -> {
            if (uniqueId != null || !gamertag.contains("_")) {
                return CompletableFuture.completedFuture(uniqueId);
            }

            return api.getUuidFor(gamertag.replace('_', ' '));
        });
    }
}
