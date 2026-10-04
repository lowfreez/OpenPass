package org.openpass.bukkit.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.openpass.bukkit.config.ConfigManager;
import org.openpass.common.security.BruteForceManager;

import java.util.ArrayList;
import java.util.List;

public class OpenPassAdminCommand implements CommandExecutor, TabCompleter {
    private final ConfigManager configManager;
    private final BruteForceManager bruteForceManager;

    public OpenPassAdminCommand(ConfigManager configManager, BruteForceManager bruteForceManager) {
        this.configManager = configManager;
        this.bruteForceManager = bruteForceManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("openpass.admin")) {
            sender.sendMessage(configManager.getMessage("action-blocked"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(configManager.getMessage("prefix").append(Component.text("Используйте: /openpass <reload|unban>")));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            configManager.load();
            sender.sendMessage(configManager.getMessage("reload-success"));
            return true;
        }

        if (args[0].equalsIgnoreCase("unban")) {
            if (args.length < 2) {
                sender.sendMessage(configManager.getMessage("prefix").append(Component.text("Укажите IP или ник игрока: /openpass unban <цель>")));
                return true;
            }
            bruteForceManager.unban(args[1]);
            sender.sendMessage(configManager.getMessage("prefix").append(Component.text("Блокировка для '" + args[1] + "' успешно снята.")));
            return true;
        }

        sender.sendMessage(configManager.getMessage("prefix").append(Component.text("Неизвестная подкоманда. Доступно: reload, unban")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            if ("reload".startsWith(args[0].toLowerCase())) list.add("reload");
            if ("unban".startsWith(args[0].toLowerCase())) list.add("unban");
            return list;
        }
        return List.of();
    }
}
