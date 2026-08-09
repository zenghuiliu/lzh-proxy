package org.lzh.proxy.config;

import java.util.List;

/**
 * 配置加载/校验失败异常。库内绝不调用 System.exit，由入口层捕获后决定退出码。
 */
public class ConfigValidationException extends RuntimeException {

    private final List<String> errors;

    public ConfigValidationException(List<String> errors) {
        super("config validation failed with " + errors.size() + " error(s)");
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
