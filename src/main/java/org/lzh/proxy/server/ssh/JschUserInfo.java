package org.lzh.proxy.server.ssh;

import org.lzh.proxy.config.SshAuthConfig;
import org.lzh.proxy.config.SshEndpointConfig;

import com.jcraft.jsch.UserInfo;

/**
 * JSch 用户信息载体（密码已由 connectSession 预先设置，各提示方法无需实现）。
 *
 * <p>同时携带 SSH 配置与保活标志，供保活/重连逻辑使用。</p>
 */
public class JschUserInfo implements UserInfo {

    private volatile Boolean keepAliveFlag;
    private SshEndpointConfig sshInfo;

    public Boolean getKeepAliveFlag() {
        return keepAliveFlag;
    }

    public void setKeepAliveFlag(Boolean keepAliveFlag) {
        this.keepAliveFlag = keepAliveFlag;
    }

    public SshEndpointConfig getSshInfo() {
        return sshInfo;
    }

    public void setSshInfo(SshEndpointConfig sshInfo) {
        this.sshInfo = sshInfo;
    }

    @Override
    public String getPassphrase() {
        throw new UnsupportedOperationException("Unimplemented method 'getPassphrase'");
    }

    @Override
    public String getPassword() {
        throw new UnsupportedOperationException("Unimplemented method 'getPassword'");
    }

    @Override
    public boolean promptPassphrase(String message) {
        throw new UnsupportedOperationException("Unimplemented method 'promptPassphrase'");
    }

    @Override
    public boolean promptPassword(String message) {
        throw new UnsupportedOperationException("Unimplemented method 'promptPassword'");
    }

    @Override
    public boolean promptYesNo(String message) {
        throw new UnsupportedOperationException("Unimplemented method 'promptYesNo'");
    }

    @Override
    public void showMessage(String message) {
        throw new UnsupportedOperationException("Unimplemented method 'showMessage'");
    }
}
