package org.keedio.flume.source.watchdir.listener.xmlsource;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.flume.Event;
import org.apache.flume.event.EventBuilder;
import org.keedio.flume.source.watchdir.WatchDirEvent;
import org.keedio.flume.source.watchdir.metrics.MetricsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This worker proccess the xml file in order to extract the expeted events.
 * @author rolmo
 *
 */
public class WatchDirXMLWinWorker implements Runnable {

	private static final Logger LOGGER = LoggerFactory
			.getLogger(WatchDirXMLWinWorker.class);

	WatchDirXMLWinEventSourceListener listener;
	WatchDirEvent event;
	
	public WatchDirXMLWinWorker(WatchDirXMLWinEventSourceListener listener, WatchDirEvent event) {
		this.listener = listener;
		this.event = event;
	}
	
	@Override
	public void run() {
		try {
			String tagName = listener.dirProps.get(event.getSet().getPath()).getProperty("tagName");
			int tagLevel = Integer.parseInt(listener.dirProps.get(event.getSet().getPath()).getProperty("tagLevel"));
			
			int level = 0;
			Date inicio = new Date();
			int procesados = 0;
			XMLInputFactory xif = XMLInputFactory.newInstance();
			xif.setProperty("javax.xml.stream.isNamespaceAware", false);
			
			URL url = new URL("file://" + event.getPath());
			InputStream is = new BufferedInputStream(url.openStream(), listener.bufferSize);
			XMLEventReader xev = xif.createXMLEventReader(is);
			
			while (xev.hasNext()) {
			    XMLEvent xmlEvent = xev.nextEvent();
			    if (xmlEvent.isStartElement()) {
			        StartElement elem = xmlEvent.asStartElement();
			        String name = elem.getName().getLocalPart();

			        if (tagName.equals(name)) {
			        	level++;
			        	
			        	if (level == tagLevel) {
				        	StringBuilder buf = new StringBuilder();
				            String xmlFragment = readElementBody(xev);
				            // lanzamos el evento a la canal flume
				            buf.append("<" + tagName + ">").append(xmlFragment).append("</" + tagName + ">");
				            
				            procesados++;
				            
				    		Event ev = EventBuilder.withBody(String.valueOf(buf).getBytes());
				    		// Calls to getChannelProccesor are synchronyzed
				    		listener.getChannelProcessor().processEvent(ev);
				            
				    		// Notificamos un evento de nuevo mensaje
				    		listener.getMetricsController().manage(new MetricsEvent(MetricsEvent.NEW_EVENT));
			        	} else {
			        		LOGGER.debug("Watiing for proper level");
			        	}
			            
			        }
			    }			
			    if (xmlEvent.isEndElement()) {
			        EndElement elem = xmlEvent.asEndElement();
			        String name = elem.getName().getLocalPart();

			        if (tagName.equals(name)) {
			        	level--;
			        }
			    }			

			}
			
			long intervalo = new Date().getTime() - inicio.getTime();
			// Se usa el system out para procesar los test de forma correcta
			System.out.println("Se han procesado " + procesados + " elementos en " + intervalo + " milisegundos");

			// Notificamos el tiempo de procesado para las metricas
			listener.getMetricsController().manage(new MetricsEvent(MetricsEvent.MEAN_FILE_PROCESS, intervalo));
			listener.getMetricsController().manage(new MetricsEvent(MetricsEvent.TOTAL_FILE_EVENTS, procesados));
			
		} catch (Exception e) {
			LOGGER.error("Error procesando el fichero: " + event.getPath());
			LOGGER.error("Se espera fichero XML");
			
			LOGGER.error(e.getMessage());
		}
	}

	private String readElementBody(XMLEventReader eventReader)
			throws XMLStreamException {
		StringWriter buf = new StringWriter(1024);

		int depth = 0;
		while (eventReader.hasNext()) {
			// peek event
			XMLEvent xmlEvent = eventReader.peek();

			if (xmlEvent.isStartElement()) {
				++depth;
			}
			else if (xmlEvent.isEndElement()) {
				--depth;

				// reached END_ELEMENT tag?
						// break loop, leave event in stream
				if (depth < 0)
					break;
			}

			// consume event
			xmlEvent = eventReader.nextEvent();

			// print out event
			xmlEvent.writeAsEncodedUnicode(buf);
		}

		return buf.getBuffer().toString();
	}

}
