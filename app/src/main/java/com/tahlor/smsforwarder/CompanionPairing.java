package com.tahlor.smsforwarder;

import android.app.Activity;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;

import java.util.List;

final class CompanionPairing {
    static final int REQUEST_CODE = 2201;

    interface Listener {
        void onAssociationChanged();
        void onFailure(String message);
    }

    private CompanionPairing() {}

    static boolean isSupported(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && context.getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_COMPANION_DEVICE_SETUP);
    }

    static int associationCount(Context context) {
        if (!isSupported(context)) return 0;
        CompanionDeviceManager manager = manager(context);
        if (manager == null) return 0;
        try {
            @SuppressWarnings("deprecation")
            List<String> associations = manager.getAssociations();
            return associations == null ? 0 : associations.size();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    static boolean hasAssociation(Context context) {
        return associationCount(context) > 0;
    }

    static void startAssociation(Activity activity, Listener listener) {
        if (!isSupported(activity)) {
            listener.onFailure("Companion device setup is not supported on this phone.");
            return;
        }

        CompanionDeviceManager manager = manager(activity);
        if (manager == null) {
            listener.onFailure("Android companion device service is unavailable.");
            return;
        }

        BluetoothDeviceFilter filter = new BluetoothDeviceFilter.Builder().build();
        AssociationRequest request = new AssociationRequest.Builder()
                .addDeviceFilter(filter)
                .setSingleDevice(false)
                .build();

        CompanionDeviceManager.Callback callback =
                new CompanionDeviceManager.Callback() {
                    @Override
                    @SuppressWarnings("deprecation")
                    public void onDeviceFound(IntentSender chooserLauncher) {
                        launchChooser(activity, chooserLauncher, listener);
                    }

                    @Override
                    public void onFailure(CharSequence error) {
                        listener.onFailure(error == null
                                ? "Android could not create the companion association."
                                : error.toString());
                    }
                };

        try {
            @SuppressWarnings("deprecation")
            Handler handler = new Handler(activity.getMainLooper());
            @SuppressWarnings("deprecation")
            CompanionDeviceManager.Callback legacyCallback = callback;
            manager.associate(request, legacyCallback, handler);
        } catch (RuntimeException e) {
            listener.onFailure("Could not start Android companion pairing.");
        }
    }

    static void removeAllAssociations(Context context) {
        if (!isSupported(context)) return;
        CompanionDeviceManager manager = manager(context);
        if (manager == null) return;

        @SuppressWarnings("deprecation")
        List<String> associations = manager.getAssociations();
        if (associations == null) return;
        for (String address : associations) {
            @SuppressWarnings("deprecation")
            String legacyAddress = address;
            manager.disassociate(legacyAddress);
        }
    }

    private static void launchChooser(
            Activity activity, IntentSender chooserLauncher, Listener listener) {
        try {
            activity.startIntentSenderForResult(
                    chooserLauncher, REQUEST_CODE, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException e) {
            listener.onFailure("Could not open Android's companion-device chooser.");
        }
    }

    private static CompanionDeviceManager manager(Context context) {
        return (CompanionDeviceManager) context.getSystemService(
                Context.COMPANION_DEVICE_SERVICE);
    }
}
