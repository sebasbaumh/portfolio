package name.abuchen.portfolio.ui.util.viewers;

import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Tree;

public class OptionLabelProvider<O> extends CellLabelProvider
{
    public String getText(Object element, O option) // NOSONAR
    {
        return null;
    }

    public Color getForeground(Object element, O option) // NOSONAR
    {
        return null;
    }

    public Image getImage(Object element, O option) // NOSONAR
    {
        return null;
    }

    public Font getFont(Object element, O option) // NOSONAR
    {
        return null;
    }

    @Override
    public void update(ViewerCell cell)
    {
        int columnIndex = cell.getColumnIndex();
        Control control = cell.getControl();

        Object data;
        switch (control)
        {
            case Table table -> data = table.getColumn(columnIndex).getData(ShowHideColumnHelper.OPTIONS_KEY);
            case Tree tree -> data = tree.getColumn(columnIndex).getData(ShowHideColumnHelper.OPTIONS_KEY);
            default -> throw new IllegalArgumentException("Unsupported control type: " + control.getClass().getName()); //$NON-NLS-1$
        }

        @SuppressWarnings("unchecked")
        O option = (O) data;

        Object element = cell.getElement();
        cell.setText(getText(element, option));
        cell.setForeground(getForeground(element, option));
        cell.setImage(getImage(element, option));
        cell.setFont(getFont(element, option));
    }
}
