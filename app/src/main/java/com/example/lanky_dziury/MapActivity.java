package com.example.lanky_dziury;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity {
    private SensorManager sensorManager;
    public SensorEventListener sensorEventListener;
    private Sensor accelerometer;
    private float lastY;
    private boolean isJumping = false;
    private final Handler handler = new Handler();
    private MapView map;
    private MyLocationNewOverlay myLocationOverlay;
    private SQLiteDatabase database;
    private final String DB_NAME = "kebab.db";
    private final String TABLE_MARKERS = "Markers";
    private final String COLUMN_X = "x";
    private final String COLUMN_Y = "y";
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final float PROXIMITY_THRESHOLD = 50;
    private boolean isSoundPlaying = false;
    private MediaPlayer mediaPlayer;

    @SuppressLint("Range")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Ustawienie layoutu dla aktywności.
        setContentView(R.layout.activity_map);

        // Inicjalizacja komponentów UI, sensorów i bazy danych.
        map = findViewById(R.id.map);
        initializeLocationOverlay();
        initializeMap();
        holeDetection();
        initializeDatabase();
        loadMarkers();

        // Ustawienie przycisków do centrowania mapy i powrotu.
        FloatingActionButton fabCenterMap = findViewById(R.id.fab_center_map);
        fabCenterMap.setOnClickListener(view -> centerMapOnMyLocation());
        FloatingActionButton fabGoBack = findViewById(R.id.fab_go_back);
        fabGoBack.setOnClickListener(view -> finish());

        // Sprawdzenie i ewentualne żądanie uprawnień do lokalizacji.
        checkAndRequestLocationPermission();
    }

    // Metoda do sprawdzania i żądania uprawnienia do lokalizacji.
    private void checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
    }

    // Callback po otrzymaniu wyniku żądania uprawnienia.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                Toast.makeText(this, "The application will not work without access to the location!", Toast.LENGTH_LONG).show();
            }
        }
    }

    // Inicjalizacja warstwy lokalizacji na mapie.
    private void initializeLocationOverlay() {
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableFollowLocation();
        map.getOverlays().add(myLocationOverlay);

        // Automatyczne centrowanie mapy na aktualnej lokalizacji użytkownika.
        myLocationOverlay.runOnFirstFix(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        IMapController mapController = map.getController();
                        GeoPoint myLocation = myLocationOverlay.getMyLocation();
                        if (myLocation != null) {
                            mapController.setCenter(myLocation);
                            mapController.animateTo(myLocation);
                        }
                    }
                });
            }
        });
    }

    // Metoda do centrowania mapy na lokalizacji użytkownika.
    private void centerMapOnMyLocation() {
        if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
            map.getController().animateTo(myLocationOverlay.getMyLocation());
        }
    }

    // Inicjalizacja mapy z ustawieniami domyślnymi.
    private void initializeMap() {
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);

        IMapController mapController = map.getController();
        mapController.setZoom(18);

        GeoPoint startPoint = new GeoPoint(53.4287f, 14.5640f);
        mapController.setCenter(startPoint);
    }

    // Inicjalizacja bazy danych SQLite.
    private void initializeDatabase() {
        try {
            database = openOrCreateDatabase(DB_NAME, MODE_PRIVATE, null);
            database.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_MARKERS + " (" + COLUMN_X + " DOUBLE, " + COLUMN_Y + " DOUBLE)");
        } catch (SQLiteException e) {
            Log.e(getClass().getSimpleName(), "Could not create or open the database");
        }
    }

    // Wykrywanie "skoku" jako wskazania na dziurę na drodze przy pomocy akcelerometru.
    private void holeDetection(){
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if(sensorManager != null){
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        // Listener zmian wartości z akcelerometru.
        sensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                float[] acceleration = sensorEvent.values;
                float y = acceleration[1]; // Odczyt wartości przyspieszenia w osi Y.

                // Sprawdzenie, czy nastąpił "skok".
                if (lastY == 0) {
                    lastY = y;
                    return;
                }

                if (isJump(y, lastY) && !isJumping) {
                    isJumping = true;
                    // Opóźnienie resetu stanu skoku.
                    handler.postDelayed(() -> isJumping = false, 1000);
                    // Wyświetlenie dialogu z pytaniem o dodanie markera dziury.
                    // Pokaż okno dialogowe
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showHoleDetectedDialog();
                            initializeDatabase();
                            loadMarkers();
                        }
                    });
                }
                lastY = y; // Aktualizacja ostatniej wartości przyspieszenia w osi Y.
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                // Metoda wywoływana, gdy zmienia się dokładność sensora (nieużywana w tym kodzie).
            }
        };
    }

    // Sprawdzenie, czy nastąpił "skok".
    private boolean isJump(float currentY, float lastY) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        float threshold = sharedPreferences.getInt("hole_detection_threshold", 3);
        return Math.abs(currentY - lastY) > threshold;
    }

    // Wyświetlenie dialogu po wykryciu dziury.
    private void showHoleDetectedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Hole Detected!");
        builder.setMessage("Would you like to add a hole marker at this location?");

        // Obsługa przycisków dialogu.
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                addHoleMarker();
            }
        });
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // Wczytywanie markerów z bazy danych i umieszczanie ich na mapie.
    private void loadMarkers() {
        Cursor cursor = database.rawQuery("SELECT * FROM " + TABLE_MARKERS, null);
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndexX = cursor.getColumnIndex(COLUMN_X);
            int columnIndexY = cursor.getColumnIndex(COLUMN_Y);
            if (columnIndexX >= 0 && columnIndexY >= 0) {
                do {
                    float x = cursor.getFloat(columnIndexX);
                    float y = cursor.getFloat(columnIndexY);
                    loadMarker(x, y);
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
    }

    // Dodanie markera na mapie na podstawie współrzędnych.
    private void loadMarker(double x, double y) {
        Marker marker = new Marker(map);
        marker.setPosition(new GeoPoint(x, y));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle("Hole");

        // Ustawienie zachowania przy kliknięciu na marker.
        marker.setOnMarkerClickListener((marker1, mapView) -> {
            confirmAndRemoveMarker(marker1, x, y);
            return true;
        });

        map.getOverlays().add(marker);
    }

    // Wyświetlenie dialogu potwierdzającego chęć usunięcia markera.
    private void confirmAndRemoveMarker(Marker marker, double x, double y) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Remove Marker");
                builder.setMessage("Do you want to remove this marker?");

                builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        removeMarker(marker, x, y);
                    }
                });
                builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();;
    }

    // Usunięcie markera z mapy i bazy danych.
    private void removeMarker(Marker marker, double x, double y) {
        map.getOverlays().remove(marker);
        map.invalidate();
        removeMarkerFromDatabase(x, y);
    }

    // Usunięcie markera z bazy danych.
    private void removeMarkerFromDatabase(double x, double y) {
        try {
            int deletedRows = database.delete(TABLE_MARKERS, COLUMN_X + "= ? AND " + COLUMN_Y + " = ?", new String[]{String.valueOf(x), String.valueOf(y)});
            if (deletedRows > 0) {
                Log.i(getClass().getSimpleName(), "Marker deleted successfully");
            } else {
                Log.e(getClass().getSimpleName(), "No marker was deleted");
            }
        } catch (SQLiteException e) {
            Log.e(getClass().getSimpleName(), "Error while deleting marker", e);
        }
    }

    // Zaokrąglenie wartości do określonej liczby miejsc po przecinku.
    private double roundValue(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    // Dodanie markera dziury na mapie i zapisanie go w bazie danych.
    private void addHoleMarker() {
        if (myLocationOverlay.getMyLocation() != null) {
            GeoPoint currentLocation = myLocationOverlay.getMyLocation();
            double latitude = roundValue(currentLocation.getLatitude(), 6);
            double longitude = roundValue(currentLocation.getLongitude(), 6);
            addMarker(latitude, longitude);
        }
    }

    // Dodanie markera na mapie i zapisanie go w bazie danych.
    private void addMarker(double x, double y) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_X, x);
        values.put(COLUMN_Y, y);
        database.insert(TABLE_MARKERS, null, values);
        loadMarker(x, y);
    }

    // Pobranie markerów z bazy danych.
    private List<Marker> getMarkersFromDatabase() {
        List<Marker> markers = new ArrayList<>();
        try {
            SQLiteDatabase database = openOrCreateDatabase("kebab.db", MODE_PRIVATE, null);
            Cursor cursor = database.rawQuery("SELECT * FROM Markers", null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndexX = cursor.getColumnIndex("x");
                int columnIndexY = cursor.getColumnIndex("y");
                while (!cursor.isAfterLast()) {
                    float x = cursor.getFloat(columnIndexX);
                    float y = cursor.getFloat(columnIndexY);
                    Marker marker = new Marker(map);
                    marker.setPosition(new GeoPoint(x, y));
                    markers.add(marker);
                    cursor.moveToNext();
                }
                cursor.close();
            }
            database.close();
        } catch (SQLiteException e) {
            e.printStackTrace();
        }
        return markers;
    }

    // Sprawdzenie bliskości do dziur na podstawie lokalizacji użytkownika.
    private void checkProximityToHoles(GeoPoint currentLocation) {
        List<Marker> markersList = getMarkersFromDatabase();
        for (Marker marker : markersList) {
            if (currentLocation.distanceToAsDouble(marker.getPosition()) < PROXIMITY_THRESHOLD) {
                playAlertSound();
                showProximityAlert();
                break;
            }
        }
    }

    // Wyświetlenie alertu o bliskości dziury.
    private void showProximityAlert() {
        Toast.makeText(this, "You are close to a hole. Watch out!", Toast.LENGTH_LONG).show();
    }

    // Odtwarzanie dźwięku alarmu.
    private void playAlertSound() {
        try {
        if (isSoundPlaying) {
            return;
        }

        if (mediaPlayer != null) {
            mediaPlayer.release();
        }

        mediaPlayer = MediaPlayer.create(this, R.raw.alert_sound);
        mediaPlayer.setOnCompletionListener(mp -> {
            mp.release();
            isSoundPlaying = false;
            mediaPlayer = null;
        });
        mediaPlayer.start();
        isSoundPlaying = true;
        } catch (Exception e) {
            Log.e("MapActivity", "Błąd podczas odtwarzania dźwięku", e);
        }
    }

    // Runnable do cyklicznego sprawdzania bliskości dziur.
    private final Runnable proximityCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
                checkProximityToHoles(myLocationOverlay.getMyLocation());
            }
            handler.postDelayed(this, 5000);
        }
    };

    // Metoda onResume rejestrująca listener sensora i uruchamiająca cykliczne sprawdzanie bliskości dziur.
    @Override
    protected void onResume() {
        super.onResume();
        handler.post(proximityCheckRunnable);
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        if (accelerometer != null) {
            sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    // Metoda onPause zatrzymująca odtwarzanie dźwięku, cykliczne sprawdzanie i odrejestrowywanie listenera sensora.
    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        isSoundPlaying = false;
        handler.removeCallbacks(proximityCheckRunnable);
        if (accelerometer != null) {
            sensorManager.unregisterListener(sensorEventListener);
        }
    }

    // Metoda onDestroy zamykająca połączenie z bazą danych.
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (database != null) {
            database.close();
        }
    }
}