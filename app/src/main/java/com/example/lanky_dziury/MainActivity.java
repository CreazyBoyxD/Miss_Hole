package com.example.lanky_dziury;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private Button button1;
    private Button button2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Ustawienie layoutu dla tej aktywności. Layout jest definiowany w pliku XML.
        setContentView(R.layout.activity_main);

        // Przypisanie obiektów Button do tych zdefiniowanych w pliku layoutu XML za pomocą ich ID.
        button1 = findViewById(R.id.button1);
        button2 = findViewById(R.id.button2);
        // Ustawienie obiektu MainActivity jako słuchacza zdarzeń kliknięcia dla obu przycisków.
        button1.setOnClickListener(this);
        button2.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        // Deklaracja zmiennej intent, która będzie przechowywać intencję przejścia do innej aktywności.
        Intent intent;
        // Sprawdzenie, który przycisk został kliknięty na podstawie jego ID.
        if (view.getId() == R.id.button1) {
            // Utworzenie nowej intencji przejścia z MainActivity (bieżącej aktywności) do MapActivity (docelowej aktywności).
            intent = new Intent(MainActivity.this, MapActivity.class);
            // Rozpoczęcie nowej aktywności.
            startActivity(intent);
        }

        // Analogicznie, jeśli kliknięto drugi przycisk, przejście do aktywności SettingsActivity.
        if (view.getId() == R.id.button2) {
            intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        }
    }
}