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
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
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
    private LocationManager locationManager;
    private SQLiteDatabase database;
    private final String DB_NAME = "kebab.db";
    private final String TABLE_MARKERS = "Markers";
    private final String COLUMN_X = "x";
    private final String COLUMN_Y = "y";
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final float PROXIMITY_THRESHOLD = 50;
    private MediaPlayer mediaPlayer;

    @SuppressLint("Range")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        map = findViewById(R.id.map);

        initializeLocationOverlay();
        initializeMap();
        holeDetection();
        initializeDatabase();
        loadMarkers();

        FloatingActionButton fabCenterMap = findViewById(R.id.fab_center_map);
        fabCenterMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                centerMapOnMyLocation();
            }
        });
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> finish());

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        checkAndRequestLocationPermission();
    }

    private void checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
    }

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

    private void initializeLocationOverlay() {
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableFollowLocation();
        map.getOverlays().add(myLocationOverlay);

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

    private void centerMapOnMyLocation() {
        if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
            map.getController().animateTo(myLocationOverlay.getMyLocation());
        }
    }

    private void holeDetection(){
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if(sensorManager != null){
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        sensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                float[] acceleration = sensorEvent.values;
                float y = acceleration[1];

                if (lastY == 0) {
                    lastY = y;
                    return;
                }

                if (isJump(y, lastY) && !isJumping) {
                    isJumping = true;
                    handler.postDelayed(() -> isJumping = false, 1000);
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
                lastY = y;
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };
    }

    private boolean isJump(float currentY, float lastY) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        float threshold = sharedPreferences.getInt("hole_detection_threshold", 3);
        return Math.abs(currentY - lastY) > threshold;
    }

    private void showHoleDetectedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Hole Detected!");
        builder.setMessage("Would you like to add a hole marker at this location?");

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

    private void initializeDatabase() {
        try {
            database = openOrCreateDatabase(DB_NAME, MODE_PRIVATE, null);
            database.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_MARKERS + " (" + COLUMN_X + " DOUBLE, " + COLUMN_Y + " DOUBLE)");
        } catch (SQLiteException e) {
            Log.e(getClass().getSimpleName(), "Could not create or open the database");
        }
    }

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

    private void loadMarker(double x, double y) {
        Marker marker = new Marker(map);
        marker.setPosition(new GeoPoint(x, y));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle("Hole");

        marker.setOnMarkerClickListener((marker1, mapView) -> {
            confirmAndRemoveMarker(marker1, x, y);
            return true;
        });

        map.getOverlays().add(marker);
    }

    private void confirmAndRemoveMarker(Marker marker, double x, double y) {
        new AlertDialog.Builder(this)
                .setTitle("Remove Marker")
                .setMessage("Do you want to remove this marker?")
                .setPositiveButton("Yes", (dialog, which) -> removeMarker(marker, x, y))
                .setNegativeButton("No", null)
                .show();
    }

    private void removeMarker(Marker marker, double x, double y) {
        map.getOverlays().remove(marker);
        map.invalidate();
        removeMarkerFromDatabase(x, y);
    }

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

    private double roundValue(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }


    private void addHoleMarker() {
        if (myLocationOverlay.getMyLocation() != null) {
            GeoPoint currentLocation = myLocationOverlay.getMyLocation();
            double latitude = roundValue(currentLocation.getLatitude(), 6);
            double longitude = roundValue(currentLocation.getLongitude(), 6);
            addMarker(latitude, longitude);
            saveMarkerToDatabase(latitude, longitude);
        }
    }

    private void saveMarkerToDatabase(double x, double y) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_X, x);
        values.put(COLUMN_Y, y);
        try {
            database.insertOrThrow(TABLE_MARKERS, null, values);
        } catch (SQLiteException e) {
        }
    }

    private void addMarker(double x, double y) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_X, x);
        values.put(COLUMN_Y, y);
        database.insert(TABLE_MARKERS, null, values);
        loadMarker(x, y);
    }

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

    private void showProximityAlert() {
        Toast.makeText(this, "You are close to a hole. Watch out!", Toast.LENGTH_LONG).show();
    }

    private boolean isSoundPlaying = false;

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

    private final Runnable proximityCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
                checkProximityToHoles(myLocationOverlay.getMyLocation());
            }
            handler.postDelayed(this, 5000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(proximityCheckRunnable);
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        if (accelerometer != null) {
            sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (database != null) {
            database.close();
        }
    }
}