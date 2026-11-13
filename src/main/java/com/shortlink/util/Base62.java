package com.shortlink.util;

public class Base62 {

    private static final String CHARS =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     * 把数字 id 转成 62 进制字符串
     * 例：1 -> "1"，62 -> "10"，3843 -> "ZZ"
     */
    public static String encode(long id) {
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(CHARS.charAt((int) (id % 62)));
            id /= 62;
        }
        return sb.reverse().toString();
    }

    public static void main(String[] args) {
        for (long i = 1; i <= 5; i++) {
            System.out.println(i + " -> " + encode(i));
        }
        System.out.println(62 + " -> " + encode(62));
        System.out.println(3843 + " -> " + encode(3843));
    }
}