package org.openpass.bukkit.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.openpass.bukkit.auth.AuthManager;
import org.openpass.bukkit.config.ConfigManager;
import org.openpass.common.security.BruteForceManager;
import org.openpass.common.storage.DatabaseService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OpenPassAdminCommand implements CommandExecutor, TabCompleter {
    private final ConfigManager configManager;
    private final BruteForceManager bruteForceManager;
    private final AuthManager authManager;
    private final DatabaseService databaseService;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public OpenPassAdminCommand(ConfigManager configManager, BruteForceManager bruteForceManager, AuthManager authManager, DatabaseService databaseService) {
        this.configManager = configManager;
        this.bruteForceManager = bruteForceManager;
        this.authManager = authManager;
        this.databaseService = databaseService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("openpass.admin")) {
            sender.sendMessage(configManager.getMessage("action-blocked"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(configManager.getMessage("prefix").append(Component.text("Команды: /openpass <reload|unban|unregister|forceauth|info>")));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                configManager.load();
                sender.sendMessage(configManager.getMessage("reload-success"));
                return true;
            }
            case "unban" -> {
                if (args.length < 2) {
                    sender.sendMessage(configManager.getMessage("prefix").append(Component.text("Используйте: /openpass unban <цель>")));
                    return true;
                }
                bruteForceManager.unban(args[1]);
                sender.sendMessage(configManager.getMessage("prefix").append(Component.text("Блокировка для '" + args[1] + "' снята.")));
                return true;
            }
            case "unregister", "unreg", "removepass" -> {
                if (args.length < 2) {
                    sender.sendMessage(configManager.getMessage("prefix").append(Component.text("Используйте: /openpass unregister <игрок>")));
                    return true;
                }
                authManager.unregister(args[1], sender);
                return true;
            }
            case "forceauth" -> {
                if (args.length < 2) {
                    sender.sendMessage(configManager.getMessage("prefix").append(Component.text("Используйте: /openpass forceauth <игрок>")));
                    return true;
                }
                authManager.forceAuth(args[1], sender);
                return true;
            }
            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage(configManager.getMessage("prefix").append(Component.text("Используйте: /openpass info <игрок>")));
                    return true;
                }
                databaseService.getUserByName(args[1]).thenAccept(user -> {
                    if (user == null) {
                        sender.sendMessage(configManager.getMessage("admin-player-not-found", "player", args[1]));
                        return;
                    }
                    sender.sendMessage(configManager.getMessage("prefix").append(Component.text("Информация об игроке: " + user.getUsername())));
                    sender.sendMessage(Component.text("§7UUID: §9" + user.getUuid()));
                    sender.sendMessage(Component.text("§7Лицензия: §9" + (user.isPremium() ? "Да" : "Нет")));
                    sender.sendMessage(Component.text("§7Регистрация: §9" + dateFormat.format(new Date(user.getRegisteredAt()))));
                    sender.sendMessage(Component.text("§7Последний вход: §9" + dateFormat.format(new Date(user.getLastLogin()))));
                    sender.sendMessage(Component.text("§7Последний IP: §9" + user.getLastIp()));
                });
                return true;
            }
            default -> {
                sender.sendMessage(configManager.getMessage("prefix").append(Component.text("Неизвестная подкоманда. Доступно: reload, unban, unregister, forceauth, info")));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            List<String> subs = List.of("reload", "unban", "unregister", "forceauth", "info");
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) {
                    list.add(s);
                }
            }
            return list;
        }
        return List.of();
    }
}
