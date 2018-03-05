package org.keedio.flume.souce.watchdir.metrics;

import junit.framework.Assert;

import org.junit.Test;
import org.keedio.flume.source.watchdir.metrics.MetricsController;
import org.keedio.flume.source.watchdir.metrics.MetricsEvent;

public class MetricsControllerTest {

	
	@Test
	public void testManageTotalFiles() {
		// Arrange
		MetricsController mc = new MetricsController("testManageTotalFiles");
		MetricsEvent ev = new MetricsEvent(MetricsEvent.NEW_FILE);
		
		// Act
		mc.manage(ev);
		mc.manage(ev);
		
		// Assert
		Assert.assertEquals("Se ha manejado el evento de forma correcta", 2, mc.getTotalFiles());
	}

	@Test
	public void testManageTotalEvents() {
		// Arrange
		MetricsController mc = new MetricsController("testManageTotalEvents");
		MetricsEvent ev = new MetricsEvent(MetricsEvent.NEW_EVENT);
		
		// Act
		mc.manage(ev);
		mc.manage(ev);
		
		// Assert
		Assert.assertEquals("Se ha manejado el evento de forma correcta", 2, mc.getTotalEvents());
	}

	@Test
	public void testManageMeanEventFiles() {
		// Arrange
		MetricsController mc = new MetricsController("testManageMeanEventFiles");
		MetricsEvent ev1 = new MetricsEvent(MetricsEvent.TOTAL_FILE_EVENTS, 30);
		MetricsEvent ev2 = new MetricsEvent(MetricsEvent.TOTAL_FILE_EVENTS, 30);
		
		// Act
		mc.manage(ev1);
		mc.manage(ev2);
		
		// Assert
		// Ojo, no se almacenan todos los valores y por tanto el valor no se corresponde exactamente
		// a la media. Es una distribución normal bajo cierto intervalo de confianza
		Assert.assertTrue("Se ha manejado el evento de forma correcta", Math.abs(mc.getMeanFileEvents()-30)<1);
	}

}
