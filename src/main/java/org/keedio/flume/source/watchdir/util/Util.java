package org.keedio.flume.source.watchdir.util;

import org.keedio.flume.source.watchdir.WatchDirException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Created by rolmo on 16/12/15.
 */
public class Util {

    public static String getInodeID(String file) throws WatchDirException {

        BasicFileAttributes attr = null;
        Path path = Paths.get(file);
        try {
            attr = Files.readAttributes(path, BasicFileAttributes.class);
        } catch (Exception e) {
            throw new WatchDirException(e.getMessage());
        }

        Object fileKey = attr.fileKey();
        String s = fileKey.toString();
        String inode = s.substring(s.indexOf("ino=") + 4, s.indexOf(")"));

        return inode;
    }
}