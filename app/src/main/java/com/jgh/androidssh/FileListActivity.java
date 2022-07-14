package com.jgh.androidssh;

import android.app.Activity;
import android.app.ProgressDialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnDragListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import com.jgh.androidssh.adapters.FileListAdapter;
import com.jgh.androidssh.adapters.RemoteFileListAdapter;
import com.jgh.androidssh.sshutils.FileProgressDialog;
import com.jgh.androidssh.sshutils.SessionController;
import com.jgh.androidssh.sshutils.TaskCallbackHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;


/**
 * Activity to list files. Uploads chosen files to server (SFTP) using an
 * AsyncTask.
 *
 * @author Jonathan Hough
 * @since 7 Dec 2012
 */
public class FileListActivity extends Activity implements OnItemClickListener, OnClickListener, OnDragListener {

    private static final String TAG = "FileListActivity";
    private ArrayList<File> mFilenames = new ArrayList<File>();
    private GridView mLocalGridView;
    private GridView mRemoteGridView;
    private FileListAdapter mFileListAdapter;
    private RemoteFileListAdapter mRemoteFileListAdapter;
    private String[] mUserInfo;
    private File mRootFile;
    private Button mUpButton, mConnectButton;
    private TextView mStateView;
    private SessionController mSessionController;
    private RemoteClickListener mRemoteClickListener;
    private Vector<ChannelSftp.LsEntry> mRemoteFiles;
    private boolean mIsProcessing = false;

    @Override
    public boolean onDrag(View v, DragEvent event) {
        return true;
    }


    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        setContentView(R.layout.activity_filelistactivity);

        mUserInfo = getIntent().getExtras().getStringArray("UserInfo");
        mLocalGridView = (GridView) findViewById(R.id.listview);
        mRemoteGridView = (GridView) findViewById(R.id.remotelistview);
        // Get external storage
        mRootFile = Environment.getExternalStorageDirectory();
        // list files
        for (File f : mRootFile.listFiles()) {
            mFilenames.add(f);
        }

        mFileListAdapter = new FileListAdapter(this, mFilenames);

        mLocalGridView.setAdapter(mFileListAdapter);
        mLocalGridView.setOnItemClickListener(this);
        //----------------- buttons ---------------//
        mUpButton = (Button) findViewById(R.id.upbutton);
        mUpButton.setOnClickListener(this);
        mConnectButton = (Button) findViewById(R.id.connectbutton);
        mConnectButton.setOnClickListener(this);

        mStateView = (TextView) findViewById(R.id.statetextview);

        mSessionController = SessionController.getSessionController();
        mSessionController.connect();

        mRemoteClickListener = new RemoteClickListener();
        mRemoteGridView.setOnItemClickListener(mRemoteClickListener);


        if (mSessionController.getSession().isConnected()) {
            mStateView.setText("Connected");
            showRemoteFiles();

        } else {
            mStateView.setText("Disconnected");
        }

    }

    /**
     * Sets up the remote file list.
     * @param remoteFileListAdapter
     */
    public void setupRemoteFiles(RemoteFileListAdapter remoteFileListAdapter) {
        mRemoteFileListAdapter = remoteFileListAdapter;
        mRemoteGridView.setAdapter(mRemoteFileListAdapter);
        remoteFileListAdapter.notifyDataSetChanged();

    }

    /**
     * Sets the adapter with the given array list
     *
     * @param files
     */
    private void setAdapter(ArrayList<File> files) {
        mFileListAdapter = new FileListAdapter(this, files);
    }

    public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
        // change the list
        if (mFilenames.get(position).isDirectory()) {
            mRootFile = mFilenames.get(position);
            Log.d(TAG, "ROOT FILE POSIITON IS " + mRootFile);
            mFilenames.clear();
            if (mRootFile.listFiles() == null) {
                return;
            }
            for (File f : mRootFile.listFiles()) {
                mFilenames.add(f);
            }

            setAdapter(mFilenames);
            mLocalGridView.setAdapter(mFileListAdapter);
            mFileListAdapter.notifyDataSetChanged();

        } else {
            // sftp the file
            FileProgressDialog progressDialog = new FileProgressDialog(this, 0);
            progressDialog.setIndeterminate(false);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.show();

            File[] arr = {mFilenames.get(position)};
            String[] des = {mFilenames.get(position).getName()};
            mSessionController.uploadFiles(arr, des, progressDialog);
        }

    }

    // go back up to parent folder
    public void onClick(View v) {
        if (v == mUpButton) {

            boolean hasParent = mRootFile.getParentFile() != null;
            if (hasParent) {
                mRootFile = mRootFile.getParentFile();
                File[] mRootFileList = mRootFile.listFiles();
                if (mRootFileList == null) {
                    return;
                }

                mFilenames.clear();
                mFilenames.addAll(Arrays.asList(mRootFileList));

                setAdapter(mFilenames);
                mLocalGridView.setAdapter(mFileListAdapter);
                mFileListAdapter.notifyDataSetChanged();
            }

        }

    }


    /**
     * Shows the remote files on the listview.
     */
    private void showRemoteFiles() {
        final ProgressDialog progressDialog = new ProgressDialog(FileListActivity.this, 0);
        progressDialog.setIndeterminate(true);
        progressDialog.setTitle(R.string.retrieve_remote_files);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        try {
            mSessionController.listRemoteFiles(new TaskCallbackHandler() {
                @Override
                public void OnBegin() {
                    progressDialog.show();
                }

                @Override
                public void onFail() {
                    Log.e(TAG, "Fail listing remote files");
                    progressDialog.dismiss();
                }

                @Override
                public void onTaskFinished(Vector<ChannelSftp.LsEntry> lsEntries) {
                    mRemoteFileListAdapter = new RemoteFileListAdapter(FileListActivity.this, lsEntries);
                    mRemoteGridView.setAdapter(mRemoteFileListAdapter);
                    mRemoteFileListAdapter.notifyDataSetChanged();
                    progressDialog.dismiss();
                }
            }, "");
        } catch (JSchException j) {
            Log.e(TAG, "ShowRemoteFiles exception " + j.getMessage());
            progressDialog.dismiss();
        } catch (SftpException s) {
            Log.e(TAG, "ShowRemoteFiles exception " + s.getMessage());
            progressDialog.dismiss();
        }
    }


    /**
     * Listener class for remote file list click events. Handles file and directory clicks
     * from user.
     */
    private class RemoteClickListener implements OnItemClickListener {


        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
            if (mIsProcessing) {
                return;
            }
            if (mRemoteFileListAdapter == null) {
                return;
            }
            //Is directory?
            if (mRemoteFileListAdapter.getRemoteFiles().get(position).getAttrs().isDir()
                    || mRemoteFileListAdapter.getRemoteFiles().get(position).getFilename().trim() == "..") {

                final ProgressDialog progressDialog = new ProgressDialog(FileListActivity.this, 0);
                progressDialog.setIndeterminate(true);
                progressDialog.setTitle(R.string.retrieve_remote_files);
                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);

                try {
                    mIsProcessing = true;
                    mSessionController.listRemoteFiles(new TaskCallbackHandler() {


                        @Override
                        public void OnBegin() {
                            progressDialog.show();
                        }

                        @Override
                        public void onFail() {
                            mIsProcessing = false;
                            progressDialog.dismiss();
                        }

                        @Override
                        public void onTaskFinished(Vector<ChannelSftp.LsEntry> lsEntries) {
                            mRemoteFileListAdapter = new RemoteFileListAdapter(FileListActivity.this, lsEntries);
                            mRemoteGridView.setAdapter(mRemoteFileListAdapter);
                            mRemoteFileListAdapter.notifyDataSetChanged();
                            mIsProcessing = false;
                            progressDialog.dismiss();

                        }
                    }, mRemoteFileListAdapter.getRemoteFiles().get(position).getFilename());
                } catch (JSchException j) {
                    Log.e(TAG, "Error on remote file click " + j.getMessage());
                    progressDialog.dismiss();
                } catch (SftpException s) {
                    Log.e(TAG, "Error on remote file click " + s.getMessage());
                    progressDialog.dismiss();
                }

            } else {

                // sftp the file
                FileProgressDialog progressDialog = new FileProgressDialog(FileListActivity.this, 0);
                progressDialog.setIndeterminate(false);
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.show();

                try {
                    String name = mRemoteFileListAdapter.getRemoteFiles().get(position).getFilename();
                    String out = mRootFile.getAbsolutePath() + "/" + name;

                    mSessionController.downloadFile(mRemoteFileListAdapter.getRemoteFiles().get(position).getFilename(), out, progressDialog);
                } catch (JSchException je) {
                    Log.d(TAG, "JschException " + je.getMessage());
                } catch (SftpException se) {
                    Log.d(TAG, "SftpException " + se.getMessage());
                }
            }


        }
    }


    /**
     * Drag shadow, for item dragging/dropping.
     */
    private class DragShadow extends View.DragShadowBuilder {

        ColorDrawable mBox;

        public DragShadow(View view) {
            super(view);
            mBox = new ColorDrawable(Color.GRAY);
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            mBox.draw(canvas);
        }

        @Override
        public void onProvideShadowMetrics(Point shadowSize,
                                           Point shadowTouchPoint) {

            View v = getView();
            int height = v.getHeight();
            int width =  v.getWidth();

            mBox.setBounds(0, 0, width, height);
            shadowSize.set(width, height);

            shadowTouchPoint.set(width / 2, height / 2);

        }
    }

    /**
     *  Drag Listener class. For dragging remote files to local and vice versa.
     */
    private class FileDragListener implements OnDragListener{

        @Override
        public boolean onDrag(View view, DragEvent dragEvent) {
            switch(dragEvent.getAction()){
                case DragEvent.ACTION_DRAG_STARTED: break; //TODO

                case DragEvent.ACTION_DRAG_ENTERED: break; //TODO

                case DragEvent.ACTION_DRAG_EXITED: break; //TODO

                case DragEvent.ACTION_DROP:
                    if(view.getId()==R.id.listview){
                        Log.d(TAG,"DROPPED");
                    }
                    break; //TODO

                case DragEvent.ACTION_DRAG_ENDED: break; //TODO

                default: break;
            }


            return false;
        }
    }
}
