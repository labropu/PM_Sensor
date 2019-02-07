package com.redbear.chat;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.location.FusedLocationProviderClient;
import java.io.File;
import android.os.Environment;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationRequest;

import java.util.Calendar;
import java.text.SimpleDateFormat;
import android.location.Location;
import android.widget.Toast;

import com.google.android.gms.tasks.OnSuccessListener;
import java.io.FileWriter;
import java.io.IOException;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.DataPointInterface;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.OnDataPointTapListener;
import com.jjoe64.graphview.series.Series;

import static java.lang.StrictMath.max;

public class Chat extends Activity {
	private final static String TAG = Chat.class.getSimpleName();

	public static final String EXTRAS_DEVICE = "EXTRAS_DEVICE";
	private TextView tv = null;
	private EditText et = null;
	private Button btn = null;
	private Button btn2 = null;
	private String mDeviceName;
	private String mDeviceAddress;
	private RBLService mBluetoothLeService;
	private Map<UUID, BluetoothGattCharacteristic> map = new HashMap<UUID, BluetoothGattCharacteristic>();

	private FusedLocationProviderClient mFusedLocationClient;
	private File textfile = new File(getPublicAlbumStorageDir("PM Sensor"), "PMdata.txt");
	private String stringDate;
	private String latitude = "";
	private String longitude = "";
	private String str = "";
	Long tsLong;
	String ts = "";
    boolean notvisible = false;
    GraphView graph;
    String[] pm = new String[2];
	String[] humtemp = new String[2];
	List<Double> pm25 = new ArrayList<>();
	List<Double> pm10 = new ArrayList<>();
	List<Date> date = new ArrayList<>();
	SimpleDateFormat sdf = new SimpleDateFormat(" dd/MM/yyyy HH:mm:ss ");
	int count = 0;


	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName,
				IBinder service) {
			mBluetoothLeService = ((RBLService.LocalBinder) service)
					.getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				finish();
			}
			// Automatically connects to the device upon successful start-up
			// initialization.
			tv.setText("Bluetooth initialized");
			mBluetoothLeService.connect(mDeviceAddress);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBluetoothLeService = null;
		}
	};

	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();

			if (RBLService.ACTION_GATT_DISCONNECTED.equals(action)) {
			} else if (RBLService.ACTION_GATT_SERVICES_DISCOVERED
					.equals(action)) {
				tv.append("\n");
				tv.append("Data are being collected");
				getGattService(mBluetoothLeService.getSupportedGattService());
			} else if (RBLService.ACTION_DATA_AVAILABLE.equals(action)) {
				displayData(intent.getByteArrayExtra(RBLService.EXTRA_DATA));
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.second);

		mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
		LocationRequest mLocationRequest = new LocationRequest();
		mLocationRequest.setInterval(30000);
		mLocationRequest.setFastestInterval(10000);
		mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
		LocationCallback mLocationCallback = new LocationCallback();
		// Register the listener with the Location Manager to receive location updates
		try {
			mFusedLocationClient.requestLocationUpdates(mLocationRequest,
					mLocationCallback,
					null /* Looper */);
		}
		catch (SecurityException e) {
			// lets the user know there is a problem with the gps
		}

		tv = (TextView) findViewById(R.id.textView);
		tv.setMovementMethod(ScrollingMovementMethod.getInstance());
		et = (EditText) findViewById(R.id.editText);
		btn = (Button) findViewById(R.id.send);
		btn.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				str = et.getText().toString();
				et.setText("");
				Toast.makeText(Chat.this, "Thank you for the information. Text \"" +str +"\" submitted to file.", Toast.LENGTH_LONG).show();

			}
		});

        graph = (GraphView) findViewById(R.id.graph);
        graph.setVisibility(View.VISIBLE);

		btn2 = (Button) findViewById(R.id.graphbut);
		btn2.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
			    if (notvisible) {
                    graph.setVisibility(View.VISIBLE);
                    notvisible = false;
                }
                else {
                    graph.setVisibility(View.INVISIBLE);
                    notvisible = true;
                }
            }
		});


		Intent intent = getIntent();

		mDeviceAddress = intent.getStringExtra(Device.EXTRA_DEVICE_ADDRESS);
		mDeviceName = intent.getStringExtra(Device.EXTRA_DEVICE_NAME);

		getActionBar().setTitle(mDeviceName);
		getActionBar().setDisplayHomeAsUpEnabled(true);

		Intent gattServiceIntent = new Intent(this, RBLService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

	}

	@Override
	protected void onResume() {
		super.onResume();

		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			Intent intent = new Intent(this, Main.class);
			this.startActivity(intent);
			mBluetoothLeService.disconnect();
			mBluetoothLeService.close();

			System.exit(0);
		}

		return super.onOptionsItemSelected(item);
	}

//	@Override
//	protected void onStop() {
//		super.onStop();
//
//		unregisterReceiver(mGattUpdateReceiver);
//	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		unregisterReceiver(mGattUpdateReceiver);
		mBluetoothLeService.disconnect();
		mBluetoothLeService.close();

		System.exit(0);
	}

	@Override
	public void onBackPressed() {
		moveTaskToBack(true);
	}

	private int n = 1;
	private void displayData(final byte[] byteArray) {

		try {

			mFusedLocationClient.getLastLocation()
					.addOnSuccessListener(this, new OnSuccessListener<Location>() {
						@Override
						public void onSuccess(Location location) {
							// Got last known location. In some rare situations this can be null.

		if (byteArray != null) {
			String data = new String(byteArray);
			tsLong = System.currentTimeMillis()/1000;
			ts = tsLong.toString();

			if (location != null) {
			    double lat = location.getLatitude();
			    double lng = location.getLongitude();
			    latitude = String.valueOf(lat);
			    longitude = String.valueOf(lng);
			}

			try {

				FileWriter fw = new FileWriter(textfile, true); //the true will append the new data
				if (!str.equals("")) {
					fw.write("\n");
					fw.write(str);
					str = "";
				}
				if (data.startsWith("a")) {
					writeDate();
					fw.write("\n");
					fw.write(ts);
					fw.write(stringDate);
					String data1 = data.substring(1);
					fw.write(data1); //appends the string to the file
					pm = data1.split(" ");
					pm25.add(Double.parseDouble(pm[0]));
					pm10.add(Double.parseDouble(pm[1]));
//					Toast.makeText(Chat.this, "Now PM2,5 is " +pm25.get(count) +" μg/m3 and PM10 is " +pm10.get(count) +" μg/m3", Toast.LENGTH_LONG).show();
					tv.setText(n + " measurements since startup.");
					n += 1;
					tv.append("\n");
					int i25 = 0;
					int i10 = 0;
					if (pm25.get(count)<10) {
						i25=1;
					}
					else if (pm25.get(count)<20) {
						i25=2;
					}
					else if (pm25.get(count)<25) {
						i25=3;
					}
					else if (pm25.get(count)<50) {
						i25=4;
					}
					else {
						i25=5;
					}
					if (pm10.get(count)<20) {
						i10=1;
					}
					else if (pm10.get(count)<35) {
						i10=2;
					}
					else if (pm10.get(count)<50) {
						i10=3;
					}
					else if (pm10.get(count)<100) {
						i10=4;
					}
					else {
						i10=5;
					}
					switch (max(i10,i25)) {
						case 1:
							tv.append("Air quality is VERY GOOD!");
							break;
						case 2:
							tv.append("Air quality is FAIR.");
							break;
						case 3:
							tv.append("Air quality is MODERATE.");
							break;
						case 4:
							tv.append("Air quality is POOR.");
							break;
						case 5:
							tv.append("Air quality is VERY POOR!");
							break;
						default:
							tv.append("Couldn't get a measurement, please wait for a minute.");
					}
					tv.append(" (" +pm25.get(count) + "/" + pm10.get(count) +")");
				} else if (data.startsWith("b")) {
				    buildgraph();
					String data1 = data.substring(1);
					fw.write(" ");
					fw.write(data1); //appends the string to the file
					fw.write(" ");
					fw.write(latitude);
					fw.write(",");
					fw.write(longitude);
					humtemp = data1.split(" ");
					tv.append("\n");
					tv.append("Humidity is " + humtemp[0] + " %");
					tv.append("\n");
					tv.append("Temperature is " + humtemp[1] +" \u00b0C");
				}
				fw.close();

			} catch (IOException ioe) {
				System.err.println("IOException: " + ioe.getMessage());
			}


			// find the amount we need to scroll. This works by
			// asking the TextView's internal layout for the position
			// of the final line and then subtracting the TextView's height
			final int scrollAmount = tv.getLayout().getLineTop(
					tv.getLineCount())
					- tv.getHeight();
			// if there is no need to scroll, scrollAmount will be <=0
			if (scrollAmount > 0)
				tv.scrollTo(0, scrollAmount);
			else
				tv.scrollTo(0, 0);
		}
	}
					});

		} catch (SecurityException e) {
			// lets the user know there is a problem with the gps
		}


	}

    private void buildgraph() {
        graph.removeAllSeries();
        count++;
		DataPoint[] point1 = new DataPoint[count];
		for (int i = 0; i < count; i++) {
			point1[i] = new DataPoint(i, pm25.get(i));
		}
		DataPoint[] point2 = new DataPoint[count];
		for (int i = 0; i < count; i++) {
			point2[i] = new DataPoint(i, pm10.get(i));
		}
		LineGraphSeries<DataPoint> series = new LineGraphSeries<>(point1);
		LineGraphSeries<DataPoint> series1 = new LineGraphSeries<>(point2);
        series.setTitle("PM2,5");
        series.setColor(Color.GREEN);
        series.setDrawDataPoints(true);
        series.setDataPointsRadius(10);
        series.setThickness(8);
		series.setOnDataPointTapListener(new OnDataPointTapListener() {
			@Override
			public void onTap(Series series, DataPointInterface dataPoint) {
				Toast.makeText(Chat.this, "PM2,5 value is "+dataPoint.getY() +" μg/m3", Toast.LENGTH_LONG).show();
			}
		});
        series1.setTitle("PM10");
        series1.setColor(Color.BLUE);
        series1.setDrawDataPoints(true);
        series1.setDataPointsRadius(10);
        series1.setThickness(8);
		series1.setOnDataPointTapListener(new OnDataPointTapListener() {
			@Override
			public void onTap(Series series, DataPointInterface dataPoint) {
				Toast.makeText(Chat.this, "PM10 value is "+dataPoint.getY() +" μg/m3", Toast.LENGTH_LONG).show();
			}
		});
        graph.addSeries(series);
        graph.addSeries(series1);
        graph.getViewport().setMinY(0);
        graph.getViewport().setMinX(0);
		graph.getViewport().setMaxX(count-1);
        graph.getViewport().setScalableY(true); // enables vertical scrolling
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getLegendRenderer().setVisible(true);
		// as we use dates as labels, the human rounding to nice readable numbers
		// is not necessary
		graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
		graph.getGridLabelRenderer().setNumVerticalLabels(5);
        graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.BOTTOM);
    }


    private void getGattService(BluetoothGattService gattService) {
		if (gattService == null)
			return;

		BluetoothGattCharacteristic characteristic = gattService
				.getCharacteristic(RBLService.UUID_BLE_SHIELD_TX);
		map.put(characteristic.getUuid(), characteristic);

		BluetoothGattCharacteristic characteristicRx = gattService
				.getCharacteristic(RBLService.UUID_BLE_SHIELD_RX);
		mBluetoothLeService.setCharacteristicNotification(characteristicRx,
				true);
		mBluetoothLeService.readCharacteristic(characteristicRx);
	}

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();

		intentFilter.addAction(RBLService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(RBLService.ACTION_GATT_DISCONNECTED);
		intentFilter.addAction(RBLService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(RBLService.ACTION_DATA_AVAILABLE);

		return intentFilter;
	}


	private void writeDate() {
		Calendar c = Calendar.getInstance();
		stringDate = sdf.format(c.getTime());
		date.add(c.getTime());
	}

	public File getPublicAlbumStorageDir(String albumName) {
		// Get the directory for the user's public pictures directory.
		File path = new File(Environment.getExternalStorageDirectory(), albumName);
		if (!path.mkdirs()) {
			Log.e("PM Sensor", "Directory existed or not created");
		}
		else if (!path.exists()) {
			Log.e("PM Sensor", "Directory not created, ERROR");
		}
		return path;
	}

}
