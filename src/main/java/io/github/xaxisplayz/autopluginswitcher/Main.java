package io.github.xaxisplayz.autopluginswitcher;

import io.github.xaxisplayz.autopluginswitcher.commands.ResetCommand;
import io.github.xaxisplayz.autopluginswitcher.commands.StartCommand;
import io.github.xaxisplayz.autopluginswitcher.commands.StopCommand;
import io.github.xaxisplayz.autopluginswitcher.util.FileUtil;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class Main extends JavaPlugin {

    private String GITHUB_LINK;
    private long cooldown;
    private long timestamp;
    private long taskInterval;
    private String pluginFileName;
    private List<String> mapFolderNames = new ArrayList<>();

    private Logger logger;
    private File zipFileDestinationPath;
    private File zipFile;
    private File pluginsFolder;
    private File mainFolder;
    private BukkitTask task;
    private boolean checked = false;

    @Override
    public void onEnable() {

        getCommand("reset").setExecutor(new ResetCommand(this));
        getCommand("stop").setExecutor(new StopCommand(this));
        getCommand("start").setExecutor(new StartCommand(this));

        run();

    }

    public void run(){

        if(!getConfig().getBoolean("il")){
            downloadGithubRelease();
        }

        loadConfiguration();

        getLogger().info("AutoPluginSwitcher has successfully loaded!");

        startCooldownCheck();

        if(checked) saveData();

    }

    public void stop(){
        task.cancel();
    }

    public void resume(){
        startCooldownCheck();
    }

    public void reset(){
        task.cancel();
        timestamp = System.currentTimeMillis();
        startCooldownCheck();
    }

    @Override
    public void onDisable() {
        cancelCooldownCheck();
    }

    private void loadConfiguration(){

        if(!getDataFolder().exists()) getDataFolder().mkdirs();

        File file = new File(getDataFolder(), "config.yml");

        if(!file.exists()) saveDefaultConfig();

        reloadConfig();

        mainFolder = getDataFolder().getParentFile().getParentFile();
        pluginsFolder = getDataFolder().getParentFile();

        cooldown = getConfig().getLong("cooldown");
        if(getConfig().getString("github_link") == null || getConfig().getString("github_link").isBlank()) {
            getLogger().severe("Found no github link! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        GITHUB_LINK = getConfig().getString("github_link");
        taskInterval = getConfig().getLong("task_interval");

        timestamp = getConfig().getLong("timestamp");
        pluginFileName = getConfig().getString("file_name");
        mapFolderNames = getConfig().getStringList("map_names");

    }

    private void saveData(){
        getConfig().set("timestamp", timestamp);
        getConfig().set("file_name", pluginFileName);
        getConfig().set("map_names", mapFolderNames);
    }

    private long calculateExpirationTime(){
        return timestamp + cooldown;
    }

    private void startCooldownCheck(){
        task = getServer().getScheduler().runTaskTimer(this, ()->{
            if(System.currentTimeMillis() >= calculateExpirationTime()){
                downloadGithubRelease();
                checked = true;
            }
        }, 0L, taskInterval);
    }

    private void cancelCooldownCheck(){
        if(task != null) task.cancel();
    }

    private BukkitTask asyncTask;
    private BukkitTask restartTask;

    private void downloadGithubRelease() {

        loadFiles();

        executeAsyncTask();

        resetExpirationTime();

        saveData();

        logger.info("restarting server in "+ getConfig().getList("server_restart_delay") +" seconds...");

        restartTask = Bukkit.getScheduler().runTaskLater(this, this::restartServer, getConfig().getLong("server_restart_delay") * 20L);

    }

    private void loadFiles(){
        logger = LogManager.getLogger("AutoPluginSwitcher");

        logger.info("initializing files...");

        zipFileDestinationPath = new File(getDataFolder() + "/data");

        if (!zipFileDestinationPath.exists()) zipFileDestinationPath.mkdirs();

        logger.info("created zip file destination path...");

        zipFile = new File(getDataFolder() + "/data/release.zip");
    }

    private void executeAsyncTask(){
        asyncTask = Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                downloadFile(GITHUB_LINK);
                logger.info("successfully downloaded github release!");
                logger.info("unzipping...");
                unzipFile();
                logger.info("Successfully unzipped file!");
                FileUtil.deleteFile(zipFile);
                performFileCleanup();
                performMapCleanup();
                logger.info("Performing file cleanup...");
                logger.info("reading data...");
                readData();
                logger.info("moving files/directories...");
                moveFilesAndDirectories();
                zipFile.delete();
                logger.info("deleted zip file...");
                asyncTask.cancel();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void downloadFile(String url) throws IOException{
        try(CloseableHttpClient httpClient = HttpClients.createDefault()){
            HttpGet httpGet = new HttpGet(url);
            try(CloseableHttpResponse response = httpClient.execute(httpGet)){
                InputStream inputStream = response.getEntity().getContent();
                FileOutputStream outputStream = new FileOutputStream(zipFile);
                byte[] buffer = new byte[1024];
                int bytesRead;
                while((bytesRead = inputStream.read(buffer)) != -1){
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.close();
            }
        }
        task.cancel();
    }

    private void unzipFile() {
        try {
            new ZipFile(zipFile).extractAll(zipFileDestinationPath.getPath());
        } catch (ZipException e) {
            throw new RuntimeException(e);
        }
    }

    private void readData() {
        File[] files = zipFileDestinationPath.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory())
                    continue;
                if (file.isFile() || file.getName().contains(".jar")) {
                    pluginFileName = file.getName();
                }
            }
        }
        File[] folders = new File(zipFileDestinationPath, "maps").listFiles();
        if (folders != null) {
            for (File folder : folders) {
                mapFolderNames.add(folder.getName());
            }
        }
    }

    private void performMapCleanup() {
        File[] dirs = getDataFolder().getParentFile().getParentFile().listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                if (mapFolderNames.stream().anyMatch(s -> s.equalsIgnoreCase(dir.getName()))) {
                    FileUtil.deleteDirectory(dir);
                }
            }
        }
    }

    private void moveFilesAndDirectories() {
        File[] mapFolders = new File(zipFileDestinationPath + "/maps").listFiles();
        if (mapFolders != null) {
            for (File mapFolder : mapFolders) {
                FileUtil.moveDirectory(mapFolder, mainFolder.getPath());
            }
        }
        File plugin = new File(zipFileDestinationPath, getConfig().getString("file_name"));
        FileUtil.moveFile(plugin, getDataFolder().getParentFile().getPath());
    }

    private void performFileCleanup(){
        FileUtil.deleteFile(new File(pluginsFolder, pluginFileName));
    }

    private void restartServer() {
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "restart");
        if (restartTask != null)
            restartTask.cancel();
    }

    private void resetExpirationTime(){
        getConfig().set("timestamp", System.currentTimeMillis());
    }

}
