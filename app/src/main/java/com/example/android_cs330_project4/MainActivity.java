package com.example.android_cs330_project4;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioRecord;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private String[] permissions = new String[]{"android.permission.RECORD_AUDIO"};
    private int PERMISSIONS_REQUEST = 1;
    private static final String YAMNET_MODEL = "yamnet.tflite";
    private static final float SOUND_THRESHOLD = (float) 0.3;
    private AudioClassifier mAudioClassifier;
    private AudioRecord mAudioRecord;
    private long classficationInterval = 500;
    private Handler mHandler;
    private boolean is_monitoring = false;
    private boolean fall_detected = false;
    private TextView _display_onoff;
    private TextView _status_log;
    private Button _onoff;
    private Button _help;
    private ConstraintLayout _Background;
    private SensorManager sensorManager;
    private Sensor sensor;
    private float sensor_threshold = (float) 20;
    public int Silence_Count = 0;
    public int Classifications_Count = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        _display_onoff = (TextView) findViewById(R.id.display_onoff);
        _status_log = (TextView) findViewById(R.id.status);
        _onoff = (Button) findViewById(R.id.button_monitoring_onoff);
        _help = (Button) findViewById(R.id.button_help);
        _Background = (ConstraintLayout) findViewById(R.id.Backgroud);

        setUpSensorStuff();

        HandlerThread handlerThread = new HandlerThread("backgroundThread");
        handlerThread.start();
//        mHandler = HandlerCompat.createAsync(handlerThread.getLooper());

//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.) != PackageManager.PERMISSION_GRANTED) {
//            Toast toast = Toast.makeText(this, "Enter Permission Request", Toast.LENGTH_LONG);
//            toast.show();
//            requestPermissions(permissions, 1);
//        }

        requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 1);

    }

    private void startAudioClassification(){

        Silence_Count = 0;
        Classifications_Count = 0;

        try {
            AudioClassifier classifier = AudioClassifier.createFromFile(this, YAMNET_MODEL);

            Toast toast = Toast.makeText(this, "Enter startAudioClassification", Toast.LENGTH_LONG);
            toast.show();

            TensorAudio tensor = classifier.createInputTensorAudio();
            AudioRecord recorder = classifier.createAudioRecord();

            recorder.startRecording();

            tensor.load(recorder);
            List<Classifications> output = classifier.classify(tensor);
            List<Category> filterModelOutput = output.get(0).getCategories();

            Classifications_Count++;
            for(Category c : filterModelOutput) {
                if (c.getLabel() == "Silence" && c.getScore() > SOUND_THRESHOLD){
                    Silence_Count++;
                    break;
                }
            }

//            Runnable run = new Runnable() {
//                @Override
//                public void run() {
//
//                    tensor.load(recorder);
//                    List<Classifications> output = classifier.classify(tensor);
//                    List<Category> filterModelOutput = output.get(0).getCategories();
//                    Classifications_Count++;
//                    for(Category c : filterModelOutput) {
//                        if (c.getLabel() == "Silence" && c.getScore() > SOUND_THRESHOLD){
//                            Silence_Count++;
//                            break;
//                        }
//                    }
//                    mHandler.postDelayed(this,classficationInterval);
//                }
//            };
//
//            mHandler.post(run);
            mAudioRecord = recorder;
            mAudioRecord.stop();

            toast = Toast.makeText(this, "End startAudioClassification", Toast.LENGTH_LONG);
            toast.show();
        }
        catch (IOException e){
            Silence_Count = -1;
            e.printStackTrace();
        }
    }

    private void stopAudioClassfication(){
        Toast toast = Toast.makeText(this, "Enter stopAudioClassfication", Toast.LENGTH_LONG);
        toast.show();
        mAudioRecord.stop();
        mAudioRecord = null;
    }

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

    public void monitoring_onoff(View view) {
        if (_onoff != null) {
            if (is_monitoring) {
                is_monitoring = false;
                fall_detected = false;
                normal_ui_setup();

            }
            else {
                monitoring_ui_setup();
                is_monitoring = true;
            }
        }
    }

    public void emergency(View view) {
        TextView textView = (TextView) view;
        if (textView.getText().toString().equals("Help!")) {
            help();
        }
        else {
            solve();
        }
    }

    public void enable_audio_classifier() {
        Toast toast = Toast.makeText(this, "Enable Classifier", Toast.LENGTH_LONG);
        toast.show();

        startAudioClassification();

        _display_onoff.setTextSize(50);
        _display_onoff.setText(String.format("Count : %d %d",Silence_Count, Classifications_Count));

        new Handler().postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                if (Silence_Count == 1) {
                    help();
                }
                else
                    solve();
            }
        }, 6000);


    }

    public void help() {
        Toast toast = Toast.makeText(this, "help function entered", Toast.LENGTH_LONG);
        toast.show();
    }

    public void solve() {
        Toast toast = Toast.makeText(this, "It's OK", Toast.LENGTH_LONG);
        toast.show();

        fall_detected = false;
        monitoring_ui_setup();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && is_monitoring && !fall_detected) {
            float rad_x = event.values[0];
            float rad_y = event.values[1];
            float rad_z = event.values[2];

            _status_log.setText(String.format("angular v_x: %f\nangular v_y: %f\nangular v_z: %f\n", rad_x, rad_y, rad_z));
            _status_log.setTextSize(40);

            if (sensor_threshold < Math.pow(rad_x, 2) + Math.pow(rad_z, 2)) {
                fall_detected = true;
                emergency_ui_setup();
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

    private void emergency_ui_setup() {
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
}