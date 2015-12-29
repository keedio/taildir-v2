package org.keedio.flume.source.watchdir.listener.simpletxtsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.flume.Event;
import org.apache.flume.event.EventBuilder;
import org.keedio.flume.source.watchdir.FileUtil;
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
		}
	}

	public void commitPendings() {
    listener.getChannelProcessor().processEventBatch(getBuffer());
    getBuffer().clear();
	}
	
	private void readLines(WatchDirEvent event) throws Exception {

	  String path = event.getPath();
		BufferedReader lReader = new BufferedReader(new FileReader(new File(path)));
		String inode = Util.getInodeID(path);
		
		//
		Long lastByte = 0L;
		if (listener.getFilesObserved().containsKey(inode))
			lastByte = listener.getFilesObserved().get(inode).getPosition();
		else {
			// Probablemente se ha producido algún fallo de lo que no nos podamos recuperar
			// Ponemos el contador de eventos al final del del fichero
			LOGGER.debug("No se encontraba el registro en la tabla de contadores de lineas.");
			lastByte = getBytesSize(path);
			
			// seteamos el contador
			InodeInfo inodeInfo = new InodeInfo(lastByte, path);
			listener.getFilesObserved().put(inode, inodeInfo);
			
			return;
		}
		
		if (lastByte < 0) {
			LOGGER.error("Se esta intentando procesar un fichero procedente del rotado");
			return;
		}
		
		lReader.skip(lastByte);
		
		try {
			int lines = 0;
			String line;
			while ((line = lReader.readLine())!=null) {
				Event ev = EventBuilder.withBody(line.getBytes());
				
				// Put header props
				Map<String,String> headers = new HashMap<String, String>();
				if (listener.fileHeader)
					headers.put(listener.fileHeaderName, event.getPath());
				if (listener.baseHeader)
					headers.put(listener.baseHeaderName, new File(event.getPath()).getName());
				if (!headers.isEmpty())
					ev.setHeaders(headers);				
				
				
				getBuffer().add(ev);
				
				lastByte =  lastByte + line.length() + 1;
				
				InodeInfo inodeInfo = new InodeInfo(lastByte, path);
				listener.getFilesObserved().put(inode, inodeInfo);

	    	// Notificamos un evento de nuevo mensaje
	    	listener.getMetricsController().manage(new MetricsEvent(MetricsEvent.NEW_EVENT));
	    	
	    	if (getBuffer().size() > listener.eventsCapacity) {
	    	  listener.getChannelProcessor().processEventBatch(getBuffer());
	    	  getBuffer().clear();
	    	}
	    	
			}
		} catch (IOException e) {
			LOGGER.error("Error al procesar el fichero: " + event.getPath(), e);
			throw e;
		} finally {
			lReader.close();
		}
	}
	
	public long getBytesSize(String filename)
	{
        File url = null;

        url = new File(filename);
            
        return url.length();

    }	
}
