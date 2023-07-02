package io.github.xaxisplayz.autopluginswitcher.util;

import com.google.common.io.Files;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import java.io.File;
import java.io.IOException;

public class FileUtil {

    public static void moveDirectory(File sourceDirectory, File destinationDirectory) {
        if (!sourceDirectory.exists()) return;
        if(!destinationDirectory.exists()) destinationDirectory.mkdirs();

        for(File file : sourceDirectory.listFiles()){
            if(file.isDirectory()){
                File folder = new File(destinationDirectory, file.getName());
                moveDirectory(file, folder);
            }else if(file.isFile()){
                File f = new File(destinationDirectory, file.getName());
                moveFile(file, f);
            }
        }
        sourceDirectory.delete();
    }

    public static File getFolderGeneratedByZip(String zipFileDestination){
        File folder = new File(zipFileDestination);
        if(!folder.exists()) folder.mkdirs();
        if(folder.listFiles() == null ) return null;
        File[] folders = folder.listFiles(File::isDirectory);
        if(folders == null) return null;
        return folders[0];
    }

    public static void unzipFile(File file, String destinationPath) throws ZipException {
        if(file == null) return;
        ZipFile zip = new ZipFile(file);
        zip.extractAll(destinationPath);
    }

    public static void moveFile(File sourceFile, File destinationFile) {
        try {
            Files.move(sourceFile, destinationFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
