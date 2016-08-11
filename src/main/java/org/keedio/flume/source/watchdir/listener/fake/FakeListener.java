package org.keedio.flume.source.watchdir.listener.fake;

import org.keedio.flume.source.watchdir.WatchDirEvent;
import org.keedio.flume.source.watchdir.WatchDirException;
import org.keedio.flume.source.watchdir.WatchDirListener;

/**
 * 
 * Very simple fake example. Implements WatchDirListener
 *
 */
public class FakeListener implements WatchDirListener {

	@Override
	public void process(WatchDirEvent event) throws WatchDirException {
		System.out.println("Got event: " + event.getPath() + " " + event.getType());
	}
}
