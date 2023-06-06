package com.example.android_cs330_project4;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioRecord;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    /**
     * Variable for Permissions
     */
    private String[] permissions = new String[]{"android.Manifest.permission.RECORD_AUDIO"};
    private int PERMISSIONS_REQUEST = 1;

    /**
     * Variable for silence classification
     */
    private static final String YAMNET_MODEL = "lite-model_yamnet_classification_tflite_1.tflite";
    private static final float SOUND_THRESHOLD = (float) 0.5;
    private AudioRecord recorder = null;
    public int Silence_Count = 0;
    public int Classifications_Count = 0;

    /**
     * Variable for current state
     */
    private boolean is_monitoring = false;
    private boolean is_fall_detected = false;

    /**
     * Variable for Buttons
     */
    private TextView _display_onoff;
    private TextView _status_log;
    private Button _onoff;
    private Button _help;
    private ConstraintLayout _Background;

    /**
     * Variable for IMU Sensor
     */
    private SensorManager sensorManager;
    private Sensor sensor;
    private float sensor_threshold = (float) 40;

    /**
     * Variable for Timer
     */
    public CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /**
         * link button and variable
         */
        _display_onoff = (TextView) findViewById(R.id.display_onoff);
        _status_log = (TextView) findViewById(R.id.status);
        _onoff = (Button) findViewById(R.id.button_monitoring_onoff);
        _help = (Button) findViewById(R.id.button_help);
        _Background = (ConstraintLayout) findViewById(R.id.Backgroud);

        /**
         * Initialization for sensor, timer, and permission requesting
         */
        setUpSensorStuff();
        CountDownTimerInit();
        requestPermissions(permissions, PERMISSIONS_REQUEST);
    }

    /**
     * Function for initialize IMU sensor
     */
    private void setUpSensorStuff() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (sensor != null) {
            sensorManager.registerListener(this,
                    sensor,
                    sensorManager.SENSOR_DELAY_GAME,
                    sensorManager.SENSOR_DELAY_GAME);
        }
    }

    /**
     * Function for initialize CountDownTimerInit
     * This is for repeating sound classification later
     */
    public void CountDownTimerInit() {
        countDownTimer = new CountDownTimer(20000, 1000) {
            // Executed at every timer interrupt
            public void onTick(long millisUntilFinished) {
                startAudioClassification();
//                _display_onoff.setText(String.format("Count : %d %d", Silence_Count, Classifications_Count));
                String progress = "Detecting Sound .";
                for (int i = 0; i < Classifications_Count % 4; i++) progress += " .";
                _display_onoff.setText(progress);
            }
            // Executed at finish of countdown timer
            public void onFinish() {
                _display_onoff.setTextSize(50);
                _display_onoff.setText("Detection Finished.");
                stopAudioClassification();
            }
        };
    }

    /**
     * Function for initialize CountDownTimerInit
     * This is for repeating sound classification later
     */
    public void enable_audio_classifier() {
        _display_onoff.setTextSize(50);
        countDownTimer.start();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                TextView textView = (TextView) _help;
                Log.d("MytensorAudio_java", textView.getText().toString());
                if (Silence_Count > 15 && textView.getText().toString().equals("I'm OK")) {
                    help();
                } else {
                    solve();
                }
            }
        }, 20000);

    }

    /**
     * Do Audio Classification Here
     */
    private void startAudioClassification() {
        try {
            AudioClassifier classifier = AudioClassifier.createFromFile(this, YAMNET_MODEL);

            TensorAudio tensor = classifier.createInputTensorAudio();

            if (recorder == null) {
                recorder = classifier.createAudioRecord();
                recorder.startRecording();
            }

            tensor.load(recorder);
            List<Classifications> output = classifier.classify(tensor);
            List<Category> filterModelOutput = output.get(0).getCategories();

            Classifications_Count++;
            for (Category c : filterModelOutput) {
                if (c.getLabel().equals("Silence")) {
                    Log.d("MytensorAudio_java", " label : " + c.getLabel() + " score : " + c.getScore());
                    if (c.getScore() > SOUND_THRESHOLD) {
                        Silence_Count++;
                        break;
                    }
                }
            }

        } catch (IOException e) {}
    }

    /**
     * Stop recording audio Here
     */
    private void stopAudioClassification() {
        recorder.stop();
        recorder = null;
    }

    /**
     * Button Control for upper button
     */
    public void monitoring_onoff(View view) {
        /**
         * If monitoring is on, and try to off monitoring
         */
        if (is_monitoring) {
            is_monitoring = false;
            is_fall_detected = false;
            Silence_Count = 0;
            Classifications_Count = 0;
            _help.setEnabled(false);
            normal_ui_setup();

        }
        /**
         * monitoring is off, and try to enable monitoring
         */
        else {
            monitoring_ui_setup();
            is_monitoring = true;
            _help.setEnabled(true);
        }
    }

    /**
     * Button Control for lower button
     * It has different behavior depend on current state
     */
    public void emergency(View view) {
        TextView textView = (TextView) view;
        if (textView.getText().toString().equals("Help!"))
            help();
        else {
            countDownTimer.cancel();
            solve();
        }
    }

    /**
     * Function for handling emergency situation
     */
    public void help() {
        is_fall_detected = true;
        emergency_ui_setup();
        // Emergency Behavior Implementation
    }

    /**
     * Function for handling problem-solved situation
     */
    public void solve() {
        is_fall_detected = false;
        monitoring_ui_setup();
    }

    /**
     * Get the information of IMU gyroscope sensor
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && is_monitoring && !is_fall_detected) {
            float rad_x = event.values[0];
            float rad_y = event.values[1]; // but not use this one : fall detection
            float rad_z = event.values[2];

            // can be changed into just picture
            _status_log.setText(String.format("GYROSCOPE INFO\n\nangular v_x:\n%.3f rad/s\nangular v_y:\n%.3f rad/s\nangular v_z:\n%.3f rad/s\n", rad_x, rad_y, rad_z));
            _status_log.setTextSize(40);

            if (sensor_threshold < Math.pow(rad_x, 2) + Math.pow(rad_z, 2)) {
                is_fall_detected = true;
                fall_detected_ui_setup();
                enable_audio_classifier();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        return;
    }

    @Override
    protected void onDestroy() {
        sensorManager.unregisterListener(this);
        super.onDestroy();
    }

    /**
     * dispaly mode change to normal mode
     */
    private void normal_ui_setup() {
        _onoff.setText(R.string.button_label_monitoring_start);
        _onoff.setBackgroundColor(getColor(R.color.blue));
        _onoff.setTextColor(getColor((R.color.white)));

        _help.setBackgroundColor(getColor((R.color.blue)));
        _help.setTextColor(getColor((R.color.white)));
        _help.setText("Help!");

        _status_log.setText(R.string.status_initial_value);
        _status_log.setTextSize(100);
        _status_log.setTextColor(getColor((R.color.blue)));

        _display_onoff.setText(R.string.display_off);
        _display_onoff.setTextSize(48);
        _display_onoff.setTextColor(getColor((R.color.blue)));
        _display_onoff.setBackground(getDrawable(R.drawable.borderline_blue));

        _Background.setBackgroundColor(getColor((R.color.white)));
    }

    /**
     * dispaly mode change to monitoring mode
     */
    private void monitoring_ui_setup() {
        _onoff.setText(R.string.button_label_monitoring_stop);
        _onoff.setBackgroundColor(getColor(R.color.red));
        _onoff.setTextColor(getColor((R.color.white)));

        _help.setBackgroundColor(getColor((R.color.red)));
        _help.setTextColor(getColor((R.color.white)));
        _help.setText("Help!");

        _status_log.setTextColor(getColor((R.color.red)));

        _display_onoff.setText(R.string.display_on);
        _display_onoff.setTextSize(48);
        _display_onoff.setTextColor(getColor((R.color.red)));
        _display_onoff.setBackground(getDrawable(R.drawable.borderline_red));

        _Background.setBackgroundColor(getColor((R.color.white)));
    }

    /**
     * dispaly mode change to fall_detected mode
     */
    private void fall_detected_ui_setup() {
        _status_log.setTextColor(getColor((R.color.white)));

        _display_onoff.setText("Fall!");
        _display_onoff.setTextSize(100);
        _display_onoff.setTextColor(getColor((R.color.white)));
        _display_onoff.setBackground(getDrawable(R.drawable.borderline_white));

        _onoff.setBackgroundColor(getColor((R.color.white)));
        _onoff.setTextColor(getColor((R.color.red)));

        _help.setBackgroundColor(getColor((R.color.white)));
        _help.setTextColor(getColor((R.color.red)));
        _help.setText("I'm OK");

        _Background.setBackgroundColor(getColor((R.color.red)));

        _status_log.setTextSize(50);
        _status_log.setText(R.string.detect_fall);
    }

    /**
     * dispaly mode change to emergency mode
     */
    private void emergency_ui_setup() {
        _Background.setBackgroundColor(getColor((R.color.rred)));

        _status_log.setTextSize(55);
        _status_log.setText(R.string.EMERGENCY);
        _status_log.setTextColor(getColor((R.color.white)));

        _display_onoff.setText("Fall!");
        _display_onoff.setTextSize(100);
        _display_onoff.setTextColor(getColor((R.color.white)));
        _display_onoff.setBackground(getDrawable(R.drawable.borderline_white));

        _onoff.setTextColor(getColor((R.color.rred)));
        _onoff.setBackgroundColor(getColor((R.color.white)));

        _help.setTextColor(getColor((R.color.rred)));
        _help.setBackgroundColor(getColor((R.color.white)));
    }
}