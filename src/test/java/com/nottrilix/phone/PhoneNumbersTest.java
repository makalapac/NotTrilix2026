package com.nottrilix.phone;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Caller ids and stored numbers are compared as international digits; logs get a masked form. */
class PhoneNumbersTest {

    @Test
    void digitsNormaliseSpellings() {
        assertThat(PhoneNumbers.digits("+385 91 111 2222")).isEqualTo("385911112222");
        assertThat(PhoneNumbers.digits("385911112222")).isEqualTo("385911112222");
        assertThat(PhoneNumbers.digits("0038591 111 2222")).isEqualTo("385911112222");
        assertThat(PhoneNumbers.digits("091/111-2222")).isEqualTo("385911112222");
        assertThat(PhoneNumbers.digits(null)).isEmpty();
    }

    @Test
    void maskKeepsOnlyTheEnds() {
        assertThat(PhoneNumbers.mask("+385 91 111 2222")).isEqualTo("38591***222");
        assertThat(PhoneNumbers.mask("")).isEqualTo("(bez broja)");
        assertThat(PhoneNumbers.mask("1234")).isEqualTo("***");
    }
}
