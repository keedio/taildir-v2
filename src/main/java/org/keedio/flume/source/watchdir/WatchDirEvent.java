package org.keedio.flume.source.watchdir;

/**
 * 
 * Events to be launch between components
 *
 */
public class WatchDirEvent {

	private int type;
	private String path;
	private WatchDirFileSet set;
	private String oldPath;
	
	public WatchDirEvent(String path, String oldPath, int type, WatchDirFileSet set) {
		this.type = type;
		this.path = path;
		this.set = set;
		this.oldPath = oldPath;
	}
	
	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public WatchDirFileSet getSet() {
		return set;
	}

	public void setSet(WatchDirFileSet set) {
		this.set = set;
	}

	public String getOldPath() {
		return oldPath;
	}

	public void setOldPath(String oldPath) {
		this.oldPath = oldPath;
	}
}
