package com.fuoverflow.common.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentParserTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 | Chrome 126 | Windows",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36 | Chrome 125 | macOS",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0 | Firefox 128 | Windows",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15 | Safari 17 | macOS",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0 | Edge 126 | Windows",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36 | Chrome 126 | Android",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1 | Safari 17 | iOS",
    })
    void parse_knownBrowsers(String ua, String expectedBrowser, String expectedOs) {
        UserAgentParser.DeviceLabel label = UserAgentParser.parse(ua);
        assertThat(label.browser()).isEqualTo(expectedBrowser);
        assertThat(label.os()).isEqualTo(expectedOs);
        assertThat(label.label()).isEqualTo(expectedBrowser + " on " + expectedOs);
    }

    @Test
    void parse_nullUserAgent_returnsUnknown() {
        UserAgentParser.DeviceLabel label = UserAgentParser.parse(null);
        assertThat(label.browser()).isEqualTo("Unknown browser");
        assertThat(label.os()).isEqualTo("Unknown OS");
    }

    @Test
    void parse_emptyUserAgent_returnsUnknown() {
        UserAgentParser.DeviceLabel label = UserAgentParser.parse("");
        assertThat(label.browser()).isEqualTo("Unknown browser");
        assertThat(label.os()).isEqualTo("Unknown OS");
    }

    @Test
    void parse_unknownUserAgent_returnsUnknown() {
        UserAgentParser.DeviceLabel label = UserAgentParser.parse("curl/7.88.1");
        assertThat(label.browser()).isEqualTo("Unknown browser");
        assertThat(label.os()).isEqualTo("Unknown OS");
    }
}
