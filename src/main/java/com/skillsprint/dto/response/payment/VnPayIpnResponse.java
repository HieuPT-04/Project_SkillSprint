package com.skillsprint.dto.response.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VnPayIpnResponse {

    @JsonProperty("RspCode")
    String rspCode;

    @JsonProperty("Message")
    String message;
}