package zemris.fer.hr.inthingy;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Patterns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import com.gdubina.multiprocesspreferences.MultiprocessPreferences;
import com.guna.libmultispinner.MultiSelectionSpinner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import zemris.fer.hr.inthingy.custom.DataForSpinnerTask;
import zemris.fer.hr.inthingy.custom.EmptyTabFactory;
import zemris.fer.hr.inthingy.custom.MyUtils;
import zemris.fer.hr.inthingy.gps.GPSLocator;
import zemris.fer.hr.inthingy.messages.MessageReplyService;
import zemris.fer.hr.inthingy.sensors.DeviceSensors;

/**
 * Activity for displaying main screen. It provides user options to send new message or to see received messages.
 * When application is loaded, it needs to populate {@link com.guna.libmultispinner.MultiSelectionSpinner} with
 * available sensors on device.
 * It handles sending messages where user needs to choose sensor which data will be sent
 */
public class MainActivity extends AppCompatActivity implements MultiSelectionSpinner.OnMultipleItemsSelectedListener,
        View.OnClickListener {

    /** Tab host for sensor data. */
    private TabHost tabHost;
    /** TextView for displaying sensor data. */
    private TextView tvSensorData;
    /** List of sensors and its data. */
    private Map<String, String> sensorDataMap = new HashMap<>();
    ;
    /** Multi selection spinner for sensors. */
    private MultiSelectionSpinner spDeviceSensors;
    /** Edit text which contains address of destination. */
    private EditText etDestination;
    /** GPS service intent. */
    private Intent gpsService;
    /** Sensor service intent. */
    private Intent sensorService;
    /** Auto reply service intent. */
    private Intent autoReplyService;
    /** Unique device ID. */
    public String thingId;
    /** Default sensor data. */
    private static final String DEFAULT_SENSOR_DATA = "NULL\nNULL\nNULL";
    /** Empty tab name. */
    private static final String EMPTY_TAB_TAG = "EMPTY";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        thingId = Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        initializeSensorSpinner();
        initializeElements();
        gpsService = new Intent(this, GPSLocator.class);
        sensorService = new Intent(this, DeviceSensors.class);
        autoReplyService = new Intent(this, MessageReplyService.class);
        //run task to populate spinner with proper data
        (new DataForSpinnerTask(MainActivity.this, spDeviceSensors)).execute();
    }

    /**
     * Method for initializing spinner with list of sensor's that are available for current device.
     */
    private void initializeSensorSpinner() {
        //init spinner
        spDeviceSensors = (MultiSelectionSpinner) findViewById(R.id.spDeviceSensors);
        spDeviceSensors.setListener(MainActivity.this);
    }

    /**
     * Method for initializing global elements which are used in this class.
     */
    private void initializeElements() {
        //tab host
        initTabHost();
        //device id
        ((TextView) findViewById(R.id.tvThingID)).setText(thingId);
        //text view for sensor data
        tvSensorData = (TextView) findViewById(R.id.tvSensorData);
        //edit text for destination
        etDestination = (EditText) findViewById(R.id.etDestination);
        //buttons
        initButtons();
    }

    /**
     * Method for initialization of all buttons (setting onClickListener, etc)
     */
    private void initButtons() {
        int[] buttonIds = new int[]{R.id.bCheckSensors, R.id.bChooseDestination, R.id.bPreviewMessage,
                R.id.bSendMessage};
        for (int buttonId : buttonIds) {
            findViewById(buttonId).setOnClickListener(MainActivity.this);
        }
    }

    /**
     * Method for {#link tabHost} initialization.
     */
    private void initTabHost() {
        tabHost = (TabHost) findViewById(R.id.tabhost);
        tabHost.setup();
        tabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            @Override
            public void onTabChanged(String tabId) {
                if (!EMPTY_TAB_TAG.equals(tabId)) {
                    String text = "Sensor: " + tabId + "\n" + sensorDataMap.get(tabId);
                    tvSensorData.setText(text);
                }
            }
        });
        //add some tab
        addTabHostEmptyTab();
    }

    /**
     * Method for creating empty tab for tab host.
     */
    private void addTabHostEmptyTab() {
        TabHost.TabSpec spec = tabHost.newTabSpec(EMPTY_TAB_TAG);
        spec.setContent(new EmptyTabFactory(MainActivity.this));
        spec.setIndicator(EMPTY_TAB_TAG);
        tabHost.addTab(spec);
    }

    @Override
    public void onResume() {
        super.onResume();
        //check location permission
        startService(gpsService);
        startService(sensorService);
    }

    @Override
    public void onPause() {
        super.onPause();
        stopService(gpsService);
        stopService(sensorService);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //delete shared preferences; no need for apply()
        MultiprocessPreferences.getDefaultSharedPreferences(getApplicationContext()).edit().clear();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //service for automatic message reply
        if (item.getItemId() == R.id.menuAutoReply) {
            Drawable offIcon = ContextCompat.getDrawable(getApplicationContext(), R.drawable.icon_auto_reply_off).getCurrent();
            Drawable onIcon = ContextCompat.getDrawable(getApplicationContext(), R.drawable.icon_auto_reply_on);
            if (item.getIcon().getConstantState().equals(onIcon.getConstantState())) {
                item.setIcon(offIcon);
                Toast.makeText(getApplicationContext(), "AutoReply service off", Toast.LENGTH_SHORT).show();
                if (MyUtils.isServiceRunning(MessageReplyService.class, MainActivity.this)) {
                    stopService(autoReplyService);
                }
            } else {
                item.setIcon(onIcon);
                Toast.makeText(getApplicationContext(), "AutoReply service on", Toast.LENGTH_SHORT).show();
                if (!MyUtils.isServiceRunning(MessageReplyService.class, MainActivity.this)) {
                    startService(autoReplyService);
                }
            }
        }
        return super.onOptionsItemSelected(item);
    }

    //
    // HANDLING SPINNER CHECKED INDICES
    //
    @Override
    public void selectedIndices(List<Integer> indices) {
        //empty stuff
    }

    @Override
    public void selectedStrings(final List<String> strings) {
        //first clear all tabHost
        tabHost.clearAllTabs();
        //clear data from map
        sensorDataMap.clear();
        //clear text view for sensor data
        tvSensorData.setText(R.string.text_sensor_data_default);
        if (strings != null && strings.size() > 0) {
            for (int i = 0; i < strings.size(); ++i) {
                String name = strings.get(i);
                //populate map with data
                String value = MultiprocessPreferences.
                        getDefaultSharedPreferences(getApplicationContext()).getString(name, DEFAULT_SENSOR_DATA);
                sensorDataMap.put(name, value);
                //add new tabHost
                TabHost.TabSpec spec = tabHost.newTabSpec(name);
                spec.setContent(new EmptyTabFactory(MainActivity.this));
                spec.setIndicator(String.valueOf(i + 1));
                tabHost.addTab(spec);
            }
        } else {  //no selection
            addTabHostEmptyTab();
        }
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bCheckSensors:
                checkSensors();
                break;
            case R.id.bChooseDestination:
                chooseDestination();
                break;
            case R.id.bPreviewMessage:
                if (checkAllParameters()) {
                    previewMessage();
                }
                break;
            case R.id.bSendMessage:
                if (checkAllParameters()) {
                    String encryption = ((Spinner) findViewById(R.id.spEncryption)).getSelectedItem().toString();
                    String sendMode = ((Spinner) findViewById(R.id.spSendMode)).getSelectedItem().toString();
                    String destination = etDestination.getText().toString();
                    String source = MyUtils.getLocalIpAddress();
                    sendMessage(thingId, source, destination, encryption, sendMode, sensorDataMap);
                }
                break;
            default:
                //some error
                break;
        }
    }

    /**
     * Method for sending message with given parameters.
     * It checks if device is connected to the Internet or not.
     *
     * @param thingId
     *         id of device which is sending message
     * @param source
     *         source address
     * @param destination
     *         destination address
     * @param encryption
     *         encryption which will be used in message
     * @param sendMode
     *         adapter through which message will be send
     * @param dataMap
     *         map containing data.
     * @return true if message is sent, otherwise false
     */
    private boolean sendMessage(String thingId, String source, String destination,
                                String encryption, String sendMode, Map<String, String> dataMap) {
        if (!MyUtils.isNetworkAvailable(MainActivity.this)) {
            Toast.makeText(MainActivity.this, "You aren't connected to the Internet.\nAbort!", Toast.LENGTH_LONG).show();
            return false;
        }
        String message = MyUtils.createMessage(thingId, source, destination, encryption, dataMap);

        return false;
    }


    /**
     * Method for checking if user selected proper values and entered valid destination address.
     *
     * @return true if everything is ok, otherwise false.
     */
    private boolean checkAllParameters() {
        if (sensorDataMap.isEmpty()) {
            Toast.makeText(MainActivity.this, "Please choose sensor's which data ypu want to send!",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        String destination = etDestination.getText().toString();
        if ("".equals(destination)) {
            Toast.makeText(MainActivity.this, "Please enter or select destination!", Toast.LENGTH_SHORT).show();
            return false;
        } else if (!Patterns.IP_ADDRESS.matcher(destination).matches()) {
            Toast.makeText(MainActivity.this, "Invalid address format!", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /**
     * Method for creating dialog which will display some destination addresses.
     */
    private void chooseDestination() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle("Select destination:");
        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, android.R.layout.select_dialog_singlechoice);
        //some adresses
        arrayAdapter.add("192.168.1.1");

        dialogBuilder.setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        dialogBuilder.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                etDestination.setText(arrayAdapter.getItem(which));
                dialog.dismiss();
            }
        });
        dialogBuilder.show();
    }

    /**
     * Method for creating dialog which will display message which will be sent.
     */
    private void previewMessage() {
        String encryption = ((Spinner) findViewById(R.id.spEncryption)).getSelectedItem().toString();
        String destination = etDestination.getText().toString();
        String source = MyUtils.getLocalIpAddress();
        String message = MyUtils.createMessage(thingId, source, destination, encryption, sensorDataMap);
        AlertDialog.Builder builderSingle = new AlertDialog.Builder(this);
        builderSingle.setTitle("Message preview:");
        builderSingle.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builderSingle.setMessage(message);
        builderSingle.show();
    }

    /**
     * Method for checking sensors data changes and if services are running or not.
     * It is used for button bChecksensors.
     */
    private void checkSensors() {
        //if one of service is stopped, run it again
        if (!MyUtils.isServiceRunning(GPSLocator.class, MainActivity.this)) {
            startService(gpsService);
        }
        if (!MyUtils.isServiceRunning(DeviceSensors.class, MainActivity.this)) {
            startService(sensorService);
        }
        //get new values
        for (String name : spDeviceSensors.getSelectedStrings()) {
            String value = MultiprocessPreferences.
                    getDefaultSharedPreferences(getApplicationContext()).getString(name, DEFAULT_SENSOR_DATA);
            sensorDataMap.put(name, value);
        }
        if (spDeviceSensors.getSelectedStrings().size() > 0) {
            //update value
            String tabName = tabHost.getCurrentTabTag();
            String text = "Sensor: " + tabName + "\n" + sensorDataMap.get(tabName);
            tvSensorData.setText(text);
        }
    }

}
