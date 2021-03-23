package com.agrointelligent.arduinobletest;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {


    private BluetoothAdapter      bluetoothAdapter;
    private BluetoothGattCallback mGattCallback;
    private BluetoothGatt         mBluetoothGatt;
    private ScanCallback          scanCallback;
    private BluetoothLeScanner    btScanner;

    List<BluetoothGattCharacteristic> characteristics = new ArrayList<>();
    BleThread bleThread;
    boolean status_thread = true;

    private Button close;
    private Button connect;
    private TextView value;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewByIds();
        setOnClickListeners();

        BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = btManager.getAdapter();
        btScanner = bluetoothAdapter.getBluetoothLeScanner();

        if(!checkFeature()){
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
        }else{
            callbacks();
        }

    }

    private void setOnClickListeners() {
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBluetoothGatt.close();
                if(bleThread != null){
                    bleThread = null;
                }
                status_thread = false;
            }
        });

        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanDevices();
            }
        });
    }

    private void findViewByIds() {
        close = findViewById(R.id.close_button);
        connect = findViewById(R.id.open_button);

        value = findViewById(R.id.value_label);
    }

    private void callbacks() {
        //For scan
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                Log.d("acho", "Mac: " + result.getDevice().getAddress() + " | Name: " + result.getDevice().getName() + " | LocalName: " + result.getScanRecord().getDeviceName());

                btScanner.stopScan(scanCallback);
                startClient(result.getDevice().getAddress());

            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);

                for (ScanResult result : results) {
                    Log.d("acho", "Mac --> " + result.getDevice().getAddress() + " | Name: " + result.getDevice().getName());
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.d("acho","Error --> " + errorCode);
            }
        };

        //When connected
        mGattCallback = new BluetoothGattCallback() {
            @Override
            public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
                super.onPhyUpdate(gatt, txPhy, rxPhy, status);
                Log.d("acho", "onPhyUpdate");
                Log.d("acho", "txPhy" + txPhy + " | rxPhy: " + rxPhy + " | Status: " + status);
            }

            @Override
            public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
                super.onPhyRead(gatt, txPhy, rxPhy, status);
                Log.d("acho", "onPhyRead");
                Log.d("acho", "txPhy" + txPhy + " | rxPhy: " + rxPhy + " | Status: " + status);
            }

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                Log.d("acho", "Status: " + status + " | NewState: " + newState + " | ReadRssi: " + gatt.readRemoteRssi());

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("acho", "Conectado");
                    mBluetoothGatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("acho", "Desconectado");
                }
            }

            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                Log.d("acho", "onServicesDiscovered");

                List<BluetoothGattService> servicios = gatt.getServices();

                for (BluetoothGattService service : servicios) {
                    Log.d("acho", "Servicio: " + service.getUuid().toString());
                    characteristics.addAll(service.getCharacteristics());
                }

                status_thread = true;

                if (!characteristics.isEmpty() && bleThread == null) {
                    bleThread = new BleThread();
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);
                Log.d("acho", "onCharacteristicRead | Caracteristica: " + characteristic.getUuid().toString() + " | Value: " + characteristic.getValue()[0]);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        value.setText("Valor: " + characteristic.getValue()[0]);
                    }
                });
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
                Log.d("acho", "onCharacteristicWrite");
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                Log.d("acho", "onCharacteristicChanged");
            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorRead(gatt, descriptor, status);
                Log.d("acho", "onDescriptorRead | Descriptor: " + descriptor.getUuid().toString() + " | Value: " + new String(descriptor.getValue()));
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorWrite(gatt, descriptor, status);
                Log.d("acho", "onDescriptorWrite");
            }

            @Override
            public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                super.onReliableWriteCompleted(gatt, status);
                Log.d("acho", "onReliableWriteCompleted");
            }

            @Override
            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                super.onReadRemoteRssi(gatt, rssi, status);
                Log.d("acho", "onReadRemoteRssi");
                Log.d("acho", "rssi: " + rssi);
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                super.onMtuChanged(gatt, mtu, status);
                Log.d("acho", "onMtuChanged");
            }
        };
    }

    private boolean checkFeature() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    private class BleThread extends Thread {
        BleThread() {
            start();
        }

        @Override
        public void run() {
            while (status_thread) {
                for (BluetoothGattCharacteristic caractericticas : characteristics) {
                    try {
                        Log.d("charac", "Leyendo característica --> " + caractericticas.getUuid().toString());

                        if (caractericticas.getUuid().toString().equals("00002a19-0000-1000-8000-00805f9b34fb")) {
                            mBluetoothGatt.readCharacteristic(caractericticas);
                        }

                        sleep(50);

                        Log.d("charac", "Leída la característica");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            characteristics.clear();
        }
    }

    public void startClient(String mac) {
        try {
            BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(mac);
            mBluetoothGatt = bluetoothDevice.connectGatt(this, true, mGattCallback);

            if (mBluetoothGatt == null) {
                Log.d("acho", "Unable to create GATT client");
                Toast.makeText(this, "Cant connect to " + mac, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.d("acho", "Unable to create GATT client");
        }
    }

    private void scanDevices() {
        ScanSettings settings = new ScanSettings.Builder().setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES).setMatchMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();

        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter       filter  = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"))).build();
        filters.add(filter);

        btScanner.startScan(filters, settings, scanCallback);
    }

}
