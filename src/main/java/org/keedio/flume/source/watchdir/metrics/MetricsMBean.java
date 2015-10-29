package org.keedio.flume.source.watchdir.metrics;

import javax.management.MXBean;

@MXBean
public interface MetricsMBean {

	
	long getTotalEvents();
	double getEventsFifteenMinuteRate();
	double getEventsFiveMinuteRate();
	double getEventsOneMinuteRate();
	double getEventsMeanRate();

	long getTotalFiles();
	double getFilesFifteenMinuteRate();
	double getFilesFiveMinuteRate();
	double getFilesOneMinuteRate();
	double getFilesMeanRate();

	double getMeanProcessTime();
	double get99thPercentileProcessTime();
	double get75thPercentileProcessTime();
	long getMaxProcessTime();
	long getMinProcessTime();
	double getMedianProcessTime();


	double getMeanFileEvents();
	double get99thPercentileFileEvents();
	double get75thPercentileFileEvents();
	long getMaxFileEvents();
	long getMinFileEvents();
	double getMedianFileEvents();
}
