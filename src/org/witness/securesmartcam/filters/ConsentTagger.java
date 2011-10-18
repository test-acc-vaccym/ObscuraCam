package org.witness.securesmartcam.filters;

import java.util.Properties;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;

public class ConsentTagger implements RegionProcesser
{
	Properties mProps;
	
	public ConsentTagger ()
	{
		mProps = new Properties ();
		mProps.put("id", "");
		mProps.put("consent", "false");
		
	}
	
	@Override
	public void processRegion (RectF rect, Canvas canvas,  Bitmap bitmap) 
	{
		
	}

	public Properties getProperties()
	{
		return mProps;
	}
	
	public void setProperties(Properties props)
	{
		mProps = props;
	}
}