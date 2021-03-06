/*
 * Copyright (c) Microsoft. All rights reserved. Licensed under the MIT license.
 * See LICENSE in the project root for license information.
 */
package com.microsoft.office365.meetingfeedback;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.microsoft.office365.meetingfeedback.inject.ActivityModule;
import com.microsoft.office365.meetingfeedback.model.DataStore;
import com.microsoft.office365.meetingfeedback.model.authentication.AuthenticationManager;
import com.microsoft.office365.meetingfeedback.model.meeting.RatingData;
import com.microsoft.office365.meetingfeedback.model.outlook.CalendarService;
import com.microsoft.office365.meetingfeedback.model.outlook.EmailService;
import com.microsoft.office365.meetingfeedback.model.outlook.payload.Event;
import com.microsoft.office365.meetingfeedback.model.webservice.RatingServiceManager;
import com.microsoft.office365.meetingfeedback.util.ConnectivityUtil;
import com.microsoft.office365.meetingfeedback.util.DialogUtil;
import com.microsoft.office365.meetingfeedback.view.RateMyMeetingsDialogFragment;

import javax.inject.Inject;

import dagger.ObjectGraph;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public abstract class BaseActivity extends AppCompatActivity {

    private static final String TAG = "BaseActivity";

    private ObjectGraph mActivityGraph;

    @Inject
    AuthenticationManager mAuthenticationManager;
    @Inject
    CalendarService mCalendarService;
    @Inject
    EmailService mEmailService;
    @Inject
    RatingServiceManager mRatingServiceManager;
    @Inject
    public DataStore mDataStore;
    @Inject
    DialogUtil mDialogUtil;
    @Inject
    ConnectivityUtil mConnectivityUtil;

    //common dialog fragment for messaging
    private RateMyMeetingsDialogFragment mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MeetingFeedbackApplication application = (MeetingFeedbackApplication) getApplication();
        mActivityGraph = application.getApplicationGraph().plus(new ActivityModule(this));
        mActivityGraph.inject(this);
        //ensure a network connection exists
        validateNetworkConnection();
    }

    private void validateNetworkConnection() {
        if (!mConnectivityUtil.hasNetworkConnection()) {
            finish();
            startActivity(new Intent(this, NoNetworkActivity.class));
        }
    }

    public void setDialog(RateMyMeetingsDialogFragment progressDialog) {
        mProgressDialog = progressDialog;
    }

    public RateMyMeetingsDialogFragment getDialog() {
        return mProgressDialog;
    }

    public void sendRating(Event event, RatingData ratingData, Runnable postRatingTasks) {
        mDialogUtil.showProgressDialog(
                this,
                getString(R.string.submit_rating),
                getString(R.string.submitting_rating_description)
        );

        mEmailService.sendRatingMail(
                event,
                ratingData
        );

        mRatingServiceManager.addRating(
                event.mOrganizer.emailAddress.mAddress,
                ratingData,
                dismissDialogCallback(
                        "Rating Sent!",
                        getString(R.string.failure_title),
                        getString(R.string.send_rating_failed_exception),
                        postRatingTasks
                )
        );
    }

    protected Callback<Void> dismissDialogCallback(
            final String toastMessage,
            final String alertDialogTitle,
            final String alertDialogMessage,
            final Runnable action) {
        return new Callback<Void>() {
            @Override
            public void success(Void aVoid, Response response) {
                //update the webservice with the ratingEvent rating
                mDialogUtil.dismissDialog(BaseActivity.this);
                Log.d(TAG, "DismissDialogCallback Success");
                if(null != toastMessage) {
                    Toast.makeText(BaseActivity.this, toastMessage, Toast.LENGTH_SHORT).show();
                }
                if (null != action){
                    action.run();
                }
            }

            @Override
            public void failure(RetrofitError error) {
                Log.e(TAG, "DismissDialogCallback Failure");
                mDialogUtil.dismissDialog(BaseActivity.this);
                mDialogUtil.showAlertDialog(BaseActivity.this, alertDialogTitle, alertDialogMessage);
                if (null != action){
                    action.run();
                }
            }
        };
    }
}
