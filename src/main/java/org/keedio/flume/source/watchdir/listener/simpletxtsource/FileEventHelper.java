package org.keedio.flume.source.watchdir.listener.simpletxtsource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.flume.Event;
import org.apache.flume.event.EventBuilder;
import org.keedio.flume.source.watchdir.InodeInfo;
import org.keedio.flume.source.watchdir.WatchDirEvent;
import org.keedio.flume.source.watchdir.metrics.MetricsEvent;
import org.keedio.flume.source.watchdir.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This worker proccess the xml file in order to extract the expeted events.
 * @author rolmo
 *
 */
public class FileEventHelper {

	private static final Logger LOGGER = LoggerFactory
			.getLogger(FileEventHelper.class);

	FileEventSourceListener listener;
	private ArrayList<Event> buffer;

  public FileEventHelper(FileEventSourceListener listener) {
		this.listener = listener;
		this.buffer = new ArrayList<>();
	}
  
  public synchronized ArrayList<Event> getBuffer() {
    return buffer;
  }
  
	public void process(String inode) {
	  String path = "";
		try {
			Date inicio = new Date();
			int procesados = 0;
			path = this.listener.getFilesObserved().get(inode).getFileName();
			
			readLines(inode);
			
			long intervalo = new Date().getTime() - inicio.getTime();

			// Notificamos el tiempo de procesado para las metricas
			listener.getMetricsController().manage(new MetricsEvent(MetricsEvent.MEAN_FILE_PROCESS, intervalo));
			listener.getMetricsController().manage(new MetricsEvent(MetricsEvent.TOTAL_FILE_EVENTS, procesados));
			
		} catch (Exception e) {
			LOGGER.error("Error procesando el fichero: " + path);
			LOGGER.error(e.getMessage());
			e.printStackTrace();
		}
	}

	public void commitPendings() {
    listener.getChannelProcessor().processEventBatch(getBuffer());
    getBuffer().clear();
	}
	
	private void readLines(String inode) throws Exception {

	  String realInode = Util.getInodeID(this.listener.getFilesObserved().get(inode).getFileName());
	  
		LOGGER.debug("ENTRAMOS EN EL HELPER......");
		//
		Long lastByte = 0L;
		processInode(this.listener.getFilesObserved().get(inode).getFileName(), inode);

	}

	private void processInode(String path, String inode) throws Exception {
		Long lastByte = listener.getFilesObserved().get(inode).getPosition();

		if (lastByte < 0) {
			LOGGER.debug(String.format("Negative lastByte: %d", lastByte));
			return;
		}

		Long fileSize = getBytesSize(path);

		try (FileInputStream fis = new FileInputStream(new File(path))){		  
			
		  List<String> linesPending;
		  if (lastByte > 0) {
	      fis.skip(lastByte -1);
	      
	      linesPending = IOUtils.readLines(fis);

		  } else {
        fis.skip(lastByte);
        
        linesPending = IOUtils.readLines(fis);		    
		  }

						
				for (String line : linesPending) {
				  // Find the gap
          LOGGER.debug("MENSAJE:: " + line);
					if (!line.contains("{") && !line.contains("}")) {
					  continue;
					}

					Event ev = EventBuilder.withBody(line.getBytes());

					// Put header props
					Map<String, String> headers = new HashMap<String, String>();
					if (listener.fileHeader)
						headers.put(listener.fileHeaderName, path);
					if (listener.baseHeader)
						headers.put(listener.baseHeaderName, new File(path).getName());
					if (!headers.isEmpty())
						ev.setHeaders(headers);


					getBuffer().add(ev);

					lastByte = lastByte + line.length() + 1;

					listener.getFilesObserved().get(inode).setPosition(lastByte);


					// Notificamos un evento de nuevo mensaje
					listener.getMetricsController().manage(new MetricsEvent(MetricsEvent.NEW_EVENT));

					if (getBuffer().size() > listener.eventsCapacity) {
						listener.getChannelProcessor().processEventBatch(getBuffer());
						getBuffer().clear();
					}

				}
		} catch (IOException e) {
			LOGGER.error("Error al procesar el fichero: " + path, e);
			throw e;
		} 
	}

	public long getBytesSize(String filename)
	{
        File url = null;

        url = new File(filename);
            
        return url.length();

    }	
}
