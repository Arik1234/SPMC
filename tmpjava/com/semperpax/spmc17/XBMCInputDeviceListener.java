package com.semperpax.spmc17;

import android.hardware.input.InputManager.InputDeviceListener;
import android.util.Log;

public class XBMCInputDeviceListener implements InputDeviceListener
{
  native void _onInputDeviceAdded(int deviceId);
  native void _onInputDeviceChanged(int deviceId);
  native void _onInputDeviceRemoved(int deviceId);

  @Override
  public void onInputDeviceAdded(int deviceId)
  {
    _onInputDeviceAdded(deviceId);
  }

  @Override
  public void onInputDeviceChanged(int deviceId)
  {
    _onInputDeviceChanged(deviceId);
  }

  @Override
  public void onInputDeviceRemoved(int deviceId)
  {
    _onInputDeviceRemoved(deviceId);
  }
}
