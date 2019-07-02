# Neptune SDK混淆配置
-dontwarn android.content.IContentProvider
-dontwarn android.app.ActivityThread
-dontwarn android.webkit.WebViewFactory

-keepclassmembers class org.qiyi.pluginlibrary.component.wraper.ContentResolverWrapper {
    <methods>;
}
-keepclassmembers class org.qiyi.pluginlibrary.component.wraper.PluginInstrument {
    <methods>;
}
# 兼容Oppo机型主题资源查找问题
-keepclassmembers class org.qiyi.pluginlibrary.context.CustomContextWrapper {
    public boolean isOppoStyle();
}
-keepclassmembers class org.qiyi.pluginlibrary.component.InstrActivityProxy1 {
    public boolean isOppoStyle();
}
## 反射字段兼容多个插件依赖使用support库View/Fragment冲突问题
-keepclassmembers class android.support.v4.app.Fragment {
    private static final android.support.v4.util.SimpleArrayMap sClassMap;
}
-keepclassmembers class android.support.design.widget.CoordinatorLayout {
    static final java.lang.ThreadLocal sConstructors;
}
