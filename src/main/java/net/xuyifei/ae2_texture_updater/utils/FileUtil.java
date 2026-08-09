package net.xuyifei.ae2_texture_updater.utils;

import java.io.File;

public class FileUtil {
    public static boolean deleteDirectory(File directory) {
        if (!directory.exists()) {
            return false;
        }

        if (!directory.isDirectory()) {
            return false;
        }

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

        return directory.delete();
    }

    public static boolean cleanDirectory(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return false;
        }

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

        return true;
    }
}
