package com.deluxedevelopment.bluetoothterminal;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.UUID;

public class TerminalActivity extends AppCompatActivity {
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Variables to store device information from intent
    private String deviceName;
    private String deviceAddress;

    BluetoothAdapter bluetoothAdapter;
    BluetoothSocket bluetoothSocket;
    InputStream inputStream;

    Button btnDisconnect;
    ListView lvMessages;
    ArrayAdapter<String> adapter;
    ArrayList<String> messageList;

    Handler handler;
    boolean isConnected = false;
    Thread listenThread;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_terminal);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Get device information from intent
        Intent intent = getIntent();
        deviceName = intent.getStringExtra("DEVICE_NAME");
        deviceAddress = intent.getStringExtra("DEVICE_ADDRESS");

        // Set title to show connected device
        setTitle("Connected to: " + deviceName);

        btnDisconnect = findViewById(R.id.btnConnect);
        lvMessages = findViewById(R.id.lvMessages);

        messageList = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, messageList);
        lvMessages.setAdapter(adapter);

        handler = new Handler(Looper.getMainLooper());
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Change button text to "Disconnect"
        btnDisconnect.setText("Disconnect");

        // Set up disconnect button listener
        btnDisconnect.setOnClickListener(v -> {
            disconnectFromDevice();
        });

        // Automatically connect to the device
        connectToDevice();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void connectToDevice() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
            return;
        }

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);

        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            bluetoothSocket.connect();

            inputStream = bluetoothSocket.getInputStream();

            isConnected = true;
            Toast.makeText(this, "Connected to " + device.getName(), Toast.LENGTH_SHORT).show();

            listenForMessages();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show();
            finish(); // Return to MainActivity if connection fails
        }
    }

    private void disconnectFromDevice() {
        try {
            if (listenThread != null) {
                listenThread.interrupt();
            }
            if (inputStream != null) inputStream.close();
            if (bluetoothSocket != null) bluetoothSocket.close();

            isConnected = false;
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();

            // Return to MainActivity
            finish();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void listenForMessages() {
        listenThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;

            while (!Thread.currentThread().isInterrupted() && isConnected) {
                try {
                    bytes = inputStream.read(buffer);
                    String message = new String(buffer, 0, bytes).trim();

                    handler.post(() -> {
                        messageList.add(message);
                        adapter.notifyDataSetChanged();
                        lvMessages.setSelection(adapter.getCount() - 1); // Auto-scroll to latest

                        if(message.equals("ALERT")){
                            Intent intent = new Intent(getPackageName() + ".INTENT_ALERT");
                            sendBroadcast(intent); // This sends the system broadcast
                        }
                    });

                } catch (IOException e) {
                    e.printStackTrace();
                    // Connection lost, return to MainActivity
                    handler.post(() -> {
                        Toast.makeText(TerminalActivity.this, "Connection lost", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    break;
                }
            }
        });
        listenThread.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Ensure proper cleanup when activity is destroyed
        if (isConnected) {
            disconnectFromDevice();
        }
    }
}