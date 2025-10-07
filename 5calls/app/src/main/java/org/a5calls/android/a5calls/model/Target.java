package org.a5calls.android.a5calls.model;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.gson.annotations.SerializedName;

/**
 * Represents a corporate target for email campaigns.
 */
public class Target implements Parcelable {
    public String id;
    public String name;
    public String email;
    public String phone;
    public String department;
    @SerializedName("title")
    public String jobTitle;

    public Target() {
    }

    protected Target(Parcel in) {
        id = in.readString();
        name = in.readString();
        email = in.readString();
        phone = in.readString();
        department = in.readString();
        jobTitle = in.readString();
    }

    public static final Creator<Target> CREATOR = new Creator<Target>() {
        @Override
        public Target createFromParcel(Parcel in) {
            return new Target(in);
        }

        @Override
        public Target[] newArray(int size) {
            return new Target[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeString(email);
        dest.writeString(phone);
        dest.writeString(department);
        dest.writeString(jobTitle);
    }
}
