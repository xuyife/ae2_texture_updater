package net.xuyifei.ae2_texture_updater.modification;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarExtractor {
    public static void extractJar(String jarPath, String folderPath, String destDir) throws IOException {
        String folderToExtract = folderPath.endsWith("/") ? folderPath : folderPath + "/";

        String folderName = getFolderName(folderPath);

        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (entryName.startsWith(folderToExtract) &&
                        !entryName.equals(folderToExtract)) {

                    String relativePath = entryName.substring(folderToExtract.length());

                    File destFile = new File(destDir, folderName + File.separator + relativePath);

                    if (entry.isDirectory()) {
                        destFile.mkdirs();
                    } else {
                        destFile.getParentFile().mkdirs();

                        try (InputStream in = jar.getInputStream(entry);
                             OutputStream out = new FileOutputStream(destFile)) {

                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                        }
                    }
                }
            }
        }
    }

    public static List<String> listSubDirectories(String jarPath, String directoryPath) throws IOException {
        List<String> subDirs = new ArrayList<>();
        Set<String> uniqueDirs = new HashSet<>();

        String targetDir = directoryPath.endsWith("/") ?
                directoryPath : directoryPath + "/";

        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (entryName.startsWith(targetDir) &&
                        entry.isDirectory() &&
                        !entryName.equals(targetDir)) {

                    String relativePath = entryName.substring(targetDir.length());

                    int slashIndex = relativePath.indexOf('/');
                    if (slashIndex > 0) {
                        String directSubDir = relativePath.substring(0, slashIndex);
                        uniqueDirs.add(directSubDir);
                    } else if (!relativePath.isEmpty() && relativePath.endsWith("/")) {
                        String directSubDir = relativePath.substring(0, relativePath.length() - 1);
                        uniqueDirs.add(directSubDir);
                    }
                }
            }
        }

        subDirs.addAll(uniqueDirs);
        Collections.sort(subDirs);
        return subDirs;
    }

    private static String getFolderName(String folderPath) {
        String path = folderPath.endsWith("/") ?
                folderPath.substring(0, folderPath.length() - 1) : folderPath;

        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
}
