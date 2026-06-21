package com.skillsprint.dto.request.community;

import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCommunityPostRequest {

    @Size(max = 5000, message = "Nội dung bài viết tối đa 5000 ký tự")
    private String content;

    @Size(max = 10, message = "Tối đa 10 hashtag")
    private List<@Size(max = 50, message = "Hashtag tối đa 50 ký tự") String> hashtags;
}
