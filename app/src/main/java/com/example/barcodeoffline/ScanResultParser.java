package com.example.barcodeoffline;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 扫描结果结构化解析器
 * 支持: URL, 电话, 邮件, WiFi, vCard, 纯文本, ISBN
 */
public class ScanResultParser {

    public enum ContentType {
        URL, PHONE, EMAIL, WIFI, VCARD, SMS, GEO, ISBN, PLAIN_TEXT
    }

    public static class ParsedResult {
        public final ContentType type;
        public final String displayType;
        public final String rawValue;
        public final String friendlyValue;
        public final String actionLabel;

        ParsedResult(ContentType type, String displayType, String rawValue, String friendlyValue, String actionLabel) {
            this.type = type;
            this.displayType = displayType;
            this.rawValue = rawValue;
            this.friendlyValue = friendlyValue;
            this.actionLabel = actionLabel;
        }
    }

    public static ParsedResult parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new ParsedResult(ContentType.PLAIN_TEXT, "文本", "", "", "复制");
        }
        String trimmed = raw.trim();

        // WiFi: WIFI:T:WPA;S:MyNetwork;P:password;;
        if (trimmed.toUpperCase().startsWith("WIFI:")) {
            return parseWifi(trimmed);
        }
        // vCard
        if (trimmed.toUpperCase().startsWith("BEGIN:VCARD")) {
            return parseVCard(trimmed);
        }
        // vEvent / Calendar
        if (trimmed.toUpperCase().startsWith("BEGIN:VEVENT")) {
            return new ParsedResult(ContentType.PLAIN_TEXT, "日历事件", trimmed, trimmed, "复制");
        }
        // URL
        if (isUrl(trimmed)) {
            return new ParsedResult(ContentType.URL, "网址", trimmed, trimmed, "打开链接");
        }
        // EMAIL: mailto:xxx or xxx@xxx.xxx
        if (trimmed.toLowerCase().startsWith("mailto:") || isEmail(trimmed)) {
            String email = trimmed.toLowerCase().startsWith("mailto:") ? trimmed.substring(7) : trimmed;
            return new ParsedResult(ContentType.EMAIL, "邮件", trimmed, email, "发送邮件");
        }
        // SMS: sms:xxx or smsto:xxx
        if (trimmed.toLowerCase().startsWith("sms:") || trimmed.toLowerCase().startsWith("smsto:")) {
            return new ParsedResult(ContentType.SMS, "短信", trimmed, trimmed, "发送短信");
        }
        // TEL: tel:xxx or +xxx
        if (trimmed.toLowerCase().startsWith("tel:") || isPhoneNumber(trimmed)) {
            String phone = trimmed.toLowerCase().startsWith("tel:") ? trimmed.substring(4) : trimmed;
            return new ParsedResult(ContentType.PHONE, "电话", trimmed, phone, "拨打电话");
        }
        // GEO: geo:lat,lng
        if (trimmed.toLowerCase().startsWith("geo:")) {
            return new ParsedResult(ContentType.GEO, "地理位置", trimmed, trimmed.substring(4), "打开地图");
        }
        // ISBN (13 digits starting with 978 or 979)
        if (isIsbn(trimmed)) {
            return new ParsedResult(ContentType.ISBN, "ISBN", trimmed, formatIsbn(trimmed), "搜索图书");
        }
        // Plain text
        return new ParsedResult(ContentType.PLAIN_TEXT, "文本", trimmed, trimmed, "复制");
    }

    /** 创建对应Intent */
    public static Intent createActionIntent(Context ctx, ParsedResult result) {
        switch (result.type) {
            case URL:
                String url = result.rawValue;
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url;
                }
                return new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            case PHONE:
                return new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + result.friendlyValue));
            case EMAIL:
                return new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + result.friendlyValue));
            case SMS:
                return new Intent(Intent.ACTION_SENDTO, Uri.parse(result.rawValue));
            case GEO:
                return new Intent(Intent.ACTION_VIEW, Uri.parse(result.rawValue));
            case ISBN:
                return new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://book.douban.com/subject_search?search_text=" + result.friendlyValue));
            default:
                return null;
        }
    }

    /** 获取WiFi SSID和密码 */
    public static String[] getWifiInfo(String raw) {
        String ssid = extractWifiField(raw, "S");
        String password = extractWifiField(raw, "P");
        return new String[]{ssid, password};
    }

    // ========== 内部方法 ==========

    private static ParsedResult parseWifi(String raw) {
        String ssid = extractWifiField(raw, "S");
        String password = extractWifiField(raw, "P");
        String type = extractWifiField(raw, "T");
        String friendly = "网络: " + ssid + "\n密码: " + password;
        if (type != null && !type.isEmpty()) {
            friendly = "加密: " + type + "\n" + friendly;
        }
        return new ParsedResult(ContentType.WIFI, "WiFi", raw, friendly, "复制密码");
    }

    private static String extractWifiField(String wifi, String field) {
        Pattern p = Pattern.compile(field + ":([^;]*)");
        Matcher m = p.matcher(wifi);
        return m.find() ? m.group(1) : "";
    }

    private static ParsedResult parseVCard(String raw) {
        String name = extractVCardField(raw, "FN");
        if (name.isEmpty()) name = extractVCardField(raw, "N");
        String tel = extractVCardField(raw, "TEL");
        String email = extractVCardField(raw, "EMAIL");
        StringBuilder friendly = new StringBuilder();
        if (!name.isEmpty()) friendly.append("姓名: ").append(name).append("\n");
        if (!tel.isEmpty()) friendly.append("电话: ").append(tel).append("\n");
        if (!email.isEmpty()) friendly.append("邮件: ").append(email);
        if (friendly.length() == 0) friendly.append(raw);
        return new ParsedResult(ContentType.VCARD, "联系人", raw, friendly.toString().trim(), "添加联系人");
    }

    private static String extractVCardField(String vcard, String field) {
        Pattern p = Pattern.compile(field + "[;:](.*)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(vcard);
        if (m.find()) {
            String val = m.group(1).trim();
            int nl = val.indexOf('\n');
            return nl > 0 ? val.substring(0, nl).trim() : val;
        }
        return "";
    }

    private static boolean isUrl(String s) {
        return s.matches("(?i)(https?://|www\\.).*") ||
               s.matches("(?i)[a-z0-9][-a-z0-9]*\\.[a-z]{2,}(/.*)?");
    }

    private static boolean isEmail(String s) {
        return s.matches("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    }

    private static boolean isPhoneNumber(String s) {
        String digits = s.replaceAll("[\\s\\-+()]", "");
        return digits.matches("\\d{7,15}");
    }

    private static boolean isIsbn(String s) {
        String digits = s.replaceAll("[\\s\\-]", "");
        return digits.matches("97[89]\\d{10}");
    }

    private static String formatIsbn(String s) {
        String d = s.replaceAll("[\\s\\-]", "");
        if (d.length() == 13) {
            return d.substring(0, 3) + "-" + d.substring(3, 4) + "-" + d.substring(4, 6) +
                   "-" + d.substring(6, 12) + "-" + d.substring(12);
        }
        return d;
    }
}
