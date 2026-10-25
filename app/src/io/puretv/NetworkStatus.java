package io.puretv;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/** Read-only Android connectivity snapshot. No probes, sockets, scanning or extra permissions. */
final class NetworkStatus {
    /** A visible page observes changes; static pages never poll connectivity. */
    static final class Watch {
        private final ConnectivityManager manager;
        private final Context context;
        private final Runnable changed;
        private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        private boolean registered;
        private volatile boolean dirty=true;
        private Snapshot snapshot=unknown();
        private final Runnable refresh = this::refresh;
        private void refresh() { if(registered) changed.run(); }
        private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network n) { schedule(); }
            @Override public void onLost(Network n) { schedule(); }
            @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { schedule(); }
            @Override public void onLinkPropertiesChanged(Network n, LinkProperties p) { schedule(); }
        };
        Watch(Context c,Runnable changed) {context=c.getApplicationContext();manager=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);this.changed=changed;}
        Snapshot snapshot(){if(dirty){dirty=false;snapshot=read(context);}return snapshot;}
        private void schedule() {dirty=true;main.removeCallbacks(refresh);main.postDelayed(refresh,150);}
        void start() {
            dirty=true;
            if(registered||manager==null)return;
            try{manager.registerDefaultNetworkCallback(callback);registered=true;}catch(RuntimeException ignored){}
        }
        void stop() {
            if(registered)try{manager.unregisterNetworkCallback(callback);}catch(RuntimeException ignored){}
            registered=false;main.removeCallbacks(refresh);
        }
    }
    static final int UNKNOWN = 0, OFFLINE = 1, LOCAL = 2, ONLINE = 3;
    static final class Snapshot {
        final int state;
        final String transport, addresses;
        Snapshot(int state, String transport, String addresses) {
            this.state = state;
            this.transport = transport;
            this.addresses = addresses;
        }
    }
    static Snapshot read(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return unknown();
            Network network = cm.getActiveNetwork();
            if (network == null) return new Snapshot(OFFLINE, "", "");
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps == null) return unknown();
            String transport = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? "VPN"
                    : caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "Ethernet"
                    : caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi" : Ui.tr("Rede", "Network");
            List<String> ips = new ArrayList<>();
            List<String> ipv6 = new ArrayList<>();
            LinkProperties links = cm.getLinkProperties(network);
            if (links != null) for (LinkAddress link : links.getLinkAddresses()) {
                InetAddress address = link.getAddress();
                if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress()) {
                    ips.add(address.getHostAddress());
                } else if (!address.isLoopbackAddress() && !address.isLinkLocalAddress()) {
                    ipv6.add(address.getHostAddress());
                }
            }
            return new Snapshot(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? ONLINE : LOCAL,
                    transport, android.text.TextUtils.join(", ", ips.isEmpty() ? ipv6 : ips));
        } catch (RuntimeException e) {
            return unknown();
        }
    }
    private static Snapshot unknown() { return new Snapshot(UNKNOWN, "", ""); }
    static String label(Snapshot snapshot) {
        switch (snapshot.state) {
            case ONLINE: return Ui.tr("Internet disponível", "Internet available");
            case LOCAL: return Ui.tr("Internet não verificada", "Internet not verified");
            case OFFLINE: return Ui.tr("Sem conexão", "Disconnected");
            default: return Ui.tr("Status indisponível", "Status unavailable");
        }
    }
}
