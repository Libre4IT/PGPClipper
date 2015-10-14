package moe.minori.pgpclipper.activities;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.openintents.openpgp.OpenPgpDecryptionResult;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import moe.minori.pgpclipper.R;

/**
 * Created by Minori on 2015-09-26.
 */
public class PGPClipperResultShowActivity extends Activity {

    OpenPgpServiceConnection serviceConnection;

    public static final int REQUEST_CODE_DECRYPT_AND_VERIFY = 9913;

    public static final String DATA = "DATA";
    public ArrayList<String> KEY_ID = null;

    private Intent intent;

    TextView sigStatus;
    TextView decStatus;
    EditText decResult;
    TextView fastReplyIndc;

    boolean isReplyable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String currentTheme = sharedPreferences.getString("themeSelection", "dark");

        switch (currentTheme)
        {
            case "dark":
                setTheme(R.style.PseudoDialogDarkTheme);
                break;
            case "light":
                setTheme(R.style.PseudoDialogLightTheme);
                break;
        }

        super.onCreate(savedInstanceState);

        intent = getIntent();

        setContentView(R.layout.resultactivitylayout);

        sigStatus = (TextView) findViewById(R.id.sigStatusTitle);
        decStatus = (TextView) findViewById(R.id.decryptionStatusTitle);
        decResult = (EditText) findViewById(R.id.decryptionResultText);
        fastReplyIndc = (TextView) findViewById(R.id.fastReplyIndicator);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        String currentPgpProvider = preferences.getString("pgpServiceProviderApp", null);

        if ( currentPgpProvider == null || "".equals(currentPgpProvider))
        {
            // Default security provider is not set
            Log.e("PGPClipperService", "Security provider is not set!");
        }
        else
        {
            Log.d("PGPClipperService", "Current security provider: " + currentPgpProvider );

            serviceConnection = new OpenPgpServiceConnection(this, currentPgpProvider);
            serviceConnection.bindToService();

            tryDecryption();
        }
    }

    private void tryDecryption ()
    {
        if(serviceConnection != null) {
            if (serviceConnection.isBound()) {
                if (intent != null) {
                    attemptPgpApiAccess(intent.getStringExtra(DATA));
                }
            } else {
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        tryDecryption();
                    }
                }, 500);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if ( serviceConnection != null )
            serviceConnection.unbindFromService();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ( resultCode == RESULT_OK )
        {
            switch (requestCode)
            {
                case 5298:
                {
                    tryDecryption();
                }
            }
        }
    }


    private void attemptPgpApiAccess (String input)
    {
        // PGP data (possibly) detected, try using OpenPGP API

        Intent data = new Intent();
        data.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);

        InputStream is;
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        try
        {
            is = new ByteArrayInputStream(input.getBytes("UTF-8"));
        }
        catch (UnsupportedEncodingException e)
        {
            e.printStackTrace();
            return;
        }

        OpenPgpApi api = new OpenPgpApi(this, serviceConnection.getService());
        api.executeApiAsync(data, is, os, new CallBack(os, REQUEST_CODE_DECRYPT_AND_VERIFY));
    }

    public void onClick (View v) {
        if (isReplyable)
        {
            // start quick reply activity
            Intent intent = new Intent(this, PGPClipperQuickReplyActivity.class);
            if (KEY_ID != null) {
                intent.putExtra("KEY_ID", convertToStringArray(KEY_ID));
            }

            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
        }
        else
        {
            //try again
            tryDecryption();
        }
    }


    private String[] convertToStringArray (ArrayList<String> input)
    {
        String[] toReturn = new String[input.size()];

        for (int i=0; i<input.size(); i++)
        {
            int startIdx = input.get(i).lastIndexOf("<");
            int endIdx = input.get(i).lastIndexOf(">");

            toReturn[i] = input.get(i).substring(startIdx+1, endIdx);
        }

        return toReturn;
    }

    private class CallBack implements OpenPgpApi.IOpenPgpCallback
    {

        ByteArrayOutputStream os;
        int requestCode;

        private CallBack(ByteArrayOutputStream os, int requestCode)
        {
            this.os = os;
            this.requestCode = requestCode;
        }

        @Override
        public void onReturn(Intent result) {
            switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR) )
            {
                case OpenPgpApi.RESULT_CODE_SUCCESS:
                {
                    try
                    {
                        //TODO: Use this somewhere!
                        String finalResult = os.toString("UTF-8");
                        OpenPgpSignatureResult signatureResult = result.getParcelableExtra(OpenPgpApi.RESULT_SIGNATURE);
                        OpenPgpDecryptionResult decryptionResult = result.getParcelableExtra(OpenPgpApi.RESULT_DECRYPTION);

                        if ( signatureResult.getResult() == 1 )
                        {
                            sigStatus.setText(getString(R.string.signatureStatusText) + "O \n(" + signatureResult.getPrimaryUserId() + ")");
                            KEY_ID = signatureResult.getUserIds();
                        }
                        else
                        {
                            sigStatus.setText(getString(R.string.signatureStatusText) + "X");
                        }


                        if ( decryptionResult.getResult() == 1)
                        {
                            decStatus.setText(getString(R.string.decryptionStatusText) + "O");
                        }
                        else
                        {
                            decStatus.setText(getString(R.string.decryptionStatusText) + "X");
                        }

                        if ( finalResult != null ) {
                            decResult.setText(finalResult);
                            fastReplyIndc.setText(R.string.fastReplyText);
                            isReplyable = true;
                        }else
                            decResult.setText(R.string.errorCannotProcess);
                    }
                    catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }

                    break;
                }
                case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                {
                    PendingIntent pi = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);

                    try
                    {
                        PGPClipperResultShowActivity.this.startIntentSenderFromChild(PGPClipperResultShowActivity.this, pi.getIntentSender(), 5298, null, 0, 0, 0);
                    }
                    catch (IntentSender.SendIntentException e)
                    {
                        e.printStackTrace();
                    }
                    break;
                }
                case OpenPgpApi.RESULT_CODE_ERROR:
                {
                    //TODO: Show user error dialog

                    sigStatus.setText(getString(R.string.signatureStatusText) + "X");
                    decStatus.setText(getString(R.string.decryptionStatusText) + "X");
                    decResult.setText(R.string.errorCannotProcess);

                    break;
                }
            }
        }
    }
}
