package com.bcomesafe.app.objects;

import com.bcomesafe.app.Constants;
import com.google.gson.annotations.SerializedName;

public class BCSObject {

    @SerializedName(Constants.REQUEST_PARAM_BCS_ID)
    private String mBCSId;

    @SerializedName(Constants.REQUEST_PARAM_BCS_NAME)
    private String mBCSName;

    @SerializedName(Constants.REQUEST_PARAM_BCS_URL)
    private String mBCSUrl;

    @SerializedName(Constants.REQUEST_PARAM_POLICE_NUMBER)
    private String mPoliceNumber;

    @SerializedName(Constants.REQUEST_PARAM_DEBUG)
    private Boolean mDebug;

    public String getBCSId() {
        return mBCSId;
    }

    public void setBCSId(String BCSId) {
        this.mBCSId = BCSId;
    }

    public String getBCSName() {
        return mBCSName;
    }

    public void setBCSName(String BCSName) {
        this.mBCSName = BCSName;
    }

    public String getBCSUrl() {
        return mBCSUrl;
    }

    public void setBCSUrl(String BCSUrl) {
        this.mBCSUrl = BCSUrl;
    }

    public String getPoliceNumber() {
        return mPoliceNumber;
    }

    public void setPoliceNumber(String policeNumber) {
        this.mPoliceNumber = policeNumber;
    }

    public boolean getDebug() {
        if (mDebug == null) {
            return false;
        }
        return mDebug;
    }

    public void setDebug(Boolean debug) {
        this.mDebug = debug;
    }
}
