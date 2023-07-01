package io.github.xaxisplayz.autopluginswitcher.util;

import java.io.File;

public class FileUtil {
    public static void deleteFile(File file) {
        if (file.exists()) {
            file.delete();
        }
    }

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
            directory.delete();
        }
    }

    public static void moveDirectory(File sourceDirectory, String destinationPath) {

        File destinationDirectory = new File(destinationPath);

        if (sourceDirectory.exists()) {

            if (!destinationDirectory.exists()) destinationDirectory.mkdirs();

            for (File file : sourceDirectory.listFiles()) {
                if (file.isDirectory()) {
                    moveDirectory(file, destinationPath + File.separator + file.getName());
                } else {
                    moveFile(file, destinationPath + File.separator + file.getName());
                }
            }
            sourceDirectory.delete();
        }
    }

    public static void moveFile(File sourceFile, String destinationPath) {
        File destinationFile = new File(destinationPath);
        if (sourceFile.exists()) {
            if (!destinationFile.getParentFile().exists()) {
                destinationFile.getParentFile().mkdirs();
            }
            sourceFile.renameTo(destinationFile);
        }
    }
}
