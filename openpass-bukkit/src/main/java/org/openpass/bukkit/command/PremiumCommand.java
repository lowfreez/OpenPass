package org.openpass.bukkit.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.openpass.bukkit.auth.AuthManager;

import java.util.Collections;
import java.util.List;

public class PremiumCommand implements CommandExecutor, TabCompleter {
    private final AuthManager authManager;

    public PremiumCommand(AuthManager authManager) {
        this.authManager = authManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только игроки могут выполнять эту команду.");
            return true;
        }

        authManager.togglePremium(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
