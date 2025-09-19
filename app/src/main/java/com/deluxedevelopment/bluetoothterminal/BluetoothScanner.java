package com.deluxedevelopment.bluetoothterminal;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BluetoothScanner {

    public interface ScanCallback {
        void onScanComplete(List<BluetoothDevice> devices);
        void onScanStarted();
    }

    public static void scanDevices(Context context, int seconds, final ScanCallback callback) {
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            callback.onScanComplete(new ArrayList<>());
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            callback.onScanComplete(new ArrayList<>());
            return;
        }

        // Check permissions
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            callback.onScanComplete(new ArrayList<>());
            return;
        }

        final Set<String> deviceAddresses = new HashSet<>();
        final List<BluetoothDevice> devices = new ArrayList<>();
        final Handler handler = new Handler(Looper.getMainLooper());

        callback.onScanStarted();

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        synchronized (deviceAddresses) {
                            if (deviceAddresses.add(device.getAddress())) {
                                devices.add(device);
                            }
                        }
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    context.unregisterReceiver(this);
                    callback.onScanComplete(new ArrayList<>(devices));
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(receiver, filter);

        bluetoothAdapter.startDiscovery();

        handler.postDelayed(new Runnable() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
            @Override
            public void run() {
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }
            }
        }, seconds * 1000L);
    }


}
