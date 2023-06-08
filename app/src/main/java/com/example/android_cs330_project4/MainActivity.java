package com.example.android_cs330_project4;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioRecord;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    public String TAG = "MyApp_dont_fall";

    /**
     * Variable for Permissions
     */
    public String[] permissions = new String[] {Manifest.permission.RECORD_AUDIO, Manifest.permission.SEND_SMS};

    /**
     * Variable for silence classification
     */
    private static final String YAMNET_MODEL = "lite-model_yamnet_classification_tflite_1.tflite";
    private static final float SOUND_THRESHOLD = (float) 0.5;
    private AudioRecord recorder = null;
    private final long REFRESH_INTERVAL_MS = 1000L;
    private final long NUMBER_OF_ITERATION = 10;
    private final long ENTIRE_TIME = REFRESH_INTERVAL_MS * NUMBER_OF_ITERATION;

    /**
     * Variable for count classification
     */
    public int Silence_Count = 0;
    public int Classifications_Count = 0;
    public int Speech_Count = 0;

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
    private Button _info;
    private Button _help;
    private ConstraintLayout _Background;

    /**
     * Variable for IMU Sensor
     */
    private SensorManager sensorManager;

    /**
     * Variable for Timer
     */
    public CountDownTimer countDownTimer;

    /**
     * Variable for Sending SMS
     */
    String phoneNo = "01012345678";
    String message = "EMERGENCY!";

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // link button and variable
        _display_onoff = findViewById(R.id.display_onoff);
        _status_log = findViewById(R.id.status);
        _onoff = findViewById(R.id.button_monitoring_onoff);
        _info = findViewById(R.id.button_info);
        _help = findViewById(R.id.button_help);
        _help.setEnabled(false);
        _help.setTextColor(getColor((R.color.white)));
        _Background = findViewById(R.id.Background);

        // Initialization for sensor, timer, and permission requesting
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    == PackageManager.PERMISSION_DENIED) {
                requestPermissions (permissions, 1);
            }
        }
        setUpSensorStuff();
        CountDownTimerInit();
    }

    /**
     * Function for initialize IMU sensor
     */
    private void setUpSensorStuff() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (sensor != null) {
            sensorManager.registerListener(this,
                    sensor,
                    SensorManager.SENSOR_DELAY_GAME,
                    SensorManager.SENSOR_DELAY_GAME);
        }
    }

    /**
     * Function for initialize CountDownTimerInit
     * This is for repeating sound classification later
     */
    public void CountDownTimerInit() {
        countDownTimer = new CountDownTimer(ENTIRE_TIME, REFRESH_INTERVAL_MS) {
            // Executed at every timer interrupt
            public void onTick(long millisUntilFinished) {
                startAudioClassification();
                StringBuilder progress = new StringBuilder("Detecting Sound .");
                for (int i = 0; i < Classifications_Count % 4; i++) progress.append(" .");
                _display_onoff.setText(progress.toString());
            }
            // Executed at finish of countdown timer
            public void onFinish() {
                // show Classification result using toast
                @SuppressLint("DefaultLocale")
                String result = String.format(
                        "# Silence number = %d\n" +
                        "# Speech number = %d",
                        Silence_Count, Speech_Count);
                Toast.makeText (getApplicationContext(), result, Toast.LENGTH_LONG).show();

                Log.d(TAG," Detection Finished.");
                stopAudioClassification();

                if ((Silence_Count - 2 * Speech_Count) > NUMBER_OF_ITERATION * 0.6 &&  _help.getText().toString().equals("I'm OK")) {help();}
                else {solve();}
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
    }

    /**
     * Do Audio Classification Here
     */
    private void startAudioClassification() {
        try {
            AudioClassifier classifier = AudioClassifier.createFromFile(this, YAMNET_MODEL);

            TensorAudio tensor = classifier.createInputTensorAudio();

            if (recorder == null) {
                Log.d(TAG," Detection Start.");
                recorder = classifier.createAudioRecord();
                recorder.startRecording();
            }

            tensor.load(recorder);
            List<Classifications> output = classifier.classify(tensor);
            List<Category> filterModelOutput = output.get(0).getCategories();

            Classifications_Count++;
            float silence_score = -1;
            float speech_score = -1;
            for (Category c : filterModelOutput) {
                if (c.getLabel().equals("Silence")) {
                    Log.d(TAG, " label : " + c.getLabel() + " | score : " + c.getScore() + " (Iteration " + Classifications_Count + ")");
                    silence_score = c.getScore();
                    if (silence_score > SOUND_THRESHOLD && speech_score != -1 && silence_score > speech_score) {
                        Silence_Count++;
                        break;
                    }
                    else if (speech_score > SOUND_THRESHOLD && speech_score > silence_score) {
                        Speech_Count++;
                        break;
                    }
                }
                else if (c.getLabel().equals("Speech")) {
                    Log.d(TAG, " label : " + c.getLabel() + " | score : " + c.getScore() + " (Iteration " + Classifications_Count + ")");
                    speech_score = c.getScore();
                    if (speech_score > SOUND_THRESHOLD && silence_score != -1 && speech_score > silence_score) {
                        Speech_Count++;
                        break;
                    }
                    else if (silence_score > SOUND_THRESHOLD && silence_score > speech_score ) {
                        Silence_Count++;
                        break;
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Stop recording audio Here
     */
    private void stopAudioClassification() {
        recorder.stop();
        recorder = null;
    }

    /**
     * Button Control for _onoff button
     */
    public void monitoring_onoff(View view) {
        // If monitoring is on, and try to off monitoring
        if (is_monitoring) {
            is_monitoring = false;
            is_fall_detected = false;
            Silence_Count = 0;
            Speech_Count = 0;
            Classifications_Count = 0;
            normal_ui_setup();

        }
        // monitoring is off, and try to enable monitoring
        else {
            monitoring_ui_setup();
            is_monitoring = true;
        }
    }

    /**
     * Button Control for _help button
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
     * Button Control for information button
     * It has different behavior depend on current state
     */
    public void register_information(View view) {

        AlertDialog.Builder popup1 = new AlertDialog.Builder(this);

        popup1.setIcon(R.mipmap.ic_launcher);
        popup1.setTitle("Input phone number");
        popup1.setMessage("Input phone number without \"-\"\nWrong format number will not saved");

        AlertDialog.Builder popup2 = new AlertDialog.Builder(this);
        popup2.setIcon(R.mipmap.ic_launcher);
        popup2.setTitle("Input message.");
        popup2.setMessage("Input message to send.\nChanges will not save if not press \"Save\"");

        final EditText phone_number_blank = new EditText(this);
        phone_number_blank.setHint(phoneNo);
        popup1.setView(phone_number_blank);

        final EditText message_number_blank = new EditText(this);
        message_number_blank.setHint(message);
        popup2.setView(message_number_blank);

        popup1.setPositiveButton("cancel", (dialog, which) -> dialog.dismiss());

        popup1.setNegativeButton("Next", (dialog, which) -> popup2.show());

        popup2.setPositiveButton("cancel", (dialog, which) -> dialog.dismiss());

        popup2.setNegativeButton("Save", (dialog, which) -> {
            message = message_number_blank.getText().toString();
            String tmp = phone_number_blank.getText().toString();
            if (Pattern.matches ("^[0-9]*$", tmp) && (tmp.length() == 3 || tmp.length() == 11))
                phoneNo = tmp;
            dialog.dismiss();
        });

        popup1.show();
    }

    /**
     * Function for handling emergency situation
     */
    public void help() {
        is_fall_detected = true;
        emergency_ui_setup();

        // Emergency Behavior Implementation
        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage(phoneNo, null, message, null, null);

        Toast.makeText(this,"sending message complete", Toast.LENGTH_LONG).show();
    }

    /**
     * Function for handling problem-solved situation
     */
    public void solve() {
        is_fall_detected = false;
        monitoring_ui_setup();

        Classifications_Count = 0;
        Silence_Count = 0;
        Speech_Count = 0;
    }

    /**
     * Get the information of IMU gyroscope sensor
     */
    @SuppressLint("DefaultLocale")
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && is_monitoring && !is_fall_detected) {
            float rad_x = event.values[0];
            float rad_y = event.values[1]; // but not use this one : fall detection
            float rad_z = event.values[2];

            // can be changed into just picture
            _status_log.setText(String.format(
                    "GYROSCOPE INFO\n\n" +
                    "angv_x: %.2f rad/s\n" +
                    "angv_y: %.2f rad/s\n" +
                    "angv_z: %.2f rad/s\n", rad_x, rad_y, rad_z));
            _status_log.setTextSize(40);

            float SENSOR_THRESHOLD = 35;
            if (SENSOR_THRESHOLD < Math.pow(rad_x, 2) + Math.pow(rad_z, 2)) {
                is_fall_detected = true;
                fall_detected_ui_setup();
                enable_audio_classifier();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onDestroy() {
        sensorManager.unregisterListener(this);
        super.onDestroy();
    }

    /**
     * display mode change to normal mode
     */
    private void normal_ui_setup() {
        _onoff.setText(R.string.button_label_monitoring_start);
        _onoff.setBackgroundColor(getColor(R.color.blue));
        _onoff.setTextColor(getColor((R.color.white)));

        _info.setBackgroundColor(getColor(R.color.blue));
        _info.setTextColor(getColor((R.color.white)));

        _help.setBackgroundColor(getColor((R.color.blue)));
        _help.setTextColor(getColor((R.color.white)));
        _help.setText(R.string.button_label_help);
        _help.setEnabled(false);

        _status_log.setText(R.string.status_initial_value);
        _status_log.setTextSize(100);
        _status_log.setTextColor(getColor((R.color.blue)));

        _display_onoff.setText(R.string.display_off);
        _display_onoff.setTextSize(48);
        _display_onoff.setTextColor(getColor((R.color.blue)));
        _display_onoff.setBackground(AppCompatResources.getDrawable(this, R.drawable.borderline_blue));

        _Background.setBackgroundColor(getColor((R.color.white)));
    }

    /**
     * display mode change to monitoring mode
     */
    private void monitoring_ui_setup() {
        _onoff.setText(R.string.button_label_monitoring_stop);
        _onoff.setBackgroundColor(getColor(R.color.red));
        _onoff.setTextColor(getColor((R.color.white)));

        _info.setBackgroundColor(getColor(R.color.red));
        _info.setTextColor(getColor((R.color.white)));
        _info.setEnabled(true);

        _help.setBackgroundColor(getColor((R.color.red)));
        _help.setTextColor(getColor((R.color.white)));
        _help.setText(R.string.button_label_help);
        _help.setEnabled(true);

        _status_log.setTextColor(getColor((R.color.red)));

        _display_onoff.setText(R.string.display_on);
        _display_onoff.setTextSize(48);
        _display_onoff.setTextColor(getColor((R.color.red)));
        _display_onoff.setBackground(AppCompatResources.getDrawable(this, R.drawable.borderline_red));

        _Background.setBackgroundColor(getColor((R.color.white)));
    }

    /**
     * display mode change to fall_detected mode
     */
    @SuppressLint("SetTextI18n")
    private void fall_detected_ui_setup() {
        _status_log.setTextColor(getColor((R.color.white)));

        _display_onoff.setText(R.string.label_Fall);
        _display_onoff.setTextSize(100);
        _display_onoff.setTextColor(getColor((R.color.white)));
        _display_onoff.setBackground(AppCompatResources.getDrawable(this, R.drawable.borderline_white));

        _onoff.setBackgroundColor(getColor((R.color.white)));
        _onoff.setTextColor(getColor((R.color.red)));

        _info.setBackgroundColor(getColor((R.color.white)));
        _info.setTextColor(getColor((R.color.red)));
        _info.setEnabled(true);

        _help.setBackgroundColor(getColor((R.color.white)));
        _help.setTextColor(getColor((R.color.red)));
        _help.setText(R.string.label_OK);

        _Background.setBackgroundColor(getColor((R.color.red)));

        _status_log.setTextSize(50);
        _status_log.setText("Are You Okay?\n\n\"Silence\"\nwill call \n" + phoneNo);
    }

    /**
     * display mode change to emergency mode
     */
    @SuppressLint("SetTextI18n")
    private void emergency_ui_setup() {
        _Background.setBackgroundColor(getColor((R.color.rred)));

        _status_log.setTextSize(50);
        _status_log.setText("EMERGENCY!\n\nSEND\nMESSAGE\nTO\n" + phoneNo);
        _status_log.setTextColor(getColor((R.color.white)));

        _display_onoff.setText(R.string.label_Fall);
        _display_onoff.setTextSize(100);
        _display_onoff.setTextColor(getColor((R.color.white)));
        _display_onoff.setBackground(AppCompatResources.getDrawable(this, R.drawable.borderline_white));

        _onoff.setTextColor(getColor((R.color.rred)));
        _onoff.setBackgroundColor(getColor((R.color.white)));

        _info.setTextColor(getColor((R.color.rred)));
        _info.setBackgroundColor(getColor((R.color.white)));
        _info.setEnabled(false);

        _help.setTextColor(getColor((R.color.rred)));
        _help.setBackgroundColor(getColor((R.color.white)));
    }
}