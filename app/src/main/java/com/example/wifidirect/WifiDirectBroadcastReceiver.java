package com.example.wifidirect;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;

public class WifiDirectBroadcastReceiver extends BroadcastReceiver {

    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel channel;
    private MainActivity mainActivity;

    //private boolean connectedOccurredAtLeastOnce = false;

    public WifiDirectBroadcastReceiver(WifiP2pManager wifiP2pManager, WifiP2pManager.Channel channel, MainActivity mainActivity) {
        this.wifiP2pManager = wifiP2pManager;
        this.channel = channel;
        this.mainActivity = mainActivity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        //mainActivity.wifiOnOffDetection();

        String action = intent.getAction();
        //int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {


            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);

            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                mainActivity.connectionStatusTextView_wifiOn();
                mainActivity.turnWifiOnOffButton_turnWifiOff();
            }
            else if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
                mainActivity.connectionStatusTextView_wifiOff();
                mainActivity.turnWifiOnOffButton_turnWifiOn();

            }





        } else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {


            int state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, 10000);

            if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) mainActivity.connectionStatusTextView_discoveryStarted();
            else if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) mainActivity.connectionStatusTextView_discoveryStopped();





        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {



            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && mainActivity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                mainActivity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MainActivity.PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION);
                // After this point you wait for callback in
                // onRequestPermissionsResult(int, String[], int[]) overridden method

            } else {
                //do something, permission was previously granted; or legacy device
                reqPeers();
            }




        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {





            if (wifiP2pManager == null) return;

            NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            if (networkInfo.isConnected()) {
                wifiP2pManager.requestConnectionInfo(channel, mainActivity.getConnectionInfoListener());

                //connectedOccurredAtLeastOnce = true;
            } else
                /*if (connectedOccurredAtLeastOnce)*/ mainActivity.connectionStatus_noConnection();




        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {

        }
    }

    public void reqPeers() {
        if (wifiP2pManager != null) {
            wifiP2pManager.requestPeers(channel, mainActivity.getPeerListListener());
        }
    }
}
