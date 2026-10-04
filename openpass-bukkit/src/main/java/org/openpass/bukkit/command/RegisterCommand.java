package org.openpass.bukkit.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.openpass.bukkit.auth.AuthManager;
import org.openpass.bukkit.config.ConfigManager;

import java.util.Collections;
import java.util.List;

public class RegisterCommand implements CommandExecutor, TabCompleter {
    private final AuthManager authManager;
    private final ConfigManager configManager;

    public RegisterCommand(AuthManager authManager, ConfigManager configManager) {
        this.authManager = authManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только игроки могут выполнять эту команду.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(configManager.getMessage("register-prompt"));
            return true;
        }

        authManager.register(player, args[0], args[1]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
