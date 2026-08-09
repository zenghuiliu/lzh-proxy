package org.lzh.proxy.lifecycle;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 有序生命周期注册表。
 *
 * <p>start() 按注册顺序启动，任一步失败则逆序回滚已启动组件；stop() 逆序停止，
 * 单组件失败不影响其余组件继续停止。</p>
 */
public class LifecycleRegistry {

    private static final Logger log = LoggerFactory.getLogger(LifecycleRegistry.class);

    private final List<Lifecycle> components = new ArrayList<>();

    public LifecycleRegistry register(Lifecycle component) {
        components.add(component);
        return this;
    }

    public void start() throws Exception {
        List<Lifecycle> started = new ArrayList<>();
        try {
            for (Lifecycle component : components) {
                component.start();
                started.add(component);
            }
        } catch (Exception e) {
            for (int i = started.size() - 1; i >= 0; i--) {
                try {
                    started.get(i).stop();
                } catch (Exception rollbackEx) {
                    log.warn("rollback stop failed for component", rollbackEx);
                }
            }
            throw e;
        }
    }

    public void stop() {
        for (int i = components.size() - 1; i >= 0; i--) {
            try {
                components.get(i).stop();
            } catch (Exception e) {
                log.warn("stop failed for component {}", components.get(i).getClass().getSimpleName(), e);
            }
        }
    }
}
