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
