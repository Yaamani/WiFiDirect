package com.example.wifidirect;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = MainActivity.class.getSimpleName();
    public static final int PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION = 1001;
    public static final int PORT_NUMBER = 4331;
    public static final int CLIENT_SIDE_TIMEOUT = 1000; // ms
    public static final int SERVER_SIDE_THREAD_SLEEP_DURATION = 500; // ms
    public static final int CLIENT_SIDE_THREAD_SLEEP_DURATION = 500; // ms
    public static final int MESSAGE_READ = 1;
    public static final int BUFFER_SIZE = 1024;

    // --- wifi ---
    private WifiManager wifiManager;

    private final IntentFilter intentFilter = new IntentFilter();
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel channel;
    private WifiDirectBroadcastReceiver wifiDirectBroadcastReceiver;

    private WifiP2pManager.PeerListListener peerListListener;
    private ArrayList<WifiP2pDevice> peerList = new ArrayList<>();
    private String[] deviceNameArray;
    private WifiP2pDevice[] deviceArray;

    private WifiP2pManager.ConnectionInfoListener connectionInfoListener;

    private Handler handler;

    private ServerSocket serverSide_serverSocket;
    private ServerThread serverThread;
    private ClientThread clientThread;
    private SendReceive sendReceive;

    // --- ui ---
    private Button button_wifiOnOff, button_wifiDiscover, button_send;
    private TextView textView_connectionStatus, textView_messageReceived;
    private EditText editText_sendMessage;
    private ListView listView_peers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


    }

    @Override
    protected void onStart() {
        super.onStart();

        initializeWifiManager();

        initializeIntentFilterActions();

        initializeWifiDirectBroadcastReceiver();

        initializePeerListListener();

        initializeConnectionInfoListener();

        initializeHandler();

        initializeUiElements();

        initializeButtonsListener();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(wifiDirectBroadcastReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(wifiDirectBroadcastReceiver, intentFilter);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Do something with granted permission
            wifiDirectBroadcastReceiver.reqPeers();
        }
    }

    // ------------------------------------------------------------
    // ------------------------------------------------------------
    // ------------------------------------------------------------
    // ------------------------------------------------------------

    public void wifiOnOffDetection() {
        if (wifiManager.isWifiEnabled())
            button_wifiOnOff.setText(R.string.turn_wifi_off);
        else
            button_wifiOnOff.setText(R.string.turn_wifi_on);
    }

    private void populatePeersListView(Collection<WifiP2pDevice> deviceList) {
        deviceNameArray = new String[deviceList.size()];
        deviceArray = new WifiP2pDevice[deviceList.size()];

        int i = 0;
        for (WifiP2pDevice device : deviceList) {
            deviceNameArray[i] = device.deviceName;
            deviceArray[i] = device;
            i++;
        }

        Log.i(TAG, Arrays.toString(deviceNameArray));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), R.layout.simple_list_item_1, deviceNameArray);
        listView_peers.setAdapter(adapter);

    }

    public WifiP2pManager.PeerListListener getPeerListListener() {
        return peerListListener;
    }

    public WifiP2pManager.ConnectionInfoListener getConnectionInfoListener() {
        return connectionInfoListener;
    }

    public void connectionStatusTextView_discoveryStarted() {
        textView_connectionStatus.setText(R.string.discovery_started);
    }

    public void connectionStatusTextView_discoveryStopped() {
        textView_connectionStatus.setText(R.string.discovery_stopped);
    }

    public void connectionStatusTextView_wifiOff() {
        textView_connectionStatus.setText(R.string.wifi_off);
    }

    public void connectionStatusTextView_wifiOn() {
        textView_connectionStatus.setText(R.string.wifi_on);
    }

    public void turnWifiOnOffButton_turnWifiOn() {
        button_wifiOnOff.setText(R.string.turn_wifi_on);
    }

    public void turnWifiOnOffButton_turnWifiOff() {
        button_wifiOnOff.setText(R.string.turn_wifi_off);
    }

    public void connectionStatus_noConnection() {
        textView_connectionStatus.setText(R.string.no_connection);
    }

    public void connectionStatusTextView_connectionFailed() {
        textView_connectionStatus.setText(R.string.connection_failed);
    }

    // ------------------------------------------------------------
    // ------------------------------------------------------------
    // ------------------------------------------------------------
    // ------------------------------------------------------------

    private void initializeWifiManager() {
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    private void initializeWifiDirectBroadcastReceiver() {
        wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = wifiP2pManager.initialize(this, getMainLooper(), null);

        wifiDirectBroadcastReceiver = new WifiDirectBroadcastReceiver(wifiP2pManager, channel, this);
    }

    private void initializeIntentFilterActions() {
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    private void initializePeerListListener() {
        peerListListener = new WifiP2pManager.PeerListListener() {
            @Override
            public void onPeersAvailable(WifiP2pDeviceList peers) {

                Collection<WifiP2pDevice> deviceList = peers.getDeviceList();

                if (!deviceList.equals(peerList)) {
                    peerList.clear();
                    peerList.addAll(peers.getDeviceList());

                    populatePeersListView(peerList);
                }

                if (deviceList.isEmpty())
                    Toast.makeText(getApplicationContext(), R.string.no_devices_found, Toast.LENGTH_SHORT).show();
            }
        };
    }

    private void initializeConnectionInfoListener() {
        connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
            @Override
            public void onConnectionInfoAvailable(WifiP2pInfo info) {
                final InetAddress groupOwnerAddress = info.groupOwnerAddress;

                if (info.groupFormed)
                    if (info.isGroupOwner) {
                        textView_connectionStatus.setText(R.string.host);



                        serverThread = new ServerThread(groupOwnerAddress);
                        serverThread.start();

                        /*if (sendReceive == null) {
                            connectionStatusTextView_connectionFailed();
                            wifiManager.setWifiEnabled(false);
                        }*/


                    } else {
                        textView_connectionStatus.setText(R.string.client);

                        clientThread = new ClientThread(groupOwnerAddress);
                        clientThread.start();
                    }
            }
        };
    }

    private void initializeHandler() {
        handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case MESSAGE_READ:
                        Log.i(TAG, "Receiving ...");

                        byte[] readBuff = (byte[]) msg.obj;
                        String tempMsg = new String(readBuff, 0, msg.arg1);
                        textView_messageReceived.setText(tempMsg);

                        if (tempMsg.equals("opencv")) {
                            Intent intent = new Intent(MainActivity.this, OpenCvCameraActivity.class);
                            startActivity(intent);
                        }

                        break;
                }
                return true;
            }
        });
    }

    private void initializeUiElements() {
        button_send = findViewById(R.id.button_send);
        button_wifiOnOff = findViewById(R.id.button_wifiOnOff);
        button_wifiDiscover = findViewById(R.id.button_wifiDiscover);

        textView_connectionStatus = findViewById(R.id.textView_connectionStatus);
        textView_messageReceived = findViewById(R.id.textView_messageReceived);

        editText_sendMessage = findViewById(R.id.editText_sendMessage);

        listView_peers = findViewById(R.id.listView_peers);
    }

    private void initializeButtonsListener() {
        button_wifiOnOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (wifiManager.isWifiEnabled()) {
                    wifiManager.setWifiEnabled(false);
                } else {
                    wifiManager.setWifiEnabled(true);
                }

                //wifiOnOffDetection();
            }
        });



        button_wifiDiscover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        textView_connectionStatus.setText(R.string.attempting_to_start_discovery);
                    }

                    @Override
                    public void onFailure(int reason) {
                        textView_connectionStatus.setText(R.string.discovery_starting_failed);
                    }
                });
            }
        });


        listView_peers.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final WifiP2pDevice device = deviceArray[position];
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;

                wifiP2pManager.connect(channel, config, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        textView_connectionStatus.setText(R.string.attempting_to_connect);
                    }

                    @Override
                    public void onFailure(int reason) {
                        textView_connectionStatus.setText(R.string.connection_failed);
                    }
                });
            }
        });


        button_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText_sendMessage.getText().toString();

                //sendReceive.write(msg.getBytes());

                SendTask sendTask = new SendTask(msg);
                sendTask.execute();
            }
        });
    }

    // ------------------------------------------------------------
    // ------------------------------------------------------------
    // ------------------------------------------------------------
    // ------------------------------------------------------------










    public class ServerThread extends Thread {
        private Socket socket;
        private InetAddress inetAddress;

        public ServerThread(InetAddress inetAddress) {
            this.inetAddress = inetAddress;
        }

        @Override
        public void run() {
            super.run();


            //throw new RuntimeException("e");
            try {
                sleep(SERVER_SIDE_THREAD_SLEEP_DURATION);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


            try {
                if (serverSide_serverSocket == null) {
                    serverSide_serverSocket = new ServerSocket(PORT_NUMBER/*, -1, inetAddress*/);
                    serverSide_serverSocket.setReuseAddress(true);
                }

                socket = serverSide_serverSocket.accept();
                Log.i(TAG, "Socket Accepted ServerThread");

                // Send and receive code
                sendReceive = new SendReceive(socket);
                sendReceive.start();

            } catch (IOException e) {
                e.printStackTrace();
                //throw new RuntimeException(e.getMessage());
            }

        }


    }




    public class ClientThread extends Thread {
        private String hostAdd;
        private Socket socket;

        public ClientThread(InetAddress hostAddress) {
            hostAdd = hostAddress.getHostAddress();
            socket = new Socket();
        }

        @Override
        public void run() {
            super.run();


            try {
                sleep(CLIENT_SIDE_THREAD_SLEEP_DURATION);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


            try {

                socket.connect(new InetSocketAddress(hostAdd, PORT_NUMBER), CLIENT_SIDE_TIMEOUT);

                // Send and receive code
                sendReceive = new SendReceive(socket);
                sendReceive.start();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }



    private class SendReceive extends Thread {
        private Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public SendReceive(Socket socket) {
            this.socket = socket;

            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void run() {
            super.run();

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytes;

            while (socket != null) {
                //Log.i(TAG, "Waiting to receive ...");
                try {
                    bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        handler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void write(byte[] bytes) {
            try {
                Log.i(TAG, "Sending ...");
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }



    public class SendTask extends AsyncTask<Void, Void, Void> {

        private String msg;

        public SendTask(String msg) {
            this.msg = msg;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            sendReceive.write(msg.getBytes());
            return null;
        }
    }
}
