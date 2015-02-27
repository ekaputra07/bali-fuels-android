package com.balitechy.balipetrols;

import android.app.Fragment;
import android.content.Context;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.parse.FindCallback;
import com.parse.ParseException;
import com.parse.ParseGeoPoint;
import com.parse.ParseObject;
import com.parse.ParseQuery;

import java.util.ArrayList;
import java.util.List;

public class MainFragment extends Fragment implements OnMapReadyCallback, LocationListener, SeekBar.OnSeekBarChangeListener{

    private MapView mapView;
    private SeekBar radiusSeeker;
    private TextView radiusText;
    private List<Marker> markers = new ArrayList<Marker>();

    private int locationChangedCounter = 0;
    private Location lastLocation;
    private LocationManager locationManager;
    private GoogleMap map;
    private Circle radiusCircle;
    private final int maxRadius = 10; // 10KM
    private final int defaultRadius = 2; // 2KM
    private double currentRadius = (double) defaultRadius;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        mapView = (MapView) rootView.findViewById(R.id.map);
        radiusSeeker = (SeekBar) rootView.findViewById(R.id.radiusSeeker);
        radiusSeeker.setProgress((int) ((defaultRadius * 100.0f) / maxRadius));
        radiusSeeker.setOnSeekBarChangeListener(this);

        radiusText = (TextView) rootView.findViewById(R.id.radiusText);
        radiusText.setText(String.format("%.1f KM", currentRadius));

        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mapView.getMapAsync(this);
        mapView.onCreate(savedInstanceState);
        mapView.onResume();
        MapsInitializer.initialize(getActivity().getApplicationContext());
    }

    @Override
    public void onMapReady(GoogleMap map) {
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        map.setMyLocationEnabled(true);
        map.getUiSettings().setMyLocationButtonEnabled(true);
        this.map = map;

        locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, this);
    }

    @Override
    public void onResume() {
        mapView.onResume();
        super.onResume();
    }

    @Override
    public void onPause() {
        mapView.onPause();
        if(locationManager != null){
            locationManager.removeUpdates(this);
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mapView.onDestroy();
        if(locationManager != null){
            locationManager.removeUpdates(this);
        }
        super.onDestroy();
    }

    /* --------------------------- OnLocationChange -------------------------*/

    @Override
    public void onLocationChanged(Location location) {
        lastLocation = location;

        // on first location update, move camera to that position.
        if(locationChangedCounter == 0) {
            LatLng centerPos = new LatLng(location.getLatitude(), location.getLongitude());
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(centerPos, 13));
        }
        locationChangedCounter++;

        LatLng newPos = new LatLng(location.getLatitude(), location.getLongitude());
        if(radiusCircle == null){
            int baseColor = Color.BLUE;
            radiusCircle = map.addCircle(
                    new CircleOptions()
                            .center(newPos)
                            .radius(defaultRadius * 1000) //2 x 1000 in meters.
                            .strokeColor(Color.BLUE).strokeWidth(1)
                            .fillColor(Color.argb(10, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)))
            );
        }else{
            radiusCircle.setCenter(newPos);
        }

        findPetrolsNearby();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}


    private void findPetrolsNearby(){
        ParseGeoPoint currentPoint = new ParseGeoPoint(lastLocation.getLatitude(), lastLocation.getLongitude());

        ParseQuery<ParseObject> query = ParseQuery.getQuery("GasStation");
        query.whereWithinKilometers("point", currentPoint, currentRadius);

        query.findInBackground(new FindCallback<ParseObject>() {
            @Override
            public void done(List<ParseObject> locations, ParseException e) {
                if (e == null) {

                    //Clean markers
                    for (Marker m : markers) {
                        m.remove();
                    }
                    markers.clear();

                    // add new markers
                    for (ParseObject loc : locations) {
                        LatLng point = new LatLng(loc.getParseGeoPoint("point").getLatitude(), loc.getParseGeoPoint("point").getLongitude());
                        Marker marker = map.addMarker(new MarkerOptions().position(point));
                        markers.add(marker);
                    }
                }
            }
        });
    }

    /* ------------------------ OnSeekBarChange -------------------*/

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if(radiusCircle != null){
            double newRadiusScale = (double) ((progress * maxRadius) / 100.0f);
            radiusCircle.setRadius(newRadiusScale * 1000);
            currentRadius = newRadiusScale;
            radiusText.setText(String.format("%.1f KM", currentRadius));
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // Only fetch if radius larger than 0 km.
        if(radiusCircle != null && currentRadius > 0) {
            findPetrolsNearby();
        }
    }
}
