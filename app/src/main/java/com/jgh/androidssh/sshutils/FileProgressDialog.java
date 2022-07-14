package com.jgh.androidssh.sshutils;

import android.app.ProgressDialog;
import android.content.Context;

/**
 * @Description:
 * @Author: zongheng.wu
 * @Date: 2022/7/14 10:17
 */
public class FileProgressDialog  extends ProgressDialog implements FileProgressMonitor {

    /**
     * Size of file to transfer
     */
    private long mSize = 0;
    /**
     * Current progress count
     */
    private long mCount = 0;

    /**
     * Constructor
     *
     * @param context
     * @param theme
     */

    public FileProgressDialog(Context context, int theme) {
        super(context, theme);
        setCancelable(false);
        // TODO Auto-generated constructor stub
    }

    //
    // SftpProgressMonitor methods
    //

    /**
     * Gets the data uploaded since the last count.
     */
    public boolean count(long arg0) {
        mCount += arg0;
        this.setProgress((int) ((float) (mCount) / (float) (mSize) * (float) getMax()));
        return true;
    }

    /**
     * Data upload is ended. Dismiss progress dialog.
     */
    public void end() {
        this.setProgress(this.getMax());
        this.dismiss();

    }

    /**
     * Initializes the SftpProgressMonitor
     */
    public void init(int arg0, String arg1, String arg2, long arg3) {
        mSize = arg3;

    }


    @Override
    public void onFail() {
        dismiss();
    }
}
