
package com.jgh.androidssh;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.jgh.androidssh.dialogs.SshConnectFragmentDialog;
import com.jgh.androidssh.sshutils.ConnectionStatusListener;
import com.jgh.androidssh.sshutils.ExecTaskCallbackHandler;
import com.jgh.androidssh.sshutils.FileProgressDialog;
import com.jgh.androidssh.sshutils.SessionController;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

/**
 * Main activity. Connect to SSH server and launch command shell.
 *
 */
public class MainActivity extends Activity implements OnClickListener {

    private static final String TAG = "MainActivity";
    private final int REQ_SCP = 1;
    private TextView mConnectStatus;
    private boolean isConnected = false;

    private SshEditText mCommandEdit;
    private Button mButton, mEndSessionBtn, mSftpButton, mScpButton;

    private Handler mHandler;
    private Handler mTvHandler;
    private String mLastLine;

    private SessionController mSessionControllerScp;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        // Set no title
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_main);
        mButton = (Button) findViewById(R.id.enterbutton);
        mEndSessionBtn = (Button) findViewById(R.id.endsessionbutton);
        mSftpButton = (Button) findViewById(R.id.sftpbutton);
        mScpButton = (Button) findViewById(R.id.scpbutton);
        mCommandEdit = (SshEditText) findViewById(R.id.command);
        mConnectStatus = (TextView) findViewById(R.id.connectstatus);
        // set onclicklistener
        mButton.setOnClickListener(this);
        mEndSessionBtn.setOnClickListener(this);
        mSftpButton.setOnClickListener(this);
        mScpButton.setOnClickListener(this);

        mConnectStatus.setText("Connect Status: NOT CONNECTED");
        //handlers
        mHandler = new Handler();
        mTvHandler = new Handler();

        //text change listener, for getting the current input changes.
        mCommandEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String[] sr = editable.toString().split("\r\n");
                String s = sr[sr.length - 1];
                mLastLine = s;

            }
        });


        mCommandEdit.setOnEditorActionListener(
                new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        //Log.d(TAG, "editor action " + event);
                        if (isEditTextEmpty(mCommandEdit)) {
                            return false;
                        }

                        // run command
                        else {
                            if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) {
                                return false;
                            }
                            // get the last line of terminal
                            String command = getLastLine();
                            ExecTaskCallbackHandler t = new ExecTaskCallbackHandler() {
                                @Override
                                public void onFail() {
                                    makeToast(R.string.taskfail);
                                }

                                @Override
                                public void onComplete(String completeString) {
                                }
                            };
                            mCommandEdit.AddLastInput(command);
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    SessionController.getSessionController().executeCommand(mHandler, mCommandEdit, t, command);
                                }
                            }).start();
                            return false;
                        }
                    }
                }
        );
    }


    /**
     * Displays toast to user.
     *
     * @param text
     */

    private void makeToast(int text) {
        Toast.makeText(this, getResources().getString(text), Toast.LENGTH_SHORT).show();
    }

    /**
     * Start activity to do SFTP transfer. User will choose from list of files
     * to transfer.
     */
    private void startSftpActivity() {
        Intent intent = new Intent(this, FileListActivity.class);
        String[] info = {
                SessionController.getSessionController().getSessionUserInfo().getUser(),
                SessionController.getSessionController().getSessionUserInfo().getHost(),
                SessionController.getSessionController().getSessionUserInfo().getPassword()
        };

        intent.putExtra("UserInfo", info);

        startActivity(intent);
    }

    /**
     * @return
     */
    private String getLastLine() {
        int index = mCommandEdit.getText().toString().lastIndexOf("\n");
        if (index == -1) {
            return mCommandEdit.getText().toString().trim();
        }
        if(mLastLine == null){
            Toast.makeText(this, "no text to process", Toast.LENGTH_LONG).show();
            return "";
        }
        String[] lines = mLastLine.split(Pattern.quote(mCommandEdit.getPrompt()));
        String lastLine = mLastLine.replace(mCommandEdit.getPrompt().trim(), "");
        Log.d(TAG, "command is " + lastLine + ", prompt is  " + mCommandEdit.getPrompt());
        return lastLine.trim();
    }

    private String getSecondLastLine() {

        String[] lines = mCommandEdit.getText().toString().split("\n");
        if (lines == null || lines.length < 2) return mCommandEdit.getText().toString().trim();

        else {
            int len = lines.length;
            String ln = lines[len - 2];
            return ln.trim();
        }
    }

    /**
     * Checks if the EditText is empty.
     *
     * @param editText
     * @return true if empty
     */
    private boolean isEditTextEmpty(EditText editText) {
        if (editText.getText() == null || editText.getText().toString().equalsIgnoreCase("")) {
            return true;
        }
        return false;
    }

    public void onClick(View v) {
        if (v == mButton) {
           showDialog();

        } else if (v == mSftpButton) {
            if (SessionController.isConnected()) {

                startSftpActivity();

            }
        } else if (v == mScpButton) {
            if (SessionController.isConnected()) {
                getScpFis(REQ_SCP);
            }
        } else if (v == this.mEndSessionBtn) {
            try {
                if (SessionController.isConnected()) {
                    SessionController.getSessionController().disconnect();
                }
            } catch (Throwable t) { //catch everything!
                Log.e(TAG, "Disconnect exception " + t.getMessage());
            }

        }

    }

    void showDialog() {

        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag("dialog");

        ft.addToBackStack(null);

        // Create and show the dialog.
        SshConnectFragmentDialog newFragment = SshConnectFragmentDialog.newInstance();
        newFragment.setListener(new ConnectionStatusListener() {
            @Override
            public void onDisconnected() {
                isConnected = false;
                mTvHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mConnectStatus.setText("Connection Status: NOT CONNECTED");
                    }
                });
            }

            @Override
            public void onConnected() {
                if (!isConnected) {
                    isConnected = true;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            SessionController.getSessionController().openShell(mHandler, mCommandEdit);
                        }
                    }).start();
                }
                isConnected = true;
                mTvHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mConnectStatus.setText("Connection Status: CONNECTED");
                    }
                });
            }
        });

        newFragment.show(ft, "dialog");
    }

    private String copyOtaFileToCache(InputStream inputStream) {
        File file = new File(getExternalCacheDir(), "ScpTmpFile");
        if (file.exists()) {
            file.delete();
        }
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int count;
            while ((count = inputStream.read(buf)) > 0) {
                fileOutputStream.write(buf, 0, count);
            }
            fileOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return file.getAbsolutePath();
    }

    private void getScpFis(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, requestCode);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SCP && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            String fileName = "ScpTmpFile";
            if (uri.toString().startsWith("file:")) {
                fileName = uri.getPath();
            } else { // uri.startsWith("content:")
                Cursor c = getContentResolver().query(uri, null, null, null, null);
                if (c != null && c.moveToFirst()) {
                    int id = c.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME);
                    if (id != -1) {
                        fileName = c.getString(id);
                    }
                }
            }
            try {
                InputStream fis = getContentResolver().openInputStream(uri);
                if (fis != null) {
                    String finalFileName = fileName;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            String path = copyOtaFileToCache(fis);
                            if (SessionController.isConnected()) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        FileProgressDialog progressDialog = new FileProgressDialog(MainActivity.this, 0);
                                        progressDialog.setIndeterminate(false);
                                        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                                        progressDialog.show();

                                        mSessionControllerScp = SessionController.getSessionController();
                                        // Always upload file to /tmp directory
                                        mSessionControllerScp.scpUploadFile(path, "/tmp/" + finalFileName, progressDialog);
                                    }
                                });
                            }
                        }
                    }).start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
