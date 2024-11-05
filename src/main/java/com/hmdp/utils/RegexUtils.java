package com.hmdp.utils;

import cn.hutool.core.text.CharSequenceUtil;
import com.hmdp.constants.RegexPatterns;

public class RegexUtils {

    private RegexUtils() {

    }

    /**
     * 是否是无效手机格式
     *
     * @param phone 要校验的手机号
     * @return true:符合，false：不符合
     */
    public static boolean isPhoneInvalid(String phone) {
        return mismatch(phone, RegexPatterns.PHONE_REGEX);
    }

    /**
     * 是否是无效邮箱格式
     *
     * @param email 要校验的邮箱
     * @return true:符合，false：不符合
     */
    public static boolean isEmailInvalid(String email) {
        return mismatch(email, RegexPatterns.EMAIL_REGEX);
    }

    /**
     * 是否是无效验证码格式
     *
     * @param code 要校验的验证码
     * @return true:符合，false：不符合
     */
    public static boolean isCodeInvalid(String code) {
        return mismatch(code, RegexPatterns.VERIFY_CODE_REGEX);
    }

    /**
     * 校验是否不符合正则格式
     *
     * @param str   待校验字符
     * @param regex 校验规则
     * @return true:符合，false：不符合
     */
    private static boolean mismatch(String str, String regex) {
        if (CharSequenceUtil.isBlank(str)) {
            return true;
        }
        return !str.matches(regex);
    }
}
