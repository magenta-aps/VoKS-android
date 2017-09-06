/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.adapters;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.bcomesafe.app.AppUser;
import com.bcomesafe.app.R;
import com.bcomesafe.app.objects.BCSObject;
import com.bcomesafe.app.utils.FontsUtils;

import java.util.List;

public class BCSListAdapter extends RecyclerView.Adapter<BCSListAdapter.ViewHolder> {

    // Debugging
    private static final boolean D = false;
    private static final String TAG = BCSListAdapter.class.getSimpleName();

    // Data array
    private static List<BCSObject> mData;

    // Client
    private static BCSListAdapterClient mCaller;

    public interface BCSListAdapterClient {
        void onBCSSelected(int position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView tvBCSName;

        public ViewHolder(View v) {
            super(v);
            tvBCSName = (TextView) v.findViewById(R.id.tv_bcs_name);
            tvBCSName.setTypeface(FontsUtils.getInstance().getTfLight());

            tvBCSName.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mCaller != null) {
                        try {
                            mCaller.onBCSSelected((Integer) v.getTag());
                        } catch (Exception e) {
                            // Nothing to do
                        }
                    }
                }
            });
        }
    }

    public BCSListAdapter(List<BCSObject> dataSet, BCSListAdapterClient caller) {
        mData = dataSet;
        mCaller = caller;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_bcs, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        log("onBindViewHolder() pos=" + position);
        holder.tvBCSName.setText(mData.get(position).getBCSName());

        String selectedBCSId = AppUser.get().getBCSId();
        holder.tvBCSName.setTag(position);
        holder.tvBCSName.setSelected(selectedBCSId != null && mData.get(position).getBCSId() != null && mData.get(position).getBCSId().equals(selectedBCSId));
    }


    @Override
    public int getItemCount() {
        return mData.size();
    }

    private void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
        }
    }
}