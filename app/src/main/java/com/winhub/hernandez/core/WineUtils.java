package com.winhub.hernandez.core;

import android.content.Context;
import android.net.Uri;

import com.winhub.hernandez.container.Container;
import com.winhub.hernandez.xenvironment.ImageFs;
import com.winhub.hernandez.xenvironment.XEnvironment;
import com.winhub.hernandez.xenvironment.components.GuestProgramLauncherComponent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class WineUtils {
    public static void createDosdevicesSymlinks(Container container) {
        String dosdevicesPath = (new File(container.getRootDir(), ".wine/dosdevices")).getPath();
        File[] files = (new File(dosdevicesPath)).listFiles();
        if (files != null) for (File file : files) if (file.getName().matches("[a-z]:")) file.delete();

        FileUtils.symlink("../drive_c", dosdevicesPath+"/c:");
        FileUtils.symlink(container.getRootDir().getPath() + "/../..", dosdevicesPath+"/z:");

        for (String[] drive : container.drivesIterator()) {
            File linkTarget = new File(drive[1]);
            String path = linkTarget.getAbsolutePath();
            if (!linkTarget.isDirectory() && path.endsWith("/com.winhub.hernandez/storage")) {
                linkTarget.mkdirs();
                FileUtils.chmod(linkTarget, 0771);
            }
            FileUtils.symlink(path, dosdevicesPath+"/"+drive[0].toLowerCase(Locale.ENGLISH)+":");
        }
    }

    private static void setWindowMetrics(WineRegistryEditor registryEditor) {
        byte[] fontNormalData = (new MSLogFont()).toByteArray();
        byte[] fontBoldData = (new MSLogFont()).setWeight(700).toByteArray();
        registryEditor.setHexValue("Control Panel\\Desktop\\WindowMetrics", "CaptionFont", fontBoldData);
        registryEditor.setHexValue("Control Panel\\Desktop\\WindowMetrics", "IconFont", fontNormalData);
        registryEditor.setHexValue("Control Panel\\Desktop\\WindowMetrics", "MenuFont", fontNormalData);
        registryEditor.setHexValue("Control Panel\\Desktop\\WindowMetrics", "MessageFont", fontNormalData);
        registryEditor.setHexValue("Control Panel\\Desktop\\WindowMetrics", "SmCaptionFont", fontNormalData);
        registryEditor.setHexValue("Control Panel\\Desktop\\WindowMetrics", "StatusFont", fontNormalData);
    }

    public static void applySystemTweaks(Context context, WineInfo wineInfo) {
        File rootDir = ImageFs.find(context).getRootDir();
        File systemRegFile = new File(rootDir, ImageFs.WINEPREFIX+"/system.reg");
        File userRegFile = new File(rootDir, ImageFs.WINEPREFIX+"/user.reg");

        try (WineRegistryEditor registryEditor = new WineRegistryEditor(systemRegFile)) {
            registryEditor.setStringValue("Software\\Classes\\.reg", null, "REGfile");
            registryEditor.setStringValue("Software\\Classes\\.reg", "Content Type", "application/reg");
            registryEditor.setStringValue("Software\\Classes\\REGfile\\Shell\\Open\\command", null, "C:\\windows\\regedit.exe /C \"%1\"");

            registryEditor.setStringValue("Software\\Classes\\dllfile\\DefaultIcon", null, "shell32.dll,-154");
            registryEditor.setStringValue("Software\\Classes\\lnkfile\\DefaultIcon", null, "shell32.dll,-30");
            registryEditor.setStringValue("Software\\Classes\\inifile\\DefaultIcon", null, "shell32.dll,-151");
        }

        final String[] direct3dLibs = {"d3d8", "d3d9", "d3d10", "d3d10_1", "d3d10core", "d3d11", "d3d12", "d3d12core", "ddraw", "dxgi", "wined3d"};
        final String dllOverridesKey = "Software\\Wine\\DllOverrides";

        try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
            for (String name : direct3dLibs) registryEditor.setStringValue(dllOverridesKey, name, "native,builtin");
            setWindowMetrics(registryEditor);
        }
    }

    public static void overrideWinComponentDlls(Context context, Container container, String identifier, boolean useNative) {
        final String dllOverridesKey = "Software\\Wine\\DllOverrides";
        File userRegFile = new File(container.getRootDir(), ".wine/user.reg");

        try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
            JSONObject wincomponentsJSONObject = new JSONObject(FileUtils.readString(context, "wincomponents/wincomponents.json"));
            JSONArray dlnames = wincomponentsJSONObject.getJSONArray(identifier);
            for (int i = 0; i < dlnames.length(); i++) {
                String dlname = dlnames.getString(i);
                if (useNative) {
                    registryEditor.setStringValue(dllOverridesKey, dlname, "native,builtin");
                }
                else registryEditor.removeValue(dllOverridesKey, dlname);
            }
        }
        catch (JSONException e) {}
    }

    public static void setWinComponentRegistryKeys(File systemRegFile, String identifier, boolean useNative, Context context) {
        if (identifier.equals("directsound")) {
            try (WineRegistryEditor registryEditor = new WineRegistryEditor(systemRegFile)) {
                final String key64 = "Software\\Classes\\CLSID\\{083863F1-70DE-11D0-BD40-00A0C911CE86}\\Instance\\{E30629D1-27E5-11CE-875D-00608CB78066}";
                final String key32 = "Software\\Classes\\Wow6432Node\\CLSID\\{083863F1-70DE-11D0-BD40-00A0C911CE86}\\Instance\\{E30629D1-27E5-11CE-875D-00608CB78066}";

                if (useNative) {
                    registryEditor.setStringValue(key32, "CLSID", "{E30629D1-27E5-11CE-875D-00608CB78066}");
                    registryEditor.setHexValue(key32, "FilterData", "02000000000080000100000000000000307069330200000000000000010000000000000000000000307479330000000038000000480000006175647300001000800000aa00389b710100000000001000800000aa00389b71");
                    registryEditor.setStringValue(key32, "FriendlyName", "Wave Audio Renderer");

                    registryEditor.setStringValue(key64, "CLSID", "{E30629D1-27E5-11CE-875D-00608CB78066}");
                    registryEditor.setHexValue(key64, "FilterData", "02000000000080000100000000000000307069330200000000000000010000000000000000000000307479330000000038000000480000006175647300001000800000aa00389b710100000000001000800000aa00389b71");
                    registryEditor.setStringValue(key64, "FriendlyName", "Wave Audio Renderer");
                }
                else {
                    registryEditor.removeKey(key32);
                    registryEditor.removeKey(key64);
                }
            }
        }
        else if (identifier.equals("xaudio")) {
            try (WineRegistryEditor registryEditor = new WineRegistryEditor(systemRegFile)) {
                if (useNative) {
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{074B110F-7F58-4743-AEA5-12F1B5074ED}\\InprocServer32", null, "C:\\windows\\syswow64\\xactengine3_5.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{0977D092-2D95-4E43-8D42-9DDCC2545ED5}\\InprocServer32", null, "C:\\windows\\syswow64\\xactengine3_4.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{0AA000AA-F404-11D9-BD7A-0010DC4F8F81}\\InprocServer32", null, "C:\\windows\\syswow64\\xactengine2_0.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{1138472B-D187-44E9-81F2-AE1B0E7785F1}\\InprocServer32", null, "C:\\windows\\syswow64\\xactengine2_3.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{1F1B577E-5E5A-4E8A-BA73-C657EA8E8598}\\InprocServer32", null, "C:\\windows\\syswow64\\xactengine2_1.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{248D8A3B-6256-44D3-A018-2AC96C459F47}\\InprocServer32", null, "C:\\windows\\syswow64\\xactengine3_6.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{343E68E6-8F82-4A8D-A2DA-6E9A944B378C}\\InprocServer32", null, "C:\\windows\\syswow64\\xactengine2_9.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{3A2495CE-31D0-435B-8CCF-E9F0843FD960}\\InprocServer32", null, "C:\\windows\\syswow64\\xactengine2_6.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{3B80EE2A-B0F5-4780-9E30-90CB39685B03}\\InprocServer32", null, "C:\\windows\\syswow64\\xactengine3_0.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{54B68BC7-3A45-416B-A8C9-19BF19EC1DF5}\\InprocServer32", null, "C:\\windows\\syswow64\\xactengine2_5.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{65D822A4-4799-42C6-9B18-D26CF66DD320}\\InprocServer32", null, "C:\\windows\\syswow64\\xactengine2_10.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{77C56BF4-18A1-42B0-88AF-5072CE814949}\\InprocServer32", null, "C:\\windows\\syswow64\\xactengine2_8.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{94C1AFFA-66E7-4961-9521-CFDEF3128D4F}\\InprocServer32", null, "C:\\windows\\syswow64\\xactengine3_3.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{962F5027-99BE-4692-A468-85802CF8DE61}\\InprocServer32", null, "C:\\windows\\syswow64\\xactengine3_1.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{BC3E0FC6-2E0D-4C45-BC61-D9C328319BD8}\\InprocServer32", null, "C:\\windows\\syswow64\\xactengine2_4.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{BCC782BC-6492-4C22-8C35-F5D72FE73C6E}\\InprocServer32", null, "C:\\windows\\syswow64\\xactengine3_7.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{C60FAE90-4183-4A3F-B2F7-AC1DC49B0E5C}\\InprocServer32", null, "C:\\windows\\syswow64\\xactengine2_2.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{CD0D66EC-8057-43F5-ACBD-66DFB36FD78C}\\InprocServer32", null, "C:\\windows\\syswow64\\xactengine2_7.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{D3332F02-3DD0-4DE9-9AEC-20D85C4111B6}\\InprocServer32", null, "C:\\windows\\syswow64\\xactengine3_2.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{03219E78-5BC3-44D1-B92E-F63D89CC6526}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_4.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{2139E6DA-C341-4774-9AC3-B4E026347F64}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_5.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{3EDA9B49-2085-498B-9BB2-39A6778493DE}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_6.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{4C5E637A-16C7-4DE3-9C46-5ED22181962D}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_3.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{4C9B6DDE-6809-46E6-A278-9B6A97588670}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_5.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{5A508685-A254-4FBA-9B82-9A24B00306AF}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_7.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{629CF0DE-3ECC-41E7-9926-F7E43EEBEC51}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_2.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{6A93130E-1D53-41D1-A9CF-E758800BB179}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_7.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{8BB7778B-645B-4475-9A73-1DE3170BD3AF}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_4.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{9CAB402C-1D37-44B4-886D-FA4F36170A4C}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_3.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{B802058A-464A-42DB-BC10-B650D6F2586A}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_2.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{C1E3F122-A2EA-442C-854F-20D98F8357A1}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_1.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{C7338B95-52B8-4542-AA79-42EB016C8C1C}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_4.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{CAC1105F-619B-4D04-831A-44E1CBF12D57}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_7.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{CECEC95A-D894-491A-BEE3-5E106FB59F2D}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_6.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{D06DF0D0-8518-441E-822F-5451D5C595B8}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_5.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{E180344B-AC83-4483-959E-18A5C56A5E19}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_3.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{E21A7345-EB21-468E-BE50-804DB97CF708}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_1.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{E48C5A3F-93EF-43BB-A092-2C7CEB946F27}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_6.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{F4769300-B949-4DF9-B333-00D33932E9A6}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_1.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{F5CA7B34-8055-42C0-B836-216129EB7E30}\\InprocServer32", null, "C:\\windows\\syswow64\\xaudio2_2.dll");
                } else {
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{074B110F-7F58-4743-AEA5-12F1B5074ED}\\InprocServer32", null, "C:\\windows\\system32\\xactengine3_5.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{0977D092-2D95-4E43-8D42-9DDCC2545ED5}\\InprocServer32", null, "C:\\windows\\system32\\xactengine3_4.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{0AA000AA-F404-11D9-BD7A-0010DC4F8F81}\\InprocServer32", null, "C:\\windows\\system32\\xactengine2_0.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{1138472B-D187-44E9-81F2-AE1B0E7785F1}\\InprocServer32", null, "C:\\windows\\system32\\xactengine2_3.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{1F1B577E-5E5A-4E8A-BA73-C657EA8E8598}\\InprocServer32", null, "C:\\windows\\system32\\xactengine2_1.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{248D8A3B-6256-44D3-A018-2AC96C459F47}\\InprocServer32", null, "C:\\windows\\system32\\xactengine3_6.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{343E68E6-8F82-4A8D-A2DA-6E9A944B378C}\\InprocServer32", null, "C:\\windows\\system32\\xactengine2_9.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{3A2495CE-31D0-435B-8CCF-E9F0843FD960}\\InprocServer32", null, "C:\\windows\\system32\\xactengine2_6.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{3B80EE2A-B0F5-4780-9E30-90CB39685B03}\\InprocServer32", null, "C:\\windows\\system32\\xactengine3_0.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{54B68BC7-3A45-416B-A8C9-19BF19EC1DF5}\\InprocServer32", null, "C:\\windows\\system32\\xactengine2_5.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{65D822A4-4799-42C6-9B18-D26CF66DD320}\\InprocServer32", null, "C:\\windows\\system32\\xactengine2_10.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{77C56BF4-18A1-42B0-88AF-5072CE814949}\\InprocServer32", null, "C:\\windows\\system32\\xactengine2_8.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{94C1AFFA-66E7-4961-9521-CFDEF3128D4F}\\InprocServer32", null, "C:\\windows\\system32\\xactengine3_3.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{962F5027-99BE-4692-A468-85802CF8DE61}\\InprocServer32", null, "C:\\windows\\system32\\xactengine3_1.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{BC3E0FC6-2E0D-4C45-BC61-D9C328319BD8}\\InprocServer32", null, "C:\\windows\\system32\\xactengine2_4.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{BCC782BC-6492-4C22-8C35-F5D72FE73C6E}\\InprocServer32", null, "C:\\windows\\system32\\xactengine3_7.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{C60FAE90-4183-4A3F-B2F7-AC1DC49B0E5C}\\InprocServer32", null, "C:\\windows\\system32\\xactengine2_2.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{CD0D66EC-8057-43F5-ACBD-66DFB36FD78C}\\InprocServer32", null, "C:\\windows\\system32\\xactengine2_7.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{D3332F02-3DD0-4DE9-9AEC-20D85C4111B6}\\InprocServer32", null, "C:\\windows\\system32\\xactengine3_2.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{03219E78-5BC3-44D1-B92E-F63D89CC6526}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_4.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{2139E6DA-C341-4774-9AC3-B4E026347F64}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_5.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{3EDA9B49-2085-498B-9BB2-39A6778493DE}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_6.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{4C5E637A-16C7-4DE3-9C46-5ED22181962D}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_3.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{4C9B6DDE-6809-46E6-A278-9B6A97588670}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_5.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{5A508685-A254-4FBA-9B82-9A24B00306AF}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_7.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{629CF0DE-3ECC-41E7-9926-F7E43EEBEC51}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_2.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{6A93130E-1D53-41D1-A9CF-E758800BB179}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_7.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{8BB7778B-645B-4475-9A73-1DE3170BD3AF}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_4.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{9CAB402C-1D37-44B4-886D-FA4F36170A4C}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_3.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{B802058A-464A-42DB-BC10-B650D6F2586A}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_2.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{C1E3F122-A2EA-442C-854F-20D98F8357A1}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_1.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{C7338B95-52B8-4542-AA79-42EB016C8C1C}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_4.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{CAC1105F-619B-4D04-831A-44E1CBF12D57}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_7.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{CECEC95A-D894-491A-BEE3-5E106FB59F2D}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_6.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{D06DF0D0-8518-441E-822F-5451D5C595B8}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_5.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{E180344B-AC83-4483-959E-18A5C56A5E19}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_3.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{E21A7345-EB21-468E-BE50-804DB97CF708}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_1.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{E48C5A3F-93EF-43BB-A092-2C7CEB946F27}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_6.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{F4769300-B949-4DF9-B333-00D33932E9A6}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_1.dll");
                    registryEditor.setStringValue("Software\\Classes\\Wow6432Node\\CLSID\\{F5CA7B34-8055-42C0-B836-216129EB7E30}\\InprocServer32", null, "C:\\windows\\system32\\xaudio2_2.dll");
                }
            }
        }
    }

    // ── Startup services (single source of truth) ──────────────────────────────────────────────
    // The 23 services the startup-selection feature manages, as "RawName:DefaultStartValue"
    // (2=auto / 3=manual = ON; the writer flips a service OFF by writing 4 = disabled). The ORDER is
    // load-bearing: STARTUP_SERVICE_LABELS below is parallel, and both Custom toggle lists (container
    // + shortcut settings) render this list, so they can never drift from what changeServicesStatus
    // actually writes.
    public static final String[] STARTUP_SERVICES = { "BITS:3", "Eventlog:2", "FontCache:3", "FontCache3.0.0.0:3", "HTTP:3", "LanmanServer:3", "MSIServer:3", "NDIS:2", "nsiproxy:3", "PlugPlay:2", "RpcSs:3", "scardsvr:3", "Schedule:3", "SharedGpuResources:2", "Spooler:3", "StiSvc:3", "TermService:3", "TrkWks:3", "W32Time:3", "winebus:2", "winehid:2", "Winmgmt:3", "wuauserv:3"};
    // Friendly names, parallel to STARTUP_SERVICES (same order). The Custom UI shows "Label (RawName)".
    public static final String[] STARTUP_SERVICE_LABELS = { "Background Intelligent Transfer", "Windows Event Log", "Windows Font Cache", ".NET Font Cache", "HTTP Service", "Server (file sharing)", "Windows Installer", "NDIS network driver", "Network Store Interface", "Plug and Play", "Remote Procedure Call (RPC)", "Smart Card", "Task Scheduler", "Shared GPU Resources", "Print Spooler", "Windows Image Acquisition", "Remote Desktop Services", "Distributed Link Tracking", "Windows Time", "Wine Bus — gamepad/HID", "Wine HID — gamepad/HID", "Windows Management Instrumentation (WMI)", "Windows Update"};

    // Raw service name from a "RawName:DefaultStartValue" entry.
    public static String startupServiceRawName(String entry) {
        int i = entry.indexOf(":");
        return i >= 0 ? entry.substring(0, i) : entry;
    }

    // Tolerant parse of a Custom-startup enabled CSV into a set of raw service names (trims, drops
    // blanks) so a hand-edited container JSON or an imported config can't throw here.
    public static java.util.Set<String> parseStartupServicesCsv(String csv) {
        java.util.Set<String> set = new java.util.HashSet<>();
        if (csv == null) return set;
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) set.add(t);
        }
        return set;
    }

    public static void changeServicesStatus(Container container, String startupSelection) {
        changeServicesStatus(container, startupSelection, null);
    }

    public static void changeServicesStatus(Container container, String startupSelection, String customEnabledCsv) {
        final String[] services = {"BITS:3", "Eventlog:2", "HTTP:3", "LanmanServer:3", "NDIS:2", "PlugPlay:2", "RpcSs:3", "scardsvr:3", "Schedule:3", "Spooler:3", "StiSvc:3", "TermService:3", "winebus:2", "winehid:2", "Winmgmt:3", "wuauserv:3"};
        final String[] aggressiveServices = STARTUP_SERVICES;
        File systemRegFile = new File(container.getRootDir(), ".wine/system.reg");

        byte selection = Container.STARTUP_SELECTION_NORMAL;
        try {
            selection = Byte.parseByte(startupSelection);
        }
        catch (NumberFormatException e) {}

        // Custom: only names present in the enabled CSV keep their ON default; everything else is
        // disabled. Note winebus/winehid follow the CSV like any other service here (unlike the
        // presets, which force them on) — the user is warned in the UI.
        java.util.Set<String> customEnabled = parseStartupServicesCsv(customEnabledCsv);

        try (WineRegistryEditor registryEditor = new WineRegistryEditor(systemRegFile)) {
            registryEditor.setCreateKeyIfNotExist(false);
            List<String> servicesList = Arrays.asList(services);

            for (String service : aggressiveServices) {
                String name = service.substring(0, service.indexOf(":"));
                int value = Character.getNumericValue(service.charAt(service.length() - 1));

                if (selection == Container.STARTUP_SELECTION_ESSENTIAL) {
                    if (servicesList.contains(service)) {
                        if (!name.equals("winebus") && !name.equals("winehid")) {
                            value = 4;
                        }
                    }
                } else if (selection == Container.STARTUP_SELECTION_AGGRESSIVE) {
                    if (!name.equals("winebus") && !name.equals("winehid")) {
                        value = 4;
                    }
                } else if (selection == Container.STARTUP_SELECTION_CUSTOM) {
                    if (!customEnabled.contains(name)) {
                        value = 4;
                    }
                }
                registryEditor.setDwordValue("System\\CurrentControlSet\\Services\\" + name, "Start", value);
                registryEditor.setDwordValue("System\\ControlSet001\\Services\\" + name, "Start", value);
            }
        }
    }

    /**
     * Configure Wine DirectInput joystick registry keys for all gamepads (core.JoystickRegistry): no
     * entry when DirectInput games may see the controller (core.DInputVisibility), "override" when
     * that is forced for the launch, "disabled" when they may not.
     *
     * Wine matches these entries by the pad's DirectInput name, which follows the container's
     * Controller identity, so the names come from {@link ControllerIdentity#dinputNames} (for the
     * Generic identity the eight "Generic HID Gamepad N" / "ric HID Gamepad N" values this always
     * wrote). Names belonging to the other identities are removed, so switching identity between
     * launches leaves no stale hide/show rule behind. Visible writes none, as the old "Exclusive Input
     * off" did: "override" makes Wine's DirectInput open its internal XInput HID device for the pad,
     * which some DirectInput games did not take (see JoystickRegistry).
     *
     * @param container The container to configure
     * @param dinputVisible Whether DirectInput games may see the controller
     * @param forced Visibility is forced on for this launch (controller passthrough, Classic DirectInput pad)
     * @param identity The launch's effective Controller identity (ControllerIdentity value)
     */
    public static void setJoystickRegistryKeys(Container container, boolean dinputVisible, boolean forced, String identity) {
        File userRegFile = new File(container.getRootDir(), ".wine/user.reg");
        // The same builder the in-game change uses (LiveWineRegistry), so launch and live never disagree.
        JoystickRegistry.Desired desired = JoystickRegistry.desired(identity, dinputVisible, forced);
        try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
            for (String stale : desired.delete) registryEditor.removeValue(JoystickRegistry.KEY, stale);
            for (Map.Entry<String, String> e : desired.set.entrySet()) {
                registryEditor.setStringValue(JoystickRegistry.KEY, e.getKey(), e.getValue());
            }
        }
    }

    /**
     * winebus options (HKLM\System\CurrentControlSet\Services\winebus, read by
     * dlls/winebus.sys/main.c), written before every Wine launch:
     * <ul>
     *   <li>"Map Controllers": absent = Wine's default, mapped. Written as 0 only while the Controller
     *       identity wants the pads presented unmapped (the Classic DirectInput pad), removed again
     *       otherwise so a container goes back to Wine's default as soon as the identity changes back.</li>
     *   <li>"DisableHidraw" = 1, always. winebus prefers hidraw for some ids (DualShock 4 / DualSense)
     *       and drops the same pad arriving any other way ("ignoring non-hidraw device"), which made
     *       the PlayStation identities lose the pad. PROTON_DISABLE_HIDRAW cannot fix that: winebus runs
     *       in winedevice.exe, whose Windows environment services.exe builds from the registry only
     *       (CreateEnvironmentBlock(..., FALSE)), and winebus reads it from that environment. The option
     *       is read per device, so it applies to every pad. No downside: WinHub's layers are built
     *       --with-sdl --without-udev, so there is no hidraw bus to disable; every pad comes through SDL.</li>
     * </ul>
     */
    public static void setWinebusOptions(Container container, boolean mapControllers) {
        File systemRegFile = new File(container.getRootDir(), ".wine/system.reg");
        try (WineRegistryEditor registryEditor = new WineRegistryEditor(systemRegFile)) {
            registryEditor.setCreateKeyIfNotExist(false);
            for (String key : new String[]{"System\\CurrentControlSet\\Services\\winebus", "System\\ControlSet001\\Services\\winebus"}) {
                if (mapControllers) registryEditor.removeValue(key, "Map Controllers");
                else registryEditor.setDwordValue(key, "Map Controllers", 0);
                registryEditor.setDwordValue(key, "DisableHidraw", 1);
            }
        }
    }
}

