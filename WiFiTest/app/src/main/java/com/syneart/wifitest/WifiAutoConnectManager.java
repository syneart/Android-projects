package com.syneart.wifitest;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.TextUtils;
import android.util.Log;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by lake on 2017/10/17.
 * [email protected]
 */
public class WifiAutoConnectManager {
    private static final String TAG = WifiAutoConnectManager.class
            .getSimpleName();

    public static WifiManager wifiManager = null;
    private static WifiAutoConnectManager mWifiAutoConnectManager;

    // 定義幾種加密方式，一種是WEP，一種是WPA，還有沒有密碼的情況
    public enum WifiCipherType {
        WIFICIPHER_WEP, WIFICIPHER_WPA, WIFICIPHER_NOPASS, WIFICIPHER_INVALID
    }

    // 建構函式
    private WifiAutoConnectManager(WifiManager wifiManager) {
        this.wifiManager = wifiManager;
    }

    public static WifiAutoConnectManager newInstance(WifiManager wifiManager) {
        if (mWifiAutoConnectManager == null) {
            mWifiAutoConnectManager = new WifiAutoConnectManager(wifiManager);
        }
        return mWifiAutoConnectManager;
    }


    // 檢視以前是否也配置過這個網路 with SSID only
    public WifiConfiguration isExsits(String SSID) {
        List<WifiConfiguration> existingConfigs = wifiManager
                .getConfiguredNetworks();
        for (WifiConfiguration existingConfig : existingConfigs) {
            if (existingConfig.SSID.equals("\"" + SSID + "\"")) {
                return existingConfig;
            }
        }
        return null;
    }

    // 檢視以前是否也配置過這個網路 with SSID & BSSIS
    public WifiConfiguration isExsits(String SSID, String BSSID) {
        List<WifiConfiguration> existingConfigs = wifiManager
                .getConfiguredNetworks();
        for (WifiConfiguration existingConfig : existingConfigs) {
            Log.e("qaq", "existingConfig.BSSID = " + existingConfig.BSSID);
            Log.e("qaq", "BSSID = " + BSSID);
            if (existingConfig.SSID.equals("\"" + SSID + "\"") && existingConfig.BSSID.equals(BSSID) ) {
                return existingConfig;
            }
        }
        return null;
    }

    /**
     * 建立wifi配置檔案
     *
     * @param SSID
     * @param Password
     * @param Type
     * @return
     */
    public WifiConfiguration createWifiInfo(String SSID, String BSSID, String Password, WifiCipherType Type) {
        WifiConfiguration config = new WifiConfiguration();
        config.allowedAuthAlgorithms.clear();
        config.allowedGroupCiphers.clear();
        config.allowedKeyManagement.clear();
        config.allowedPairwiseCiphers.clear();
        config.allowedProtocols.clear();
        config.SSID = "\"" + SSID + "\"";
        config.BSSID = BSSID;
        // config.SSID = SSID;
        // nopass
        if (Type == WifiCipherType.WIFICIPHER_NOPASS) {
            // config.wepKeys[0] = "";
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            // config.wepTxKeyIndex = 0;
        } else if (Type == WifiCipherType.WIFICIPHER_WEP) {// wep
            if (!TextUtils.isEmpty(Password)) {
                if (isHexWepKey(Password)) {
                    config.wepKeys[0] = Password;
                } else {
                    config.wepKeys[0] = "\"" + Password + "\"";
                }
            }
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            config.wepTxKeyIndex = 0;
        } else if (Type == WifiCipherType.WIFICIPHER_WPA) {// wpa
            config.preSharedKey = "\"" + Password + "\"";
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            config.status = WifiConfiguration.Status.ENABLED;
        }
        return config;
    }

    // 開啟wifi功能
    public boolean openWifi() {
        boolean bRet = true;
        if (!wifiManager.isWifiEnabled()) {
            bRet = wifiManager.setWifiEnabled(true);
        }
        return bRet;
    }

    // 關閉WIFI
    private void closeWifi() {
        if (wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(false);
        }
    }

    private static boolean isHexWepKey(String wepKey) {
        final int len = wepKey.length();

        // WEP-40, WEP-104, and some vendors using 256-bit WEP (WEP-232?)
        if (len != 10 && len != 26 && len != 58) {
            return false;
        }

        return isHex(wepKey);
    }

    private static boolean isHex(String key) {
        for (int i = key.length() - 1; i >= 0; i--) {
            final char c = key.charAt(i);
            if (!(c >= '0' && c <= '9' || c >= 'A' && c <= 'F' || c >= 'a'
                    && c <= 'f')) {
                return false;
            }
        }

        return true;
    }

    /**
     * 根據給定的ssid訊號量和總級別，判斷當前訊號量，在什麼級別
     *
     * @param rssi
     * @param numLevels
     * @return
     */
    public static int getSignalNumsLevel(int rssi, int numLevels) {
        if (wifiManager == null) {
            return -1;
        }
        return WifiManager.calculateSignalLevel(rssi, numLevels);
    }

    /**
     * 獲取ssid的加密方式
     */
    public static WifiCipherType getCipherType(String ssid) {
        if (wifiManager == null) {
            return null;
        }
        List<ScanResult> list = wifiManager.getScanResults();

        for (ScanResult scResult : list) {

            if (!TextUtils.isEmpty(scResult.SSID) && scResult.SSID.equals(ssid)) {
                String capabilities = scResult.capabilities;
                if (!TextUtils.isEmpty(capabilities)) {

                    if (capabilities.contains("WPA")
                            || capabilities.contains("wpa")) {
                        Log.e("wifidemo", "wpa");
                        return WifiCipherType.WIFICIPHER_WPA;
                    } else if (capabilities.contains("WEP")
                            || capabilities.contains("wep")) {
                        Log.e("wifidemo", "wep");
                        return WifiCipherType.WIFICIPHER_WEP;
                    } else {
                        Log.e("wifidemo", "no");
                        return WifiCipherType.WIFICIPHER_NOPASS;
                    }
                }
            }
        }
        return WifiCipherType.WIFICIPHER_INVALID;
    }

    /**
     * 獲取 bssid 接入點的地址
     * @return
     */
    public static String getBSSID() {
        if (wifiManager == null) {
            return null;
        }
        WifiInfo info = wifiManager.getConnectionInfo();
        Log.e("wifidemo", "getBSSID - " + info.getBSSID());
        if (info == null) {
            return null;
        }
        return info.getBSSID();
    }

    /**
     * 獲取閘道器地址
     *
     * @return
     */
    public static String getGateway() {
        if (wifiManager == null) {
            return "";
        }
        InetAddress inetAddress = NetworkUtils.intToInetAddress(wifiManager.getDhcpInfo().gateway);
        if (inetAddress == null) {
            return "";
        }
        return inetAddress.getHostAddress();
    }

    /**
     * 獲取ip地址
     * @return
     */
    public static String getIpAddress(){
        if (wifiManager == null) {
            return "";
        }
        InetAddress inetAddress = NetworkUtils.intToInetAddress(wifiManager.getConnectionInfo().getIpAddress());
        if (inetAddress == null) {
            return "";
        }
        return inetAddress.getHostAddress();
    }
    /**
     * 獲取mac地址
     * @return
     */
    public static String getMacAddress(){
        if (wifiManager == null) {
            return "";
        }
        return wifiManager.getConnectionInfo().getMacAddress();
    }
    /**
     * 獲取wifi名稱
     *
     * @return
     */
    public static String getSSID() {
        if (wifiManager == null) {
            return null;
        }
        WifiInfo info = wifiManager.getConnectionInfo();
        String ssid = info.getSSID();
        if (ssid != null) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }
        return ssid;
    }

    /**
     * 掃描WIFI AP
     */
    public static boolean startStan() {
        if (wifiManager == null) {
            return false;
        }
        return wifiManager.startScan();
    }

    /**
     * 獲取所有WIFI AP
     */
    public static List<ScanResult> getScanResults() {
        List<ScanResult> srList = wifiManager.getScanResults();
        if (srList == null) {
            srList = new ArrayList<ScanResult>();
        }
        return srList;
    }

}