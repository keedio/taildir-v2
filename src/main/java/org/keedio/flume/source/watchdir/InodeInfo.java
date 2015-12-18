package org.keedio.flume.source.watchdir;

import java.io.Serializable;

/**
 * Created by rolmo on 17/12/15.
 */
public class InodeInfo implements Serializable {

    private String fileName;
    private Long position;
    private boolean process;

    public InodeInfo(Long position, String fileName) {
        this.position = position;
        this.fileName = fileName;
        this.process = false;
    }

    public Long getPosition() {
        return position;
    }

    public void setPosition(Long position) {
        this.position = position;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean isProcess() {
        return process;
    }

    public void setProcess(boolean process) {
        this.process = process;
    }
}
