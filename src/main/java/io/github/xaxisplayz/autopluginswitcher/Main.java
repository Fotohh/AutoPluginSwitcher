package io.github.xaxisplayz.autopluginswitcher;

import io.github.xaxisplayz.autopluginswitcher.commands.ResetCommand;
import io.github.xaxisplayz.autopluginswitcher.commands.StartCommand;
import io.github.xaxisplayz.autopluginswitcher.commands.StopCommand;
import io.github.xaxisplayz.autopluginswitcher.util.Download;
import io.github.xaxisplayz.autopluginswitcher.util.FileUtil;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final String LOGGER_NAME = "AutoPluginSwitcher";
    private static final String ZIP_FILE_NAME = "release.zip";
    private static final String GITHUB_LINK_PATH = "github_link";
    private static final String TASK_INTERVAL_PATH = "task_interval";
    private static final String SERVER_RESTART_DELAY_PATH = "server_restart_delay";
    private static final String TIME_STAMP_PATH = "time_stamp";
    private static final String FILE_NAME_PATH = "file_name";
    private static final String COOLDOWN_PATH = "cooldown";
    private static final String MAPS_PATH = "maps";

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

        if(getConfig().getString(GITHUB_LINK_PATH) == null || getConfig().getString(GITHUB_LINK_PATH).isBlank()) {
            getLogger().severe("Found no github link! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
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
        getConfig().set(COOLDOWN_PATH, System.currentTimeMillis());
        startCooldownCheck();
    }

    private void cancelCooldownCheck(){
        if(task != null) task.cancel();
    }

    @Override
    public void onDisable() {
        cancelCooldownCheck();
    }

    private long calculateExpirationTime(){
        return getConfig().getLong(TIME_STAMP_PATH) + getConfig().getLong(COOLDOWN_PATH);
    }

    private void startCooldownCheck(){
        task = getServer().getScheduler().runTaskTimer(this, ()->{
            if(System.currentTimeMillis() >= calculateExpirationTime()) {

                logger = LogManager.getLogger(LOGGER_NAME);

                File zipPath = new File(getDataFolder() + "/data");

                if(!zipPath.exists()) zipPath.mkdirs();

                zipFile = new File(zipPath,ZIP_FILE_NAME);

                try {
                    zipFile.createNewFile();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                asyncTask = Bukkit.getScheduler().runTaskAsynchronously(this, ()-> downloadAndUnzip(zipPath));

                restart();

                task.cancel();

            }
        }, 0L, getConfig().getLong(TASK_INTERVAL_PATH));
    }

    private void downloadAndUnzip(File zipfilePath){

        try {
            Download.download(getConfig().getString(GITHUB_LINK_PATH), zipFile.getPath());
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

        saveJarFile();
        saveMaps();
        logger.info("saved jar file info!");

        logger.info("Performing file cleanup...");
        performFileCleanup();

        logger.info("moving files/directories...");
        moveFilesAndDirectories();

        logger.info("saved map info!");

        logger.info("deleting zip file...");
        zipFile.delete();

        logger.info("deleting directories...");
        try {
            FileUtils.deleteDirectory(extractedZipFolder);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        zipfilePath.delete();

        logger.info("restarting server...");
        asyncTask.cancel();

    }

    private void restart(){

        getConfig().set(TIME_STAMP_PATH, System.currentTimeMillis());

        saveConfig();

        logger.info("restarting server in " + getConfig().getLong(SERVER_RESTART_DELAY_PATH)/20 + " seconds...");

        restartTask = Bukkit.getScheduler().runTaskLater(this, ()->{
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "restart");
            if (restartTask != null)
                restartTask.cancel();
        }, getConfig().getLong(SERVER_RESTART_DELAY_PATH) * 20L);

    }

    private void saveJarFile() {
        File[] files = extractedZipFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory())
                    continue;
                if (file.isFile()) {
                    getConfig().set(FILE_NAME_PATH, file.getName());
                }
            }
        }
        saveConfig();
    }

    private void saveMaps(){
        File mapsFolder = new File(extractedZipFolder, "maps");
        File[] folders = mapsFolder.listFiles();
        if (folders != null && folders.length > 0) {
            List<String> names = new ArrayList<>(folders.length);
            for (File folder : folders) {
                names.add(folder.getName());
            }
            getConfig().set(MAPS_PATH, names);
        }
        saveConfig();
    }

    private void moveFilesAndDirectories() {
        File[] mapFolders = new File(extractedZipFolder,"maps").listFiles();
        Arrays.stream(mapFolders).forEach(file -> FileUtil.moveDirectory(file, new File(worldContainer.getPath(), file.getName())));
        File[] files = extractedZipFolder.listFiles(File::isFile);
        if (files != null && files.length > 0) {
            File file = files[0];
            File plugin = new File(extractedZipFolder, file.getName());
            FileUtil.moveFile(plugin, new File(getDataFolder().getParent() +"/"+ file.getName()));
        }
    }

    private void performFileCleanup() {

        File file = new File(pluginsFolder, getConfig().getString(FILE_NAME_PATH));

        if(file.exists()) file.delete();

        File folder = Bukkit.getServer().getWorldContainer();

        File[] dirs = folder.listFiles(File::isDirectory);

        if (dirs == null) return;

        for (File dir : dirs) {
            if (getConfig().getStringList(MAPS_PATH).stream().anyMatch(s -> s.equalsIgnoreCase(dir.getName()))) {
                try {
                    FileUtils.deleteDirectory(dir);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

}
