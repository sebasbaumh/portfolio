package ol.proj;

import com.google.gwt.core.client.js.JsType;

import ol.*;

/**
 * 
 * @author Tino Desjardins
 *
 */
@JsType
public abstract class Projection {
    
	public static native Projection newInstance(ProjectionOptions projectionOptions) /*-{
    	return new $wnd.ol.proj.Projection(projectionOptions);
	}-*/;
	
	public static native void addProjection(Projection projection) /*-{
		$wnd.ol.proj.addProjection(projection);
	}-*/;
	
	public static native Projection get(String projectionCode) /*-{
		return $wnd.ol.proj.get(projectionCode);
	}-*/;
	
	public static native Coordinate transform(Coordinate coordinate, String source, String destination) /*-{
	return $wnd.ol.proj.transform(coordinate, source, destination);
	}-*/;

	public static native Extent transformExtent(Extent extent, String source, String destination) /*-{
		return $wnd.ol.proj.transformExtent(extent, source, destination);
	}-*/;
	
    public abstract String getCode();
    
    public abstract Extent getExtent();
    
    public abstract void setExtent(Extent extent);
    
    public abstract double getMetersPerUnit();
    
    public abstract String getUnits();
    
    public abstract boolean isGlobal();

}

