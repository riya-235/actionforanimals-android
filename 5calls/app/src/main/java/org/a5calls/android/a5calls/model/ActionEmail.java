package org.a5calls.android.a5calls.model;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.gson.annotations.SerializedName;

/**
 * Represents the email action configuration within the actions object.
 */
public class ActionEmail implements Parcelable {
    public boolean enabled;
    public String subject;
    public String template;
    @SerializedName("distribution_method")
    public String distributionMethod;

    public ActionEmail() {
    }

    protected ActionEmail(Parcel in) {
        enabled = in.readByte() != 0;
        subject = in.readString();
        template = in.readString();
        distributionMethod = in.readString();
    }

    public static final Creator<ActionEmail> CREATOR = new Creator<ActionEmail>() {
        @Override
        public ActionEmail createFromParcel(Parcel in) {
            return new ActionEmail(in);
        }

        @Override
        public ActionEmail[] newArray(int size) {
            return new ActionEmail[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (enabled ? 1 : 0));
        dest.writeString(subject);
        dest.writeString(template);
        dest.writeString(distributionMethod);
    }
}