package com.esteban.odometrotutorias;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST=110;
    private static final int CREATE_CSV_REQUEST=111;
    private OdometerService odometer;
    private boolean bound=false, binding=false, shouldStartAfterPermission=false;
    private TextView distance, status, currentLocation, history, totalText, countText;
    private EditText minDistance, interval;
    private Button startButton, stopButton;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable redraw=new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this,1000); }
    };
    private final ServiceConnection connection=new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name,IBinder service) {
            odometer=((OdometerService.OdometerBinder)service).getOdometer();
            bound=true;
            if(shouldStartAfterPermission) { shouldStartAfterPermission=false; startSession(); }
            refresh();
        }
        @Override public void onServiceDisconnected(ComponentName name) { odometer=null; bound=false; binding=false; refresh(); }
    };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_main);
        distance=findViewById(R.id.distance); status=findViewById(R.id.status);
        currentLocation=findViewById(R.id.currentLocation); history=findViewById(R.id.history);
        totalText=findViewById(R.id.totalText); countText=findViewById(R.id.countText);
        minDistance=findViewById(R.id.minDistance); interval=findViewById(R.id.interval);
        startButton=findViewById(R.id.startButton); stopButton=findViewById(R.id.stopButton);
        android.content.SharedPreferences preferences=getPreferences(MODE_PRIVATE);
        minDistance.setText(preferences.getString("movement","5"));
        interval.setText(preferences.getString("interval","3"));
        startButton.setOnClickListener(v->startSession());
        stopButton.setOnClickListener(v->{ if(bound && odometer!=null && odometer.stopTracking()) {
            Toast.makeText(this,"Recorrido guardado",Toast.LENGTH_SHORT).show(); refreshHistory(); refresh();
        }});
        findViewById(R.id.exportButton).setOnClickListener(v->exportCsv());
        refreshHistory();
    }
    @Override protected void onStart() {
        super.onStart();
        if(!binding) binding=bindService(new Intent(this,OdometerService.class),connection,Context.BIND_AUTO_CREATE);
        handler.post(redraw);
    }
    @Override protected void onStop() {
        handler.removeCallbacks(redraw);
        // Solo modo foreground: al dejar la actividad se guarda el recorrido.
        if(bound && odometer!=null && odometer.isTracking()) odometer.stopTracking();
        if(binding) { unbindService(connection); binding=false; }
        bound=false; odometer=null;
        refreshHistory();
        super.onStop();
    }
    private void startSession() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            shouldStartAfterPermission=true;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_REQUEST);
            return;
        }
        if(!bound || odometer==null) { Toast.makeText(this,"Conectando servicio...",Toast.LENGTH_SHORT).show(); return; }
        try {
            float movement=Float.parseFloat(minDistance.getText().toString().replace(',','.'));
            int seconds=Integer.parseInt(interval.getText().toString().trim());
            if(!Float.isFinite(movement) || movement<1 || movement>100 || seconds<1 || seconds>300) {
                Toast.makeText(this,"Usa de 1 a 100 m y de 1 a 300 segundos",Toast.LENGTH_LONG).show(); return;
            }
            getPreferences(MODE_PRIVATE).edit().putString("movement",minDistance.getText().toString()).putString("interval",interval.getText().toString()).apply();
            if(!odometer.startTracking(movement,seconds)) Toast.makeText(this,odometer.getState(),Toast.LENGTH_LONG).show();
        } catch(NumberFormatException ex) { Toast.makeText(this,"Ingresa valores numéricos válidos",Toast.LENGTH_LONG).show(); }
        refresh();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==LOCATION_REQUEST) {
            if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) {
                if(bound) { shouldStartAfterPermission=false; startSession(); }
            } else { shouldStartAfterPermission=false; Toast.makeText(this,"Sin permiso de ubicación precisa no es posible medir",Toast.LENGTH_LONG).show(); }
        }
    }
    private void refresh() {
        if(distance==null) return;
        boolean tracking=bound && odometer!=null && odometer.isTracking();
        distance.setText(String.format(Locale.getDefault(),"%.1f m",bound && odometer!=null?odometer.getDistance():0d));
        status.setText(bound && odometer!=null?odometer.getState():"Servicio desconectado");
        currentLocation.setText(bound && odometer!=null?odometer.getCoordinates():"Ubicación: pendiente");
        startButton.setEnabled(bound && !tracking); stopButton.setEnabled(tracking);
        minDistance.setEnabled(!tracking); interval.setEnabled(!tracking);
    }
    private void refreshHistory() {
        if(totalText==null) return;
        TripDatabase db=new TripDatabase(this); TripDatabase.Summary s=db.summary();
        totalText.setText(String.format(Locale.getDefault(),"Distancia total: %.1f m",s.meters));
        countText.setText("Sesiones registradas: "+s.count); history.setText(db.lastTrips()); db.close();
    }
    private void exportCsv() {
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE,"reporte_odometro_tutorias.csv");
        startActivityForResult(intent,CREATE_CSV_REQUEST);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=CREATE_CSV_REQUEST || resultCode!=RESULT_OK || data==null || data.getData()==null) return;
        try {
            TripDatabase db=new TripDatabase(this);
            String csv=db.csv(); db.close();
            try(OutputStream stream=getContentResolver().openOutputStream(data.getData())) {
                if(stream==null) throw new java.io.IOException("No se pudo abrir archivo");
                stream.write(("\uFEFF"+csv).getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(this,"Reporte CSV guardado",Toast.LENGTH_LONG).show();
        } catch(Exception ex) {
            Toast.makeText(this,"Error al guardar CSV: "+ex.getMessage(),Toast.LENGTH_LONG).show();
        }
    }
}
