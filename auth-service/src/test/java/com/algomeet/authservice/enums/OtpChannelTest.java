package com.algomeet.authservice.enums;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OtpChannelTest {
  @Test void parse_email() { assertThat(OtpChannel.parse("EMAIL")).isEqualTo(OtpChannel.EMAIL); }
  @Test void parse_phone_alias() { assertThat(OtpChannel.parse("PHONE")).isEqualTo(OtpChannel.SMS); }
  @Test void externalType_email() { assertThat(OtpChannel.EMAIL.externalType()).isEqualTo("EMAIL"); }
  @Test void externalType_sms_as_phone() { assertThat(OtpChannel.SMS.externalType()).isEqualTo("PHONE"); }
  @Test void parse_invalid_throws() {
    assertThatThrownBy(() -> OtpChannel.parse("FAX")).isInstanceOf(IllegalArgumentException.class);
  }
}