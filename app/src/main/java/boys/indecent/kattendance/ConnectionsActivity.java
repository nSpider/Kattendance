package boys.indecent.kattendance;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public abstract class ConnectionsActivity extends AppCompatActivity {

    private static final String[] REQUIRED_PERMISSIONS =
            new String[] {
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
    /** The request code to handle the permission requests */
    private static final  int REQUEST_CODE_REQUIRED_PERMISSION = 1;

    private static final String TAG = "ConnectionsActivity";

    /** NearbyConnection handler */
    private ConnectionsClient mConnectionClient;

    /** The devices we've discovered near us. */
    private final Map<String, Endpoint> mDiscoveredEndpoints = new HashMap<>();

    /**
     * The devices we have pending connections to. They will stay pending until we call {@link
     * #acceptConnection(Endpoint)} or {@link #rejectConnection(Endpoint)}.
     */
    private final Map<String, Endpoint> mPendingConnections = new HashMap<>();

    /** The child devices we are currently connected to. */
    private final Map<String, Endpoint> mEstablishedChildConnections = new HashMap<>();

    /** The parent device we are currently connected to */
    private Endpoint mEstablishedParentConnection = null;

    /**
     * True if we are asking a discovered device to connect to us. While we ask, we cannot ask another
     * device.
     */
    private boolean mIsConnecting = false;

    /** True if we are discovering. */
    private boolean mIsDiscovering = false;

    /** True if we are advertising. */
    private boolean mIsAdvertising = false;

    /** Callback for connections to parent devices as a child device */
    private final ConnectionLifecycleCallback mConnectionLifecycleCallbackAsChild =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo connectionInfo) {

                }

                @Override
                public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution connectionResolution) {

                }

                @Override
                public void onDisconnected(@NonNull String endpointId) {

                }
            };

    /** Callback for connections to child devices as a parent device */
    private final ConnectionLifecycleCallback mConnectionLifecycleCallbackAsParent =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo connectionInfo) {
                    Log.d(TAG, String.format(
                            "onConnectionInitiated(endpointId=%s, endpointName=%s)",
                            endpointId, connectionInfo.getEndpointName()));

                    Endpoint endpoint = new Endpoint(endpointId, connectionInfo.getEndpointName());
                    mPendingConnections.put(endpointId,endpoint);
                    ConnectionsActivity.this.onConnectionInitiated(endpoint, connectionInfo);
                }

                @Override
                public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution connectionResolution) {
                    Log.d(
                            TAG,
                            String.format(
                                    "onConnectionResponse(endpointId=%s, result=%s)",
                                    endpointId,
                                    connectionResolution
                            )
                    );

                    mIsConnecting = false;

                    if (!connectionResolution.getStatus().isSuccess()){
                        Log.w(
                                TAG,
                                "Connection failed. Received status "
                                        + ConnectionsActivity.toString(connectionResolution.getStatus())
                        );
                        onConnectionFailed(mPendingConnections.remove(endpointId));
                        return;
                    }
                    connectedToEndpointAsParent(mPendingConnections.remove(endpointId));
                }

                @Override
                public void onDisconnected(@NonNull String endpointId) {
                    //TODO : Start frm here
                }
            };

    private final PayloadCallback payloadCallbackAsChild =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(@NonNull String s, @NonNull Payload payload) {

                }

                @Override
                public void onPayloadTransferUpdate(@NonNull String s, @NonNull PayloadTransferUpdate payloadTransferUpdate) {

                }
            };

    private final PayloadCallback payloadCallbackAsParent =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(@NonNull String s, @NonNull Payload payload) {

                }

                @Override
                public void onPayloadTransferUpdate(@NonNull String s, @NonNull PayloadTransferUpdate payloadTransferUpdate) {

                }
            };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mConnectionClient = Nearby.getConnectionsClient(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!hasPermissions(this, getRequiredPermissions())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(getRequiredPermissions(), REQUEST_CODE_REQUIRED_PERMISSION);
            }
        }
    }

    /** Called when the user has accepted (or denied) our permission request. */
    @CallSuper
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_REQUIRED_PERMISSION) {
            for (int grantResult : grantResults) {
                if (grantResult == PackageManager.PERMISSION_DENIED) {
                    Toast.makeText(this, R.string.error_missing_permissions, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
            recreate();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    protected void startAdvertising(){
        mIsAdvertising = true;
        final String localEndpointName = getName();
        final AdvertisingOptions advertisingOptions =
                new AdvertisingOptions.Builder().setStrategy(getStrategy()).build();

        mConnectionClient
                .startAdvertising(
                        localEndpointName,
                        getServiceId(),
                        mConnectionLifecycleCallbackAsParent,
                        advertisingOptions
                ).addOnSuccessListener(
                new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.v(TAG,"Now advertising endpoint " + localEndpointName);
                        onAdvertisingStarted();
                    }
                }).addOnFailureListener(
                new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        mIsAdvertising = false;
                        Log.w(TAG, "startAdvertising() failed", e);
                        onAdvertisingFailed();
                    }
                });

    }

    /** Stops advertising. */
    protected void stopAdvertising() {
        mIsAdvertising = false;
        mConnectionClient.stopAdvertising();
    }

    /** Returns {@code true} if currently advertising. */
    protected boolean isAdvertising() {
        return mIsAdvertising;
    }

    /** Called when advertising successfully starts. Override this method to act on the event. */
    protected void onAdvertisingStarted() {}

    /** Called when advertising fails to start. Override this method to act on the event. */
    protected void onAdvertisingFailed() {}

    /**
     * Called when a pending connection with a remote endpoint is created. Use {@link ConnectionInfo}
     * for metadata about the connection (like incoming vs outgoing, or the authentication token). If
     * we want to continue with the connection, call {@link #acceptConnection(Endpoint)}. Otherwise,
     * call {@link #rejectConnection(Endpoint)}.
     */
    protected void onConnectionInitiated(Endpoint endpoint, ConnectionInfo connectionInfo) {}

    /** Accepts a connection request*/
    protected void acceptConnection(final Endpoint endpoint){
        mConnectionClient
                .acceptConnection(endpoint.getId(), payloadCallbackAsParent)
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG,"acceptConnection() failed.",e);
                    }
                });
    }

    /** Rejects a connection request */
    protected void rejectConnection(final Endpoint endpoint){
        mConnectionClient
                .rejectConnection(endpoint.getId())
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG,"rejectConnection() failed.",e);
                    }
                });
    }

    /**
     * Sets the device to discovery mode. It will now listen for devices in advertising mode. Either
     * {@link #onDiscoveryStarted()} or {@link #onDiscoveryFailed()} will be called once we've found
     * out if we successfully entered this mode.
     */
    protected void startDiscovering() {
        mIsDiscovering = true;
        mDiscoveredEndpoints.clear();
        final DiscoveryOptions discoveryOptions =
                new DiscoveryOptions.Builder()
                        .setStrategy(getStrategy())
                        .build();

        mConnectionClient
                .startDiscovery(
                        getServiceId(),
                        new EndpointDiscoveryCallback() {
                            @Override
                            public void onEndpointFound(@NonNull String s, @NonNull DiscoveredEndpointInfo discoveredEndpointInfo) {

                            }

                            @Override
                            public void onEndpointLost(@NonNull String s) {

                            }
                        },
                        discoveryOptions
                ).addOnSuccessListener(
                        new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                onDiscoveryStarted();
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                mIsDiscovering = false;
                                Log.w(TAG,"startDiscovery() failed",e);
                                onDiscoveryFailed();
                            }
                        });

    }

    /** Stops discovery. */
    protected void stopDiscovering() {
        mIsDiscovering = false;
        mConnectionClient.stopDiscovery();
    }

    /** Returns {@code true} if currently discovering. */
    protected boolean isDiscovering() {
        return mIsDiscovering;
    }

    /** Called when discovery successfully starts. Override this method to act on the event. */
    protected void onDiscoveryStarted() {}

    /** Called when discovery fails to start. Override this method to act on the event. */
    protected void onDiscoveryFailed() {}

    /**
     * Called when a remote endpoint is discovered. To connect to the device, call {@link
     * #connectToEndpoint(Endpoint)}.
     */
    protected void onEndpointDiscovered(Endpoint endpoint) {}

    /** Disconnects from the given endpoint. */
    protected void disconnect(Endpoint endpoint) {
        mConnectionClient.disconnectFromEndpoint(endpoint.getId());
        mEstablishedChildConnections.remove(endpoint.getId());
    }

    /** Disconnects from all currently connected endpoints. */
    protected void disconnectFromAllEndpoints() {
        for (Endpoint endpoint : mEstablishedChildConnections.values()) {
            mConnectionClient.disconnectFromEndpoint(endpoint.getId());
        }
        mEstablishedChildConnections.clear();

        if (mEstablishedParentConnection != null){
            mConnectionClient.disconnectFromEndpoint(mEstablishedParentConnection.getId());
        }
        mEstablishedParentConnection = null;
    }

    /** Resets and clears all state in Nearby Connections. */
    protected void stopAllEndpoints() {
        mConnectionClient.stopAllEndpoints();
        mIsAdvertising = false;
        mIsDiscovering = false;
        mIsConnecting = false;
        mDiscoveredEndpoints.clear();
        mPendingConnections.clear();
        mEstablishedChildConnections.clear();
        mEstablishedParentConnection = null;
    }

    /**
     * Sends a connection request to the endpoint. Either {@link #onConnectionInitiated(Endpoint,
     * ConnectionInfo)} or {@link #onConnectionFailed(Endpoint)} will be called once we've found out
     * if we successfully reached the device.
     */
    protected void connectToEndpoint(final Endpoint endpoint){
        Log.v(TAG,"Sending a connection request to endpoint " + endpoint.toString());

        mIsConnecting = true;

        mConnectionClient
                .requestConnection(getName(),endpoint.getId(), mConnectionLifecycleCallbackAsChild)
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG,"requestConnection() failed",e);
                        mIsConnecting = false;
                        onConnectionFailed(endpoint);
                    }
                });
    }

    /** Returns {@code true} if we're currently attempting to connect to another device. */
    protected final boolean isConnecting() {
        return mIsConnecting;
    }

    private void connectedToEndpointAsChild(Endpoint endpoint) {
        Log.d(TAG,String.format("connectedToEndpoint(endpoint=%s)", endpoint));
        mEstablishedParentConnection = endpoint;
        onEndpointConnectedAsChild(endpoint);
    }

    private void connectedToEndpointAsParent(Endpoint endpoint) {
        Log.d(TAG,String.format("connectedToEndpoint(endpoint=%s)", endpoint));
        mEstablishedChildConnections.put(endpoint.getId(), endpoint);
        onEndpointConnectedAsParent(endpoint);
    }

    private void disconnectedFromEndpointAsChild(Endpoint endpoint) {
        Log.d(TAG,String.format("disconnectedFromEndpoint(endpoint=%s)", endpoint));
        mEstablishedParentConnection = null;
        onEndpointDisconnectedAsChild(endpoint);
    }

    private void disconnectedFromEndpointAsParent(Endpoint endpoint) {
        Log.d(TAG,String.format("disconnectedFromEndpoint(endpoint=%s)", endpoint));
        mEstablishedChildConnections.remove(endpoint.getId());
        onEndpointDisconnectedAsParent(endpoint);
    }

    /**
     * Called when a connection with this endpoint has failed. Override this method to act on the
     * event.
     */
    protected void onConnectionFailed(Endpoint endpoint) {}

    /** Called when we are connected to a parent. Override this method to act on the event. */
    protected void onEndpointConnectedAsChild(Endpoint endpoint) {}

    /** Called when a child got connected to us. Override this method to act on the event. */
    protected void onEndpointConnectedAsParent(Endpoint endpoint) {}

    /** Called when we have disconnected from a parent. Override this method to act on the event. */
    protected void onEndpointDisconnectedAsChild(Endpoint endpoint) {}

    /** Called when a child got disconnected from us. Override this method to act on the event. */
    protected void onEndpointDisconnectedAsParent(Endpoint endpoint) {}

    /** Returns a list of currently connected endpoints. */
    protected Set<Endpoint> getDiscoveredEndpoints() {
        return new HashSet<>(mDiscoveredEndpoints.values());
    }

    /** Returns a list of currently connected child endpoints. */
    protected Set<Endpoint> getConnectedChildEndpoints() {
        return new HashSet<>(mEstablishedChildConnections.values());
    }

    /** Returns a list of currently connected parent endpoint. */
    protected Endpoint getConnectedParentEndpoint() {
        return mEstablishedParentConnection;
    }









    /** @return All permissions required for the app to properly function. */
    private String[] getRequiredPermissions() {
        return REQUIRED_PERMISSIONS;
    }

    /** Returns the client's name. Visible to others when connecting. */
    protected abstract String getName();

    /**
     * Returns the service id. This represents the action this connection is for. When discovering,
     * we'll verify that the advertiser has the same service id before we consider connecting to them.
     */
    protected abstract String getServiceId();

    /**
     * Returns the strategy we use to connect to other devices. Only devices using the same strategy
     * and service id will appear when discovering. Strategies determine how many incoming and outgoing
     * connections are possible at the same time, as well as how much bandwidth is available for use.
     */
    protected abstract Strategy getStrategy();

    /**
     * Transforms a {@link Status} into a English-readable message for logging.
     *
     * @param status The current status
     * @return A readable String. eg. [404]File not found.
     */
    private static String toString(Status status) {
        return String.format(
                Locale.US,
                "[%d]%s",
                status.getStatusCode(),
                status.getStatusMessage() != null
                        ? status.getStatusMessage()
                        : ConnectionsStatusCodes.getStatusCodeString(status.getStatusCode()));
    }

    /**
     * Returns {@code true} if the app was granted all the permissions. Otherwise, returns {@code
     * false}.
     */
    public static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }


    /** Represents a device communicate */
    protected static class Endpoint {
        @NonNull private final String id;
        @NonNull private final String name;

        private Endpoint(@NonNull String id, @NonNull String name) {
            this.id = id;
            this.name = name;
        }

        @NonNull
        public String getId() {
            return id;
        }

        @NonNull
        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Endpoint) {
                Endpoint other = (Endpoint) obj;
                return id.equals(other.id);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return String.format("Endpoint{id=%s, name=%s}", id, name);
        }
    }
}
