package org.a5calls.android.a5calls.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Represents corporate information for company campaigns.
 */
public class CorporateInfo implements Parcelable {
    public String company;
    public String industry;
    public String ticker;
    public String headquarters;

    public CorporateInfo() {
    }

    protected CorporateInfo(Parcel in) {
        company = in.readString();
        industry = in.readString();
        ticker = in.readString();
        headquarters = in.readString();
    }

    public static final Creator<CorporateInfo> CREATOR = new Creator<CorporateInfo>() {
        @Override
        public CorporateInfo createFromParcel(Parcel in) {
            return new CorporateInfo(in);
        }

        @Override
        public CorporateInfo[] newArray(int size) {
            return new CorporateInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(company);
        dest.writeString(industry);
        dest.writeString(ticker);
        dest.writeString(headquarters);
    }
}
