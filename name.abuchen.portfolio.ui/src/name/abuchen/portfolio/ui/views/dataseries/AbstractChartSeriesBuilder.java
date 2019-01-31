package name.abuchen.portfolio.ui.views.dataseries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.swt.graphics.Color;
import org.swtchart.IBarSeries;
import org.swtchart.ILineSeries;

import name.abuchen.portfolio.ui.util.chart.TimelineChart;

public abstract class AbstractChartSeriesBuilder
{
    private final TimelineChart chart;
    private final DataSeriesCache cache;
    private final LocalResourceManager resources;
    private final List<BiConsumer<DataSeries, double[]>> listeners = new ArrayList<>();

    public AbstractChartSeriesBuilder(TimelineChart chart, DataSeriesCache cache)
    {
        this.chart = chart;
        this.cache = cache;

        this.resources = new LocalResourceManager(JFaceResources.getResources(), chart);
    }
    
    /**
     * Add listener for value changes.
     * 
     * @param listener
     *            listener
     */
    public void addValuesListener(BiConsumer<DataSeries, double[]> listener)
    {
        this.listeners.add(listener);
    }

    /**
     * Fire value change event to listeners.
     * 
     * @param series
     *            {@link DataSeries}
     * @param values
     *            values
     */
    protected void fireValuesUpdate(DataSeries series, double[] values)
    {
        for (BiConsumer<DataSeries, double[]> listener : listeners)
        {
            listener.accept(series, values);
        }
    }

    public DataSeriesCache getCache()
    {
        return cache;
    }

    public TimelineChart getChart()
    {
        return chart;
    }

    protected void configure(DataSeries series, ILineSeries lineSeries)
    {
        Color color = resources.createColor(series.getColor());

        lineSeries.setLineColor(color);
        lineSeries.setSymbolColor(color);
        lineSeries.enableArea(series.isShowArea());
        lineSeries.setLineStyle(series.getLineStyle());
        
        fireValuesUpdate(series, lineSeries.getYSeries());
    }

    protected void configure(DataSeries series, IBarSeries barSeries)
    {
        barSeries.setBarPadding(50);
        barSeries.setBarColor(resources.createColor(series.getColor()));
        
        fireValuesUpdate(series, barSeries.getYSeries());
    }
}
