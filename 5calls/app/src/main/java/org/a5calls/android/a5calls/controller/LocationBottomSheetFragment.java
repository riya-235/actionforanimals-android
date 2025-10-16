package org.a5calls.android.a5calls.controller;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.a5calls.android.a5calls.R;
import org.a5calls.android.a5calls.model.AccountManager;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class LocationBottomSheetFragment extends BottomSheetDialogFragment {
    
    private static final int LOCATION_PERMISSION_REQUEST = 1;
    
    public interface LocationSetListener {
        void onLocationSet(String location);
    }
    
    private LocationSetListener listener;
    private EditText locationInput;
    private ImageView submitButton;
    private TextView detectLocationButton;
    private TextView locationError;
    private TextView locationAccuracyWarning;
    private LocationListener mLocationListener;
    
    public static LocationBottomSheetFragment newInstance() {
        return new LocationBottomSheetFragment();
    }
    
    public void setLocationSetListener(LocationSetListener listener) {
        this.listener = listener;
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.location_bottom_sheet, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        locationInput = view.findViewById(R.id.location_input);
        submitButton = view.findViewById(R.id.submit_location_button);
        detectLocationButton = view.findViewById(R.id.detect_location_button);
        locationError = view.findViewById(R.id.location_error);
        locationAccuracyWarning = view.findViewById(R.id.location_accuracy_warning);

        // Show iOS-style warning if location accuracy is low
        showWarningIfLowAccuracy();
        
        submitButton.setOnClickListener(v -> {
            String location = locationInput.getText().toString().trim();
            if (!TextUtils.isEmpty(location)) {
                // Follow old LocationActivity exactly: just validate and save
                onSubmitAddress(location);
            } else {
                Toast.makeText(getContext(), "Please enter a location", Toast.LENGTH_SHORT).show();
            }
        });
        
        detectLocationButton.setOnClickListener(v -> {
            tryGettingLocation();
        });
        
        // Auto focus the input field
        locationInput.requestFocus();
    }

    private void showWarningIfLowAccuracy() {
        if (getContext() != null) {
            boolean isLowAccuracy = AccountManager.Instance.getLocationLowAccuracy(getContext());
            String message = AccountManager.Instance.getLocationLowAccuracyMessage(getContext());

            if (isLowAccuracy && !TextUtils.isEmpty(message)) {
                // Use server message if available
                locationAccuracyWarning.setText("⚠️ " + message);
                locationAccuracyWarning.setVisibility(View.VISIBLE);
            } else if (isLowAccuracy) {
                // Use iOS fallback message
                locationAccuracyWarning.setText("⚠️ Showing state/federal officials only. For local officials, please use \"Detect My Location\" or provide a specific street address.");
                locationAccuracyWarning.setVisibility(View.VISIBLE);
            } else {
                locationAccuracyWarning.setVisibility(View.GONE);
            }
        }
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK) {
            // Location was set via old location activity, close the bottom sheet
            dismiss();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == LOCATION_PERMISSION_REQUEST && grantResults.length > 0 && (
                grantResults[0] == PackageManager.PERMISSION_GRANTED || (grantResults.length > 1 &&
                        grantResults[1] == PackageManager.PERMISSION_GRANTED))) {
            // Try getting the location
            detectLocationButton.post(() -> tryGettingLocation());
        }
    }
    
    private void tryGettingLocation() {
        if (getActivity() == null) return;
        
        if (ActivityCompat.checkSelfPermission(getActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getActivity(),
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }

        LocationManager locationManager = (LocationManager) getActivity().getSystemService(Activity.LOCATION_SERVICE);
        if (locationManager == null) return;
        
        String provider = locationManager.getBestProvider(new Criteria(), false);
        if (provider == null) {
            Toast.makeText(getContext(), "Location services not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Location location = locationManager.getLastKnownLocation(provider);

        if (location == null) {
            // Show loading state
            detectLocationButton.setText("Detecting...");
            detectLocationButton.setEnabled(false);
            detectLocationButton.setCompoundDrawables(null, null, null, null); // Remove icon while loading
            
            mLocationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    onReceiveLocation(location);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {}

                @Override
                public void onProviderEnabled(String provider) {}

                @Override
                public void onProviderDisabled(String provider) {}
            };
            locationManager.requestLocationUpdates(provider, 10, 0, mLocationListener);

        } else {
            onReceiveLocation(location);
        }
    }

    private void onReceiveLocation(Location location) {
        if (getActivity() == null) return;
        
        // Clean up location listener
        if (mLocationListener != null) {
            LocationManager locationManager = (LocationManager) getActivity().getSystemService(Activity.LOCATION_SERVICE);
            if (locationManager != null) {
                locationManager.removeUpdates(mLocationListener);
            }
            mLocationListener = null;
        }
        
        // Reset button state
        detectLocationButton.setText("Detect My Location");
        detectLocationButton.setEnabled(true);
        // Restore the location icon
        detectLocationButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_location_on, 0);
        // Set icon tint to match the blue text color
        
        // Save GPS coordinates
        AccountManager.Instance.setLat(getActivity(), String.valueOf(location.getLatitude()));
        AccountManager.Instance.setLng(getActivity(), String.valueOf(location.getLongitude()));
        
        // Try to get city name from coordinates using reverse geocoding
        String cityName = "Current Location"; // Default fallback
        
        try {
            Geocoder geocoder = new Geocoder(getActivity(), Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(
                location.getLatitude(), location.getLongitude(), 1);
            
            if (addresses != null && addresses.size() > 0) {
                Address address = addresses.get(0);
                // Try to get city name in order of preference
                if (!TextUtils.isEmpty(address.getLocality())) {
                    cityName = address.getLocality(); // City name
                } else if (!TextUtils.isEmpty(address.getAdminArea())) {
                    cityName = address.getAdminArea(); // State/Province
                } else if (!TextUtils.isEmpty(address.getSubAdminArea())) {
                    cityName = address.getSubAdminArea(); // County
                } else if (!TextUtils.isEmpty(address.getPostalCode())) {
                    cityName = address.getPostalCode(); // ZIP code as last resort
                }
                
                // Set the geocoded address for API calls
                AccountManager.Instance.setAddress(getActivity(), 
                    address.getLocality() + ", " + address.getAdminArea());
            } else {
                // No geocoding result, clear address
                AccountManager.Instance.setAddress(getActivity(), null);
            }
        } catch (IOException e) {
            // Geocoding failed, use default
            AccountManager.Instance.setAddress(getActivity(), null);
        }
        
        // Notify listener with the city name (or fallback)
        if (listener != null) {
            listener.onLocationSet(cityName);
        }
        
        dismiss();
    }
    
    private void onSubmitAddress(String address) {
        address = address.trim();
        if (TextUtils.isEmpty(address)) {
            Toast.makeText(getContext(), "Please enter a location", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Same validation as old LocationActivity
        if (address.length() < 5 ||
                (address.length() == 5 && !TextUtils.isDigitsOnly(address))) {
            Toast.makeText(getContext(), "Please enter a valid address or zip code", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Exactly like old LocationActivity: save address, clear coordinates
        AccountManager.Instance.setAddress(getActivity(), address);
        AccountManager.Instance.setLat(getActivity(), null);
        AccountManager.Instance.setLng(getActivity(), null);

        // Clear any previous error
        locationError.setVisibility(View.GONE);
        
        // Return to main - server will do geocoding and return location name
        if (listener != null) {
            listener.onLocationSet(address); // Use original input, server will provide real location name
        }

        // Dismiss immediately like GPS location - let the caller handle validation
        dismiss();
    }
    
    public void onLocationValidationSuccess() {
        // Location was valid, dismiss the bottom sheet
        dismiss();
    }
    
    public void onLocationValidationError(String errorMessage) {
        // Show error and keep bottom sheet open
        locationError.setText(errorMessage);
        locationError.setVisibility(View.VISIBLE);
    }
    
    @Override
    public void onPause() {
        super.onPause();
        if (mLocationListener != null && getActivity() != null) {
            LocationManager locationManager = (LocationManager) getActivity().getSystemService(Activity.LOCATION_SERVICE);
            if (locationManager != null) {
                locationManager.removeUpdates(mLocationListener);
            }
        }
    }
}