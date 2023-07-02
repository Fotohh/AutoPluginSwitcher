package io.github.xaxisplayz.autopluginswitcher.commands;

import io.github.xaxisplayz.autopluginswitcher.Main;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StopCommand implements CommandExecutor {

    private final Main plugin;

    public StopCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {

        if(commandSender instanceof Player player && player.hasPermission("autopluginswitcher.admin")){
            plugin.stop();
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&4Stopped tasks!"));
        }
        return true;
    }
}
