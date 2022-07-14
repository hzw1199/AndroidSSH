package com.jgh.androidssh.sshutils;

import android.os.AsyncTask;
import android.util.Log;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpProgressMonitor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Controller class for SCP functions. Performs SCP
 * todo
 * ls, get, put commands between local device and remote SSH
 * server. For each process a new sftpchannel is opened and closed
 * after completion.
 */
public class ScpController {

    /**
     * Tag name
     */
    public static final String TAG = "ScpController";

    /**
     * Remote directory path. The path to the current remote directory.
     */
    private String mCurrentPath = "/";


    /**
     * Creates instance of SftpController. Performs SFTP functions.
     */
    public ScpController() {

    }

    /**
     * Creates instance of SftpController. Performs SFTP functions.
     *
     * @param path path to chosen directory on remote host.
     */
    public ScpController(String path) {
        mCurrentPath = path;
    }


    /**
     * Resets the current path on remote server.
     */
    public void resetPathToRoot() {
        mCurrentPath = "/";
    }


    /**
     * Returns the path to the current directory on the
     * remote server.
     *
     * @return
     */
    public String getPath() {
        return mCurrentPath;
    }


    /**
     * Sets the path to current directory on the remote
     * server.
     *
     * @param path
     */
    public void setPath(String path) {
        mCurrentPath = path;
    }


    /**
     * Appends <b>relPath</b> to the current path on remote host.
     *
     * @param relPath relative path
     */
    public void appendToPath(String relPath) {
        if (mCurrentPath == null) {
            mCurrentPath = relPath;
        } else mCurrentPath += relPath;
    }


    /**
     * Disconnects SFTP.
     */
    public void disconnect() {
        //nothing yet
    }


    /**
     * Upload file(s) Task. Aysnc task for uploading local files to remote
     * server.
     */
    public class UploadTask extends AsyncTask<Void, Void, Boolean> {

        /**
         * JSch Session Instance
         */
        private Session mSession;

        /**
         * Progress dialog to monitor upload progress.
         */
        private FileProgressMonitor mProgressDialog;

        /**
         * Local file to be uploaded
         */
        private String mLocalFile;

        private String mDestination;

        //
        // Constructor
        //

        public UploadTask(Session session, String localFile, String destination, FileProgressMonitor spd) {

            mProgressDialog = spd;
            mLocalFile = localFile;
            mDestination = destination;
            mSession = session;
        }

        @Override
        protected void onPreExecute() {
            //nothing
        }

        @Override
        protected Boolean doInBackground(Void... voids) {

            boolean success = true;
            try {
                uploadFile(mSession, mLocalFile, mDestination, mProgressDialog);
            } catch (JSchException e) {
                e.printStackTrace();
                Log.e(TAG, "JSchException " + e.getMessage());
                success = false;
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "IOException " + e.getMessage());
                success = false;
            } catch (ScpException e) {
                e.printStackTrace();
                Log.e(TAG, "ScpException " + e.getMessage());
                success = false;
            } finally {
                return success;
            }
        }

        @Override
        protected void onPostExecute(Boolean b) {
            if (!b) {
                if (mProgressDialog != null) {
                    mProgressDialog.onFail();
                }
            }
        }

    }


    /**
     * Uploads the files in <b>localFiles</b> to the current directory on
     * remote server.
     * http://www.jcraft.com/jsch/examples/ScpTo.java.html
     *
     * @param session    the Jsch SSH session instance
     * @param lfile file to be uploaded
     * @param rfile file remote
     * @param spm        progress monitor
     * @throws JSchException
     * @throws IOException
     */
    public void uploadFile(Session session, String lfile, String rfile, SftpProgressMonitor spm) throws JSchException, IOException, ScpException {
        if (session == null || !session.isConnected()) {
            session.connect();
        }

        // https://stackoverflow.com/a/23579733
        boolean ptimestamp = false;

        // exec 'scp -t rfile' remotely
        rfile=rfile.replace("'", "'\"'\"'");
        rfile="'"+rfile+"'";
        String command="scp " + (ptimestamp ? "-p" :"") +" -t "+rfile;
        Channel channel=session.openChannel("exec");
        ((ChannelExec)channel).setCommand(command);

        // get I/O streams for remote scp
        OutputStream out=channel.getOutputStream();
        InputStream in=channel.getInputStream();

        channel.connect();

        int res;
        if((res = checkAck(in))!=0){
            out.close();
            channel.disconnect();
            throw new ScpException(res, res+"");
        }

        File _lfile = new File(lfile);

        if(ptimestamp){
            command="T "+(_lfile.lastModified()/1000)+" 0";
            // The access time should be sent here,
            // but it is not accessible with JavaAPI ;-<
            command+=(" "+(_lfile.lastModified()/1000)+" 0\n");
            out.write(command.getBytes()); out.flush();
            if((res = checkAck(in))!=0){
                out.close();
                channel.disconnect();
                throw new ScpException(res, res+"");
            }
        }

        // send "C0644 filesize filename", where filename should not include '/'
        long filesize=_lfile.length();
        if (spm != null) {
            spm.init(SftpProgressMonitor.PUT, lfile, rfile, filesize);
        }
        command="C0644 "+filesize+" ";
        if(lfile.lastIndexOf('/')>0){
            command+=lfile.substring(lfile.lastIndexOf('/')+1);
        }
        else{
            command+=lfile;
        }
        command+="\n";
        out.write(command.getBytes()); out.flush();
        if((res = checkAck(in))!=0){
            out.close();
            channel.disconnect();
            throw new ScpException(res, res+"");
        }

        // send a content of lfile
        FileInputStream fis=new FileInputStream(lfile);
        byte[] buf=new byte[1024];
        while(true){
            int len=fis.read(buf, 0, buf.length);
            if(len<=0) break;
            out.write(buf, 0, len); //out.flush();

            if (spm != null) {
                spm.count(len);
            }
        }
        fis.close();
        fis=null;
        // send '\0'
        buf[0]=0; out.write(buf, 0, 1); out.flush();
        if((res = checkAck(in))!=0){
            out.close();
            channel.disconnect();
            throw new ScpException(res, res+"");
        }
        out.close();

        channel.disconnect();
//        session.disconnect();

        if (spm != null) {
            spm.end();
        }
    }

    private int checkAck(InputStream in) throws IOException{
        int b=in.read();
        // b may be 0 for success,
        //          1 for error,
        //          2 for fatal error,
        //          -1
        if(b==0) return b;
        if(b==-1) return b;

        if(b==1 || b==2){
            StringBuffer sb=new StringBuffer();
            int c;
            do {
                c=in.read();
                sb.append((char)c);
            }
            while(c!='\n');
            if(b==1){ // error
                Log.d(TAG, sb.toString());
            }
            if(b==2){ // fatal error
                Log.d(TAG, sb.toString());
            }
        }
        return b;
    }

}
