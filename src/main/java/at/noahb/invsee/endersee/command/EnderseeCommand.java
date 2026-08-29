package at.noahb.invsee.endersee.command;

import at.noahb.invsee.InvseePlugin;
import at.noahb.invsee.common.command.AbstractPluginCommand;
import at.noahb.invsee.common.session.SessionManager;

import java.util.List;

public class EnderseeCommand extends AbstractPluginCommand {

    private static final String PERMISSION = "invsee.endersee.command";

    public EnderseeCommand(InvseePlugin instance) {
        super(
                instance,
                "endersee",
                "Endersee command",
                "/endersee <player|uuid>",
                PERMISSION,
                List.of("esee", "es", "ecsee")
        );
    }

    @Override
    protected String getCommandPermission() {
        return EnderseeCommand.PERMISSION;
    }

    @Override
    protected SessionManager getSessionManager() {
        return getInstance().getEnderseeSessionManager();
    }

}
