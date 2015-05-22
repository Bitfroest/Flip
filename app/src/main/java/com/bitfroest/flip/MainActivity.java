package com.bitfroest.flip;

import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.games.*;


import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.DecimalFormat;

import static android.util.FloatMath.cos;
import static android.util.FloatMath.sin;
import static android.util.FloatMath.sqrt;
import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.asin;


public class MainActivity extends ActionBarActivity implements SensorEventListener,
        ConnectionCallbacks, OnConnectionFailedListener, Runnable {

    private GoogleApiClient mGoogleApiClient;
    static final int REQUEST_CODE_RECOVER_PLAY_SERVICES = 1001;

    // Request code to use when launching the resolution activity
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    // Unique tag for the error dialog fragment
    private static final String DIALOG_ERROR = "dialog_error";
    // Bool to track whether the app is already resolving an error
    private boolean mResolvingError = false;


    public final static String EXTRA_MESSAGE = "com.bitfroest.flip.MESSAGE";
    private static final int EPSILON = 0;
    DecimalFormat decF = new DecimalFormat("##.#");
    private SensorManager mSensorManager;
    private int evaluateSpeed = SensorManager.SENSOR_DELAY_GAME;

    //Initiate an Accelerometer Sensor
    private Sensor mSensorAccelerometer;
    private double[] gravity = {0, 0, 0};
    private double[] linear_acceleration = {0, 0, 0};

    //initiate a Gyroscope Sensor
    private Sensor mSensorGyroscope;
    // Create a constant to convert nanoseconds to seconds.
    private static final float NS2S = 1.0f / 1000000000.0f;
    private final float[] deltaRotationVector = new float[4];
    private float timestamp;

    //initiate a Rotation Sensor
    private Sensor mSensorRotation;

    //Revolution
    private float[] last = {0, 0, 0};
    private float[] rev = {0, 0, 0, 0};

    //UDP Connection
    int serverPort = 9876;
    DatagramSocket socket;
    InetAddress local;

    public boolean running = true;

    public float x,y;

    public boolean toggleButton = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Remove title bar
        //this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        EditText hostIP = (EditText) findViewById(R.id.hostIP);

        try {
            socket = new DatagramSocket();
            local = InetAddress.getByName(hostIP.getText().toString());
        }catch (Exception e) {
            e.printStackTrace();
        }


        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        //implements sensors
        mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorRotation = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        //set listening speed of sensors
        mSensorManager.registerListener(this, mSensorAccelerometer, evaluateSpeed);
        mSensorManager.registerListener(this, mSensorGyroscope, evaluateSpeed);
        mSensorManager.registerListener(this, mSensorRotation, evaluateSpeed);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Games.API)
                .addScope(Games.SCOPE_GAMES)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        running = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mSensorAccelerometer, evaluateSpeed);
        mSensorManager.registerListener(this, mSensorGyroscope, evaluateSpeed);
        mSensorManager.registerListener(this, mSensorRotation, evaluateSpeed);

        running = true;
        Thread t = new Thread(this);
        t.start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void uploadScore(View view) {
        if(mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            Games.Leaderboards.submitScore(mGoogleApiClient, getString(R.string.leaderboard_easy),
                    (long) (rev[3] * 10));
        }else{
            //mGoogleApiClient.connect();
        }
    }

    public void setHostIP(View view){
        EditText hostIP = (EditText) findViewById(R.id.hostIP);
        EditText hostPort = (EditText) findViewById(R.id.hostPort);

        try {
            socket = new DatagramSocket();
            serverPort = Integer.valueOf(hostPort.getText().toString());
            local = InetAddress.getByName(hostIP.getText().toString());
        }catch (Exception e) {
            e.printStackTrace();
        }
        if(!running){
            running = true;
            new Thread(this).start();
        }
    }


    public void openLeaderboard(View view){
        if(mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            startActivityForResult(Games.Leaderboards.getLeaderboardIntent(mGoogleApiClient,
                    getString(R.string.leaderboard_easy)), 1);
        }else{
            mGoogleApiClient.connect();
        }
        //Intent intent = new Intent(this, .class);
        //startActivity(intent);
    }

    public void getRevolutions(float x, float y, float z) {
        x += 180;
        y += 180;
        z += 180;

        if (abs(x - last[0]) >= 2 && abs(x - last[0]) <= 300) {
            rev[0] += abs(x - last[0]);
        }
        if (abs(y - last[1]) >= 2 && abs(y - last[1]) <= 300) {
            rev[1] += abs(y - last[1]);
        }
        if (abs(z - last[2]) >= 2 && abs(z - last[2]) <= 300) {
            rev[2] += abs(z - last[2]);
        }

        rev[3] = (rev[0] + rev[1] + rev[2]) / 360;

        TextView textViewRev = (TextView) findViewById(R.id.textViewRev);
        textViewRev.setText("Revolutions\nX: " + decF.format(rev[0] / 360) + "\nY: " + decF.format(rev[1] / 360) +
                "\nZ:" + decF.format(rev[2] / 360) + "\nSum: " + decF.format(rev[3]));

        last[0] = x;
        last[1] = y;
        last[2] = z;
    }


    @Override
    public void onSensorChanged(SensorEvent event) {

        //Check if SensorEvent Type is Rotation_Vector
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {

            //Factor Rad to Degree
            float R2D = (float) (180 / PI);

            //axisX is an angle of rotation the
            float axisX = (float) (asin(event.values[0]) * 2) * R2D;
            float axisY = (float) (asin(event.values[1]) * 2) * R2D;
            float axisZ = (float) (asin(event.values[2]) * 2) * R2D;
            getRevolutions(axisX, axisY, axisZ);
            x = (float) (asin(event.values[0]) * 2) * Math.signum((float) (asin(event.values[2]) * 2));
            y = (float) (asin(event.values[1]) * 2) * Math.signum((float) (asin(event.values[2]) * 2));
            TextView textViewRot = (TextView) findViewById(R.id.textViewRot);
            textViewRot.setText("Rotation\nX: " + x + "\nY: " + y + "\nZ: " + event.values[2]);

        }

        //Check if SensorEvent Type is Gyroscope
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            // This timestep's delta rotation to be multiplied by the current rotation
            // after computing it from the gyro sample data.
            if (timestamp != 0) {
                final float dT = (event.timestamp - timestamp) * NS2S;
                // Axis of the rotation sample, not normalized yet.
                float axisX = event.values[0];
                float axisY = event.values[1];
                float axisZ = event.values[2];

                // Calculate the angular speed of the sample
                float omegaMagnitude = sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);

                // Normalize the rotation vector if it's big enough to get the axis
                // (that is, EPSILON should represent your maximum allowable margin of error)
                if (omegaMagnitude > EPSILON) {
                    axisX /= omegaMagnitude;
                    axisY /= omegaMagnitude;
                    axisZ /= omegaMagnitude;
                }

                // Integrate around this axis with the angular speed by the timestep
                // in order to get a delta rotation from this sample over the timestep
                // We will convert this axis-angle representation of the delta rotation
                // into a quaternion before turning it into the rotation matrix.
                float thetaOverTwo = omegaMagnitude * dT / 2.0f;
                float sinThetaOverTwo = sin(thetaOverTwo);
                float cosThetaOverTwo = cos(thetaOverTwo);
                deltaRotationVector[0] = sinThetaOverTwo * axisX;
                deltaRotationVector[1] = sinThetaOverTwo * axisY;
                deltaRotationVector[2] = sinThetaOverTwo * axisZ;
                deltaRotationVector[3] = cosThetaOverTwo;

                //TextView textViewGyro = (TextView) findViewById(R.id.textViewGyro);
                //textViewGyro.setText("Gyro\nX: " + axisX + "\nY: " + axisY + "\nZ: " + axisZ);
            }
            timestamp = event.timestamp;
            float[] deltaRotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(deltaRotationMatrix, deltaRotationVector);
            // User code should concatenate the delta rotation we computed with the current rotation
            // in order to get the updated rotation.
            // rotationCurrent = rotationCurrent * deltaRotationMatrix;


        }

        //Check if SensorEvent Type is Accelerometer
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // In this example, alpha is calculated as t / (t + dT),
            // where t is the low-pass filter's time-constant and
            // dT is the event delivery rate.

            final float alpha = (float) 0.8;

            // Isolate the force of gravity with the low-pass filter.
            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

            // Remove the gravity contribution with the high-pass filter.
            linear_acceleration[0] = event.values[0] - gravity[0];
            linear_acceleration[1] = event.values[1] - gravity[1];
            linear_acceleration[2] = event.values[2] - gravity[2];

            //TextView textView = (TextView) findViewById(R.id.textView);
            //textView.setText("Accel\nX: " + linear_acceleration[0] + "\nY: " + linear_acceleration[1] + "\nZ: " + linear_acceleration[2]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (result.hasResolution()) {
            try {
                mResolvingError = true;
                result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                mGoogleApiClient.connect();
            }
        } else {
            // Show dialog using GooglePlayServicesUtil.getErrorDialog()
            showErrorDialog(result.getErrorCode());
            mResolvingError = true;
        }
    }

    void showErrorDialog(int code) {
        GooglePlayServicesUtil.getErrorDialog(code, this,
                REQUEST_CODE_RECOVER_PLAY_SERVICES).show();
    }

    public void stopUDP(View view){
        running = false;
    }

    public void startUDP(View view){
        running = true;
    }

    @Override
    public void run() {
        while (running){
            try {

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
                dos.writeFloat(x);
                if(toggleButton) {
                    dos.writeFloat(x);
                }else{
                    dos.writeFloat(-x);
                }
                dos.writeFloat(y);

                byte[] message = baos.toByteArray();
                DatagramPacket p = new DatagramPacket(message, baos.size(), local,
                        serverPort);
                System.out.println(baos.size());
                socket.send(p);
                Thread.sleep(10);
            } catch (Exception e) {
                e.printStackTrace();
            }

    }
    }

    public void setToggleButton(View view){
        if(toggleButton){
            toggleButton = false;
        }else{
            toggleButton = true;
        }
    }
}