/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.adapters;

import java.util.ArrayList;


import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

import com.bcomesafe.app.Constants;
import com.bcomesafe.app.R;
import com.bcomesafe.app.objects.ChatMessageObject;
import com.bcomesafe.app.utils.FontsUtils;

public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder> {

    // Debugging
    private static final boolean D = false;
    private static final String TAG = RecyclerViewAdapter.class.getSimpleName();

    // Data array
    private final ArrayList<ChatMessageObject> mData;

    // Client
    private static RecyclerViewAdapterClient mCaller;

    public interface RecyclerViewAdapterClient {
        void onGotItItemClick(int position);

        void onGPSOffItemClick(int position);
    }

    @Override
    public int getItemViewType(int position) {
        int messageType = mData.get(position).getMessageType();
        if (messageType == Constants.MESSAGE_TYPE_GPS_OFF) {
            return 4; // GPS OFF
        } else if (messageType == Constants.MESSAGE_TYPE_PUSH) {
            return 3; // GCM
        } else if (messageType == Constants.MESSAGE_TYPE_APP) {
            return 2; // App
        } else if (messageType == Constants.MESSAGE_TYPE_OWN) {
            return 1; // User
        } else {
            return 0; // Operator
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView tvMessage;
        public Button bGotIt;
        public Button bGPSOff;
        public TextView tvCrisisCenterTitle;

        public ViewHolder(View v) {
            super(v);
            tvMessage = (TextView) v.findViewById(R.id.row_message_text);
            tvMessage.setTypeface(FontsUtils.getInstance().getTfLight());
            try {
                bGotIt = (Button) v.findViewById(R.id.b_row_message_got_it);
                bGotIt.setTypeface(FontsUtils.getInstance().getTfBold());
                bGotIt.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mCaller != null) {
                            try {
                                mCaller.onGotItItemClick((Integer) v.getTag());
                            } catch (Exception e) {
                                // Nothing to do
                            }
                        }
                    }
                });
            } catch (Exception e) {
                // Nothing to do
            }
            try {
                tvCrisisCenterTitle = (TextView) v.findViewById(R.id.row_message_title);
                tvCrisisCenterTitle.setTypeface(FontsUtils.getInstance().getTfLight());
            } catch (Exception e) {
                // Nothing to do
            }
            try {
                bGPSOff = (Button) v.findViewById(R.id.b_row_message_gps_settings);
                bGPSOff.setTypeface(FontsUtils.getInstance().getTfBold());
                bGPSOff.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mCaller != null) {
                            try {
                                mCaller.onGPSOffItemClick((Integer) v.getTag());
                            } catch (Exception e) {
                                // Nothing to do
                            }
                        }
                    }
                });
            } catch (Exception ignored) {
                // Nothing to do
            }
        }
    }

    public RecyclerViewAdapter(ArrayList<ChatMessageObject> dataSet, RecyclerViewAdapterClient caller) {
        mData = dataSet;
        mCaller = caller;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v;
        switch (viewType) {
            case 0: // Operator
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_message_operator, parent, false);
                break;
            case 1: // User
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_message_own, parent, false);
                break;
            case 2: // App
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_message_system, parent, false);
                break;
            case 3: // GCM
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_message_push, parent, false);
                break;
            case 4: // GPS OFF
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_message_gps_off, parent, false);
                break;
            default: // System
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_message_system, parent, false);
                break;
        }
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        log("onBindViewHolder() pos=" + position);
        holder.tvMessage.setText(mData.get(position).getMessageText());

        if (holder.bGotIt != null) {
            holder.bGotIt.setTag(position);
            if (mData.get(position).getGotIt()) {
                holder.bGotIt.setVisibility(View.GONE);
            } else {
                holder.bGotIt.setVisibility(View.VISIBLE);
            }
        }
        if (holder.bGPSOff != null) {
            holder.bGPSOff.setTag(position);
        }
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