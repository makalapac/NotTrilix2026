package com.nottrilix.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** What the customer says or types is matched against the stored serial without caring about hyphens, spaces or case. */
class VoucherSerialTest {

    @Test
    void compactIgnoresHyphensSpacesAndCase() {
        assertThat(VoucherSerial.compact("STM-25-003")).isEqualTo("STM25003");
        assertThat(VoucherSerial.compact("stm 25 003")).isEqualTo("STM25003");
        assertThat(VoucherSerial.compact("STM25003")).isEqualTo("STM25003");
        assertThat(VoucherSerial.compact(" a1-10-001. ")).isEqualTo("A110001");
        assertThat(VoucherSerial.compact(null)).isEmpty();
    }
}
