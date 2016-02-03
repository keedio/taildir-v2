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

	private static final Map<String, String> PATH_TO_INODES = new HashMap<>();

  public FileEventHelper(FileEventSourceListener listener) {
		this.listener = listener;
		this.buffer = new ArrayList<>();
	}
  
  public synchronized ArrayList<Event> getBuffer() {
    return buffer;
  }
  
	public void process(WatchDirEvent event) {
		try {
			Date inicio = new Date();
			int procesados = 0;
			
			readLines(event);
			
			long intervalo = new Date().getTime() - inicio.getTime();

			// Notificamos el tiempo de procesado para las metricas
			listener.getMetricsController().manage(new MetricsEvent(MetricsEvent.MEAN_FILE_PROCESS, intervalo));
			listener.getMetricsController().manage(new MetricsEvent(MetricsEvent.TOTAL_FILE_EVENTS, procesados));
			
		} catch (Exception e) {
			LOGGER.error("Error procesando el fichero: " + event.getPath());
			LOGGER.error(e.getMessage());
			e.printStackTrace();
		}
	}

	public void commitPendings() {
    listener.getChannelProcessor().processEventBatch(getBuffer());
    getBuffer().clear();
	}
	
	private void readLines(WatchDirEvent event) throws Exception {

	  String path = event.getPath();

		String inode = Util.getInodeID(path);

		synchronized (PATH_TO_INODES){
			if (!PATH_TO_INODES.containsKey(path)){
				PATH_TO_INODES.put(path, inode);
			} else if (!PATH_TO_INODES.get(path).equals(inode)) {
				LOGGER.error("Inode num changed for " + path + ", oldInode: " + PATH_TO_INODES.get(path) + ", newInode: " + inode);
			}
		}

		LOGGER.debug("ENTRAMOS EN EL HELPER......");
		//
		Long lastByte = 0L;
		if (listener.getFilesObserved().containsKey(inode)) {

			synchronized (listener.getFilesObserved().get(inode)) {
				LOGGER.debug(String.format("Acquired lock on: %s", path));
				processInode(path, inode);
			}
		} else {
			// Probablemente se ha producido algún fallo de lo que no nos podamos recuperar
			// Ponemos el contador de eventos al final del del fichero
			LOGGER.debug("No se encontraba el registro en la tabla de contadores de lineas.");
			lastByte = getBytesSize(path);
			
			// seteamos el contador
			InodeInfo inodeInfo = new InodeInfo(0L, path);
			listener.getFilesObserved().put(inode, inodeInfo);
			
			return;
		}

	}

	private void processInode(String path, String inode) throws IOException {
		Long lastByte = listener.getFilesObserved().get(inode).getPosition();

		if (lastByte < 0) {
			LOGGER.debug(String.format("Negative lastByte: %d", lastByte));
			return;
		}

		Long fileSize = getBytesSize(path);

		if (fileSize < lastByte){
			LOGGER.debug(String.format("File size decreased. lastByte: %d, realFileSize: %d", lastByte, fileSize));

			listener.getFilesObserved().get(inode).setPosition(0L);
		}

		try (FileInputStream fis = new FileInputStream(new File(path))){
			fis.skip(lastByte);

			List<String> linesPending = IOUtils.readLines(fis);

				for (String line : linesPending) {
					// Find the gap
					if (!line.contains("{")) {
						LOGGER.debug("---KO: " + path + "|||" + line + "|||" + lastByte);
						try {
							FileUtils.copyFile(new File(path), new File("/tmp/" + path));
							FileUtils.copyFile(new File(path + ".1"), new File("/tmp/" + path + ".1"));
						} catch (Exception e) {
							LOGGER.debug("No se puedieron copiar los ficheros de respaldo. Continuamos");
						}
					} else {
						LOGGER.debug("---OK: " + path + "|||" + line + "|||" + lastByte);
					}

					//LOGGER.debug("OK: " + line);
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

					//InodeInfo inodeInfo = new InodeInfo(lastByte, path);
					//listener.getFilesObserved().put(inode, inodeInfo);
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
