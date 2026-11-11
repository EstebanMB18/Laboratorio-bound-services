package com.esteban.odometrotutorias;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TripDatabase extends SQLiteOpenHelper {
    public TripDatabase(Context context) { super(context, "odometro_tutorias.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE trips (id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL, ended_at INTEGER NOT NULL, meters REAL NOT NULL, points INTEGER NOT NULL, min_movement REAL NOT NULL, interval_seconds INTEGER NOT NULL, start_lat REAL, start_lon REAL, end_lat REAL, end_lon REAL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
    public void save(long start, long end, double meters, int points, float minMovement, int interval, Double startLat, Double startLon, Double endLat, Double endLon) {
        ContentValues values = new ContentValues();
        values.put("started_at", start); values.put("ended_at", end); values.put("meters", meters);
        values.put("points", points); values.put("min_movement", minMovement); values.put("interval_seconds", interval);
        putOptional(values,"start_lat",startLat); putOptional(values,"start_lon",startLon);
        putOptional(values,"end_lat",endLat); putOptional(values,"end_lon",endLon);
        getWritableDatabase().insertOrThrow("trips", null, values);
    }
    private static void putOptional(ContentValues values, String key, Double number) {
        if (number == null) values.putNull(key); else values.put(key, number);
    }
    public Summary summary() {
        Summary result = new Summary();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*), COALESCE(SUM(meters),0) FROM trips", null)) {
            if(c.moveToFirst()) { result.count=c.getInt(0); result.meters=c.getDouble(1); }
        }
        return result;
    }
    public String lastTrips() {
        StringBuilder out=new StringBuilder();
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        try (Cursor c=getReadableDatabase().rawQuery("SELECT started_at, meters, points FROM trips ORDER BY id DESC LIMIT 12",null)) {
            while(c.moveToNext()) out.append(df.format(new Date(c.getLong(0)))).append("  ·  ")
                .append(String.format(Locale.getDefault(),"%.1f m",c.getDouble(1)))
                .append("  ·  ").append(c.getInt(2)).append(" puntos\n");
        }
        return out.length()==0 ? "Sin recorridos guardados" : out.toString();
    }
    public String csv() {
        StringBuilder csv=new StringBuilder("id,fecha_inicio,fecha_fin,distancia_metros,puntos,precision_movimiento_m,actualizacion_s,lat_inicio,lon_inicio,lat_fin,lon_fin\n");
        try (Cursor c=getReadableDatabase().rawQuery("SELECT id,started_at,ended_at,meters,points,min_movement,interval_seconds,start_lat,start_lon,end_lat,end_lon FROM trips ORDER BY id",null)) {
            while(c.moveToNext()) {
                csv.append(c.getLong(0)).append(',').append(c.getLong(1)).append(',').append(c.getLong(2)).append(',')
                  .append(String.format(Locale.US,"%.2f",c.getDouble(3))).append(',').append(c.getInt(4)).append(',')
                  .append(String.format(Locale.US,"%.2f",c.getDouble(5))).append(',').append(c.getInt(6));
                for(int i=7;i<=10;i++) csv.append(',').append(c.isNull(i)?"":String.format(Locale.US,"%.7f",c.getDouble(i)));
                csv.append('\n');
            }
        }
        Summary summary=summary();
        csv.append("TOTAL,,,").append(String.format(Locale.US,"%.2f",summary.meters)).append("\n");
        return csv.toString();
    }
    public static class Summary { public int count; public double meters; }
}
