package name.abuchen.portfolio.ui.util.viewers;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.Widget;

/**
 * ColumnLabelProvider subclass which allows to access "option value" associated
 * with a column. This can be e.g. reporting period (and there can be multiple
 * instance of the same base column with different reporting periods set).
 */
public class ParameterizedColumnLabelProvider<O> extends ColumnLabelProvider
{
    private Widget column;

    public TableColumn getTableColumn()
    {
        return this.column instanceof TableColumn tableColumn ? tableColumn : null;
    }

    public TreeColumn getTreeColumn()
    {
        return this.column instanceof TreeColumn treeColumn ? treeColumn : null;
    }

    public void setTableColumn(TableColumn tableColumn)
    {
        setColumn(tableColumn);
    }

    public void setTreeColumn(TreeColumn treeColumn)
    {
        setColumn(treeColumn);
    }

    private void setColumn(Widget column)
    {
        if (this.column != null)
            throw new IllegalStateException(
                            "ParameterizedColumnLabelProvider cannot be reused across multiple columns. Use Column#setLabelProvider(Supplier<CellLabelProvider> labelProvider) method."); //$NON-NLS-1$
        this.column = column;
    }

    @SuppressWarnings("unchecked")
    public O getOption()
    {
        if (this.column == null)
            throw new IllegalStateException("ParameterizedColumnLabelProvider is not attached to a column"); //$NON-NLS-1$

        return (O) this.column.getData(ShowHideColumnHelper.OPTIONS_KEY);
    }
}
