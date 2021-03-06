package ru.dizraelapps.msgtodscts.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SearchRecentSuggestionsProvider;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PersistableBundle;
import android.provider.SearchRecentSuggestions;
import android.util.ArraySet;
import android.util.Log;
import android.view.MenuItem;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.squareup.picasso.Picasso;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import ru.dizraelapps.msgtodscts.ListAdapter;
import ru.dizraelapps.msgtodscts.R;
import ru.dizraelapps.msgtodscts.broadcast.BatteryBroadcastReceiver;
import ru.dizraelapps.msgtodscts.broadcast.NetworkBroadcastReceiver;
import ru.dizraelapps.msgtodscts.database.App;
import ru.dizraelapps.msgtodscts.database.RecentSearchQueryProvider;
import ru.dizraelapps.msgtodscts.database.SearchHistoryViewModel;
import ru.dizraelapps.msgtodscts.database.SearchHistoryDao;
import ru.dizraelapps.msgtodscts.database.SearchText;
import ru.dizraelapps.msgtodscts.interfaces.IOpenWeather;
import ru.dizraelapps.msgtodscts.weather.Daily;
import ru.dizraelapps.msgtodscts.weather.ResponseWeather;
import ru.dizraelapps.msgtodscts.weather.Weather;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener,
        SensorEventListener{

    //Strings
    private final static String NOT_SUPPORTED_MSG = "Sorry, sensor not available for this device";
    private final static String ACTION_SEND_MESSAGE = "ru.dizraelapps.msgtodscts.message";
    private final static String NAME_MSG = "MSG";
    private final static String units = "metric";
    private final static String exclude = "minutely,hourly";
    private final static String apiKey = "d4a256e168a940fb210b109445d77de4";
    private CharSequence searchableCity;

    //View
    private RecyclerView recyclerView;
    private ImageView currentWeatherIcon;
    private TextView ambientTempLabel;
    private TextView ambientHumidLabel;
    private Button currentWeatherButton;
    private MapView mapView;
    private GoogleMap gMap;
    private TextView currentTempTextView;

    public static final int FLAG_RECEIVER_INCLUDE_BACKGROUND = 0x01000000;
    private static final int PERMISSION_REQUEST_CODE = 10;
    private Activity activity;
    private NetworkBroadcastReceiver networkBroadcastReceiver;
    private BatteryBroadcastReceiver batteryBroadcastReceiver;
    private Handler mHandler;
    private ListAdapter adapter;
    private SensorManager mSensorManager;
    private SensorEventListener mSensorListener;
    private Sensor mTemperature;
    private Sensor mHumidity;
    private IOpenWeather openWeather;
    private ArrayList<String> latitudeLongitude = new ArrayList<>();
    private SearchHistoryViewModel searchHistoryViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = getIntent();
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
                    RecentSearchQueryProvider.AUTHORITY, RecentSearchQueryProvider.MODE);
            suggestions.saveRecentQuery(query, null);
        }
        checkPermissions();

        Toolbar toolbar = initToolbar();
        initDrawer(toolbar);
        initSensors();
        initRetrofit();
        initHandler();
        initBroadcastReceivers();
        initGetToken();
        initNotificationChannel();
        initView();
        initDatabase();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
                    RecentSearchQueryProvider.AUTHORITY, RecentSearchQueryProvider.MODE);
            suggestions.saveRecentQuery(query, null);
        }
    }

    private void initDatabase() {
        SearchHistoryDao searchHistoryDao = App
                .getInstance()
                .getHistoryDao();
        searchHistoryViewModel = new SearchHistoryViewModel(searchHistoryDao);
    }

    private void checkPermissions() {
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CALL_PHONE)) {
            // Запрашиваем эти два Permission’а у пользователя
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    PERMISSION_REQUEST_CODE);
        }
    }

    //inits

    private void initView() {
        currentTempTextView = findViewById(R.id.main_tv_current_temp);
        mapView = findViewById(R.id.map);
        currentWeatherButton = findViewById(R.id.fragment_current_weather_button);
        initListeners();
    }

    private void initListeners() {
        Context context = this.getBaseContext();
        currentWeatherButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.R)
            @SuppressLint("ShowToast")
            @Override
            public void onClick(View view) {

                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        || ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                {
                    LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                    Criteria criteria = new Criteria();
                    criteria.setAccuracy(Criteria.ACCURACY_COARSE);
                    String provider = manager.getBestProvider(criteria, true);
                    if (provider != null) {

                        manager.requestSingleUpdate(provider, new LocationListener() {
                            @Override
                            public void onLocationChanged(@NonNull Location location) {
                                String longitude = String.valueOf(location.getLongitude());
                                String latitude = String.valueOf(location.getLatitude());
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        requestRetrofit(latitude, longitude, exclude, apiKey, units);
                                    }
                                }).start();
//                                mHandler.post(() -> mapView.getMapAsync((GoogleMap) -> {
//                                    GoogleMap googleMap = gMap;
//                                    LatLng position = new LatLng(Double.parseDouble(latitude), Double.parseDouble(longitude));
//                                    CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(position,15);
//                                    googleMap.animateCamera(cameraUpdate);
//                                    googleMap.addMarker(new MarkerOptions().position(position).title("Current position"));
//
//                                }));
                            }
                        }, Looper.getMainLooper());

                    } else {
                        Toast.makeText(getParent(), "Включите GPS", Toast.LENGTH_SHORT);
                    }
                }
            }
        });
    }

    private void initBroadcastReceivers() {
        batteryBroadcastReceiver = new BatteryBroadcastReceiver();
        networkBroadcastReceiver = new NetworkBroadcastReceiver();

        registerReceiver(batteryBroadcastReceiver, new IntentFilter(Intent.ACTION_BATTERY_LOW));
        registerReceiver(networkBroadcastReceiver,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

    }

    private void initGetToken() {
        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                                           @Override
                                           public void onComplete(@NonNull Task<InstanceIdResult> task) {
                                                if (!task.isSuccessful()){
                                                    Log.w("PushMessage", "getIsntanceId failed",
                                                            task.getException());
                                                    return;
                                                }

                                                String token = task.getResult().getToken();
                                               Log.d("PushMessage", token);
                                           }
                                       }
                );
    }

    private void initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotificationManager notificationManager = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel =
                    new NotificationChannel("2", "MyChannel", importance);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void initHandler() {
        HandlerThread handlerThread = new HandlerThread("MyHandlerThread");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
    }

    private void initRetrofit() {
        //Создаем логер для ретрофита
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder();
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        okHttpClientBuilder.addInterceptor(logging);

        Retrofit retrofit;
        retrofit = new Retrofit.Builder()
                .baseUrl("https://api.openweathermap.org/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(okHttpClientBuilder.build())
                .build();
        // Создаём объект, при помощи которого будем выполнять запросы
        openWeather = retrofit.create(IOpenWeather.class);
    }

    private void initSensors() {
        ambientTempLabel = (TextView) findViewById(R.id.main_tv_ambient_temp_sensor);
        ambientHumidLabel = (TextView) findViewById(R.id.main_tv_humidity_sensor);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorListener = new SensorEventListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onSensorChanged(SensorEvent event) {
                Sensor sensor = event.sensor;
                if (sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                    float ambient_temp = event.values[0];
                    ambientTempLabel.setText("Ambient temperature: " + ambient_temp + "°C");
                } else if (sensor.getType() == Sensor.TYPE_RELATIVE_HUMIDITY) {
                    float humidity = event.values[0];
                    ambientHumidLabel.setText("Humidity: " + humidity + "%");
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            mTemperature = mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
            mHumidity = mSensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);
        } else {
            ambientHumidLabel.setText(NOT_SUPPORTED_MSG);
            ambientTempLabel.setText(NOT_SUPPORTED_MSG);
        }

    }


    private void requestRetrofit(String latitude, String longitude, String exclude, String apiKey, String units) {
        Log.d("TAG", "Retrofit In");
        openWeather.loadWeather(latitude, longitude, exclude, apiKey, units)
                .enqueue(new Callback<ResponseWeather>() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void onResponse(Call<ResponseWeather> call, Response<ResponseWeather> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Log.d("onResponse: ", "Response catched");
                            ((TextView) findViewById(R.id.main_tv_current_temp)).setText("+" +
                                    (Math.round(response.body().getCurrent().getTemp())) + "°");
                            List<Daily> weatherData = response.body().getDaily();

                            initList(weatherData);

                            //Сетим иконку текущей погоды на главном экране
                            currentWeatherIcon = findViewById(R.id.main_im_icon_current_weather);
                            List<Weather> currentWeatherList = response.body().getCurrent().getWeather();
                            Weather currentWeather = currentWeatherList.get(0);
                            String iconCurrentWeather = currentWeather.getIcon();
                            String iconUrl = "http://openweathermap.org/img/wn/" + iconCurrentWeather + "@4x.png";
                            Uri iconUri = Uri.parse(iconUrl);
                            Picasso.get()
                                    .load(iconUri)
                                    .into(currentWeatherIcon);

                        } else {
                            Log.d("OnResponse", "Server return an error");
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseWeather> call, Throwable t) {
                        Log.d("FAILURE", "FAILURE");
                        if (t instanceof IOException) {
                            Log.d("FAILURE", "this is an actual network failure");
                        } else {
                            Log.d("FAILURE", "conversion issue! big problems");
                        }
                    }
                });
        }


    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(mSensorListener, mTemperature, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(mSensorListener, mHumidity, SensorManager.SENSOR_DELAY_NORMAL);

        SharedPreferences preferences = getSharedPreferences("MyPref", MODE_PRIVATE);
        String currentTemp = preferences.getString("currentTemp", "");
        currentTempTextView.setText(currentTemp);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(mSensorListener);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState, @NonNull PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    private void initList(List<Daily> weatherData) {
        recyclerView = findViewById(R.id.recycler_list);

        // Эта установка служит для повышения производительности системы
        recyclerView.setHasFixedSize(true);

        // Будем работать со встроенным менеджером
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        // Установим адаптер
        adapter = new ListAdapter(weatherData, getParent());
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(getBaseContext(), DividerItemDecoration.VERTICAL));
    }

    private void initDrawer(Toolbar toolbar) {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
    }

    private Toolbar initToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        return toolbar;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);

        //Находим нашу SearchView и вешаем слушатель ввода текста
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        MenuItem search = menu.findItem(R.id.action_search);
        final SearchView searchText = (SearchView) search.getActionView();
        searchText.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchText.setQueryHint("Type city here");

        //Получаем введенный пользователем город
        getWeatherInSearchableCity(searchText);
        return true;
    }

    private void sendWeatherRequest(String cityName) {
        final int CITY_LATITUDE = 0;
        final int CITY_LONGITUDE = 1;
        final String[] latitude = new String[1];
        final String[] longitude = new String[1];

        Thread getGoelocThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Geocoder geocoder = new Geocoder(getBaseContext());
                if (Geocoder.isPresent()) {
                    try {
                        List<Address> addresses = geocoder.getFromLocationName(cityName, 1);

                        List<Double> latLon = new ArrayList<>(addresses.size());
                        for (Address address : addresses) {
                            if (address != null && address.hasLatitude() && address.hasLongitude()) {
                                latLon.add(address.getLatitude());
                                latLon.add(address.getLongitude());
                                latitude[0] = String.valueOf(latLon.get(CITY_LATITUDE));
                                longitude[0] = String.valueOf(latLon.get(CITY_LONGITUDE));
                                Log.d("GET LON_LAT", String.valueOf(latLon.get(CITY_LATITUDE)) + "  "
                                        + latLon.get(CITY_LONGITUDE));
                                mHandler.post(() -> {
                                    latitudeLongitude.add(latitude[0]);
                                    latitudeLongitude.add(longitude[0]);
                                });
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        getGoelocThread.start();
    }

    private void getWeatherInSearchableCity(SearchView searchText) {
        // Вешаем листнер на нашу строку ввода города
        searchText.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchableCity = query;
                searchHistoryViewModel.addSearch(new SearchText(String.valueOf(searchableCity)));


                searchText.clearFocus();
                Log.d("CITY", String.valueOf(searchableCity));

                //Получаем координаты введенного города
                sendWeatherRequest(String.valueOf(searchableCity));

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(5000);
                            mHandler.post(() -> {
                                if (latitudeLongitude.get(0) != null && latitudeLongitude.get(1) != null) {
                                    String latitude = latitudeLongitude.get(0);
                                    String longitude = latitudeLongitude.get(1);
                                    latitudeLongitude.clear();
                                    String units = "metric";
                                    String exclude = "minutely,hourly";
                                    String apiKey = "d4a256e168a940fb210b109445d77de4";
                                    requestRetrofit(latitude, longitude, exclude, apiKey, units);
                                }
                            });
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                return false;
            }
        });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

            item.setChecked(true);


        } else if (id == R.id.nav_slideshow) {

            item.setChecked(true);

        } else if (id == R.id.nav_about) {

            Intent intent2AboutActivity = new Intent(getBaseContext(), AboutUsActivity.class);
            startActivity(intent2AboutActivity);
            item.setChecked(true);

        } else if (id == R.id.nav_contact) {

            Intent intent2ContactActivity = new Intent(getBaseContext(), ContactUsActivity.class);
            startActivity(intent2ContactActivity);
            item.setChecked(true);
        }
        item.setChecked(false);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);

        return true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(networkBroadcastReceiver);
        unregisterReceiver(batteryBroadcastReceiver);

        saveCurrentTemperature();

    }

    private void saveCurrentTemperature() {
        String currentTemp = String.valueOf(currentTempTextView.getText());
        SharedPreferences preferences = getSharedPreferences("MyPref", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("currentTemp", currentTemp);
        editor.apply();
    }
}