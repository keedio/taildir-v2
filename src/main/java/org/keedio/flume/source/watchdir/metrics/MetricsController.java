package org.keedio.flume.source.watchdir.metrics;

import org.apache.flume.instrumentation.MonitoredCounterGroup;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

/**
*This class represents the controller metrics to publish to the source. 
*Extends MonitoredCounterGroup class to allow the publication of JMX metrics 
*following the mechanism established by Flume. 
*/
public class MetricsController extends MonitoredCounterGroup implements MetricsMBean {

	private Meter meterEvents;
	private Meter meterFiles;
	private Histogram meanProcessTime;
	private Histogram totalFileEvents;
	private MetricRegistry metrics;	
	
	private static final String[] ATTRIBUTES = {
			"source.meter.events",
			"source.meter.events.fifteen.minute.rate",
			"source.meter.events.five.minute.rate",
			"source.meter.events.one.minute.rate",
			"source.meter.events.mean.rate",
			"source.meter.files",
			"source.meter.files.fifteen.minute.rate",
			"source.meter.files.five.minute.rate",
			"source.meter.files.one.minute.rate",
			"source.meter.files.mean.rate",
			"source.mean.process.time",
			"source.99th.percentile.process.time",
			"source.75th.percentile.process.time",
			"source.max.process.time",
			"source.min.process.time",
			"source.median.process.time",
			"source.total.file.events",
			"source.99th.percentile.file.events",
			"source.75th.percentile.file.events",
			"source.max.file.events",
			"source.min.file.events",
			"source.median.file.events"
	};
	
	public MetricsController(String name) {
		super(MonitoredCounterGroup.Type.SOURCE, name, ATTRIBUTES);
		
		metrics = new MetricRegistry();
		meterEvents = metrics.meter("events");
		meterFiles = metrics.meter("files");
		meanProcessTime = metrics.histogram("meanProcessTime");
		totalFileEvents = metrics.histogram("totalFileEvents");
	}

	/**
	 * This method manages metric based on events received.
	 * <p>
	 * For new metrics will need to create the corresponding event type in 
	 * MetricsEvent class and then define their behavior here
	 * @param  event event to manage.
	 */
	public void manage(MetricsEvent event) {
		switch (event.getCode()) {
		case MetricsEvent.NEW_FILE:
			meterFiles.mark();
			break;
		case MetricsEvent.NEW_EVENT:
			meterEvents.mark();
			break;
		case MetricsEvent.TOTAL_FILE_EVENTS:
			totalFileEvents.update(event.getValue());
			break;
		case MetricsEvent.MEAN_FILE_PROCESS:
			meanProcessTime.update(event.getValue());
			break;
		default:
			throw new IllegalStateException();
		}
	}

	@Override
	public long getTotalEvents() {
		return meterEvents.getCount();
	}

	@Override
	public double getEventsFifteenMinuteRate() {
		return meterEvents.getFifteenMinuteRate();
	}

	@Override
	public double getEventsFiveMinuteRate() {
		return meterEvents.getFiveMinuteRate();
	}

	@Override
	public double getEventsOneMinuteRate() {
		return meterEvents.getOneMinuteRate();
	}

	@Override
	public double getEventsMeanRate() {
		return meterEvents.getMeanRate();
	}

	@Override
	public long getTotalFiles() {
		return meterFiles.getCount();
	}

	@Override
	public double getFilesFifteenMinuteRate() {
		return meterFiles.getFifteenMinuteRate();
	}

	@Override
	public double getFilesFiveMinuteRate() {
		return meterFiles.getFiveMinuteRate();
	}

	@Override
	public double getFilesOneMinuteRate() {
		return meterFiles.getOneMinuteRate();
	}

	@Override
	public double getFilesMeanRate() {
		return meterFiles.getMeanRate();
	}

	@Override
	public double getMeanProcessTime() {


		return meanProcessTime.getSnapshot().getMean();
	}

	@Override
	public double get99thPercentileProcessTime() {
		return meanProcessTime.getSnapshot().get99thPercentile();
	}

	@Override
	public double get75thPercentileProcessTime() {
		return meanProcessTime.getSnapshot().get75thPercentile();
	}

	@Override
	public long getMaxProcessTime() {
		return meanProcessTime.getSnapshot().getMax();
	}

	@Override
	public long getMinProcessTime() {
		return meanProcessTime.getSnapshot().getMin();
	}

	@Override
	public double getMedianProcessTime() {
		return meanProcessTime.getSnapshot().getMedian();
	}

	@Override
	public double getMeanFileEvents() {
		return totalFileEvents.getSnapshot().getMean();
	}

	@Override
	public double get99thPercentileFileEvents() {
		return totalFileEvents.getSnapshot().get99thPercentile();
	}

	@Override
	public double get75thPercentileFileEvents() {
		return totalFileEvents.getSnapshot().get75thPercentile();
	}

	@Override
	public long getMaxFileEvents() {
		return totalFileEvents.getSnapshot().getMax();
	}

	@Override
	public long getMinFileEvents() {
		return totalFileEvents.getSnapshot().getMin();
	}

	@Override
	public double getMedianFileEvents() {
		return totalFileEvents.getSnapshot().getMedian();
	}
}
