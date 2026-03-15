package name.abuchen.portfolio.ui.util.viewers;

import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.ViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.TreeItem;

import name.abuchen.portfolio.PortfolioLog;

/**
 * ColumnLabelProvider subclass which allows to completely override rendering of
 * cell foreground, e.g. draw graphics, etc.
 */
public class ParameterizedOwnerDrawLabelProvider<O> extends ParameterizedColumnLabelProvider<O> implements Listener
{
    @Override
    protected void initialize(ColumnViewer viewer, ViewerColumn column)
    {
        Control control = viewer.getControl();
        control.addListener(SWT.MeasureItem, this);
        control.addListener(SWT.EraseItem, this);
        control.addListener(SWT.PaintItem, this);
        super.initialize(viewer, column);
    }

    /**
     * Measure size of contents - optional to override.
     */
    public void measure(Event event)
    {
    }

    /**
     * Actually paint contents via event.gc, etc. Column is passed to
     * differentiate multiple columns with owner-draw contents.
     */
    public void paint(Event event)
    {
        PortfolioLog.error("paint() not implemented"); //$NON-NLS-1$
    }

    @Override
    public void handleEvent(Event event)
    {
        if (event.item instanceof TableItem tableItem)
        {
            var table = tableItem.getParent();
            var tableColumn = table.getColumn(event.index);
            if (tableColumn != getTableColumn())
                return;
        }
        else if (event.item instanceof TreeItem treeItem)
        {
            var tree = treeItem.getParent();
            var treeColumn = tree.getColumn(event.index);
            if (treeColumn != getTreeColumn())
                return;
        }
        else
        {
            return;
        }

        switch (event.type)
        {
            case SWT.MeasureItem:
                this.measure(event);
                break;
            case SWT.PaintItem:
                this.paint(event);
                break;
            case SWT.EraseItem:
                // We're saying that we'll draw actual things ("foreground")
                // ourselves. Things like background, selection, etc. will
                // still be handled by SWT.
                event.detail &= ~SWT.FOREGROUND;
                break;
            default:
        }
    }
}
