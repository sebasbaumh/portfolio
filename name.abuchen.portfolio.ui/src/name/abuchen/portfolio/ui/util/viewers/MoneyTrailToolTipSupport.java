package name.abuchen.portfolio.ui.util.viewers;

import java.util.Optional;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;

import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.trail.Trail;
import name.abuchen.portfolio.snapshot.trail.TrailProvider;
import name.abuchen.portfolio.snapshot.trail.TrailRecord;
import name.abuchen.portfolio.ui.Messages;

public class MoneyTrailToolTipSupport extends ColumnViewerToolTipSupport
{
    protected MoneyTrailToolTipSupport(ColumnViewer viewer, int style, boolean manualActivation)
    {
        super(viewer, style, manualActivation);
    }

    public static final void enableFor(ColumnViewer viewer, int style)
    {
        new MoneyTrailToolTipSupport(viewer, style, false);
    }

    @Override
    protected Composite createViewerToolTipContentArea(Event event, ViewerCell cell, Composite parent)
    {
        Object element = cell.getElement();

        if (!(element instanceof TrailProvider))
            return super.createViewerToolTipContentArea(event, cell, parent);

        Optional<Trail> trail = ((TrailProvider) element).explain(getText(event));

        if (!trail.isPresent())
            return super.createViewerToolTipContentArea(event, cell, parent);

        return createTrailTable(parent, trail.get());
    }

    @Override
    public boolean isHideOnMouseDown()
    {
        return false;
    }

    private Composite createTrailTable(Composite parent, Trail trail)
    {
        int depth = depth(1, trail.getRecord());

        Composite composite = new Composite(parent, SWT.NONE);
        GridLayoutFactory.swtDefaults().numColumns(depth + 3).applyTo(composite);

        Label heading = new Label(composite, SWT.NONE);
        GridDataFactory.fillDefaults().span(depth + 3, 1).applyTo(heading);
        heading.setText(trail.getLabel());

        addRow(composite, trail.getRecord(), depth - 1, depth);

        return composite;
    }

    private void addRow(Composite composite, TrailRecord trail, int level, int depth)
    {
        for (TrailRecord child : trail.getInputs())
            addRow(composite, child, level - 1, depth);

        Label date = new Label(composite, SWT.NONE);
        if (trail.getDate() != null)
            date.setText(Values.Date.format(trail.getDate()));

        Label label = new Label(composite, SWT.NONE);
        label.setText(trail.getLabel());

        Label shares = new Label(composite, SWT.RIGHT);
        GridDataFactory.fillDefaults().applyTo(shares);
        if (trail.getShares() != null)
            shares.setText(Values.Share.format(trail.getShares()));

        for (int index = 0; index < depth; index++)
        {
            Label column = new Label(composite, SWT.RIGHT);
            GridDataFactory.fillDefaults().applyTo(column);

            if (index == level)
                column.setText(trail.getValue() != null ? Values.Money.format(trail.getValue())
                                : Messages.LabelNotAvailable);
        }
    }

    private int depth(int level, TrailRecord t)
    {
        if (t.getInputs().isEmpty())
            return level;

        int d = level;

        for (TrailRecord child : t.getInputs())
            d = Math.max(d, depth(level + 1, child));

        return d;
    }
}
