package com.semperpax.spmc17;

import android.content.Context;
import android.graphics.PixelFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.view.View;

/**
 * Created by cbro on 5/13/17.
 */

public class XBMCMainView extends SurfaceView implements SurfaceHolder.Callback, View.OnLayoutChangeListener
{
  native void _attach();
  native void _surfaceChanged(SurfaceHolder holder, int format, int width, int height);
  native void _surfaceCreated(SurfaceHolder holder);
  native void _surfaceDestroyed(SurfaceHolder holder);
  native void _onLayoutChange(int left, int top, int width, int height);

  private static final String TAG = "XBMCMainView";

  public boolean mIsCreated = false;

  public XBMCMainView(Context context)
  {
    super(context);
    setZOrderOnTop(true);
    getHolder().addCallback(this);
    addOnLayoutChangeListener(this);
    getHolder().setFormat(PixelFormat.TRANSPARENT);

    Log.d(TAG, "Created");
}

  public XBMCMainView(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs, defStyle);
    setZOrderOnTop(true);
    getHolder().addCallback(this);
    addOnLayoutChangeListener(this);
    getHolder().setFormat(PixelFormat.TRANSPARENT);

    Log.d(TAG, "Created");
  }

  public XBMCMainView(Context context, AttributeSet attrs)
  {
    this(context, attrs, 0);
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder)
  {
    Log.d(TAG, "Surface Created");
    mIsCreated = true;
    _attach();
    _surfaceCreated(holder);
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width,
                             int height)
  {
    if (holder != getHolder())
      return;

    Log.d(TAG, "Surface Changed, format:" + format + ", width:" + width + ", height:" + height);
    _surfaceChanged(holder, format, width, height);
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder)
  {
    Log.d(TAG, "Surface Destroyed");
    mIsCreated = false;
    _surfaceDestroyed(holder);
  }

  @Override
  public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom)
  {
    int[] outLocation = new int[2];
    getLocationInWindow(outLocation);

    Log.d(TAG, "Layout changed: " + outLocation[0] + "+" + outLocation[1] + "-" + (right-left) + "x" + (bottom-top));
    _onLayoutChange(outLocation[0], outLocation[1], (right-left), (bottom-top));
  }
}
