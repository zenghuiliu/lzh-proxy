/*
 * Copyright 2023-2026 lzh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
