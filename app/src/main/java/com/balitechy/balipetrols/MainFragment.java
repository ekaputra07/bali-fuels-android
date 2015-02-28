/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Eka Putra - ekaputra@balitechy.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
import android.widget.LinearLayout;
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
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.parse.FindCallback;
import com.parse.ParseException;
import com.parse.ParseGeoPoint;
import com.parse.ParseObject;
import com.parse.ParseQuery;

import java.util.ArrayList;
import java.util.List;

public class MainFragment extends Fragment implements OnMapReadyCallback, LocationListener, SeekBar.OnSeekBarChangeListener {

    private final int maxRadius = 10; // 10KM
    private final int defaultRadius = 2; // 2KM
    private double currentRadius = (double) defaultRadius;
    private MapView mapView;
    private SeekBar radiusSeeker;
    private LinearLayout seekbarFrame;
    private TextView radiusText;
    private List<Marker> markers = new ArrayList<Marker>();
    private Location lastLocation;
    private LocationManager locationManager;
    private GoogleMap map;
    private LatLng currentPos;
    private Circle radiusCircle;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        mapView = (MapView) rootView.findViewById(R.id.map);

        seekbarFrame = (LinearLayout) rootView.findViewById(R.id.seekbarFrame);
        seekbarFrame.setVisibility(View.GONE);

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
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mapView.onDestroy();
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
        super.onDestroy();
    }

    /* --------------------------- OnLocationChange -------------------------*/

    @Override
    public void onLocationChanged(Location location) {
        lastLocation = location;

        currentPos = new LatLng(location.getLatitude(), location.getLongitude());
        if (radiusCircle == null) {
            int baseColor = Color.BLUE;
            radiusCircle = map.addCircle(
                    new CircleOptions()
                            .center(currentPos)
                            .radius(defaultRadius * 1000) //2 x 1000 in meters.
                            .strokeColor(Color.BLUE).strokeWidth(1)
                            .fillColor(Color.argb(10, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)))
            );

            LatLngBounds bounds = calculateBoundsWithCenter(currentPos);
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 50));

            seekbarFrame.setVisibility(View.VISIBLE);

        } else {
            radiusCircle.setCenter(currentPos);
        }

        findPetrolsNearby();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }


    private void findPetrolsNearby() {
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


    /*
    * Helper method to calculate the offset for the bounds used in map zooming
    * Source: https://github.com/ParsePlatform/AnyWall/blob/master/AnyWall-android/Anywall/src/com/parse/anywall/MainActivity.java
    */
    private double calculateLatLngOffset(LatLng myLatLng, boolean bLatOffset) {
        // The return offset, initialized to the default difference
        double latLngOffset = 1.0;
        // Set up the desired offset distance in meters
        float desiredOffsetInMeters = (float) currentRadius * 1000;
        // Variables for the distance calculation
        float[] distance = new float[1];
        boolean foundMax = false;
        double foundMinDiff = 0;
        // Loop through and get the offset
        do {
            // Calculate the distance between the point of interest
            // and the current offset in the latitude or longitude direction
            if (bLatOffset) {
                Location.distanceBetween(myLatLng.latitude, myLatLng.longitude, myLatLng.latitude
                        + latLngOffset, myLatLng.longitude, distance);
            } else {
                Location.distanceBetween(myLatLng.latitude, myLatLng.longitude, myLatLng.latitude,
                        myLatLng.longitude + latLngOffset, distance);
            }

            // Compare the current difference with the desired one
            float distanceDiff = distance[0] - desiredOffsetInMeters;
            if (distanceDiff < 0) {
                // Need to catch up to the desired distance
                if (!foundMax) {
                    foundMinDiff = latLngOffset;
                    // Increase the calculated offset
                    latLngOffset *= 2;
                } else {
                    double tmp = latLngOffset;
                    // Increase the calculated offset, at a slower pace
                    latLngOffset += (latLngOffset - foundMinDiff) / 2;
                    foundMinDiff = tmp;
                }
            } else {
                // Overshot the desired distance
                // Decrease the calculated offset
                latLngOffset -= (latLngOffset - foundMinDiff) / 2;
                foundMax = true;
            }
        } while (Math.abs(distance[0] - desiredOffsetInMeters) > 0.01f);
        return latLngOffset;
    }

    /*
    * Helper method to calculate the bounds for map zooming
    */
    LatLngBounds calculateBoundsWithCenter(LatLng myLatLng) {
        // Create a bounds
        LatLngBounds.Builder builder = LatLngBounds.builder();
        // Calculate east/west points that should to be included
        // in the bounds
        double lngDifference = calculateLatLngOffset(myLatLng, false);
        LatLng east = new LatLng(myLatLng.latitude, myLatLng.longitude + lngDifference);
        builder.include(east);
        LatLng west = new LatLng(myLatLng.latitude, myLatLng.longitude - lngDifference);
        builder.include(west);
        // Calculate north/south points that should to be included
        // in the bounds
        double latDifference = calculateLatLngOffset(myLatLng, true);
        LatLng north = new LatLng(myLatLng.latitude + latDifference, myLatLng.longitude);
        builder.include(north);
        LatLng south = new LatLng(myLatLng.latitude - latDifference, myLatLng.longitude);
        builder.include(south);
        return builder.build();
    }


    /* ------------------------ OnSeekBarChange -------------------*/

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (radiusCircle != null) {
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
        if (radiusCircle != null && currentRadius > 0) {
            findPetrolsNearby();

            LatLngBounds bounds = calculateBoundsWithCenter(currentPos);
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 50));
        }
    }
}
