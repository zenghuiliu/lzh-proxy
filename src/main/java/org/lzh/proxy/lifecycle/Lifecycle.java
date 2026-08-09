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
package org.lzh.proxy.lifecycle;

/**
 * 组件生命周期：由组合根按注册顺序启动、逆序停止。
 *
 * <p>所有组件（NettyFactory、Schedulers、SSH 会话、Server、Register、Client 等）
 * 实现本接口并交由 {@link LifecycleRegistry} 统一编排，保证优雅停机。</p>
 */
public interface Lifecycle {

    /** 启动组件；失败时由注册表回滚已启动项。 */
    void start() throws Exception;

    /** 停止组件；要求幂等，可在任意状态安全调用。 */
    void stop();
}
