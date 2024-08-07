# 意图过滤器重载

修改应用 Activity 的 Intent-Filter 的 Xposed 模块

**仅支持 Android 11-14**

## 说明

1. 支持移除本身的 intent filter 和增加新的 intent-filter 。  
2. 你可以通过 XML 配置，类似 [IFW](https://bbs.letitfly.me/d/395) 。XML 配置文件目录位于 `/data/system/ifo` ，你可以放置多个 XML 配置文件。  
3. XML 的根标签是 `<intent-filter-override>` ，包含若干子标签 `<activity>` ，attribute name 指定 Activity 组件名（被 `ComponentName.unflatterFromString` 处理）  
4. 每个 activity 标签可以包含若干 `add` 或 `remove` 标签，其子标签为若干 `intent-filter` ，如何解析取决于系统的 `IntentFilter.readFromXml` 实现。  
5. intent-filter 标签一般来说包含 `action` 、 `cat` 、 `scheme` 、 `auth` 等子标签，对应于 intent-filter 的 action, categories, scheme, authority 。  
6. 对于 remove 中的 intent-filter ，只要所写的项目全部属于 Activity 的某个原 intent filter ，则会被匹配并从 activity 的 intent-filter 列表移除。  
7. 对于 add 中的 intent-filter ，它会被直接添加到 activity 的 intent-filter 列表。  
8. 修改 xml 和安装 app 都会触发重载的更新。如果不需要重载，可以删除 XML 配置，或者修改后缀名为非 xml ，这样相应的重载配置会被还原。

## XML 示例

```xml
<?xml version="1.0" encoding="utf-8"?>
<intent-filter-override>
    <activity name="tv.danmaku.bili/.ui.intent.IntentHandlerActivity">
        <add>
            <!-- 让 Bilibili 支持打开更多它本来支持的链接 -->
            <intent-filter>
                <action name="android.intent.action.VIEW" />
                <cat name="android.intent.category.DEFAULT" />
                <cat name="android.intent.category.BROWSABLE" />
                <auth host="t.bilibili.com" />
                <auth host="live.bilibili.com" />
                <auth host="m.bilibili.com" />
                <scheme name="http" />
                <scheme name="https" />
            </intent-filter>
        </add>
    </activity>
    <activity name="com.netease.cloudmusic/.activity.RedirectActivity">
        <remove>
            <!-- 屏蔽网易云音乐的浏览器 -->
            <intent-filter>
                <action name="android.intent.action.VIEW" />
                <cat name="android.intent.category.BROWSABLE" />
                <scheme name="http" />
            </intent-filter>
        </remove>
        <add>
            <!-- 允许网易云音乐打开特定 URL -->
            <intent-filter>
                <action name="android.intent.action.VIEW" />
                <cat name="android.intent.category.DEFAULT" />
                <cat name="android.intent.category.BROWSABLE" />
                <auth host="music.163.com" />
                <auth host="y.music.163.com" />
                <scheme name="http" />
                <scheme name="https" />
            </intent-filter>
        </add>
    </activity>
    <activity name="com.jingdong.app.mall/.open.BrowserActivity">
        <remove>
            <!-- 屏蔽京东的浏览器 -->
            <intent-filter>
                <action name="android.intent.action.VIEW" />
                <cat name="android.intent.category.BROWSABLE" />
                <scheme name="http" />
            </intent-filter>
        </remove>
    </activity>
</intent-filter-override>
```
