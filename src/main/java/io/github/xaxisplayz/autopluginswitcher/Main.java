package io.github.xaxisplayz.autopluginswitcher;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main extends JavaPlugin {

    private String GITHUB_LINK;
    private long cooldown;
    private long time;
    private long currentTime;
    private long taskInterval;
    private BukkitTask task;
    private String zipFileDestinationPath;
    private String zipFile;

    private String pluginFileName;
    private List<String> mapFolderNames = new ArrayList<>();
    private File mainFolder;

    @Override
    public void onEnable() {

        loadConfiguration();

        getLogger().info("AutoPluginSwitcher has successfully loaded!");

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
        cooldown = getConfig().getLong("cooldown");
        GITHUB_LINK = getConfig().getString("github_link");
        time = getConfig().getLong("time");
        taskInterval = getConfig().getLong("task_interval");
        pluginFileName = getConfig().getString("file_name");
        currentTime = System.currentTimeMillis();
        mapFolderNames = getConfig().getStringList("map_names");

    }

    private long calculateExpirationTime(){
        return time + cooldown;
    }

    private void startCooldownCheck(){
        task = getServer().getScheduler().runTaskTimerAsynchronously(this, ()->{
            if(currentTime >= calculateExpirationTime()){
                downloadGithubRelease();
            }
        }, 0L, taskInterval);
    }

    private void cancelCooldownCheck(){
        if(task != null) task.cancel();
    }

    private void downloadGithubRelease(){

        Bukkit.getServer().broadcastMessage("restarting server soon...!");

        zipFileDestinationPath = getDataFolder() + "/data";

        File folder = new File(zipFileDestinationPath);

        if(!folder.exists()) folder.mkdirs();

        zipFile =  getDataFolder() + "/data/" + "release.zip";

        try {
            downloadFile(GITHUB_LINK);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        unzipFile();
        deleteFile(zipFile);

        performFileCleanup();
        performMapCleanup();

        try {
            readInfoFile(zipFileDestinationPath + "/info.txt");
            saveConfig();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for(File mapFolder : new File(zipFileDestinationPath + "/maps").listFiles()){
            moveDirectory(mapFolder.getPath(), mainFolder.getPath());
        }

        File plugin = new File(zipFileDestinationPath, getConfig().getString("file_name"));

        moveFile(plugin.getPath(), getDataFolder().getParentFile().getPath());

        new File(zipFileDestinationPath).delete();

        resetExpirationTime();

        restartServer();

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
    }

    private void unzipFile(){
        ZipFile zipFile = new ZipFile(new File(this.zipFile));
        try {
            zipFile.extractAll(zipFileDestinationPath);
        } catch (ZipException e) {
            throw new RuntimeException(e);
        }
    }

    private void readInfoFile(String infoFile) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(infoFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Process each line of the info.txt file and extract necessary data
                if(line.startsWith("plugin_file_name:")){
                    String name = line.split(" ")[1];
                    getConfig().set("file_name", name);
                } else if (line.startsWith("map_names:{")) {
                    String s = line.split("\\{")[1];
                    String b = s.replace(" ", "");
                    String[] a = b.split(",");
                    mapFolderNames.addAll(Arrays.asList(a));
                    getConfig().set("map_names", mapFolderNames);
                }
            }
        }
    }

    private void deleteFile(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            file.delete();
        }
    }

    private void deleteDirectory(String directoryPath) {
        File directory = new File(directoryPath);
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file.getAbsolutePath());
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    private void moveDirectory(String sourcePath, String destinationPath) {

        File sourceDirectory = new File(sourcePath);

        File destinationDirectory = new File(destinationPath);

        if (sourceDirectory.exists()) {

            if (!destinationDirectory.exists()) destinationDirectory.mkdirs();

            for (File file : sourceDirectory.listFiles()) {
                if (file.isDirectory()) {
                    moveDirectory(file.getAbsolutePath(), destinationPath + File.separator + file.getName());
                } else {
                    moveFile(file.getAbsolutePath(), destinationPath + File.separator + file.getName());
                }
            }
            sourceDirectory.delete();
        }
    }

    private void moveFile(String sourcePath, String destinationPath) {
        File sourceFile = new File(sourcePath);
        File destinationFile = new File(destinationPath);
        if (sourceFile.exists()) {
            if (!destinationFile.getParentFile().exists()) {
                destinationFile.getParentFile().mkdirs();
            }
            sourceFile.renameTo(destinationFile);
        }
    }

    private void performMapCleanup(){
        //aps file        plugins           main folder
        File folder = getDataFolder().getParentFile().getParentFile();

        for(File dir : folder.listFiles()){
            if(!dir.isDirectory()) return;
            if(mapFolderNames.stream().anyMatch(s -> s.equalsIgnoreCase(dir.getName()))){
                deleteDirectory(dir.getPath());
            }
        }
    }

    private void performFileCleanup(){
       deleteFile(new File(getDataFolder(), pluginFileName).getPath());
    }

    private void restartServer(){
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "restart");
    }

    private void resetExpirationTime(){
        getConfig().set("time", System.currentTimeMillis());
    }

}
