package com.jgh.androidssh.sshutils;

import com.jcraft.jsch.SftpProgressMonitor;

/**
 * @Description:
 * @Author: zongheng.wu
 * @Date: 2022/7/7 21:59
 */
public interface MySftpProgressMonitor extends SftpProgressMonitor {
    void onFail();
}
