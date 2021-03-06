package com.novoda.merlin.service;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.support.annotation.NonNull;

import com.novoda.merlin.NetworkStatus;
import com.novoda.merlin.RxCallbacksManager;
import com.novoda.merlin.receiver.ConnectivityChangeEvent;
import com.novoda.merlin.registerable.connection.ConnectListener;
import com.novoda.merlin.registerable.disconnection.DisconnectListener;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(JUnit4.class)
public class MerlinServiceShould {

    @Mock
    private Intent intent;
    @Mock
    private Context context;
    @Mock
    private ConnectListener connectListener;
    @Mock
    private DisconnectListener disconnectListener;
    @Mock
    private RxCallbacksManager rxCallbacksManager;

    private MerlinService merlinService;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        merlinService = new MerlinService() {

            @Override
            public void unregisterReceiver(BroadcastReceiver receiver) {
            }

            @Override
            public void onConnectivityChanged(ConnectivityChangeEvent connectivityChangeEvent) {
                // TODO : this is bad, but I blame not having a proper constructor
                if (connectivityChangeEvent.isConnected()) {
                    connectListener.onConnect();
                    rxCallbacksManager.onConnect();
                } else {
                    disconnectListener.onDisconnect();
                    rxCallbacksManager.onDisconnect();
                }
            }
        };
    }

    @Test
    public void enableConnectivityReceiverInOnBind() throws Exception {
        PackageManager mockPackageManager = mock(PackageManager.class);
        ComponentName mockReceiver = mock(ComponentName.class);
        MerlinService merlinService = new TestDoubleMerlinService(mockPackageManager, mockReceiver);

        merlinService.onBind(intent);

        verify(mockPackageManager).setComponentEnabledSetting(
                mockReceiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
        );
    }

    @Test
    public void enableConnectivityReceiverInOnBindOnApiLevel24() throws Exception {
        ContextWrapper contextWrapper = stubbedContextWrapper();

        NetworkStatusRetriever mockRetriever = mock(NetworkStatusRetriever.class);
        HostPinger mockDefaultPinger = mock(HostPinger.class);
        HostPinger mockCustomPinger = mock(HostPinger.class);

        MerlinService merlinService = buildStubbedMerlinServiceApiLevel24(contextWrapper, mockRetriever, mockDefaultPinger, mockCustomPinger);

        merlinService.onBind(intent);

        verify(contextWrapper).registerReceiver(merlinService.getConnectivityReceiver(), merlinService.getConnectivityActionIntentFilter());
    }

    @Test
    public void disableConnectivityReceiverInOnUnbind() throws Exception {
        PackageManager mockPackageManager = mock(PackageManager.class);
        ComponentName mockReceiver = mock(ComponentName.class);
        MerlinService merlinService = new TestDoubleMerlinService(mockPackageManager, mockReceiver);

        merlinService.onUnbind(intent);

        verify(mockPackageManager).setComponentEnabledSetting(
                mockReceiver,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
    }

    @Test
    public void disableConnectivityReceiverInOnUnbindOnApiLevel24() throws Exception {
        ContextWrapper contextWrapper = stubbedContextWrapper();

        NetworkStatusRetriever mockRetriever = mock(NetworkStatusRetriever.class);
        HostPinger mockDefaultPinger = mock(HostPinger.class);
        HostPinger mockCustomPinger = mock(HostPinger.class);

        MerlinService merlinService = buildStubbedMerlinServiceApiLevel24(contextWrapper, mockRetriever, mockDefaultPinger, mockCustomPinger);

        merlinService.onUnbind(intent);

        verify(contextWrapper).unregisterReceiver(merlinService.getConnectivityReceiver());
    }

    @Test
    public void callMerlinNetworkListenerOnConnectedWhenAConnectedConnectivityEventIsTriggered() throws Exception {
        boolean isConnected = true;
        ConnectivityChangeEvent connectivityChangeEvent = createConnectivityChangeEvent(isConnected);

        merlinService.onConnectivityChanged(connectivityChangeEvent);

        verify(connectListener).onConnect();
        verify(rxCallbacksManager).onConnect();
    }

    @Test
    public void callMerlinNetworkListenerOnDisconnectedWhenAConnectedConnectivityEventIsTriggered() throws Exception {
        boolean isConnected = false;
        ConnectivityChangeEvent connectivityChangeEvent = createConnectivityChangeEvent(isConnected);

        merlinService.onConnectivityChanged(connectivityChangeEvent);

        verify(disconnectListener).onDisconnect();
        verify(rxCallbacksManager).onDisconnect();
    }

    @Test
    public void useDefaultNetworkStatusRetrieverWhenPingingDefaultEndpoint() {
        ContextWrapper contextWrapper = stubbedContextWrapper();

        NetworkStatusRetriever mockRetriever = mock(NetworkStatusRetriever.class);
        HostPinger mockDefaultPinger = mock(HostPinger.class);
        HostPinger mockCustomPinger = mock(HostPinger.class);

        MerlinService merlinService = buildStubbedMerlinService(contextWrapper, mockRetriever, mockDefaultPinger, mockCustomPinger);

        merlinService.onCreate();
        merlinService.onConnectivityChanged(networkAvailableEvent());
        verify(mockRetriever).fetchWithPing(mockDefaultPinger);
    }

    @Test
    public void useCustomNetworkStatusRetrieverWhenPingingCustomEndpoint() {
        ContextWrapper contextWrapper = stubbedContextWrapper();

        NetworkStatusRetriever mockRetriever = mock(NetworkStatusRetriever.class);
        HostPinger mockDefaultPinger = mock(HostPinger.class);
        HostPinger mockCustomPinger = mock(HostPinger.class);

        MerlinService merlinService = buildStubbedMerlinService(contextWrapper, mockRetriever, mockDefaultPinger, mockCustomPinger);

        merlinService.onCreate();
        merlinService.setHostname("some new host name", mock(ResponseCodeValidator.class));
        merlinService.onConnectivityChanged(networkAvailableEvent());
        verify(mockRetriever).fetchWithPing(mockCustomPinger);
    }

    @NonNull
    private ContextWrapper stubbedContextWrapper() {
        Context context = mock(Context.class);
        ContextWrapper contextWrapper = mock(ContextWrapper.class);
        ConnectivityManager connectivityManager = mock(ConnectivityManager.class);
        when(contextWrapper.getApplicationContext()).thenReturn(context);
        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);
        return contextWrapper;
    }

    @NonNull
    private MerlinService buildStubbedMerlinService(
            ContextWrapper contextWrapper,
            NetworkStatusRetriever retriever,
            HostPinger defaultPinger,
            HostPinger customPinger
    ) {
        return new TestDoubleMerlinServiceWithStubbedBuilders(
                contextWrapper,
                retriever,
                defaultPinger,
                customPinger
        );
    }

    @NonNull
    private MerlinService buildStubbedMerlinServiceApiLevel24(
            ContextWrapper contextWrapper,
            NetworkStatusRetriever retriever,
            HostPinger defaultPinger,
            HostPinger customPinger) {
        return new TestDoubleMerlinServiceWithStubbedBuildersAndSdkVersionN(
                contextWrapper,
                retriever,
                defaultPinger,
                customPinger
        );
    }

    @NonNull
    private ConnectivityChangeEvent networkAvailableEvent() {
        ConnectivityChangeEvent connectivityChangeEvent = mock(ConnectivityChangeEvent.class);
        when(connectivityChangeEvent.asNetworkStatus()).thenReturn(NetworkStatus.newAvailableInstance());
        return connectivityChangeEvent;
    }

    private ConnectivityChangeEvent createConnectivityChangeEvent(boolean isConnected) {
        return new ConnectivityChangeEvent(isConnected, "info", "reason");
    }

}
