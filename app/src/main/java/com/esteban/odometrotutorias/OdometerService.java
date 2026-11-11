package com.esteban.odometrotutorias;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.Bundle;

/** Servicio enlazado local: acumula desplazamientos válidos de una sesión visible. */
public class OdometerService extends Service implements LocationListener {
    private final IBinder binder = new OdometerBinder();
    private LocationManager manager;
    private Location lastPoint;
    private Location currentPoint;
    private double meters;
    private boolean tracking;
    private long startTimestamp;
    private int acceptedPoints;
    private float movementMeters = 5f;
    private int updateSeconds = 3;
    private Double startLat, startLon;
    private String state = "Listo para iniciar";

    public class OdometerBinder extends Binder { public OdometerService getOdometer() { return OdometerService.this; } }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public void onCreate() {
        super.onCreate();
        manager = (LocationManager)getSystemService(LOCATION_SERVICE);
    }
    public boolean startTracking(float minMeters, int intervalSec) {
        if(tracking) return true;
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            state="Se requiere ubicación precisa"; return false;
        }
        if(manager==null || !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            state="Activa GPS/Ubicación del dispositivo"; return false;
        }
        movementMeters = minMeters; updateSeconds=intervalSec;
        meters=0; acceptedPoints=0; lastPoint=null; currentPoint=null;
        startLat=null; startLon=null; startTimestamp=System.currentTimeMillis();
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalSec*1000L, minMeters, this, Looper.getMainLooper());
            tracking=true; state="Buscando señal GPS...";
            return true;
        } catch (SecurityException | IllegalArgumentException e) {
            state="No se pudo activar el GPS: " + e.getMessage(); return false;
        }
    }
    @Override public void onLocationChanged(Location point) {
        currentPoint=new Location(point);
        // No sumar ubicaciones con precisión horizontal peor que 35 m.
        if(!tracking || !point.hasAccuracy() || point.getAccuracy()>35f) {
            if(tracking) state="GPS: esperando señal más precisa";
            return;
        }
        if(lastPoint==null) {
            lastPoint=new Location(point); startLat=point.getLatitude(); startLon=point.getLongitude();
            acceptedPoints=1; state="Registrando recorrido"; return;
        }
        float segment=lastPoint.distanceTo(point);
        // Evita sumar pequeños movimientos espurios y saltos inverosímiles (> 35 m/s).
        float seconds=Math.max(1f,(point.getElapsedRealtimeNanos()-lastPoint.getElapsedRealtimeNanos())/1_000_000_000f);
        if(segment<movementMeters) return;
        if(segment/seconds>35f) { state="GPS: salto de posición descartado"; return; }
        // Umbral adicional que reduce ruido cuando el error horizontal es elevado.
        if(segment < Math.max(movementMeters,Math.min(15f,point.getAccuracy()*0.5f))) return;
        meters+=segment; acceptedPoints++; lastPoint=new Location(point); state="Registrando recorrido";
    }
    public double getDistance() { return meters; }
    public boolean isTracking() { return tracking; }
    public String getState() { return state; }
    public String getCoordinates() {
        if(currentPoint==null) return "Ubicación: esperando GPS";
        return String.format(java.util.Locale.getDefault(),"Ubicación: %.6f, %.6f  |  ±%.0f m",currentPoint.getLatitude(),currentPoint.getLongitude(),currentPoint.hasAccuracy()?currentPoint.getAccuracy():0f);
    }
    public boolean stopTracking() {
        if(!tracking) return false;
        tracking=false;
        if(manager!=null) manager.removeUpdates(this);
        Double endLat=lastPoint==null?null:lastPoint.getLatitude();
        Double endLon=lastPoint==null?null:lastPoint.getLongitude();
        new TripDatabase(this).save(startTimestamp,System.currentTimeMillis(),meters,acceptedPoints,movementMeters,updateSeconds,startLat,startLon,endLat,endLon);
        state="Recorrido finalizado y guardado";
        return true;
    }
    @Override public void onProviderDisabled(String provider) { state="GPS desactivado; vuelve a activarlo"; }
    @Override public void onProviderEnabled(String provider) { state="GPS disponible"; }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onDestroy() {
        if(tracking) stopTracking();
        if(manager!=null) manager.removeUpdates(this);
        super.onDestroy();
    }
}
