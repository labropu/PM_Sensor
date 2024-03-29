package com.redbear.chat;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import java.io.File;
import android.os.Environment;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationRequest;

import java.util.Calendar;
import java.text.SimpleDateFormat;
import android.location.Location;
import android.widget.Toast;

import com.google.android.gms.maps.UiSettings;
import java.io.FileWriter;
import java.io.IOException;

import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Marker;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.DataPointInterface;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.OnDataPointTapListener;
import com.jjoe64.graphview.series.Series;

import static java.lang.StrictMath.max;

import android.support.v4.app.FragmentActivity;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Chat extends FragmentActivity {

	private static GoogleMap mMap;
	private final static String TAG = Chat.class.getSimpleName();

	public static final String EXTRAS_DEVICE = "EXTRAS_DEVICE";
	private static TextView tv = null;
	private static TextView tv1 = null;
	private static TextView tv2 = null;
	private static TextView tv3 = null;
	private static TextView tv4 = null;
	private static EditText et = null;
	private static Button btn = null;
	private String mDeviceName;
	private String mDeviceAddress;
	private RBLService mBluetoothLeService;
	private Map<UUID, BluetoothGattCharacteristic> map = new HashMap<UUID, BluetoothGattCharacteristic>();

	private FusedLocationProviderClient mFusedLocationClient;
	private File textfile = new File(getPublicAlbumStorageDir("PM Sensor"), "PMdata.txt");
	private String stringDate;
	private String latitude = "";
	private String longitude = "";
	private static String str = "";
	private Long tsLong;
	private String ts = "";
	private static GraphView graph;
	private String[] pm, humtemp = new String[5];
	private List<Double> pm10 = new ArrayList<>();
	private List<Double> pm25 = new ArrayList<>();
	private List<Date> date = new ArrayList<>();
	private SimpleDateFormat sdf = new SimpleDateFormat(" dd/MM/yyyy HH:mm ");
	private int count = 0;
	LatLng newlocation = new LatLng(35,28);
	Marker[] marker = new Marker[5000];
	HashMap<LatLng, String> meMap = new HashMap<>();
	RequestQueue queue;
	JsonObjectRequest jsonObjectRequest;


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
			tv.setText("Bluetooth initialized\nSensor is " +mDeviceName);
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
				tv.setText("Lost connection with sensor, please press disconnect and start again.");
			} else if (RBLService.ACTION_GATT_SERVICES_DISCOVERED
					.equals(action)) {
				tv.append("\n");
				tv.append("Data are being collected\nPlease wait 1 minute for the next measurement...");
				getGattService(mBluetoothLeService.getSupportedGattService());
			} else if (RBLService.ACTION_DATA_AVAILABLE.equals(action)) {
				displayData(intent.getByteArrayExtra(RBLService.EXTRA_DATA));
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setTheme(R.style.AppTheme);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.second);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		ViewPager viewPager = findViewById(R.id.viewPager);
		viewPager.setOffscreenPageLimit(2);  //number of ViewPager pages that will be kept in storage while swiping
		ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager());
		viewPager.setAdapter(viewPagerAdapter);

	mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
		LocationRequest mLocationRequest = new LocationRequest();
		mLocationRequest.setInterval(60000);
		mLocationRequest.setFastestInterval(10000);
		mLocationRequest.setSmallestDisplacement(70);
		mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
		LocationCallback mLocationCallback = new LocationCallback(){
			@Override
			public void onLocationResult(LocationResult locationResult) {
				if (locationResult == null) {
					return;
				}
				for (Location location : locationResult.getLocations()) {
					// Update UI with location data
					double lat = location.getLatitude();
					double lng = location.getLongitude();
					latitude = String.valueOf(lat);
					longitude = String.valueOf(lng);
					newlocation = new LatLng(lat,lng);
				}
			};
		};
		// Register the listener with the Location Manager to receive location updates
		try {
			mFusedLocationClient.requestLocationUpdates(mLocationRequest,
					mLocationCallback,
					null /* Looper */);
		}
		catch (SecurityException e) {
			// lets the user know there is a problem with the gps
		}

		Intent intent = getIntent();

		mDeviceAddress = intent.getStringExtra(Device.EXTRA_DEVICE_ADDRESS);
		mDeviceName = intent.getStringExtra(Device.EXTRA_DEVICE_NAME);

		getActionBar().setTitle("Disconnect");
		getActionBar().setDisplayHomeAsUpEnabled(true);

		Intent gattServiceIntent = new Intent(this, RBLService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

		// Instantiate the RequestQueue.
		queue = Volley.newRequestQueue(this);
		String url = "https://api.hackair.eu/hackair_data?location=28.805640%2C%2010.816884%7C%2019.878043%2C%2040.517963%20";

		jsonObjectRequest = new JsonObjectRequest
				(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {

					@Override
					public void onResponse(JSONObject response) {
						try {
							JSONArray jArray = response.getJSONArray("measurements");
							for (int i = 0; i < jArray.length(); i++) {
								JSONObject jOBJ = jArray.getJSONObject(i);
								String json_date = jOBJ.getString("date_str");
								if (json_date.matches("[0-9]+")) {
									json_date = sdf.format(new Date(Long.parseLong(json_date)));
								}
								JSONObject locOBJ = jOBJ.getJSONObject("loc");
								JSONArray locArray = locOBJ.getJSONArray("coordinates");
								String json_lat = locArray.getString(0);
								String json_lng = locArray.getString(1);
								Double hlat = Double.parseDouble(json_lat);
								Double hlng = Double.parseDouble(json_lng);
								JSONObject valueOBJ = jOBJ.getJSONObject("pollutant_q");
								String json_pm = valueOBJ.getString("name");
								String json_pmvalue = valueOBJ.getString("value");
								if (json_pm.startsWith("PM10")) {
									hlat = hlat + 0.0001;
								}
								LatLng hackairlocation = new LatLng(hlat,hlng);
								mMap.addMarker(new MarkerOptions().position(hackairlocation).title(json_date).snippet(json_pm + ": " + json_pmvalue + " μg/m3").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

							}
						} catch (JSONException e) {
							Log.e("MYAPP", "unexpected JSON exception", e);
							// Do something to recover ... or kill the app.
						}
					}
				}, new Response.ErrorListener() {

					@Override
					public void onErrorResponse(VolleyError error) {
						// TODO: Handle error

					}
				});

		// Add the request to the RequestQueue.
		queue.add(jsonObjectRequest);

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


		if (byteArray != null) {
			queue.add(jsonObjectRequest);
			String data = new String(byteArray);
			tsLong = System.currentTimeMillis()/1000;
			ts = tsLong.toString();


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
					if (n>1) {
						tv.setText(n + " measurements since startup\n(1 per minute)");
					}
					else tv.setText("First measurement arrived:");
					n += 1;
					tv.append("\n");
					chooseQuality();
					if (meMap.containsKey(newlocation)) {
						String updloc = meMap.get(newlocation);
						Integer x = Integer.valueOf(updloc);
						marker[x].remove();
						marker[x] = mMap.addMarker(new MarkerOptions().position(newlocation).title(stringDate).snippet("PM2,5: " + pm25.get(count) + " μg/m3, PM10: " + pm10.get(count) + " μg/m3"));
					}
					else {
						meMap.put(newlocation, "" + count);
						marker[count] = mMap.addMarker(new MarkerOptions().position(newlocation).title(stringDate).snippet("PM2,5: " + pm25.get(count) + " μg/m3, PM10: " + pm10.get(count) + " μg/m3"));
					}
					mMap.moveCamera(CameraUpdateFactory.newLatLng(newlocation));
					count++;
					buildGraph();
				} else if (data.startsWith("b")) {
					String data1 = data.substring(1);
					fw.write(data1); //appends the string to the file
					fw.write(" ");
					fw.write(latitude);
					fw.write(",");
					fw.write(longitude);
					humtemp = data1.split(" ");
					tv3.setText(humtemp[0] + " %");
					tv4.setText(humtemp[1] +" \u00b0C");
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


	private void chooseQuality() {
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
		String blue = "<font color='blue'>VERY GOOD!</font>";
		String green = "<font color='green'>FAIR.</font>";
		String yellow = "<font color='yellow'>MODERATE.</font>";
		String orange = "<font color='orange'>POOR.</font>";
		String red = "<font color='red'>VERY POOR!</font>";
		switch (max(i10,i25)) {
			case 1:
				tv.append(Html.fromHtml("Air quality is " + blue));
				break;
			case 2:
				tv.append(Html.fromHtml("Air quality is " + green));
				break;
			case 3:
				tv.append(Html.fromHtml("Air quality is " + yellow));
				break;
			case 4:
				tv.append(Html.fromHtml("Air quality is " + orange));
				break;
			case 5:
				tv.append(Html.fromHtml("Air quality is " + red));
				break;
			default:
				tv.append("Couldn't get a measurement, please wait for a minute.");
		}
		tv1.setText(pm25.get(count) + " μg/m3");
		tv2.setText(pm10.get(count) + " μg/m3");
//		tv.append("\nCurrent value of PM2,5 and PM10 concentration is " +pm25.get(count) + " and " + pm10.get(count) +" μg/m3 respectively.");
	}

	private void buildGraph() {
        if (count>0) {
			graph.removeAllSeries();
			DataPoint[] point1 = new DataPoint[count];
			for (int i = 0; i < count; i++) {
				point1[i] = new DataPoint(i, pm25.get(i));
			}
			DataPoint[] point2 = new DataPoint[count];
			for (int i = 0; i < count; i++) {
				point2[i] = new DataPoint(i, pm10.get(i));
			}
			DataPoint[] point3 = new DataPoint[count];
			for (int i = 0; i < count; i++) {
				point3[i] = new DataPoint(i, 25);
			}
			LineGraphSeries<DataPoint> series = new LineGraphSeries<>(point1);
			LineGraphSeries<DataPoint> series1 = new LineGraphSeries<>(point2);
			LineGraphSeries<DataPoint> series2 = new LineGraphSeries<>(point3);
			series.setTitle("PM2,5");
			series.setColor(Color.GREEN);
			series.setDrawDataPoints(true);
			series.setDataPointsRadius(10);
			series.setThickness(8);
			series.setOnDataPointTapListener(new OnDataPointTapListener() {
				@Override
				public void onTap(Series series, DataPointInterface dataPoint) {
					Toast.makeText(Chat.this, "PM2,5 value is " + dataPoint.getY() + " μg/m3", Toast.LENGTH_SHORT).show();
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
					Toast.makeText(Chat.this, "PM10 value is " + dataPoint.getY() + " μg/m3", Toast.LENGTH_SHORT).show();
				}
			});
            series2.setTitle("limit");
			series2.setColor(Color.RED);
			series2.setDrawDataPoints(false);
			series2.setThickness(2);
			graph.addSeries(series);
			graph.addSeries(series1);
			graph.addSeries(series2);
			graph.getViewport().setMinY(0);
			graph.getViewport().setMinX(0);
			graph.getViewport().setMaxX(count - 1);
			graph.getViewport().setScalableY(true); // enables vertical scrolling
			graph.getViewport().setScalable(true);
			graph.getViewport().setYAxisBoundsManual(true);
			graph.getViewport().setXAxisBoundsManual(true);
			graph.getLegendRenderer().setVisible(true);
			// as we use dates as labels, the human rounding to nice readable numbers
			// is not necessary
			graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
			graph.getGridLabelRenderer().setNumVerticalLabels(8);
			graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);
		}
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

	public class ViewPagerAdapter extends FragmentPagerAdapter {

		public ViewPagerAdapter(FragmentManager manager) {
			super(manager);
		}

		@Override
		public Fragment getItem(int position) {
			switch (position) {
				case 0:
					return new FragmentA();
				case 1:
					return new FragmentC();
				case 2:
					return new FragmentB();
			}
			return new FragmentA();
		}

		@Override
		public int getCount() {
			return 3;  //number of Fragments inside the ViewPager
		}

		@Override
		public CharSequence getPageTitle(int position) {
			switch (position) {
				case 0:
					return "Info and Live Data";
				case 1:
					return "Map of measurements";
				case 2:
					return "Graph presentation";
			}

			return null;
		}

	}

	public static class FragmentA extends Fragment {

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
								 Bundle savedInstanceState) {
			ViewGroup rootView = (ViewGroup) inflater.inflate(
					R.layout.fragment1, container, false);

			tv = (TextView) rootView.findViewById(R.id.textView);
			tv1 = (TextView) rootView.findViewById(R.id.send1);
			tv2 = (TextView) rootView.findViewById(R.id.send2);
			tv3 = (TextView) rootView.findViewById(R.id.send3);
			tv4 = (TextView) rootView.findViewById(R.id.send4);
			tv.setMovementMethod(ScrollingMovementMethod.getInstance());
			et = (EditText) rootView.findViewById(R.id.editText);
			btn = (Button) rootView.findViewById(R.id.send);
			btn.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					str = et.getText().toString();
					et.setText("");
					if (!str.equals("")) {
						Toast.makeText(FragmentA.this.getActivity().getApplicationContext(), "Thank you for the information. Text \"" + str + "\" submitted to file.", Toast.LENGTH_LONG).show();
					}
				}
			});

			return rootView;
		}
	}

	public static class FragmentB extends Fragment {

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
								 Bundle savedInstanceState) {
			ViewGroup rootView = (ViewGroup) inflater.inflate(
					R.layout.fragment2, container, false);

			graph = (GraphView) rootView.findViewById(R.id.graph);

			return rootView;
		}
	}

	public static class FragmentC extends Fragment implements OnMapReadyCallback  {

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
								 Bundle savedInstanceState) {
			ViewGroup rootView = (ViewGroup) inflater.inflate(
					R.layout.fragment3, container, false);

			SupportMapFragment mapFragment = (SupportMapFragment) this.getChildFragmentManager()
					.findFragmentById(R.id.map);
			mapFragment.getMapAsync(this);

			return rootView;
		}

		@Override
		public void onMapReady(GoogleMap googleMap) {
			mMap = googleMap;

			// Add a marker in Sydney, Australia, and move the camera.
			LatLng patras = new LatLng(38.246639, 21.734573);
			mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(patras, 6));
			updateLocationUI();
			UiSettings set = mMap.getUiSettings();
			set.setZoomControlsEnabled(true);
		}

		private void updateLocationUI() {
			if (mMap == null) {
				return;
			}
			try {
				mMap.setMyLocationEnabled(true);
				mMap.getUiSettings().setMyLocationButtonEnabled(true);
			} catch (SecurityException e)  {
				Log.e("Exception: %s", e.getMessage());
			}
		}

	}

}
