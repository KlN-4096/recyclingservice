package com.klnon.recyclingservice.compact.clientsort;

import com.klnon.recyclingservice.Recyclingservice;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * ClientSort compatibility: disable TrashBox sort/stack-fill/transfer on server startup.
 */
public final class ClientSortPolicyCompat {

    private static final String TRASHBOX_POLICY_CLASS =
            "com.klnon.recyclingservice.content.trashbox.data.TrashBox";
    private static final String POLICY_REASON =
            "Disabled by RecyclingService for TrashBox";
    private static final String POLICY_OP =
            "recyclingservice:clientsort_policy";

    private ClientSortPolicyCompat() {
    }

    /**
     * Try to disable ClientSort policy for TrashBox in memory.
     * If policy is already disabled, it will not be applied again.
     */
    public static void disableTrashBoxPolicyIfPresent() {
        boolean applied = tryDisableViaPolicyManager();
        if (!applied) {
            Recyclingservice.LOGGER.debug(
                    "[RecyclingService] ClientSort not present or policy update skipped"
            );
        }
    }

    private static boolean tryDisableViaPolicyManager() {
        try {
            Object existing = getExistingPolicy();
            if (existing != null && isPolicyDisabled(existing)) {
                Recyclingservice.LOGGER.debug(
                        "[RecyclingService] ClientSort policy already disabled for TrashBox"
                );
                return true;
            }

            Class<?> policyClass = Class.forName("dev.terminalmc.clientsort.config.ServerClassPolicy");
            Constructor<?> ctor = policyClass.getConstructor(String.class, boolean.class, boolean.class, boolean.class);
            Object policy = ctor.newInstance(TRASHBOX_POLICY_CLASS, false, false, false);

            Class<?> managerClass = Class.forName("dev.terminalmc.clientsort.network.handler.validate.PolicyManager");
            Method setPolicy = managerClass.getMethod("setPolicy", policyClass, String.class, String.class);
            setPolicy.invoke(null, policy, POLICY_OP, POLICY_REASON);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Exception e) {
            Recyclingservice.LOGGER.warn("[RecyclingService] Failed to update ClientSort policy in memory", e);
            return false;
        }
    }

    private static Object getExistingPolicy() {
        try {
            Class<?> serverConfig = Class.forName("dev.terminalmc.clientsort.config.ServerConfig");
            Method serverOptions = serverConfig.getMethod("serverOptions");
            Object options = serverOptions.invoke(null);
            Field classPoliciesField = options.getClass().getField("classPolicies");
            Object mapObj = classPoliciesField.get(options);
            if (!(mapObj instanceof Map<?, ?> map)) {
                return null;
            }
            return map.get(TRASHBOX_POLICY_CLASS);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isPolicyDisabled(Object policy) throws Exception {
        Class<?> cls = policy.getClass();
        Field sortEnabled = cls.getField("sortEnabled");
        Field stackFillEnabled = cls.getField("stackFillEnabled");
        Field transferEnabled = cls.getField("transferEnabled");

        boolean sort = (boolean) sortEnabled.get(policy);
        boolean stackFill = (boolean) stackFillEnabled.get(policy);
        boolean transfer = (boolean) transferEnabled.get(policy);

        return !sort && !stackFill && !transfer;
    }
}