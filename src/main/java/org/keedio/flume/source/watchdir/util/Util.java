package org.keedio.flume.source.watchdir.util;

import org.keedio.flume.source.watchdir.InodeInfo;
import org.keedio.flume.source.watchdir.WatchDirException;
import org.keedio.flume.source.watchdir.listener.simpletxtsource.FileEventHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

/**
 * Created by rolmo on 16/12/15.
 */
public class Util {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(Util.class);
    
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