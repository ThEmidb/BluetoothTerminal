package com.deluxedevelopment.bluetoothterminal;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private final String[] permissions = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private static final int REQUEST_ENABLE_BT = 1;
    public static UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter bluetoothAdapter;
    BluetoothSocket bluetoothSocket;
    private MaterialSwitch bluetoothSwitch;
    private LinearLayout switchContainer, deviceNameContainer;
    private TextView bluetoothText, tvDeviceName;
    private boolean isUserInteraction = true;
    private boolean isReceiverRegistered = false;
    ExpandableListView expandableListViewExample;
    ExpandableAdapter expandableListAdapter;
    List<String> expandableTitleList;
    List<String> group1Data;
    List<String> group2Data;
    private int previousGroup = -1;
    private boolean isScanning = false;
    private int lastScanFoundCount = 0;
    private BluetoothSocket connectedSocket;

    // Register the permissions callback
    @SuppressLint("MissingPermission")
    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }

                if (allGranted) {
                    // All permissions granted
                    updateUI(bluetoothAdapter.getState());
                } else {
                    // At least one permission denied
                    Toast.makeText(this, "Bluetooth and Location permissions are required", Toast.LENGTH_SHORT).show();
                    updateUI(BluetoothAdapter.STATE_OFF);
                }
            });


    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                updateUI(state);

            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    String item = (device.getName() == null ? "Unknown Device" : device.getName())
                            + "\n" + device.getAddress();

                    if (!group1Data.contains(item)) group1Data.add(item);
                    group2Data.remove(item);

                    // Refresh header counters + lists
                    expandableListAdapter.notifyDataSetChanged();
                }

            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    String item = (device.getName() == null ? "Unknown Device" : device.getName())
                            + "\n" + device.getAddress();

                    group1Data.remove(item);
                    // (optional) put back into available list if you want
                    if (!group2Data.contains(item)) group2Data.add(item);

                    expandableListAdapter.notifyDataSetChanged();
                }
            }
        }
    };


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize views
        switchContainer = findViewById(R.id.switchContainer);
        deviceNameContainer = findViewById(R.id.deviceNameContainer);
        bluetoothSwitch = findViewById(R.id.bluetoothSwitch);
        bluetoothText = findViewById(R.id.bluetoothText);
        tvDeviceName = findViewById(R.id.tvDeviceName);
        expandableListViewExample = findViewById(R.id.expandableListViewSample);

        expandableTitleList = new ArrayList<>();
        expandableTitleList.add("Connected Devices");
        expandableTitleList.add("Available Devices");

        // Group 1 items
        group1Data = new ArrayList<>();

        // Group 2 items
        group2Data = new ArrayList<>();

        expandableListAdapter = new ExpandableAdapter(this, expandableTitleList, group1Data, group2Data);
        expandableListViewExample.setAdapter(expandableListAdapter); // open Group 2 (index 1) by default
        expandableListViewExample.expandGroup(1);


        // Accordion behavior: only one open
        expandableListViewExample.setOnGroupExpandListener(new ExpandableListView.OnGroupExpandListener() {
            @Override
            public void onGroupExpand(int groupPosition) {
                if (previousGroup != -1 && previousGroup != groupPosition) {
                    expandableListViewExample.collapseGroup(previousGroup);
                }
                previousGroup = groupPosition;
            }
        });

        // --- Expand Group 2 by default ---
        expandableListViewExample.expandGroup(1);

        // Get Bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Check Bluetooth support
        if (bluetoothAdapter == null) {
            disableBluetoothUI();
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }

        // Set click listeners
        switchContainer.setOnClickListener(v -> handleBluetoothToggle());
        bluetoothText.setOnClickListener(v -> handleBluetoothToggle());
        deviceNameContainer.setOnClickListener(v -> openBluetoothSettings());

        // Initial UI update
        updateUI(bluetoothAdapter.getState());
    }

    public void startScan(Context context, MaterialButton button, ListView listView, ArrayAdapter<String> adapter, List<String> list) {
        // Begin scan state
        isScanning = true;
        lastScanFoundCount = 0;
        expandableListAdapter.setScanning(true);  // tells headers/child to show spinners

        // Simple button pulse animation during scan
        button.setEnabled(false);
        button.setText("Scanning...");
        button.animate().alpha(0.6f).setDuration(250).withEndAction(() ->
                button.animate().alpha(1f).setDuration(250)).start();

        BluetoothScanner.scanDevices(context, 10, new BluetoothScanner.ScanCallback() {
            @Override
            public void onScanStarted() {
                // already set scanning true
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            @Override
            public void onScanComplete(final List<BluetoothDevice> devices) {
                // Fill results into Group 2 data
                list.clear();
                for (BluetoothDevice device : devices) {
                    String name = device.getName();
                    if (name == null || name.isEmpty()) name = "Unknown Device";
                    list.add(name + "\n" + device.getAddress());
                }
                adapter.notifyDataSetChanged();
                setListViewHeightBasedOnChildren(listView);

                // End scan state
                isScanning = false;
                lastScanFoundCount = devices.size();
                expandableListAdapter.setFoundCount(lastScanFoundCount);
                expandableListAdapter.setScanning(false); // hides spinners, shows "Found n" if n>0

                // Reset button
                button.animate().cancel();
                button.setAlpha(1f);
                button.setText("Scan Devices");
                button.setEnabled(true);
            }
        });
    }

    public void connectToDevice(UUID MY_UUID, String DEVICE_NAME, String DEVICE_MAC_ADDRESS) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
            return;
        }

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(DEVICE_MAC_ADDRESS);

        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            bluetoothSocket.connect();

            Toast.makeText(getApplicationContext(), "Connected to " + DEVICE_NAME, Toast.LENGTH_SHORT).show();

            // ✅ Move device from Group 2 → Group 1
            String deviceEntry = DEVICE_NAME + "\n" + DEVICE_MAC_ADDRESS;

            // remove from Group 2
            group2Data.remove(deviceEntry);

            // add into Group 1
            if (!group1Data.contains(deviceEntry)) {
                group1Data.add(deviceEntry);
            }

            runOnUiThread(() -> {
                expandableListAdapter.notifyDataSetChanged();
                expandableListViewExample.expandGroup(0); // expand Group 1
            });


        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "Failed to connect to " + DEVICE_NAME, Toast.LENGTH_SHORT).show();
        }
    }

    public void disconnectFromDevice(String DEVICE_NAME, String DEVICE_MAC_ADDRESS) {
        try {
            if (bluetoothSocket != null && bluetoothSocket.isConnected()) {
                bluetoothSocket.close();
            }

            Toast.makeText(getApplicationContext(), "Disconnected from " + DEVICE_NAME, Toast.LENGTH_SHORT).show();

            String deviceEntry = DEVICE_NAME + "\n" + DEVICE_MAC_ADDRESS;

            // remove from Group 1
            group1Data.remove(deviceEntry);

            // add back into Group 2
            if (!group2Data.contains(deviceEntry)) {
                group2Data.add(deviceEntry);
            }

            runOnUiThread(() -> {
                expandableListAdapter.notifyDataSetChanged();
                expandableListViewExample.expandGroup(1); // expand Group 2
            });


        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "Failed to disconnect " + DEVICE_NAME, Toast.LENGTH_SHORT).show();
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onResume() {
        super.onResume();
        if (!isReceiverRegistered) {
            // Register for Bluetooth state changes
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            registerReceiver(bluetoothReceiver, filter);
        }

        // Update UI in case something changed while we were away
        updateUI(bluetoothAdapter != null ? bluetoothAdapter.getState() : BluetoothAdapter.STATE_OFF);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isReceiverRegistered) {
            unregisterReceiver(bluetoothReceiver);
            isReceiverRegistered = false;
        }
    }

    private void disableBluetoothUI() {
        switchContainer.setEnabled(false);
        bluetoothSwitch.setEnabled(false);
        bluetoothText.setEnabled(false);
        switchContainer.setBackgroundResource(R.drawable.bg_rounded_grey);
        tvDeviceName.setText("Bluetooth not available");
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void updateUI(int state) {
        isUserInteraction = false;

        // Check if we need and have Bluetooth permission
        if (needsBtPermission() && !hasAllPermissions()) {
            bluetoothSwitch.setChecked(false);
            switchContainer.setBackgroundResource(R.drawable.bg_rounded_grey);
            bluetoothText.setText("Enable Bluetooth");
            tvDeviceName.setText("Permission required");
            isUserInteraction = true;
            return;
        }

        // Update UI based on Bluetooth state
        bluetoothSwitch.setChecked(state == BluetoothAdapter.STATE_ON);

        if (state == BluetoothAdapter.STATE_ON) {
            switchContainer.setBackgroundResource(R.drawable.bg_rounded_blue);
            bluetoothText.setText("Disable Bluetooth");
            tvDeviceName.setText(bluetoothAdapter.getName());
        } else {
            switchContainer.setBackgroundResource(R.drawable.bg_rounded_grey);
            bluetoothText.setText("Enable Bluetooth");
            tvDeviceName.setText(hasAllPermissions() ? bluetoothAdapter.getName() : "N/A");
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(bluetoothReceiver, filter);
        isUserInteraction = true;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void handleBluetoothToggle() {
        if (!isUserInteraction || bluetoothAdapter == null) {
            return;
        }

        boolean newState = !bluetoothSwitch.isChecked();

        // Check permission first for Android 12+
        if (newState && needsBtPermission() && !hasAllPermissions()) {
            requestPermissionsLauncher.launch(permissions);
            return;
        }

        if (newState) {
            // Enable Bluetooth
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        } else {
            // Disable Bluetooth (must be done through settings)
            openBluetoothSettings();
        }
    }

    private boolean needsBtPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    private boolean hasAllPermissions() {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }


    private void openBluetoothSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open Bluetooth settings", Toast.LENGTH_SHORT).show();
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode != RESULT_OK) {
            // User declined to enable Bluetooth
            updateUI(BluetoothAdapter.STATE_OFF);
            Toast.makeText(this, "Bluetooth enabling canceled", Toast.LENGTH_SHORT).show();
        }
    }

    // --- Helper to fix ListView height inside expandable child ---
    public static void setListViewHeightBasedOnChildren(ListView listView) {
        ListAdapter listAdapter = listView.getAdapter();
        if (listAdapter == null) return;

        int totalHeight = 0;
        @SuppressLint("Range") int desiredWidth = View.MeasureSpec.makeMeasureSpec(
                listView.getWidth() == 0 ? ViewGroup.LayoutParams.MATCH_PARENT : listView.getWidth(),
                View.MeasureSpec.AT_MOST
        );
        for (int i = 0; i < listAdapter.getCount(); i++) {
            View listItem = listAdapter.getView(i, null, listView);
            listItem.measure(desiredWidth, View.MeasureSpec.UNSPECIFIED);
            totalHeight += listItem.getMeasuredHeight();
        }
        ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));
        listView.setLayoutParams(params);
        listView.requestLayout();
    }
}