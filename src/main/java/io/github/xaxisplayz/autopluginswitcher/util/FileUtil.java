package io.github.xaxisplayz.autopluginswitcher.util;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import java.io.File;

public class FileUtil {

    public static void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.deleteOnExit();
        }
    }

    public static void moveDirectory(File sourceDirectory, String destinationPath) {

        File destinationDirectory = new File(destinationPath);

        if (sourceDirectory.exists()) {

            if (!destinationDirectory.exists()) destinationDirectory.mkdirs();

            for (File file : sourceDirectory.listFiles()) {
                if (file.isDirectory()) {
                    moveDirectory(file, destinationPath + "/" + file.getName());
                } else if (file.isFile()) {
                    moveFile(file, destinationPath + "/" + file.getName());
                }
            }
            sourceDirectory.delete();
        }
    }

    public static File getFolderGeneratedByZip(String zipFileDestination){
        File folder = new File(zipFileDestination);
        if(!folder.exists()) folder.mkdirs();
        if(folder.listFiles() == null ) return null;
        File[] folders = folder.listFiles(File::isDirectory);
        if(folders == null) return null;
        return folders[0];
    }

    public static ZipFile unzipFile(File file, String destinationPath) throws ZipException {
        if(file == null) return null;
        ZipFile zip = new ZipFile(file);
        zip.extractAll(destinationPath);
        return zip;
    }

    public static void moveFile(File sourceFile, String destinationPath) {
        File destinationFile = new File(destinationPath);
        sourceFile.renameTo(destinationFile);
    }
}
