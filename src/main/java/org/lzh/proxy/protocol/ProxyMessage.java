package org.lzh.proxy.protocol;

import lombok.Data;

import java.util.Arrays;

/**
 * 客户端服务器交换协议
 *
 * @author lzh
 *
 */
@Data
public class ProxyMessage {



    /** 消息类型 */
    private byte type;

    /** 通道序列 */
    private long serial;

    /** 消息传输数据 */
    private byte[] data;


}
