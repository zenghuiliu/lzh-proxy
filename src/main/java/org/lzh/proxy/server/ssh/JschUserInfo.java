package org.lzh.proxy.server.ssh;

import org.lzh.proxy.config.GlobalConfig.SSHInfo;

import com.jcraft.jsch.UserInfo;

import lombok.Data;

@Data
public class JschUserInfo implements UserInfo{

    
    private Boolean keepAliveFlag;
    private SSHInfo sshInfo;

    @Override
    public String getPassphrase() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPassphrase'");
    }

    @Override
    public String getPassword() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPassword'");
    }

    @Override
    public boolean promptPassphrase(String arg0) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'promptPassphrase'");
    }

    @Override
    public boolean promptPassword(String arg0) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'promptPassword'");
    }

    @Override
    public boolean promptYesNo(String arg0) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'promptYesNo'");
    }

    @Override
    public void showMessage(String arg0) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'showMessage'");
    }
    
}
