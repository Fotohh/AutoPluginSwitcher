package io.github.xaxisplayz.autopluginswitcher;

import io.github.xaxisplayz.autopluginswitcher.commands.ResetCommand;
import io.github.xaxisplayz.autopluginswitcher.commands.StartCommand;
import io.github.xaxisplayz.autopluginswitcher.commands.StopCommand;
import io.github.xaxisplayz.autopluginswitcher.util.Download;
import io.github.xaxisplayz.autopluginswitcher.util.FileUtil;
import net.lingala.zip4j.exception.ZipException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main extends JavaPlugin {

    private Logger logger;
    private File zipFile;
    private File extractedZipFolder;
    private File pluginsFolder;
    private File worldContainer;
    private BukkitTask task;
    private BukkitTask asyncTask;
    private BukkitTask restartTask;

    @Override
    public void onEnable() {

        worldContainer = Bukkit.getServer().getWorldContainer();
        if(!worldContainer.exists()) worldContainer.mkdirs();
        pluginsFolder = getDataFolder().getParentFile();
        if(!pluginsFolder.exists()) pluginsFolder.mkdirs();
        if(!getDataFolder().exists()) getDataFolder().mkdirs();

        saveDefaultConfig();

        getCommand("reset").setExecutor(new ResetCommand(this));
        getCommand("stop").setExecutor(new StopCommand(this));
        getCommand("start").setExecutor(new StartCommand(this));

        if(getConfig().getString("github_link") == null || getConfig().getString("github_link").isBlank()) {
            getLogger().severe("Found no github link! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if(!getConfig().getBoolean("il")) {
            downloadGithubRelease();
            return;
        }

        getLogger().info("AutoPluginSwitcher has successfully loaded!");

        startCooldownCheck();

    }

    public void stop(){
        task.cancel();
    }

    public void resume(){
        startCooldownCheck();
    }

    public void reset(){
        task.cancel();
        getConfig().set("cooldown", System.currentTimeMillis());
        startCooldownCheck();
    }

    @Override
    public void onDisable() {
        cancelCooldownCheck();
    }

    private long calculateExpirationTime(){
        return getConfig().getLong("timestamp") + getConfig().getLong("cooldown");
    }

    private void startCooldownCheck(){
        task = getServer().getScheduler().runTaskTimer(this, ()->{
            if(System.currentTimeMillis() >= calculateExpirationTime()) {
                downloadGithubRelease();
                restart();
                task.cancel();
            }
        }, 0L, getConfig().getLong("task_interval"));
    }

    private void cancelCooldownCheck(){
        if(task != null) task.cancel();
    }

    private void downloadGithubRelease() {

        logger = LogManager.getLogger("AutoPluginSwitcher");

        File zipPath = new File(getDataFolder() + "/data");

        if(!zipPath.exists()) zipPath.mkdirs();

        zipFile = new File(zipPath, "release.zip");

        try {
            zipFile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        asyncTask = Bukkit.getScheduler().runTaskAsynchronously(this, ()-> downloadAndUnzip(zipPath));

    }


    private void downloadAndUnzip(File zipfilePath){

        try {
            Download.download(getConfig().getString("github_link"), zipFile.getPath());
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        logger.info("successfully downloaded github release!");

        try {
            FileUtil.unzipFile(zipFile, zipfilePath.getAbsolutePath());
        } catch (ZipException e) {
            e.printStackTrace();
            return;
        }
        logger.info("Successfully unzipped file!");

        extractedZipFolder = FileUtil.getFolderGeneratedByZip(zipfilePath.getAbsolutePath());

        logger.info("saving data to config.yml...");
        saveToConfig();

        logger.info("Performing file cleanup...");
        performFileCleanup();

        logger.info("moving files/directories...");
        moveFilesAndDirectories();

        logger.info("deleting zip file...");
        zipFile.delete();

        logger.info("deleting directories...");
        FileUtil.deleteDirectory(extractedZipFolder);
        zipfilePath.delete();

        logger.info("cancelling and restarting server...");
        asyncTask.cancel();

    }

    private void restart(){

        resetExpirationTime();

        getConfig().set("timestamp", System.currentTimeMillis());

        getConfig().set("il", true);

        saveConfig();

        logger.info("restarting server in " + getConfig().getLong("server_restart_delay")/20 + " seconds...");

        restartTask = Bukkit.getScheduler().runTaskLater(this, ()->{
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "restart");
            if (restartTask != null)
                restartTask.cancel();
        }, getConfig().getLong("server_restart_delay") * 20L);

    }

    private void saveToConfig() {
        File[] files = extractedZipFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory())
                    continue;
                if (file.isFile() || file.getName().contains(".jar")) {
                    getConfig().set("file_name", file.getName());
                }
            }
        }
        File mapsFolder = new File(extractedZipFolder, "maps");
        File[] folders = mapsFolder.listFiles();
        if (folders != null && folders.length > 0) {
            List<String> names = new ArrayList<>(folders.length);
            for (File folder : folders) {
                names.add(folder.getPath());
            }
            getConfig().set("maps", names);
        }
    }

    private void moveFilesAndDirectories() {
        File[] mapFolders = new File(extractedZipFolder,"/maps").listFiles();
        for (File mapFolder : mapFolders) {
            FileUtil.moveDirectory(mapFolder, worldContainer.getPath() + "/" + mapFolder.getName());
        }
        File file = extractedZipFolder.listFiles(File::isFile)[0];
        if(file == null) return;
        File plugin = new File(extractedZipFolder, file.getName());
        FileUtil.moveFile(plugin, getDataFolder().getParentFile().getPath());
    }

    private void performFileCleanup() {

        File file = new File(pluginsFolder, getConfig().getString("file_name"));

        if(file.exists()) file.delete();

        File folder = Bukkit.getServer().getWorldContainer();

        File[] dirs = folder.listFiles(File::isDirectory);

        if (dirs == null) return;

        for (File dir : dirs) {
            if (getConfig().getStringList("maps").stream().anyMatch(s -> s.equalsIgnoreCase(dir.getName()))) {
                FileUtil.deleteDirectory(dir);
            }
        }
    }

    private void resetExpirationTime(){
        getConfig().set("timestamp", System.currentTimeMillis());
    }

}
