
package com.ar.slimsettings.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import com.ar.slimsettings.R;

import com.ar.slimsettings.performance.CPUSettings;
import com.ar.slimsettings.performance.Voltage;
import com.ar.slimsettings.performance.VoltageControlSettings;
import com.ar.slimsettings.util.CMDProcessor;

public class BootService extends Service {

    public static boolean servicesStarted = false;
    public static SharedPreferences preferences;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new BootWorker().execute();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    class BootWorker extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... args) {
            Context c = getApplicationContext();
            preferences = PreferenceManager.getDefaultSharedPreferences(c);
            final CMDProcessor cmd = new CMDProcessor();

            if (HeadphoneService.getUserHeadphoneAudioMode(c) != -1
                    || HeadphoneService.getUserBTAudioMode(c) != -1) {
                c.startService(new Intent(c, HeadphoneService.class));
            }

            if (FlipService.getUserFlipAudioMode(c) != -1
                    || FlipService.getUserCallSilent(c) != 0)
                c.startService(new Intent(c, FlipService.class));


            if (preferences.getBoolean("cpu_boot", false)) {
                final String max = preferences.getString(
                        "max_cpu", null);
                final String min = preferences.getString(
                        "min_cpu", null);
                final String gov = preferences.getString(
                        "gov", null);
                final String io = preferences.getString("io", null);
                if (max != null && min != null && gov != null) {
                    cmd.su.runWaitFor("busybox echo " + max +
                            " > " + CPUSettings.MAX_FREQ);
                    cmd.su.runWaitFor("busybox echo " + min +
                            " > " + CPUSettings.MIN_FREQ);
                    cmd.su.runWaitFor("busybox echo " + gov +
                            " > " + CPUSettings.GOVERNOR);
                    cmd.su.runWaitFor("busybox echo " + io +
                            " > " + CPUSettings.IO_SCHEDULER);
                    if (new File("/sys/devices/system/cpu/cpu1").exists()) {
                        cmd.su.runWaitFor("busybox echo " + max +
                                " > " + CPUSettings.MAX_FREQ
                                .replace("cpu0", "cpu1"));
                        cmd.su.runWaitFor("busybox echo " + min +
                                " > " + CPUSettings.MIN_FREQ
                                .replace("cpu0", "cpu1"));
                        cmd.su.runWaitFor("busybox echo " + gov +
                                " > " + CPUSettings.GOVERNOR
                                .replace("cpu0", "cpu1"));
                    }
                }
            }

            if (preferences.getBoolean(VoltageControlSettings
                    .KEY_APPLY_BOOT, false)) {
                final List<Voltage> volts = VoltageControlSettings
                    .getVolts(preferences);
                final StringBuilder sb = new StringBuilder();
                for (final Voltage volt : volts) {
                    sb.append(volt.getSavedMV() + " ");
                }
                cmd.su.runWaitFor("busybox echo " + sb.toString() +
                        " > " + VoltageControlSettings.MV_TABLE0);
                if (new File(VoltageControlSettings.MV_TABLE1).exists()) {
                    cmd.su.runWaitFor("busybox echo " +
                    sb.toString() + " > " +
                    VoltageControlSettings.MV_TABLE1);
                }
            }

            if (preferences.getBoolean("fast_charge_boot", false)) {
                try {
                    File fastcharge = new File("/sys/kernel/fastcharge",
                            "force_fast_charge");
                    FileWriter fwriter = new FileWriter(fastcharge);
                    BufferedWriter bwriter = new BufferedWriter(fwriter);
                    bwriter.write("1");
                    bwriter.close();
                    Intent i = new Intent();
                    i.setAction("com.ar.slimsettings.FCHARGE_CHANGED");
                    getApplicationContext().sendBroadcast(i);
                } catch (IOException e) {
                }

                // add notification to warn user they can only charge
                CharSequence contentTitle = getApplicationContext()
                        .getText(R.string.fast_charge_notification_title);
                CharSequence contentText = getApplicationContext()
                        .getText(R.string.fast_charge_notification_message);

                Notification n = new Notification.Builder(getApplicationContext())
                        .setAutoCancel(true)
                        .setContentTitle(contentTitle)
                        .setContentText(contentText)
                        .setSmallIcon(R.drawable.ic_slim_settings_general)
                        .setWhen(System.currentTimeMillis())
                        .getNotification();

                NotificationManager nm = (NotificationManager) getApplicationContext()
                        .getSystemService(Context.NOTIFICATION_SERVICE);
                nm.notify(1337, n);
            } else {
                try {
                    File fastcharge = new File("/sys/kernel/fastcharge",
                            "force_fast_charge");
                    FileWriter fwriter = new FileWriter(fastcharge);
                    BufferedWriter bwriter = new BufferedWriter(fwriter);
                    bwriter.write("0");
                    bwriter.close();
                    Intent i = new Intent();
                    i.setAction("com.ar.slimsettings.FCHARGE_CHANGED");
                    getApplicationContext().sendBroadcast(i);
                } catch (IOException e) {
                }
            }

            if (preferences.getBoolean("free_memory_boot", false)) {
                final String values = preferences.getString(
                        "free_memory", null);
                if (!values.equals(null)) {
                    cmd.su.runWaitFor("busybox echo " + values +
                            " > /sys/module/lowmemorykiller/parameters/minfree");
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            servicesStarted = true;
            stopSelf();
        }

    }


}
