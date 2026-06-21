package com.skillsprint.dto.request.community;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateBlacklistKeywordRequest {

    @Size(max = 100, message = "Từ khóa tối đa 100 ký tự")
    private String keyword;
}
