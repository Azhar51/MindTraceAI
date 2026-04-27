package com.mindtrace.ai.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.mindtrace.ai.service.WorkScheduler;

/**
 * Boot receiver — re-schedules all WorkManager periodic workers after device reboot
 * or app update.
 *
 * <p>Delegates entirely to {@link WorkScheduler#scheduleAll(Context)} — the single
 * source of truth for the worker roster. This guarantees zero divergence between
 * boot-time and runtime scheduling configurations.</p>
 *
 * <p>Registered in AndroidManifest with RECEIVE_BOOT_COMPLETED permission.</p>
 *
 * @see WorkScheduler
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            Log.d(TAG, "Boot/update detected — re-scheduling all workers via WorkScheduler");
            WorkScheduler.scheduleAll(context);
        }
    }
}
