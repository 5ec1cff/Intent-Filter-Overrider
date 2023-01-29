# Intent Filter Overrider

This Xposed Module allows you modify intent filters of activities.

## Usage

1. Install and enable the module.  
2. Create an xml file at `/data/system/ifo/` .  
3. Write your override and save.  

An example of `ifo.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<intent-filter-override>
    <activity name="com.netease.cloudmusic/.activity.RedirectActivity">
        <remove>
            <intent-filter>
                <action name="android.intent.action.VIEW" />
                <cat name="android.intent.category.BROWSABLE" />
                <scheme name="http" />
            </intent-filter>
        </remove>
        <add>
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
            <intent-filter>
                <action name="android.intent.action.VIEW" />
                <cat name="android.intent.category.BROWSABLE" />
                <scheme name="http" />
            </intent-filter>
        </remove>
    </activity>
</intent-filter-override>
```
