/*
 *
 *  * Copyright (c) 2020. [Kevin Paul Montealegre Melo]
 *  *
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  * of this software and associated documentation files (the "Software"), to deal
 *  * in the Software without restriction, including without limitation the rights
 *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  * copies of the Software, and to permit persons to whom the Software is
 *  * furnished to do so, subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in
 *  * all copies or substantial portions of the Software.
 *
 */

package com.poligran.gopoli.retos.Services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.google.firebase.perf.metrics.AddTrace;
import com.poligran.gopoli.retos.Notifications.StepsCounterNotification;
import com.poligran.gopoli.retos.RetrofitClient.RetrofitClient;
import com.poligran.gopoli.retos.StepCounter.Interfaces.StepListener;
import com.poligran.gopoli.retos.StepCounter.StepDetector;
import com.poligran.gopoli.retos.UserSesion.SessionManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.IOException;

public class StepCounterService extends Service implements SensorEventListener, StepListener {

    private StepDetector simpleStepDetector;
    private int numSteps;
    private static boolean isRunning;
    private boolean sensorPedometer;
    private Runnable myRunnable;
    private static Handler handler;
    private SessionManager sessionManager;
    private int steps = 0;
    private int old_steps;
    private StepsCounterNotification stepsCounterNotification;



    @Override
    @AddTrace(name = "Servicio de Pasos")
    public int onStartCommand(Intent intent, int flags, int startId) {



        isRunning = true;
        sessionManager = new SessionManager(this);
        simpleStepDetector = new StepDetector();
        stepsCounterNotification = new StepsCounterNotification(this);
        startForeground();


        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor contadorPasos = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);


        try {
            numSteps = 0;
            if (contadorPasos != null) {
                sensorPedometer = true;
                sensorManager.registerListener(this, contadorPasos,  SensorManager.SENSOR_DELAY_FASTEST);
               Toast.makeText(this, "Se usara el sensor de Podometro", Toast.LENGTH_SHORT).show();

            } else {
                sensorPedometer = false;
                Toast.makeText(this, "Se usara el sensor de Acelerometro", Toast.LENGTH_SHORT).show();
                simpleStepDetector.registerListener(this);
                sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_FASTEST );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }



        handler = new Handler();
        int delay = 5000;

        handler.postDelayed(myRunnable = new Runnable() {
            public void run() {

                if (getApplicationContext() != null) {

                    if (getBatteryPercentage(StepCounterService.this) >= 15) {

                        if (internetIsConnected() && checkInternetConnection(StepCounterService.this)) {

                            if (steps != 0) {
                                if (steps != old_steps) {

                                    Log.e("Paul9834", "pasos antiguos" + old_steps);

                                    Log.e("Paul9834", "pasos registrados nuevos" + steps);

                                    int result = (steps - old_steps);


                                    //Guardamos un copia de la variables Steps//

                                    old_steps = steps;


                                    Log.e("Paul9834", "pasos con resta de diferencia" + result); //65//

                                    addSteps(result);

                                } else {
                                    Log.e("Paul9834", "es igual el numero");
                                }
                            }
                            else {
                                Log.e("Paul9834", "Los pasos estan en 0");
                            }
                        } else {

                            Log.e("No hay internet", "Internet");

                            Toast.makeText(StepCounterService.this, steps + "", Toast.LENGTH_SHORT).show();

                        }
                    } else {
                        Toast.makeText(StepCounterService.this, "Tu nivel de bateria es demasiado bajo para poder seguir contando tus pasos.", Toast.LENGTH_LONG).show();
                        stopSelf();
                    }
                }

                handler.postDelayed(this, delay);
            }
        }, delay);







        return START_NOT_STICKY;
    }

    private void startForeground() {
        startForeground(1, stepsCounterNotification.CrearNotificacionCompact(""));
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void step(long timeNs) {
        numSteps++;
        Log.e("PASOS EN ACELEROMETRO", Integer.toString(numSteps));
        Toast.makeText(this, "Podometro" + numSteps, Toast.LENGTH_SHORT).show();
        stepsCounterNotification.updateSteps(numSteps);
        steps = numSteps;
    }

    @Override
    @AddTrace(name = "Sensor de Pasos")
    public void onSensorChanged(SensorEvent event) {
        if (sensorPedometer) {
            if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
                numSteps++;
                stepsCounterNotification.updateSteps(numSteps);
                steps = numSteps;
            //    Toast.makeText(this, "Podometro" + numSteps, Toast.LENGTH_SHORT).show();
           //     Log.e("PASOS EN PODOMETRO", Integer.toString(numSteps));
            }
        }
        else {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                simpleStepDetector.updateAccel(event.timestamp, event.values[0], event.values[1], event.values[2]);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @AddTrace(name = "Petición HTTP - Pasos")
    private void addSteps(int numSteps) {
        int userId = Integer.parseInt(sessionManager.fetchIdUser());
        Call<Long> call = RetrofitClient.getInstance().addSteps().addSteps("Bearer " + sessionManager.fetchAuthToken(), userId, numSteps);
        call.enqueue(new Callback<Long>() {
            @Override
            public void onResponse(@NonNull Call<Long> call, @NonNull Response<Long> response) {

                if (response.code() == 401) {
                    sessionManager.saveStatusAuth(false);
                } /*else {
                   // Long steps = response.body();
                 *//*   Toast.makeText(getApplicationContext(), steps + " sus pasos en backend", Toast.LENGTH_SHORT).show();
                    //  Log.e("Pasos Registrados: ", steps + " pasos");*//*
                }*/
            }

            @Override
            public void onFailure(@NonNull Call<Long> call, @NonNull Throwable t) {
                Toast.makeText(getApplicationContext(), "Problema con el servidor." + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static boolean isRunning() {
        return isRunning;
    }

    public static int getBatteryPercentage(Context context) {
        BatteryManager bm = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }


    public boolean internetIsConnected() {
        Runtime runtime = Runtime.getRuntime();
        try {
            Process ipProcess = runtime.exec("/system/bin/ping -c 1 8.8.8.8");
            int exitValue = ipProcess.waitFor();
            return (exitValue == 0);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean checkInternetConnection(Context context) {
        if (context == null) return false;

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {

            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
                if (capabilities != null) {
                    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        return true;
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        return true;
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        return true;
                    }
                }
            } else {
                try {
                    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                    if (activeNetworkInfo != null && activeNetworkInfo.isConnected()) {
                        Log.i("update_statut", "Network is available : true");
                        return true;
                    }
                } catch (Exception e) {
                    Log.i("update_statut", "" + e.getMessage());
                }
            }
        }
        Log.i("update_statut", "Network is available : FALSE ");
        return false;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(myRunnable);
        handler.removeCallbacksAndMessages(null);
        isRunning = false;
        super.onDestroy();
    }
}
