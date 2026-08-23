/*
 * Copyright 2026 肖其顿 (XIAO QI DUN)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.aite.npirp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import io.github.libxposed.api.XposedModule;

/**
 * Removes Nubia's network, advertising, recommendation and vendor scanning layers while
 * deliberately leaving Android's PackageInstaller and PackageManager security flow intact.
 */
@SuppressLint("PrivateApi")
public final class NpirpModule extends XposedModule {
    private static final String PACKAGE_INSTALLER = "com.android.packageinstaller";
    private static final String SETTINGS = "com.android.settings";
    private boolean packageInstallerProcess;
    private boolean settingsProcess;
    private boolean hooksInstalled;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        String processName = param.getProcessName();
        packageInstallerProcess = PACKAGE_INSTALLER.equals(processName)
                || processName.startsWith(PACKAGE_INSTALLER + ':');
        settingsProcess = SETTINGS.equals(processName);
        if (!packageInstallerProcess && !settingsProcess) {
            detach();
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (hooksInstalled) {
            return;
        }

        if (packageInstallerProcess && PACKAGE_INSTALLER.equals(param.getPackageName())) {
            installPackageInstallerHooks(param.getClassLoader());
            hooksInstalled = true;
        } else if (settingsProcess && SETTINGS.equals(param.getPackageName())) {
            installSettingsHooks(param.getClassLoader());
            hooksInstalled = true;
        }
    }

    private void installPackageInstallerHooks(ClassLoader classLoader) {
        installPureModeHooks(classLoader);
        installConfirmationUiHooks(classLoader);
        installInlinePermissionHook(classLoader);
        installAdvertisingHooks(classLoader);
        installScanningHooks(classLoader);
        installRecommendationHooks(classLoader);
        installPluginHooks(classLoader);
    }

    private void installPureModeHooks(ClassLoader classLoader) {
        hookExactReturn(
                classLoader,
                "com.android.packageinstaller.PackageUtil",
                "isPureInstallMode",
                false,
                Context.class
        );
    }

    private void installConfirmationUiHooks(ClassLoader classLoader) {
        try {
            String activityName =
                    "com.android.packageinstaller.PackageInstallerActivity";
            Class<?> activityClass = Class.forName(activityName, false, classLoader);
            Class<?> cookToolClass = Class.forName(
                    activityName + "$UICookTool",
                    false,
                    classLoader
            );
            Method getCookUi = accessibleMethod(
                    activityClass,
                    "getCookUI",
                    int.class,
                    int.class,
                    int.class,
                    boolean.class,
                    String.class,
                    String.class
            );
            Field hidePureMode = accessibleField(
                    cookToolClass,
                    "hidePureModeSwitchLayout"
            );
            Field hideWarning = accessibleField(cookToolClass, "hideWarningLayout");
            Field showRiskCheckbox = accessibleField(cookToolClass, "showRiskCheckbox");
            Field showSwlimitLearnMore = accessibleField(
                    cookToolClass,
                    "showSwlimitLearnMore"
            );
            Field needIsolate = accessibleField(cookToolClass, "needIsolate");

            hook(getCookUi)
                    .setId(hookId(activityName, "getCookUI", 0))
                    .setPriority(PRIORITY_HIGHEST)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        hidePureMode.setBoolean(result, true);
                        hideWarning.setBoolean(result, true);
                        showRiskCheckbox.setBoolean(result, false);
                        showSwlimitLearnMore.setBoolean(result, false);
                        needIsolate.setBoolean(result, false);
                        return result;
                    });
        } catch (Throwable ignored) {
        }
    }

    private void installInlinePermissionHook(ClassLoader classLoader) {
        try {
            String activityName =
                    "com.android.packageinstaller.PackageInstallerActivity";
            Class<?> activityClass = Class.forName(activityName, false, classLoader);
            Class<?> permissionsClass = Class.forName(
                    "com.android.packageinstaller.AppSecurityPermissions",
                    false,
                    classLoader
            );
            Class<?> idClass = Class.forName(
                    "com.android.packageinstaller.R$id",
                    false,
                    classLoader
            );
            Class<?> stringClass = Class.forName(
                    "com.android.packageinstaller.R$string",
                    false,
                    classLoader
            );
            Constructor<?> permissionsConstructor = accessibleConstructor(
                    permissionsClass,
                    Context.class,
                    PackageInfo.class
            );
            Field packageInfo = accessibleField(activityClass, "mPkgInfo");
            Field appInfo = accessibleField(activityClass, "mAppInfo");
            Method getPermissionCount = accessibleMethod(
                    permissionsClass,
                    "getPermissionCount"
            );
            Method getPermissionsView = accessibleMethod(
                    permissionsClass,
                    "getPermissionsView",
                    int.class,
                    Activity.class
            );
            Method startInstallConfirm = accessibleMethod(
                    activityClass,
                    "startInstallConfirm"
            );
            int containerId = accessibleField(idClass, "ad_layout").getInt(null);
            int noPermissionsId = accessibleField(idClass, "no_permissions").getInt(null);
            int permissionsListId = accessibleField(idClass, "permissions_list").getInt(null);
            int recommendationTitleId = accessibleField(
                    idClass,
                    "ad_recommend"
            ).getInt(null);
            int installQuestionId = accessibleField(
                    stringClass,
                    "install_confirm_question"
            ).getInt(null);
            int installQuestionNoPermissionsId = accessibleField(
                    stringClass,
                    "install_confirm_question_no_perms"
            ).getInt(null);
            int updateQuestionId = accessibleField(
                    stringClass,
                    "install_confirm_question_update"
            ).getInt(null);
            int updateQuestionNoPermissionsId = accessibleField(
                    stringClass,
                    "install_confirm_question_update_no_perms"
            ).getInt(null);
            int[] duplicateLinkIds = {
                    accessibleField(idClass, "permission").getInt(null),
                    accessibleField(idClass, "perm_link").getInt(null),
                    accessibleField(idClass, "entrance_indicator").getInt(null)
            };

            hook(startInstallConfirm)
                    .setId(hookId(activityName, "startInstallConfirm", 0))
                    .setPriority(PRIORITY_HIGHEST)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Activity activity = (Activity) chain.getThisObject();
                        PackageInfo info = (PackageInfo) packageInfo.get(activity);
                        View containerView = activity.findViewById(containerId);
                        if (info == null || !(containerView instanceof LinearLayout container)) {
                            return result;
                        }

                        Object permissions = permissionsConstructor.newInstance(activity, info);
                        int permissionCount = (int) getPermissionCount.invoke(permissions);
                        View permissionsView = (View) getPermissionsView.invoke(
                                permissions,
                                0xffff,
                                activity
                        );
                        TextView question = permissionsView.findViewById(noPermissionsId);
                        if (question != null) {
                            boolean update = appInfo.get(activity) != null;
                            int questionId;
                            if (update) {
                                questionId = permissionCount == 0
                                        ? updateQuestionNoPermissionsId
                                        : updateQuestionId;
                            } else {
                                questionId = permissionCount == 0
                                        ? installQuestionNoPermissionsId
                                        : installQuestionId;
                            }
                            int padding = Math.round(
                                    16 * activity.getResources().getDisplayMetrics().density
                            );
                            question.setText(questionId);
                            question.setGravity(Gravity.START);
                            question.setPadding(padding, 0, padding, 0);
                            question.setLayoutParams(new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                            ));
                            question.setVisibility(View.VISIBLE);
                        }

                        if (permissionCount == 0) {
                            View permissionsList = permissionsView.findViewById(
                                    permissionsListId
                            );
                            if (permissionsList != null) {
                                permissionsList.setVisibility(View.GONE);
                            }
                        }

                        container.removeAllViews();
                        container.setBackground(null);
                        container.setPadding(0, 0, 0, 0);
                        container.setGravity(Gravity.TOP | Gravity.START);
                        container.addView(
                                permissionsView,
                                new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                        );
                        container.setVisibility(View.VISIBLE);

                        View recommendationTitle = activity.findViewById(
                                recommendationTitleId
                        );
                        if (recommendationTitle != null) {
                            recommendationTitle.setVisibility(View.GONE);
                        }

                        for (int id : duplicateLinkIds) {
                            View duplicateLink = activity.findViewById(id);
                            if (duplicateLink != null) {
                                duplicateLink.setVisibility(View.GONE);
                            }
                        }
                        return result;
                    });
        } catch (Throwable ignored) {
        }
    }

    private void installAdvertisingHooks(ClassLoader classLoader) {
        hookExactReturn(
                classLoader,
                "com.android.packageinstaller.ad.AdUtil",
                "isAdSwitchOn",
                false,
                Context.class
        );
        hookExactReturn(
                classLoader,
                "com.huanju.ssp.sdk.inf.AdInfFactory",
                "getInstance",
                null
        );
    }

    private void installScanningHooks(ClassLoader classLoader) {
        installStagingScanHooks(classLoader);
        installScanningScanHooks(classLoader);
    }

    private void installStagingScanHooks(ClassLoader classLoader) {
        try {
            String ownerName = "com.android.packageinstaller.InstallStaging";
            String taskName = ownerName + "$ScanningAsyncTask";
            Class<?> ownerClass = Class.forName(ownerName, false, classLoader);
            Class<?> taskClass = Class.forName(taskName, false, classLoader);
            Class<?> wrapperClass = Class.forName(ownerName + "$Wrapper", false, classLoader);
            Constructor<?> constructor = accessibleConstructor(wrapperClass, ownerClass);
            Field taskOwner = accessibleField(taskClass, "this$0");
            Field defraud = accessibleField(wrapperClass, "defraud");
            Field hitType = accessibleField(wrapperClass, "hitType");
            Field swLimit = accessibleField(wrapperClass, "swLimit");
            Field marketHit = accessibleField(wrapperClass, "marketHit");
            Field marketRecommend = accessibleField(wrapperClass, "marketRecommend");
            Field carisk = accessibleField(wrapperClass, "carisk");
            Method background = accessibleMethod(taskClass, "doInBackground", Uri[].class);
            Method postExecute = accessibleMethod(taskClass, "onPostExecute", wrapperClass);
            Method launchNext = accessibleMethod(
                    ownerClass,
                    "LaunchNextActivity",
                    wrapperClass
            );

            hook(background)
                    .setId(hookId(taskName, "doInBackground", 0))
                    .setPriority(PRIORITY_HIGHEST)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object owner = taskOwner.get(chain.getThisObject());
                        Object result = constructor.newInstance(owner);
                        defraud.setInt(result, -1);
                        hitType.setInt(result, -1);
                        swLimit.setInt(result, -1);
                        marketHit.setBoolean(result, false);
                        marketRecommend.setBoolean(result, false);
                        carisk.set(result, null);
                        return result;
                    });
            hook(postExecute)
                    .setId(hookId(taskName, "onPostExecute", 0))
                    .setPriority(PRIORITY_HIGHEST)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object owner = taskOwner.get(chain.getThisObject());
                        launchNext.invoke(owner, chain.getArg(0));
                        return null;
                    });
        } catch (Throwable ignored) {
        }
    }

    private void installScanningScanHooks(ClassLoader classLoader) {
        try {
            String ownerName = "com.android.packageinstaller.InstallScanning";
            String taskName = ownerName + "$ScanningAsyncTask";
            Class<?> ownerClass = Class.forName(ownerName, false, classLoader);
            Class<?> taskClass = Class.forName(taskName, false, classLoader);
            Class<?> wrapperClass = Class.forName(ownerName + "$Wrapper", false, classLoader);
            Constructor<?> constructor = accessibleConstructor(wrapperClass, ownerClass);
            Field taskOwner = accessibleField(taskClass, "this$0");
            Field defraud = accessibleField(wrapperClass, "defraud");
            Field marketHit = accessibleField(wrapperClass, "marketHit");
            Method background = accessibleMethod(taskClass, "doInBackground", Uri[].class);

            hook(background)
                    .setId(hookId(taskName, "doInBackground", 0))
                    .setPriority(PRIORITY_HIGHEST)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object owner = taskOwner.get(chain.getThisObject());
                        Object result = constructor.newInstance(owner);
                        defraud.setInt(result, -1);
                        marketHit.setBoolean(result, false);
                        return result;
                    });
        } catch (Throwable ignored) {
        }
    }

    private void installRecommendationHooks(ClassLoader classLoader) {
        hookExactReturn(
                classLoader,
                "com.android.packageinstaller.PackageInstallerActivity",
                "needShowOfficialRecommend",
                false,
                String.class
        );

        try {
            String className = "com.android.packageinstaller.InstallSuccess";
            Class<?> ownerClass = Class.forName(className, false, classLoader);
            Method filter = accessibleMethod(
                    ownerClass,
                    "filterInstalledRecommendApp",
                    String.class
            );
            Method hide = accessibleMethod(
                    ownerClass,
                    "setRecommandLayoutInvisible",
                    int.class
            );
            hook(filter)
                    .setId(hookId(className, "filterInstalledRecommendApp", 0))
                    .setPriority(PRIORITY_HIGHEST)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        hide.invoke(chain.getThisObject(), View.GONE);
                        return null;
                    });
        } catch (Throwable ignored) {
        }
    }

    private void installPluginHooks(ClassLoader classLoader) {
        for (String provider : Set.of(
                "com.tramini.plugin.api.TraminiContentProvider",
                "com.bytedance.sdk.openadsdk.stub.server.MainServerManager",
                "com.bytedance.pangle.provider.ContentProviderProxy",
                "com.kwad.sdk.api.proxy.app.AdSdkFileProvider",
                "com.bytedance.android.openliveplugin.process.server.LiveServerManager"
        )) {
            hookExactReturn(classLoader, provider, "onCreate", false);
        }

        hookExactReturn(
                classLoader,
                "com.bytedance.android.dy.sdk.pangle.ZeusPlatformUtils",
                "getPluginClassloader",
                null,
                String.class
        );
    }

    private void installSettingsHooks(ClassLoader classLoader) {
        hookExactReturn(
                classLoader,
                "com.zte.settings.security.PureAppPreferenceController",
                "getAvailabilityStatus",
                3
        );
    }

    private void hookExactReturn(
            ClassLoader classLoader,
            String className,
            String methodName,
            Object returnValue,
            Class<?>... parameterTypes
    ) {
        try {
            Class<?> owner = Class.forName(className, false, classLoader);
            Method method = accessibleMethod(owner, methodName, parameterTypes);
            installReturnHook(method, className, methodName, 0, returnValue);
        } catch (Throwable ignored) {
        }
    }

    private void installReturnHook(
            Method method,
            String className,
            String methodName,
            int index,
            Object returnValue
    ) {
        hook(method)
                .setId(hookId(className, methodName, index))
                .setPriority(PRIORITY_HIGHEST)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> returnValue);
    }

    private static Constructor<?> accessibleConstructor(
            Class<?> owner,
            Class<?>... parameterTypes
    ) throws NoSuchMethodException {
        Constructor<?> constructor = owner.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor;
    }

    private static Method accessibleMethod(
            Class<?> owner,
            String name,
            Class<?>... parameterTypes
    ) throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static Field accessibleField(Class<?> owner, String name)
            throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static String hookId(String className, String methodName, int index) {
        return "npirp:" + className + '#' + methodName + ':' + index;
    }
}
