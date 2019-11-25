package com.syneart.wifitest;


import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static final int WIFI_SCAN_PERMISSION_CODE = 2;
    TextView mWifiState;//wifi狀態
    TextView mWifiName;//Wi-Fi名稱
    TextView mMac;//實體地址
    TextView mIP;//ip地址
    TextView mGateway;//閘道器地址
    ListView mListWifi;//Wi-Fi列表
    Button mBtnSearch;//搜尋Wi-Fi
    Button mBtnConnect;//連線Wi-Fi
    WifiListAdapter mWifiListAdapter;
    WorkAsyncTask mWorkAsyncTask = null;
    ConnectAsyncTask mConnectAsyncTask = null;
    List<ScanResult> mScanResultList = new ArrayList<>();
    String ssid = "";
    String bssid = "";
    WifiAutoConnectManager.WifiCipherType type = WifiAutoConnectManager.WifiCipherType.WIFICIPHER_NOPASS;
    String password = "11111111";
    FrameLayout progressbar;
    boolean isLinked = false;

    String gateway = "";
    String mac = "";
    /**
     * 處理訊號量改變或者掃描結果改變的廣播
     */
    private BroadcastReceiver mWifiSearchBroadcastReceiver;
    private IntentFilter mWifiSearchIntentFilter;
    private BroadcastReceiver mWifiConnectBroadcastReceiver;
    private IntentFilter mWifiConnectIntentFilter;
    private WifiAutoConnectManager mWifiAutoConnectManager;

    @Override
    protected void onResume() {
        super.onResume();
        if (mWifiSearchIntentFilter != null && mWifiConnectIntentFilter !=null) {
            registerReceiver(mWifiSearchBroadcastReceiver, mWifiSearchIntentFilter);
            registerReceiver(mWifiConnectBroadcastReceiver, mWifiConnectIntentFilter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mWifiSearchIntentFilter != null && mWifiConnectIntentFilter !=null) {
            unregisterReceiver(mWifiSearchBroadcastReceiver);
            unregisterReceiver(mWifiConnectBroadcastReceiver);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        intiView();
        //初始化wifi工具
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mWifiAutoConnectManager = WifiAutoConnectManager.newInstance(wifiManager);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // 獲取wifi連線需要定位許可權,沒有獲取許可權
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
            }, WIFI_SCAN_PERMISSION_CODE);
            return;
        }
        //設定監聽wifi狀態變化廣播
        initWifiSate();
    }

    private void initWifiSate() {
        //wifi 搜尋結果接收廣播
        mWifiSearchBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {// 掃描結果改表
                    mScanResultList = WifiAutoConnectManager.getScanResults();
                    if (mWifiListAdapter != null) {
                        mWifiListAdapter.setmWifiList(mScanResultList);
                        mWifiListAdapter.notifyDataSetChanged();
                    }
                }
            }
        };
        mWifiSearchIntentFilter = new IntentFilter();
        mWifiSearchIntentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mWifiSearchIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mWifiSearchIntentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);

        //wifi 狀態變化接收廣播
        mWifiConnectBroadcastReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                    int wifState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                    if (wifState != WifiManager.WIFI_STATE_ENABLED) {
                        Toast.makeText(MainActivity.this, "沒有wifi", Toast.LENGTH_SHORT).show();
                    }
                } else if (action.equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)) {
                    int linkWifiResult = intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, 123);
                    Log.e("wifidemo", ssid + "linkWifiResult:" + linkWifiResult);
                    if (linkWifiResult == WifiManager.ERROR_AUTHENTICATING) {
                        Log.e("wifidemo", ssid + "onReceive:密碼錯誤");
                    }
                } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                    NetworkInfo.DetailedState state = ((NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO)).getDetailedState();
                    setWifiState(state);
                }
            }
        };
        mWifiConnectIntentFilter = new IntentFilter();
        mWifiConnectIntentFilter.addAction(WifiManager.ACTION_PICK_WIFI_NETWORK);
        mWifiConnectIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mWifiConnectIntentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        mWifiConnectIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
    }

    /**
     * 顯示wifi狀態
     *
     * @param state
     */
    public void setWifiState(final NetworkInfo.DetailedState state) {
        if (state == NetworkInfo.DetailedState.AUTHENTICATING) {

        } else if (state == NetworkInfo.DetailedState.BLOCKED) {

        } else if (state == NetworkInfo.DetailedState.CONNECTED) {
            progressbar.setVisibility(View.GONE);
            isLinked = true;
            mWifiState.setText("wifi state:連線成功");
            mWifiName.setText("wifi name:" + WifiAutoConnectManager.getSSID() + "( " + WifiAutoConnectManager.getBSSID() + " )");
            mIP.setText("ip address:" + WifiAutoConnectManager.getIpAddress());
            mGateway.setText("gateway:" + WifiAutoConnectManager.getGateway());
            mMac.setText("mac:" + WifiAutoConnectManager.getMacAddress());
            gateway = WifiAutoConnectManager.getGateway();
            mac = WifiAutoConnectManager.getMacAddress();
        } else if (state == NetworkInfo.DetailedState.CONNECTING) {
            isLinked = false;
            mWifiState.setText("wifi state:連線中...");
            mWifiName.setText("wifi name:" + WifiAutoConnectManager.getSSID() + "( " + WifiAutoConnectManager.getBSSID() + " )");
            mIP.setText("ip address");
            mGateway.setText("gateway");
        } else if (state == NetworkInfo.DetailedState.DISCONNECTED) {
            isLinked = false;
            mWifiState.setText("wifi state:斷開連線");
            mWifiName.setText("wifi name");
            mIP.setText("ip address");
            mGateway.setText("gateway");
        } else if (state == NetworkInfo.DetailedState.DISCONNECTING) {
            isLinked = false;
            mWifiState.setText("wifi state:斷開連線中...");
        } else if (state == NetworkInfo.DetailedState.FAILED) {
            isLinked = false;
            mWifiState.setText("wifi state:連線失敗");
        } else if (state == NetworkInfo.DetailedState.IDLE) {

        } else if (state == NetworkInfo.DetailedState.OBTAINING_IPADDR) {

        } else if (state == NetworkInfo.DetailedState.SCANNING) {

        } else if (state == NetworkInfo.DetailedState.SUSPENDED) {

        }
    }

    private void intiView() {
        progressbar = (FrameLayout) findViewById(R.id.progressbar);
        mWifiState = (TextView) findViewById(R.id.wifi_state);
        mWifiName = (TextView) findViewById(R.id.wifi_name);
        mMac = (TextView) findViewById(R.id.wifi_mac);
        mIP = (TextView) findViewById(R.id.ip_address);
        mGateway = (TextView) findViewById(R.id.ip_gateway);
        mListWifi = (ListView) findViewById(R.id.list_wifi);
        mBtnSearch = (Button) findViewById(R.id.search_wifi);
        mBtnConnect = (Button) findViewById(R.id.connect_wifi);

        mBtnSearch.setOnClickListener(this);
        mBtnConnect.setOnClickListener(this);

        mListWifi.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                mWifiListAdapter.setSelectItem(i);
                mWifiListAdapter.notifyDataSetChanged();
                ScanResult mScanResult = mScanResultList.get(i);
                ssid = mScanResult.SSID;
                bssid = mScanResult.BSSID;
                type = WifiAutoConnectManager.getCipherType(ssid);
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.search_wifi:
                if (mWorkAsyncTask != null) {
                    mWorkAsyncTask.cancel(true);
                    mWorkAsyncTask = null;
                }
                mWorkAsyncTask = new WorkAsyncTask();
                mWorkAsyncTask.execute();
                break;
            case R.id.connect_wifi:
                if (ssid.equals(WifiAutoConnectManager.getSSID()) && bssid.equals(WifiAutoConnectManager.getBSSID())) {
                    return;
                }
                if (mConnectAsyncTask != null) {
                    mConnectAsyncTask.cancel(true);
                    mConnectAsyncTask = null;
                }
                mConnectAsyncTask = new ConnectAsyncTask(ssid, bssid, password, type);
                mConnectAsyncTask.execute();
                break;
            default:
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case WIFI_SCAN_PERMISSION_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                } else {
                    // 不允許
                    Toast.makeText(this, "開啟許可權失敗", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }

    /**
     * 獲取wifi列表
     */
    private class WorkAsyncTask extends AsyncTask<Void, Void, List<ScanResult>> {
        private List<ScanResult> mScanResult = new ArrayList<>();

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressbar.setVisibility(View.VISIBLE);
        }

        @Override
        protected List<ScanResult> doInBackground(Void... params) {
            if (WifiAutoConnectManager.startStan()) {
                mScanResult = WifiAutoConnectManager.getScanResults();
            }
            List<ScanResult> filterScanResultList = new ArrayList<>();
            if (mScanResult != null) {
                for (ScanResult wifi : mScanResult) {
                    filterScanResultList.add(wifi);
                    Log.e("wifidemo", "doInBackground: wifi:" + wifi);
                }
            }
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return filterScanResultList;
        }

        @Override
        protected void onPostExecute(final List<ScanResult> result) {
            super.onPostExecute(result);
            progressbar.setVisibility(View.GONE);
            mScanResultList = result;
            mWifiListAdapter = new WifiListAdapter(result, LayoutInflater.from(MainActivity.this));
            mListWifi.setAdapter(mWifiListAdapter);

        }
    }

    /**
     * wifi列表介面卡
     */
    class WifiListAdapter extends BaseAdapter {

        private List<ScanResult> mWifiList;
        private LayoutInflater mLayoutInflater;
        private int selectItem = -1;

        public WifiListAdapter(List<ScanResult> wifiList, LayoutInflater layoutInflater) {
            this.mWifiList = wifiList;
            this.mLayoutInflater = layoutInflater;
        }

        @Override
        public int getCount() {
            return mWifiList.size();
        }

        @Override
        public Object getItem(int position) {
            return mWifiList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.fragment_wifi_list_item, null);
            }
            ScanResult sr = mWifiList.get(position);
            convertView.setTag(sr);
            TextView textView = (TextView) convertView.findViewById(R.id.wifi_item_name);
            int numLevel = WifiAutoConnectManager.getSignalNumsLevel(sr.level, 5);
            String password = sr.capabilities;
            String text = "加密方式:";
            if (password.contains("WPA") || password.contains("wpa")) {
                password = "WPA";
            } else if (password.contains("WEP") || password.contains("wep")) {
                password = "WEP";
            } else {
                text = "";
                password = "";
            }
            textView.setText(sr.SSID + "( " + sr.BSSID + " ) \n" + " " + text + password + " 訊號強度：" + numLevel);
            convertView.setBackgroundColor(Color.WHITE);
            if (position == selectItem) {
                convertView.setBackgroundColor(Color.GRAY);
            }
            return convertView;
        }

        public void setSelectItem(int selectItem) {
            this.selectItem = selectItem;
        }

        public void setmWifiList(List<ScanResult> mWifiList) {
            this.mWifiList = mWifiList;
        }
    }


    /**
     * 連線指定的wifi
     */
    class ConnectAsyncTask extends AsyncTask<Void, Void, Boolean> {
        WifiConfiguration tempConfig;
        private String ssid;
        private String bssid;
        private String password;
        private WifiAutoConnectManager.WifiCipherType type;

        public ConnectAsyncTask(String ssid, String bssid, String password, WifiAutoConnectManager.WifiCipherType type) {
            this.ssid = ssid;
            this.bssid = bssid;
            this.password = password;
            this.type = type;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressbar.setVisibility(View.VISIBLE);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            // 開啟wifi
            mWifiAutoConnectManager.openWifi();
            // 開啟wifi功能需要一段時間(我在手機上測試一般需要1-3秒左右)，所以要等到wifi
            // 狀態變成WIFI_STATE_ENABLED的時候才能執行下面的語句
            while (mWifiAutoConnectManager.wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLING) {
                try {
                    // 為了避免程式一直while迴圈，讓它睡個100毫秒檢測……
                    Thread.sleep(100);

                } catch (InterruptedException ie) {
                    Log.e("wifidemo", ie.toString());
                }
            }

            tempConfig = mWifiAutoConnectManager.isExsits(ssid, bssid);
            //禁掉所有wifi
            for (WifiConfiguration c : mWifiAutoConnectManager.wifiManager.getConfiguredNetworks()) {
                mWifiAutoConnectManager.wifiManager.disableNetwork(c.networkId);
            }
            if (tempConfig != null) {
                Log.d("wifidemo", ssid + "配置過！");
                boolean result = mWifiAutoConnectManager.wifiManager.enableNetwork(tempConfig.networkId, true);
                if (!isLinked && type != WifiAutoConnectManager.WifiCipherType.WIFICIPHER_NOPASS) {
                    try {
                        Thread.sleep(5000);//超過5s提示失敗
                        if (!isLinked) {
                            Log.d("wifidemo", ssid + "連線失敗！");
                            mWifiAutoConnectManager.wifiManager.disableNetwork(tempConfig.networkId);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressbar.setVisibility(View.GONE);
                                    Toast.makeText(getApplicationContext(), "連線失敗!請在系統裡刪除wifi連線，重新連線。", Toast.LENGTH_SHORT).show();
                                    new AlertDialog.Builder(MainActivity.this)
                                            .setTitle("連線失敗！")
                                            .setMessage("請在系統裡刪除wifi連線，重新連線。")
                                            .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    dialog.dismiss();
                                                }
                                            })
                                            .setPositiveButton("好的", new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int which) {
                                                    Intent intent = new Intent();
                                                    intent.setAction("android.net.wifi.PICK_WIFI_NETWORK");
                                                    startActivity(intent);
                                                }
                                            }).show();
                                }
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                Log.d("wifidemo", "result=" + result);
                return result;
            } else {
                Log.d("wifidemo", ssid + "沒有配置過！");
                if (type != WifiAutoConnectManager.WifiCipherType.WIFICIPHER_NOPASS) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            final EditText inputServer = new EditText(MainActivity.this);
                            //inputServer.setText("123456");
                            inputServer.setTransformationMethod(PasswordTransformationMethod.getInstance());
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("請輸入密碼")
                                    .setView(inputServer)
                                    .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    })
                                    .setPositiveButton("連線", new DialogInterface.OnClickListener() {

                                        public void onClick(DialogInterface dialog, int which) {
                                            password = inputServer.getText().toString();
                                            new Thread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    WifiConfiguration wifiConfig = mWifiAutoConnectManager.createWifiInfo(ssid, bssid, password,
                                                            type);
                                                    if (wifiConfig == null) {
                                                        Log.d("wifidemo", "wifiConfig is null!");
                                                        return;
                                                    }
                                                    Log.d("wifidemo", wifiConfig.SSID);

                                                    int netID = mWifiAutoConnectManager.wifiManager.addNetwork(wifiConfig);
                                                    boolean enabled = mWifiAutoConnectManager.wifiManager.enableNetwork(netID, true);
                                                    Log.d("wifidemo", "enableNetwork status enable=" + enabled);
//                                                    Log.d("wifidemo", "enableNetwork connected=" + mWifiAutoConnectManager.wifiManager.reconnect());
//                                                    mWifiAutoConnectManager.wifiManager.reconnect();
                                                }
                                            }).start();
                                        }
                                    }).show();
                        }
                    });
                } else {
                    WifiConfiguration wifiConfig = mWifiAutoConnectManager.createWifiInfo(ssid, bssid, password, type);
                    if (wifiConfig == null) {
                        Log.d("wifidemo", "wifiConfig is null!");
                        return false;
                    }
                    Log.d("wifidemo", wifiConfig.SSID);
                    int netID = mWifiAutoConnectManager.wifiManager.addNetwork(wifiConfig);
                    boolean enabled = mWifiAutoConnectManager.wifiManager.enableNetwork(netID, true);
                    Log.d("wifidemo", "enableNetwork status enable=" + enabled);
//                    Log.d("wifidemo", "enableNetwork connected=" + mWifiAutoConnectManager.wifiManager.reconnect());
//                    return mWifiAutoConnectManager.wifiManager.reconnect();
                    return enabled;
                }
                return false;


            }
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            mConnectAsyncTask = null;
        }
    }
}