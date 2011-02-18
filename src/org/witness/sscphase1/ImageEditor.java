package org.witness.sscphase1;

import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Bitmap.Config;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.AbsoluteLayout;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;

public class ImageEditor extends Activity implements OnTouchListener, OnClickListener {

	final static String LOGTAG = "CAMERA OBSCRUA";

	// Colors for region squares
	public final static int DRAW_COLOR = Color.argb(128, 0, 255, 0);// Green
	public final static int DETECTED_COLOR = Color.argb(128, 0, 0, 255); // Blue
	public final static int OBSCURED_COLOR = Color.argb(128, 255, 0, 0); // Red
	public final static int TAGGED_COLOR = Color.argb(128, 128, 128, 0); // Something else
	
	
	public final static int PREFERENCES_MENU_ITEM = 0;
	public final static int PANIC_MENU_ITEM = 1;
	public final static int SAVE_MENU_ITEM = 2;
	public final static int SHARE_MENU_ITEM = 3;
	
	// Image Matrix
	Matrix matrix = new Matrix();
	// Saved Matrix for not allowing a current operation (over max zoom)
	Matrix savedMatrix = new Matrix();
		
	// We can be in one of these 3 states
	static final int NONE = 0;
	static final int DRAG = 1;
	static final int ZOOM = 2;
	static final int DRAW = 3; 
	int mode = NONE;

	static final float MAX_SCALE = 10f;

	// For Zooming
	float startFingerSpacing = 0f;
	float endFingerSpacing = 0f;
	PointF startFingerSpacingMidPoint = new PointF();
	
	// For Dragging
	PointF startPoint = new PointF();

	Button zoomIn, zoomOut;
	ImageView imageView;
	ImageView overlayImageView;
	
	RelativeLayout regionButtonsLayout;
	
	// Touch Timer Related (Long Clicks)
	Handler touchTimerHandler;
	Runnable touchTimerRunnable;
	
	Bitmap imageBitmap;
	Bitmap overlayBitmap;
	
	Canvas overlayCanvas;
	Paint overlayPaint;
	
	Vector<ImageRegion> imageRegions = new Vector();  // Being lazy 
	//ImageRegion[] imageRegions;
	
	int originalImageWidth;
	int originalImageHeight;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
        requestWindowFeature(Window.FEATURE_NO_TITLE);  
		setContentView(R.layout.imageviewer);
		
		imageView = (ImageView) findViewById(R.id.ImageEditorImageView);
		overlayImageView = (ImageView) findViewById(R.id.ImageEditorOverlayImageView);

		zoomIn = (Button) this.findViewById(R.id.ZoomIn);
		zoomOut = (Button) this.findViewById(R.id.ZoomOut);

		zoomIn.setOnClickListener(this);
		zoomOut.setOnClickListener(this);

		Uri imageUri = getIntent().getData();
		if (imageUri != null) {
			
			try {
				// Load up the image's dimensions not the image itself
				BitmapFactory.Options bmpFactoryOptions = new BitmapFactory.Options();
				bmpFactoryOptions.inJustDecodeBounds = true;
				
				imageBitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri), null, bmpFactoryOptions);
				
				originalImageWidth = bmpFactoryOptions.outWidth;
				originalImageHeight = bmpFactoryOptions.outHeight;
				
				Display currentDisplay = getWindowManager().getDefaultDisplay();
				
				int widthRatio = (int) Math.ceil(bmpFactoryOptions.outWidth / (float) currentDisplay.getWidth());
				int heightRatio = (int) Math.ceil(bmpFactoryOptions.outHeight / (float) currentDisplay.getHeight());
	
				Log.v(LOGTAG, "HEIGHTRATIO:" + heightRatio);
				Log.v(LOGTAG, "WIDTHRATIO:" + widthRatio);
	
				// If both of the ratios are greater than 1,
				// one of the sides of the image is greater than the screen
				if (heightRatio > 1 && widthRatio > 1) {
					if (heightRatio > widthRatio) {
						// Height ratio is larger, scale according to it
						bmpFactoryOptions.inSampleSize = heightRatio;
					} else {
						// Width ratio is larger, scale according to it
						bmpFactoryOptions.inSampleSize = widthRatio;
					}
				}
		
				// Decode it for real
				bmpFactoryOptions.inJustDecodeBounds = false;
				imageBitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri), null, bmpFactoryOptions);
				
				if (imageBitmap == null) {
					Log.v(LOGTAG,"bmp is null");
				}
				
				float matrixScale = 1;
				matrix.setScale(matrixScale, matrixScale);
				imageView.setImageBitmap(imageBitmap);
				imageView.setImageMatrix(matrix);
				overlayImageView.setImageMatrix(matrix);
								
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			// Canvas for drawing
			overlayBitmap = Bitmap.createBitmap(imageBitmap.getWidth(), imageBitmap.getHeight(), Config.ARGB_8888);
			overlayCanvas = new Canvas(overlayBitmap);
			overlayPaint = new Paint();
			overlayImageView.setImageBitmap(overlayBitmap); 
			//redrawOverlay();
				
			overlayImageView.setOnTouchListener(this);
			//overlayImageView.setOnLongClickListener(this); // What about normal onClick??\
			// Long click doesn't give place.. :-(
			
			// Layout for Image Regions
			regionButtonsLayout = (RelativeLayout) this.findViewById(R.id.RegionButtonsLayout);

		}
	}
	
	// This should really have a class for the regions to obscure, each with it's own information
	private void createObscuredBmp(Rect[] obscureRects) {
		// Canvas for drawing
		Bitmap alteredBitmap = Bitmap.createBitmap(imageBitmap.getWidth(), imageBitmap.getHeight(), imageBitmap.getConfig());
		Canvas canvas = new Canvas(alteredBitmap);
		Paint paint = new Paint();
		canvas.drawBitmap(imageBitmap, 0, 0, paint);

		
		int OBSCURE_SQUARE = 0;
		int OBSCURE_BLUR = 1;
		
		int obscureMethod = OBSCURE_SQUARE;
		
        // Using the interface
        ObscureMethod om;
        
        // Load the appropriate class/method based on obscureMethod variable/constants
        if (obscureMethod == OBSCURE_BLUR) {
        	om = new BlurObscure(alteredBitmap);
        } else {
        	om = new PaintSquareObscure();		            	
        }
        
		for (int i = 0; i < obscureRects.length; i++) {				            	
			// Apply the obscure method
			om.obscureRect(obscureRects[i], canvas);
		}
	}

	private Rect[] runFaceDetection() {
		GoogleFaceDetection gfd = new GoogleFaceDetection(imageBitmap);
		int numFaces = gfd.findFaces();
        Log.v(LOGTAG,"Num Faces Found: " + numFaces); 
        Rect[] possibleFaceRects = gfd.getFaces();
		return possibleFaceRects;				
	}
	
	public boolean onTouch(View v, MotionEvent event) {
		switch (event.getAction() & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_DOWN:
				// Single Finger
				
				// Stop current timer if running
				if (touchTimerRunnable != null) {
					touchTimerHandler.removeCallbacks(touchTimerRunnable);
				}
				
				// Save the Start point. 
				startPoint.set(event.getX(), event.getY());
				
				// Start out as a drag unless they keep their finger down
				mode = DRAG;
				Log.d(LOGTAG, "mode=DRAG");
				
				// Start timer to determine if this should be drawing mode
				touchTimerRunnable = new Runnable() {
					public void run() {
						mode = DRAW;
						// Tell the user somehow
						Log.v(LOGTAG,"DRAWING DRAWING DRAWING");
					}
				};
				touchTimerHandler = new Handler();
				touchTimerHandler.postDelayed(touchTimerRunnable,1000); // Hold down for one second
				
				break;
				
			case MotionEvent.ACTION_POINTER_DOWN:
				// Two Fingers
				
				// Stop current timer if running
				if (touchTimerRunnable != null) {
					touchTimerHandler.removeCallbacks(touchTimerRunnable);
				}
				
				// Get the spacing of the fingers, 2 fingers
				float sx = event.getX(0) - event.getX(1);
				float sy = event.getY(0) - event.getY(1);
				startFingerSpacing = (float) Math.sqrt(sx * sx + sy * sy);

				Log.d(LOGTAG, "Start Finger Spacing=" + startFingerSpacing);
				
				if (startFingerSpacing > 10f) {

					float xsum = event.getX(0) + event.getX(1);
					float ysum = event.getY(0) + event.getY(1);
					startFingerSpacingMidPoint.set(xsum / 2, ysum / 2);
				
					mode = ZOOM;
					Log.d(LOGTAG, "mode=ZOOM");
				}
				
				break;
				
			case MotionEvent.ACTION_UP:
				// Single Finger Up
				
				if (touchTimerRunnable != null) {
					touchTimerHandler.removeCallbacks(touchTimerRunnable);
				}

				
				if (mode == DRAW) {
					// Create Region
					ImageRegion imageRegion = new ImageRegion(this, (int)startPoint.x, (int)startPoint.y, (int)event.getX(), (int)event.getY(), overlayCanvas.getWidth(), overlayCanvas.getHeight(), originalImageWidth, originalImageHeight, DRAW_COLOR);
					imageRegions.add(imageRegion);
					addImageRegionToLayout(imageRegion);
					clearOverlay();
				}

				mode = NONE;
				Log.v(LOGTAG,"mode=NONE");
				break;
				
			case MotionEvent.ACTION_POINTER_UP:
				// Multiple Finger Up
				
				if (touchTimerRunnable != null) {
					touchTimerHandler.removeCallbacks(touchTimerRunnable);
				}
								
				mode = NONE;
				Log.d(LOGTAG, "mode=NONE");
				break;
				
			case MotionEvent.ACTION_MOVE:
				
				if (touchTimerRunnable != null) {
					touchTimerHandler.removeCallbacks(touchTimerRunnable);
				}
				
				if (mode == DRAG) {
				
					matrix.postTranslate(event.getX() - startPoint.x, event.getY() - startPoint.y);
					imageView.setImageMatrix(matrix);
					overlayImageView.setImageMatrix(matrix);
					// Reset the start point
					startPoint.set(event.getX(), event.getY());

					putOnScreen();
					redrawRegions();

				} else if (mode == DRAW) { 
					
					clearOverlay();
					overlayPaint.setColor(DRAW_COLOR);
					overlayPaint.setStyle(Paint.Style.STROKE);
					overlayCanvas.drawRect(startPoint.x, startPoint.y, event.getX(), event.getY(), overlayPaint);
					overlayImageView.invalidate();

				} else if (mode == ZOOM) {
					
					// Save the current matrix so that if zoom goes to big, we can revert
					savedMatrix.set(matrix);
					
					// Get the spacing of the fingers, 2 fingers
					float ex = event.getX(0) - event.getX(1);
					float ey = event.getY(0) - event.getY(1);
					endFingerSpacing = (float) Math.sqrt(ex * ex + ey * ey);

					Log.d(LOGTAG, "End Finger Spacing=" + endFingerSpacing);
	
					if (endFingerSpacing > 10f) {
						
						// Ratio of spacing..  If it was 5 and goes to 10 the image is 2x as big
						float scale = endFingerSpacing / startFingerSpacing;
						// Scale from the midpoint
						matrix.postScale(scale, scale, startFingerSpacingMidPoint.x, startFingerSpacingMidPoint.y);
	
						float[] matrixValues = new float[9];
						matrix.getValues(matrixValues);
						Log.v(LOGTAG, "Total Scale: " + matrixValues[0]);
						Log.v(LOGTAG, "" + matrixValues[0] + " " + matrixValues[1]
								+ " " + matrixValues[2] + " " + matrixValues[3]
								+ " " + matrixValues[4] + " " + matrixValues[5]
								+ " " + matrixValues[6] + " " + matrixValues[7]
								+ " " + matrixValues[8]);
						if (matrixValues[0] > MAX_SCALE) {
							matrix.set(savedMatrix);
						}
						imageView.setImageMatrix(matrix);
						overlayImageView.setImageMatrix(matrix);
						
						putOnScreen();
						redrawRegions();

						// Reset Start Finger Spacing
						float esx = event.getX(0) - event.getX(1);
						float esy = event.getY(0) - event.getY(1);
						startFingerSpacing = (float) Math.sqrt(esx * esx + esy * esy);
						Log.d(LOGTAG, "New Start Finger Spacing=" + startFingerSpacing);
						
						float xsum = event.getX(0) + event.getX(1);
						float ysum = event.getY(0) + event.getY(1);
						startFingerSpacingMidPoint.set(xsum / 2, ysum / 2);
					}
				}
				break;
		}

		return true; // indicate event was handled
	}

	public void putOnScreen() {

		// Get Rectangle of Tranformed Image
		RectF theRect = new RectF(0,0,imageBitmap.getWidth(), imageBitmap.getHeight());
		matrix.mapRect(theRect);
		
		Log.v(LOGTAG,theRect.width() + " " + theRect.height());
		
		float deltaX = 0, deltaY = 0;
		if (theRect.width() < imageView.getWidth()) {
			deltaX = (imageView.getWidth() - theRect.width())/2 - theRect.left;
		} else if (theRect.left > 0) {
			deltaX = -theRect.left;
		} else if (theRect.right < imageView.getWidth()) {
			deltaX = imageView.getWidth() - theRect.right;
		}		
		
		if (theRect.height() < imageView.getHeight()) {
			deltaY = (imageView.getHeight() - theRect.height())/2 - theRect.top;
		} else if (theRect.top > 0) {
			deltaY = -theRect.top;
		} else if (theRect.bottom < imageView.getHeight()) {
			deltaY = imageView.getHeight() - theRect.bottom;
		}
		
		Log.v(LOGTAG,"Deltas:" + deltaX + " " + deltaY);
		
		matrix.postTranslate(deltaX,deltaY);
		imageView.setImageMatrix(matrix);
		overlayImageView.setImageMatrix(matrix);
	}
	
	public void clearOverlay() {
				
		Paint clearPaint = new Paint();
		clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
		overlayCanvas.drawRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight(), clearPaint);
		
		overlayImageView.invalidate();
		
	}

	public void addImageRegionToLayout(ImageRegion imageRegion) {
		Rect regionScaledRect = imageRegion.getScaledRect(overlayCanvas.getWidth(), overlayCanvas.getHeight());
    	RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(regionScaledRect.width(),regionScaledRect.height());
    	lp.leftMargin = regionScaledRect.left;
    	lp.topMargin = regionScaledRect.top;
    	imageRegion.setLayoutParams(lp);
    	imageRegion.setOnClickListener(this);
    	regionButtonsLayout.addView(imageRegion,lp);		
	}
	
	public void drawRegions() {
		Iterator<ImageRegion> i = imageRegions.iterator();
	    while (i.hasNext()) {
	    	ImageRegion currentRegion = i.next();
	    	addImageRegionToLayout(currentRegion);
	    }
	}
	
	public void redrawRegions() {
		// Easier ??
		regionButtonsLayout.removeAllViews();
		drawRegions();
		
		// Put the buttons in the right place
		/* Not working
		Iterator<ImageRegion> i = imageRegions.iterator();
	    while (i.hasNext()) {
	    	ImageRegion currentRegion = i.next();
	    	Rect scaledRect = currentRegion.getScaledRect(overlayCanvas.getWidth(), overlayCanvas.getHeight());
	    	RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) currentRegion.getLayoutParams();
	    	params.leftMargin = scaledRect.left;
	    	params.topMargin = scaledRect.top;
	    	params.height = scaledRect.height();
	    	params.width = scaledRect.width();
	    	currentRegion.setLayoutParams(params);
	    	currentRegion.invalidate();
	    }		
	    */
	}

	public void onClick(View v) {
		if (v == zoomIn) {
			float scale = 1.5f;
			
			PointF midpoint = new PointF(imageView.getWidth()/2, imageView.getHeight()/2);
			matrix.postScale(scale, scale, midpoint.x, midpoint.y);
			imageView.setImageMatrix(matrix);
			overlayImageView.setImageMatrix(matrix);
			putOnScreen();
			redrawRegions();
		} else if (v == zoomOut) {
			float scale = 0.75f;

			PointF midpoint = new PointF(imageView.getWidth()/2, imageView.getHeight()/2);
			matrix.postScale(scale, scale, midpoint.x, midpoint.y);
			imageView.setImageMatrix(matrix);
			overlayImageView.setImageMatrix(matrix);
			putOnScreen();
			redrawRegions();
		}
	}
	
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuItem panicMenuItem = menu.add(Menu.NONE, PANIC_MENU_ITEM, Menu.NONE, "Panic");
        MenuItem preferencesMenuItem = menu.add(Menu.NONE, PREFERENCES_MENU_ITEM, Menu.NONE, "Preferences");
        MenuItem saveMenuItem = menu.add(Menu.NONE, SAVE_MENU_ITEM, Menu.NONE, "Save");
        MenuItem shareMenuItem = menu.add(Menu.NONE, SHARE_MENU_ITEM, Menu.NONE, "Share");
        return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
        	case PREFERENCES_MENU_ITEM:
        	 	// Load Preferences Activity
        		Intent intent = new Intent(this, PreferencesActivity.class);
        		startActivity(intent);	
        		return true;
        	case PANIC_MENU_ITEM:
        		// Look up preferences and do what is required
        		return true;
        	case SAVE_MENU_ITEM:
        		// Save Image
        		return true;
        	case SHARE_MENU_ITEM:
        		// Share Image
        		return true;
    		default:
    			return false;
    	}
    }	
}