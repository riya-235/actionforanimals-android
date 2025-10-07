package org.a5calls.android.a5calls.model;

import android.os.Parcel;
import android.os.Parcelable;

public class IssueStats implements Parcelable {
    public int total_actions;

    public IssueStats() {
        // Default constructor
    }

    protected IssueStats(Parcel in) {
        total_actions = in.readInt();
    }

    public static final Creator<IssueStats> CREATOR = new Creator<IssueStats>() {
        @Override
        public IssueStats createFromParcel(Parcel in) {
            return new IssueStats(in);
        }

        @Override
        public IssueStats[] newArray(int size) {
            return new IssueStats[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(total_actions);
    }
}
