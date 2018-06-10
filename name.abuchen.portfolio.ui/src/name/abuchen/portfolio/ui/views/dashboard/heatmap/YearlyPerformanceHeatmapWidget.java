package name.abuchen.portfolio.ui.views.dashboard.heatmap;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import name.abuchen.portfolio.model.Dashboard.Widget;
import name.abuchen.portfolio.snapshot.PerformanceIndex;
import name.abuchen.portfolio.snapshot.ReportingPeriod;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.views.dashboard.DashboardData;
import name.abuchen.portfolio.ui.views.dashboard.MultiDataSeriesConfig;
import name.abuchen.portfolio.ui.views.dashboard.ReportingPeriodConfig;
import name.abuchen.portfolio.ui.views.dataseries.DataSeries;
import name.abuchen.portfolio.util.Interval;

public class YearlyPerformanceHeatmapWidget extends AbstractHeatmapWidget
{
    public YearlyPerformanceHeatmapWidget(Widget widget, DashboardData data)
    {
        super(widget, data);

        addConfig(new MultiDataSeriesConfig(this));
    }

    @Override
    protected HeatmapModel build()
    {
        // fill the table lines according to the supplied period
        // calculate the performance with a temporary reporting period
        // calculate the color interpolated between red and green with yellow as
        // the median
        Interval interval = get(ReportingPeriodConfig.class).getReportingPeriod().toInterval();

        List<DataSeries> dataSeries = get(MultiDataSeriesConfig.class).getDataSeries();

        // adapt interval to include the first and last year fully

        Interval calcInterval = Interval.of(
                        interval.getStart().getDayOfYear() == interval.getStart().lengthOfYear() ? interval.getStart()
                                        : interval.getStart().withDayOfYear(1).minusDays(1),
                        interval.getEnd().withDayOfYear(interval.getEnd().lengthOfYear()));

        HeatmapModel model = new HeatmapModel();
        model.setCellToolTip(Messages.YearlyPerformanceHeatmapToolTip);

        // add header
        for (DataSeries s : dataSeries)
            model.addHeader(s.getLabel());

        int numDashboardColumns = getDashboardData().getDashboard().getColumns().size();

        for (Integer year : calcInterval.iterYears())
        {
            String label = numDashboardColumns > 2 ? String.valueOf(year % 100) : String.valueOf(year);
            HeatmapModel.Row row = new HeatmapModel.Row(label);

            // yearly data
            for (DataSeries series : dataSeries)
            {
                PerformanceIndex performanceIndex = getDashboardData().calculate(series,
                                new ReportingPeriod.YearX(year));
                row.addData(performanceIndex.getFinalAccumulatedPercentage());
            }

            model.addRow(row);
        }

        // add sum
        if (get(HeatmapOrnamentConfig.class).getValues().contains(HeatmapOrnament.SUM))
        {
            HeatmapModel.Row row = new HeatmapModel.Row("\u03A3"); //$NON-NLS-1$
            for (DataSeries series : dataSeries)
            {
                PerformanceIndex performanceIndex = getDashboardData().calculate(series,
                                new ReportingPeriod.FromXtoY(calcInterval));
                row.addData(performanceIndex.getFinalAccumulatedPercentage());
            }
            model.addRow(row);
        }

        // add geometric mean
        if (get(HeatmapOrnamentConfig.class).getValues().contains(HeatmapOrnament.GEOMETRIC_MEAN))
        {
            model.addHeader("x\u0304 geom"); //$NON-NLS-1$
            model.getRows().forEach(r -> r
                            .addData(geometricMean(r.getData().filter(Objects::nonNull).collect(Collectors.toList()))));
        }

        return model;
    }
}
