package io.github.xaxisplayz.autopluginswitcher.commands;

import io.github.xaxisplayz.autopluginswitcher.Main;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class StartCommand implements CommandExecutor {

    private final Main plugin;

    public StartCommand(Main plugin) {
        this.plugin = plugin;
    }


    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        if(commandSender instanceof Player player && player.hasPermission("autopluginswitcher.admin")){
            plugin.resume();
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aResumed tasks!"));
        }
        return true;
    }
}
