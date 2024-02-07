package com.example.lanky_dziury;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity implements View.OnClickListener {
    // Deklaracja prywatnych pól klasy dla przycisków i suwaka (SeekBar).
    private Button button3;
    private Button button4;
    private SeekBar Threshold;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Ustawienie layoutu aktywności z zasobów XML.
        setContentView(R.layout.activity_settings);

        // Inicjalizacja suwaka i przypisanie mu wartości z preferencji (ustawień aplikacji).
        Threshold = findViewById(R.id.Threshold);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Odczytanie zapisanej wartości progowej detekcji dziur i ustawienie suwaka na tę wartość.
        int savedThreshold = sharedPreferences.getInt("hole_detection_threshold", 3);
        Threshold.setProgress(savedThreshold);

        // Inicjalizacja przycisków i ustawienie obiektu tej klasy jako ich nasłuchiwacza.
        button4 = findViewById(R.id.button4);
        button4.setOnClickListener(this);
        button3 = findViewById(R.id.button3);
        button3.setOnClickListener(this);

        // Ustawienie nasłuchiwacza zmiany wartości suwaka, który zapisuje nową wartość do preferencji.
        Threshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sharedPreferences.edit().putInt("hole_detection_threshold", progress).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Metoda wywoływana, gdy użytkownik zaczyna przesuwać suwak.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Metoda wywoływana, gdy użytkownik przestaje przesuwać suwak.
            }
        });
    }

    // Implementacja metody onClick z interfejsu OnClickListener, obsługuje kliknięcia na elementy.
    @Override
    public void onClick(View view) {
        Intent intent;
        if (view.getId() == R.id.button3) {
            // Jeśli kliknięto przycisk button3, uruchamiana jest główna aktywność aplikacji.
            intent = new Intent(SettingsActivity.this, MainActivity.class);
            startActivity(intent);
        }
        if (view.getId() == R.id.button4) {
            // Jeśli kliknięto przycisk button4, wywoływana jest metoda do czyszczenia bazy danych.
            clearDatabase();
        }
    }

    // Metoda do czyszczenia bazy danych - usuwa wszystkie markery z tabeli Markers.
    private void clearDatabase() {
        try {
            // Otwarcie lub utworzenie bazy danych "kebab.db" i wykonanie operacji SQL usuwającej rekordy.
            SQLiteDatabase database = openOrCreateDatabase("kebab.db", MODE_PRIVATE, null);
            database.execSQL("DELETE FROM Markers");
            database.close();
            // Wyświetlenie komunikatu o sukcesie operacji.
            Toast.makeText(this, "Markers deleted.", Toast.LENGTH_SHORT).show();
        } catch (SQLiteException e) {
            // W przypadku błędu wyświetlenie komunikatu o niepowodzeniu i wydrukowanie śladu stosu błędu.
            Toast.makeText(this, "Failed to delete markers!", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
}