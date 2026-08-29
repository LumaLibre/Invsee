package at.noahb.invsee.invsee.command;

import at.noahb.invsee.InvseePlugin;
import at.noahb.invsee.common.command.AbstractPluginCommand;
import at.noahb.invsee.common.session.SessionManager;

import java.util.List;

public class InvseeCommand extends AbstractPluginCommand {

    private static final String PERMISSION = "invsee.invsee.command";

    public InvseeCommand(InvseePlugin instance) {
        super(
                instance,
                "invsee",
                "Invsee command",
                "/invsee <player|uuid>",
                PERMISSION,
                List.of("isee", "is", "inv")
        );
    }

    @Override
    protected String getCommandPermission() {
        return InvseeCommand.PERMISSION;
    }

    @Override
    protected SessionManager getSessionManager() {
        return getInstance().getInvseeSessionManager();
    }

}
