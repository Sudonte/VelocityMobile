package com.example.velocitysuites;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

public class NetworkUtils {

    /** The OS's own downstream estimate in kbps, or 0 when unknown. Only ever a hint: real timing decides (ConnectionProbe). */
    public static int downstreamKbps(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return 0;
        Network network = cm.getActiveNetwork();
        if (network == null) return 0;
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities == null ? 0 : capabilities.getLinkDownstreamBandwidthKbps();
    }

    private NetworkUtils() { }

    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}
