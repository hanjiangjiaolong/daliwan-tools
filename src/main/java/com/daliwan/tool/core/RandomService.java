package com.daliwan.tool.core;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 随机标识生成：UUID 与自定义字符集随机串。
 *
 * <p>使用 {@link SecureRandom} 而非 {@link java.util.Random}，生成的口令类字符串不可预测。
 * 全部为纯计算，不落盘、不联网。
 */
public final class RandomService {

    /** 单次生成的最大条数，避免一次刷出几十万行把界面拖死。 */
    public static final int MAX_COUNT = 1000;

    /** 单条随机串的最大长度。 */
    public static final int MAX_LENGTH = 4096;

    private static final String DIGITS = "0123456789";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{}<>?~";

    /** 手写或口头转述时容易混淆的字符，可选剔除：0/O/o、1/l/I。 */
    private static final String CONFUSING = "0Oo1lI|";

    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomService() {
    }

    /** 随机串的字符集与长度配置。 */
    public record Options(int length, boolean digits, boolean lower, boolean upper,
                          boolean symbols, boolean avoidConfusing) {
    }

    /**
     * 生成一个 UUID（v4，随机）。
     *
     * @param hyphen 是否保留连字符
     * @param upper  是否转大写
     */
    public static String uuid(boolean hyphen, boolean upper) {
        String text = UUID.randomUUID().toString();
        if (!hyphen) {
            text = text.replace("-", "");
        }
        return upper ? text.toUpperCase(Locale.ROOT) : text;
    }

    /** 批量生成 UUID。 */
    public static List<String> uuids(int count, boolean hyphen, boolean upper) {
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(uuid(hyphen, upper));
        }
        return result;
    }

    /**
     * 按字符集配置生成一条随机串。
     *
     * @throws IllegalArgumentException 长度为非正数、超过上限，或所选字符集为空
     */
    public static String randomString(Options options) {
        char[] pool = poolOf(options);
        if (options.length() <= 0 || options.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("长度需在 1 ~ " + MAX_LENGTH + " 之间");
        }
        StringBuilder text = new StringBuilder(options.length());
        for (int i = 0; i < options.length(); i++) {
            text.append(pool[RANDOM.nextInt(pool.length)]);
        }
        return text.toString();
    }

    /** 批量生成随机串。 */
    public static List<String> randomStrings(int count, Options options) {
        char[] pool = poolOf(options);
        if (options.length() <= 0 || options.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("长度需在 1 ~ " + MAX_LENGTH + " 之间");
        }
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            StringBuilder text = new StringBuilder(options.length());
            for (int j = 0; j < options.length(); j++) {
                text.append(pool[RANDOM.nextInt(pool.length)]);
            }
            result.add(text.toString());
        }
        return result;
    }

    /** 该配置下的可选字符池，供界面预览与校验使用。 */
    public static String poolText(Options options) {
        return new String(poolOf(options));
    }

    private static char[] poolOf(Options options) {
        StringBuilder pool = new StringBuilder();
        if (options.digits()) {
            pool.append(DIGITS);
        }
        if (options.lower()) {
            pool.append(LOWER);
        }
        if (options.upper()) {
            pool.append(UPPER);
        }
        if (options.symbols()) {
            pool.append(SYMBOLS);
        }
        if (options.avoidConfusing()) {
            for (char c : CONFUSING.toCharArray()) {
                int index = pool.indexOf(String.valueOf(c));
                while (index >= 0) {
                    pool.deleteCharAt(index);
                    index = pool.indexOf(String.valueOf(c));
                }
            }
        }
        if (pool.length() == 0) {
            throw new IllegalArgumentException("请至少选择一种字符");
        }
        return pool.toString().toCharArray();
    }
}
